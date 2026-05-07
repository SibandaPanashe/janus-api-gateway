package com.sibanda.co.zw.janusgateway.service;

import com.sibanda.co.zw.janusgateway.model.ClientProfile;
import org.kie.api.runtime.KieContainer;
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

    private final KieContainer kieContainer;
    private final StringRedisTemplate redisTemplate;
    private final KeyHashingService keyHashingService;

    // Update constructor:
    public GatewayService(KieContainer kieContainer,
                          StringRedisTemplate redisTemplate,
                          KeyHashingService keyHashingService) {
        this.kieContainer = kieContainer;
        this.redisTemplate = redisTemplate;
        this.keyHashingService = keyHashingService;
    }

    public boolean processRequest(String apiKey) {
        ClientProfile client = resolveClient(apiKey);
        log.info("[Janus] Resolved client: plan={}, blocked={}", client.getPlan(), client.isBlocked());

        incrementCounters(client);
        log.info("[Janus] After increment: requestCountSecond={}", client.getRequestCountSecond());

        KieSession session = kieContainer.newKieSession();
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
        // Hash the incoming API key for lookup
        String keyHash = keyHashingService.hashKey(apiKey);
        String redisKey = "client:hash:" + keyHash;

        String plan = (String) redisTemplate.opsForHash().get(redisKey, "plan");
        if (plan == null) {
            plan = "free";
        }

        String clientId = (String) redisTemplate.opsForHash().get(redisKey, "clientId");
        if (clientId == null) {
            clientId = keyHash.substring(0, 12); // Truncated hash as fallback ID
        }

        return ClientProfile.builder()
                .clientId(clientId)
                .apiKeyHash(keyHash)  // Now stores the hash, not the raw key
                .plan(plan)
                .requestCountSecond(0)
                .requestCountMinute(0)
                .limitPerSecond(10)
                .limitPerMinute(100)
                .blocked(false)
                .surchargeAmount(0.0)
                .lastResetTime(Instant.now())
                .build();
    }

    private void incrementCounters(ClientProfile client) {
        long now = Instant.now().toEpochMilli();
        String windowKey = "usage:" + client.getClientId() + ":second";

        // Add current request with timestamp as score
        redisTemplate.opsForZSet().add(windowKey, String.valueOf(now), now);

        // Remove entries older than 1 second (1000 ms)
        long oneSecondAgo = now - 1000;
        redisTemplate.opsForZSet().removeRangeByScore(windowKey, 0, oneSecondAgo);

        // Count remaining entries (requests in the last 1 second)
        Long count = redisTemplate.opsForZSet().zCard(windowKey);

        // Set TTL so Redis cleans up stale keys
        redisTemplate.expire(windowKey, 5, TimeUnit.SECONDS);

        client.setRequestCountSecond(count != null ? count.intValue() : 0);
        log.info("[Janus] Sliding window key={}, count={}", windowKey, count);
    }

    private void saveClient(ClientProfile client) {
        String redisKey = "client:" + client.getClientId();
        redisTemplate.opsForHash().put(redisKey, "plan", client.getPlan());
        redisTemplate.opsForHash().put(redisKey, "blocked", String.valueOf(client.isBlocked()));
        redisTemplate.expire(redisKey, 5, TimeUnit.MINUTES);
    }
}