package com.sibanda.co.zw.janusgateway.service;

import com.sibanda.co.zw.janusgateway.model.ClientProfile;
import com.sibanda.co.zw.janusgateway.repository.ClientRepository;
import org.kie.api.runtime.KieSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Service
public class GatewayService {

    private static final Logger log = LoggerFactory.getLogger(GatewayService.class);

    private final DynamicRuleService dynamicRuleService;
    private final StringRedisTemplate redisTemplate;
    private final KeyHashingService keyHashingService;
    private final ClientRepository clientRepository;

    public record RequestResult(
            boolean allowed,
            String clientId,
            String plan,
            int requestCount
    ) {}

    public GatewayService(DynamicRuleService dynamicRuleService,
                          StringRedisTemplate redisTemplate,
                          KeyHashingService keyHashingService,
                          ClientRepository clientRepository) {
        this.dynamicRuleService = dynamicRuleService;
        this.redisTemplate = redisTemplate;
        this.keyHashingService = keyHashingService;
        this.clientRepository = clientRepository;
    }

    public RequestResult processRequest(String apiKey) {
        ClientProfile client = resolveClient(apiKey);
        log.info("[Janus] Resolved client: plan={}, blocked={}", client.getPlan(), client.isBlocked());

        incrementCounters(client);
        log.info("[Janus] After increment: requestCountSecond={}", client.getRequestCountSecond());

        KieSession session = dynamicRuleService.getKieContainer().newKieSession();
        session.insert(client);
        int rulesFired = session.fireAllRules();
        session.dispose();

        log.info("[Janus] Rules fired: {}, blocked after rules: {}", rulesFired, client.isBlocked());

        if (client.isBlocked()) {
            log.warn("[Janus] REQUEST DENIED for client: {}", client.getClientId());
            return new RequestResult(false, client.getClientId(), client.getPlan(), client.getRequestCountSecond());
        }

        saveClient(client);
        return new RequestResult(true, client.getClientId(), client.getPlan(), client.getRequestCountSecond());
    }

    private ClientProfile resolveClient(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new com.sibanda.co.zw.janusgateway.handler.UnauthorizedException("Missing API key");
        }

        String keyHash = keyHashingService.hashKey(apiKey);
        String redisKey = "client:hash:" + keyHash;

        String plan     = (String) redisTemplate.opsForHash().get(redisKey, "plan");
        String clientId = (String) redisTemplate.opsForHash().get(redisKey, "clientId");
        String limitStr = (String) redisTemplate.opsForHash().get(redisKey, "limitPerSecond");

        if (plan == null || clientId == null) {
            var clientEntity = clientRepository.findByApiKeyHash(keyHash);
            if (clientEntity.isEmpty()) {
                throw new com.sibanda.co.zw.janusgateway.handler.UnauthorizedException("Invalid API key");
            }
            var entity = clientEntity.get();
            plan     = entity.getPlan();
            clientId = entity.getClientId();
            limitStr = String.valueOf(entity.getRateLimitPerSecond());

            redisTemplate.opsForHash().put(redisKey, "clientId", clientId);
            redisTemplate.opsForHash().put(redisKey, "plan", plan);
            redisTemplate.opsForHash().put(redisKey, "limitPerSecond", limitStr);
            redisTemplate.expire(redisKey, 365, TimeUnit.DAYS);

            log.info("[Janus] Repopulated Redis from PostgreSQL for client: {}", clientId);
        }

        int limitPerSecond = 10;
        if (limitStr != null) {
            try { limitPerSecond = Integer.parseInt(limitStr); }
            catch (NumberFormatException ignored) {}
        }

        return ClientProfile.builder()
                .clientId(clientId)
                .apiKeyHash(keyHash)
                .plan(plan)
                .requestCountSecond(0)
                .requestCountMinute(0)
                .limitPerSecond(limitPerSecond)
                .limitPerMinute(limitPerSecond * 10)
                .blocked(false)
                .surchargeAmount(0.0)
                .lastResetTime(Instant.now())
                .build();
    }

    private void incrementCounters(ClientProfile client) {
        long now = Instant.now().toEpochMilli();
        String windowKey = "usage:" + client.getClientId() + ":second";

        redisTemplate.opsForZSet().add(windowKey, String.valueOf(now), now);
        long oneSecondAgo = now - 1000;
        redisTemplate.opsForZSet().removeRangeByScore(windowKey, 0, oneSecondAgo);
        Long count = redisTemplate.opsForZSet().zCard(windowKey);
        redisTemplate.expire(windowKey, 5, TimeUnit.SECONDS);

        client.setRequestCountSecond(count != null ? count.intValue() : 0);
        log.info("[Janus] Sliding window key={}, count={}", windowKey, count);
    }


    public RequestResult processJwtRequest(String clientId) {
        ClientProfile client = loadClientById(clientId);
        log.info("[Janus] JWT-resolved client: plan={}, blocked={}", client.getPlan(), client.isBlocked());

        incrementCounters(client);

        KieSession session = dynamicRuleService.getKieContainer().newKieSession();
        session.insert(client);
        int rulesFired = session.fireAllRules();
        session.dispose();

        log.info("[Janus] Rules fired: {}, blocked after rules: {}", rulesFired, client.isBlocked());

        if (client.isBlocked()) {
            log.warn("[Janus] REQUEST DENIED for JWT client: {}", client.getClientId());
            return new RequestResult(false, client.getClientId(), client.getPlan(), client.getRequestCountSecond());
        }

        saveClient(client);
        return new RequestResult(true, client.getClientId(), client.getPlan(), client.getRequestCountSecond());
    }

    private ClientProfile loadClientById(String clientId) {
        var entity = clientRepository.findByClientId(clientId);
        if (entity.isEmpty()) {
            throw new com.sibanda.co.zw.janusgateway.handler.UnauthorizedException("Unknown client: " + clientId);
        }
        var e = entity.get();
        int limitPerSecond = limitForPlan(e.getPlan());
        return ClientProfile.builder()
                .clientId(e.getClientId())
                .apiKeyHash(e.getApiKeyHash())
                .plan(e.getPlan())
                .requestCountSecond(0)
                .requestCountMinute(0)
                .limitPerSecond(limitPerSecond)
                .limitPerMinute(limitPerSecond * 10)
                .blocked(false)
                .surchargeAmount(0.0)
                .lastResetTime(Instant.now())
                .build();
    }

    private int limitForPlan(String plan) {
        if (plan == null) return 10;
        return switch (plan) {
            case "free" -> 10;
            case "pro" -> 100;
            case "enterprise" -> 1000;
            case "pay-as-you-go" -> Integer.MAX_VALUE;
            default -> 10;
        };
    }

    private void saveClient(ClientProfile client) {
        String redisKey = "client:hash:" + client.getApiKeyHash();
        redisTemplate.opsForHash().put(redisKey, "plan", client.getPlan());
        redisTemplate.opsForHash().put(redisKey, "blocked", String.valueOf(client.isBlocked()));
        redisTemplate.expire(redisKey, 5, TimeUnit.MINUTES);
    }
}