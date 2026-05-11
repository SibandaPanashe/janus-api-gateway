package com.sibanda.co.zw.janusgateway.controller;

import com.sibanda.co.zw.janusgateway.service.KeyHashingService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import com.sibanda.co.zw.janusgateway.entity.ClientEntity;
import com.sibanda.co.zw.janusgateway.repository.ClientRepository;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/admin/keys")
public class KeyManagementController {

    private final KeyHashingService keyHashingService;
    private final StringRedisTemplate redisTemplate;

    private final ClientRepository clientRepository;

    // Update constructor:
    public KeyManagementController(KeyHashingService keyHashingService,
                                   StringRedisTemplate redisTemplate,
                                   ClientRepository clientRepository) {
        this.keyHashingService = keyHashingService;
        this.redisTemplate = redisTemplate;
        this.clientRepository = clientRepository;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createApiKey(
            @RequestBody Map<String, String> request
    ) {
        String plan = request.getOrDefault("plan", "free");
        String clientId = request.getOrDefault("clientId", "client-" + System.currentTimeMillis());

        String apiKey = keyHashingService.generateApiKey();
        String keyHash = keyHashingService.hashKey(apiKey);

        int limitPerSecond = switch (plan) {
            case "pro" -> 100;
            case "enterprise" -> 1000;
            default -> 10;
        };

        // Persist to PostgreSQL
        ClientEntity entity = ClientEntity.builder()
                .clientId(clientId)
                .apiKeyHash(keyHash)
                .plan(plan)
                .rateLimitPerSecond(limitPerSecond)
                .rateLimitPerMinute(limitPerSecond * 10)
                .blocked(false)
                .surchargeBalance(0.0)
                .build();
        clientRepository.save(entity);

        // Also keep Redis for fast runtime lookups
        String redisKey = "client:hash:" + keyHash;
        redisTemplate.opsForHash().put(redisKey, "clientId", clientId);
        redisTemplate.opsForHash().put(redisKey, "plan", plan);
        redisTemplate.opsForHash().put(redisKey, "limitPerSecond", String.valueOf(limitPerSecond));
        redisTemplate.opsForHash().put(redisKey, "created", String.valueOf(System.currentTimeMillis()));
        redisTemplate.expire(redisKey, 365, TimeUnit.DAYS);

        return ResponseEntity.ok(Map.of(
                "apiKey", apiKey,
                "maskedKey", keyHashingService.maskKey(apiKey),
                "plan", plan,
                "clientId", clientId,
                "persisted", true,
                "warning", "Store this key securely. It will not be shown again."
        ));
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listKeys() {
        // In production: query PostgreSQL, not Redis scan
        // For now: return count of stored keys
        return ResponseEntity.ok(Map.of(
                "message", "Key listing requires PostgreSQL integration (next epic)"
        ));
    }
}