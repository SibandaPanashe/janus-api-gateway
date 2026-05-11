package com.sibanda.co.zw.janusgateway.controller;

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

    public AuthController(JwtService jwtService,
                          KeyHashingService keyHashingService,
                          StringRedisTemplate redisTemplate) {
        this.jwtService = jwtService;
        this.keyHashingService = keyHashingService;
        this.redisTemplate = redisTemplate;
    }

    /**
     * Exchange an API key for a JWT access token + refresh token.
     */
    @PostMapping("/token")
    public ResponseEntity<?> exchangeApiKey(@RequestBody Map<String, String> request) {
        String apiKey = request.get("apiKey");
        if (apiKey == null || apiKey.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "apiKey is required"));
        }

        // Hash the API key and look up the client
        String keyHash = keyHashingService.hashKey(apiKey);
        String redisKey = "client:hash:" + keyHash;
        String clientId = (String) redisTemplate.opsForHash().get(redisKey, "clientId");
        String plan = (String) redisTemplate.opsForHash().get(redisKey, "plan");

        if (clientId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid API key"));
        }

        // Generate tokens
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

    /**
     * Use a refresh token to get a new access token.
     */
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

        // Look up current plan from Redis using the stored keyHash
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