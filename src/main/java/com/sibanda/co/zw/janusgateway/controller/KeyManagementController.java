package com.sibanda.co.zw.janusgateway.controller;

import com.sibanda.co.zw.janusgateway.service.KeyHashingService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/admin/keys")
public class KeyManagementController {

    private final KeyHashingService keyHashingService;
    private final StringRedisTemplate redisTemplate;

    public KeyManagementController(KeyHashingService keyHashingService,
                                   StringRedisTemplate redisTemplate) {
        this.keyHashingService = keyHashingService;
        this.redisTemplate = redisTemplate;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createApiKey(
            @RequestBody Map<String, String> request
    ) {
        String plan = request.getOrDefault("plan", "free");
        String clientId = request.getOrDefault("clientId", "client-" + System.currentTimeMillis());

        // Generate new API key
        String apiKey = keyHashingService.generateApiKey();
        String keyHash = keyHashingService.hashKey(apiKey);

        // Store in Redis: client:hash:{hash} → profile
        String redisKey = "client:hash:" + keyHash;
        redisTemplate.opsForHash().put(redisKey, "clientId", clientId);
        redisTemplate.opsForHash().put(redisKey, "plan", plan);
        redisTemplate.opsForHash().put(redisKey, "created", String.valueOf(System.currentTimeMillis()));
        redisTemplate.expire(redisKey, 365, TimeUnit.DAYS);

        // Return the raw key ONCE — this is the only time it's visible
        return ResponseEntity.ok(Map.of(
                "apiKey", apiKey,
                "maskedKey", keyHashingService.maskKey(apiKey),
                "plan", plan,
                "clientId", clientId,
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