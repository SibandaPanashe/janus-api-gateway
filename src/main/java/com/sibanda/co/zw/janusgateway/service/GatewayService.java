package com.sibanda.co.zw.janusgateway.service;

import com.sibanda.co.zw.janusgateway.model.ClientProfile;
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

    public GatewayService(DynamicRuleService dynamicRuleService,
                          StringRedisTemplate redisTemplate,
                          KeyHashingService keyHashingService) {
        this.dynamicRuleService = dynamicRuleService;
        this.redisTemplate = redisTemplate;
        this.keyHashingService = keyHashingService;
    }

    public boolean processRequest(String apiKey) {
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
            return false;
        }

        saveClient(client);
        return true;
    }

    private ClientProfile resolveClient(String apiKey) {
        String keyHash = keyHashingService.hashKey(apiKey);
        String redisKey = "client:hash:" + keyHash;

        String plan = (String) redisTemplate.opsForHash().get(redisKey, "plan");
        if (plan == null) {
            plan = "free";
        }

        String clientId = (String) redisTemplate.opsForHash().get(redisKey, "clientId");
        if (clientId == null) {
            clientId = keyHash.substring(0, 12);
        }

        String limitStr = (String) redisTemplate.opsForHash().get(redisKey, "limitPerSecond");
        int limitPerSecond = 10;
        if (limitStr != null) {
            try {
                limitPerSecond = Integer.parseInt(limitStr);
            } catch (NumberFormatException ignored) {}
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

    private void saveClient(ClientProfile client) {
        String redisKey = "client:hash:" + client.getApiKeyHash();
        redisTemplate.opsForHash().put(redisKey, "plan", client.getPlan());
        redisTemplate.opsForHash().put(redisKey, "blocked", String.valueOf(client.isBlocked()));
        redisTemplate.expire(redisKey, 5, TimeUnit.MINUTES);
    }
}