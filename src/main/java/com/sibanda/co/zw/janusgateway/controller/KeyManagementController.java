package com.sibanda.co.zw.janusgateway.controller;

import com.sibanda.co.zw.janusgateway.service.KeyHashingService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import com.sibanda.co.zw.janusgateway.entity.ClientEntity;
import com.sibanda.co.zw.janusgateway.repository.ClientRepository;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/keys")
public class KeyManagementController {

    private final KeyHashingService keyHashingService;
    private final StringRedisTemplate redisTemplate;
    private final ClientRepository clientRepository;

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

        // FIX: Always generate a unique clientId for every new key
        // This prevents the 409 Conflict when generating multiple keys
        String clientId = "client-" + System.currentTimeMillis();

        String apiKey = keyHashingService.generateApiKey();
        String keyHash = keyHashingService.hashKey(apiKey);

        int limitPerSecond = switch (plan) {
            case "pro" -> 100;
            case "enterprise" -> 1000;
            default -> 10;
        };

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
    public ResponseEntity<List<Map<String, Object>>> listKeys() {
        List<ClientEntity> clients = clientRepository.findAll();

        List<Map<String, Object>> response = clients.stream().map(client -> {
            String hash = client.getApiKeyHash();
            String maskedKey = "sk-..." + (hash != null && hash.length() > 4 ? hash.substring(hash.length() - 4) : "****");

            return Map.<String, Object>of(
                    "id", client.getClientId(),
                    "clientId", client.getClientId(),
                    "maskedKey", maskedKey,
                    "plan", client.getPlan() != null ? client.getPlan().toUpperCase() : "FREE",
                    "status", client.isBlocked() ? "Blocked" : "Active",
                    "createdAt", Instant.now().toString()
            );
        }).collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{clientId}")
    public ResponseEntity<Map<String, Object>> revokeKey(@PathVariable String clientId) {
        var clientOpt = clientRepository.findByClientId(clientId);
        if (clientOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        ClientEntity client = clientOpt.get();
        clientRepository.delete(client);

        String redisKey = "client:hash:" + client.getApiKeyHash();
        redisTemplate.delete(redisKey);

        return ResponseEntity.ok(Map.of(
                "message", "Key successfully revoked",
                "clientId", clientId
        ));
    }
}