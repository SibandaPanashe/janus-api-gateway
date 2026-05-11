package com.sibanda.co.zw.janusgateway.controller;

import com.sibanda.co.zw.janusgateway.repository.ClientRepository;
import com.sibanda.co.zw.janusgateway.service.JwtService;
import com.sibanda.co.zw.janusgateway.service.KeyHashingService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final JwtService jwtService;
    private final KeyHashingService keyHashingService;
    private final StringRedisTemplate redisTemplate;
    private final ClientRepository clientRepository;

    public AuthController(JwtService jwtService,
                          KeyHashingService keyHashingService,
                          StringRedisTemplate redisTemplate,
                          ClientRepository clientRepository) {
        this.jwtService = jwtService;
        this.keyHashingService = keyHashingService;
        this.redisTemplate = redisTemplate;
        this.clientRepository = clientRepository;
    }

    @PostMapping("/token")
    public ResponseEntity<?> exchangeApiKey(@RequestBody Map<String, String> request) {
        String apiKey = request.get("apiKey");
        if (apiKey == null || apiKey.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "apiKey is required"));
        }

        String keyHash = keyHashingService.hashKey(apiKey);
        String redisKey = "client:hash:" + keyHash;
        String clientId = (String) redisTemplate.opsForHash().get(redisKey, "clientId");
        String plan = (String) redisTemplate.opsForHash().get(redisKey, "plan");

        // Fallback to PostgreSQL if Redis misses
        if (clientId == null) {
            var entity = clientRepository.findByApiKeyHash(keyHash);
            if (entity.isPresent()) {
                clientId = entity.get().getClientId();
                plan = entity.get().getPlan();
                // Repopulate Redis
                redisTemplate.opsForHash().put(redisKey, "clientId", clientId);
                redisTemplate.opsForHash().put(redisKey, "plan", plan);
            } else {
                return ResponseEntity.status(401).body(Map.of("error", "Invalid API key"));
            }
        }

        String accessToken = jwtService.generateAccessToken(clientId, plan, keyHash);
        String refreshToken = jwtService.generateRefreshToken(clientId, keyHash);

        return ResponseEntity.ok(Map.of(
                "accessToken", accessToken,
                "refreshToken", refreshToken,
                "tokenType", "Bearer",
                "expiresIn", 3600,
                "plan", plan
        ));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@RequestBody Map<String, String> request) {
        String refreshToken = request.get("refreshToken");
        if (refreshToken == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "refreshToken is required"));
        }

        var claims = jwtService.validateToken(refreshToken);
        if (claims == null || !"refresh".equals(claims.get("type"))) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid refresh token"));
        }

        String clientId = claims.getSubject();
        String keyHash = claims.get("keyHash", String.class);

        String redisKey = "client:hash:" + keyHash;
        String plan = (String) redisTemplate.opsForHash().get(redisKey, "plan");
        if (plan == null) {
            plan = "free";
        }

        String newAccessToken = jwtService.generateAccessToken(clientId, plan, keyHash);
        String newRefreshToken = jwtService.generateRefreshToken(clientId, keyHash);

        return ResponseEntity.ok(Map.of(
                "accessToken", newAccessToken,
                "refreshToken", newRefreshToken,
                "tokenType", "Bearer",
                "expiresIn", 3600
        ));
    }
}