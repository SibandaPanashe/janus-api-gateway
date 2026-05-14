package com.sibanda.co.zw.janusgateway.controller;

import com.sibanda.co.zw.janusgateway.entity.ClientEntity;
import com.sibanda.co.zw.janusgateway.entity.UserEntity;
import com.sibanda.co.zw.janusgateway.repository.ClientRepository;
import com.sibanda.co.zw.janusgateway.repository.UserRepository;
import com.sibanda.co.zw.janusgateway.service.JwtService;
import com.sibanda.co.zw.janusgateway.service.KeyHashingService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final JwtService jwtService;
    private final KeyHashingService keyHashingService;
    private final StringRedisTemplate redisTemplate;
    private final ClientRepository clientRepository;
    private final UserRepository userRepository;

    public AuthController(JwtService jwtService,
                          KeyHashingService keyHashingService,
                          StringRedisTemplate redisTemplate,
                          ClientRepository clientRepository,
                          UserRepository userRepository) {
        this.jwtService = jwtService;
        this.keyHashingService = keyHashingService;
        this.redisTemplate = redisTemplate;
        this.clientRepository = clientRepository;
        this.userRepository = userRepository;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String password = request.get("password");

        if (email == null || password == null || email.isBlank() || password.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "email and password required"));
        }

        if (userRepository.existsByEmail(email)) {
            return ResponseEntity.status(409).body(Map.of("error", "email already registered"));
        }

        String passwordHash = keyHashingService.hashKey(password);
        String apiKey = keyHashingService.generateApiKey();
        String keyHash = keyHashingService.hashKey(apiKey);
        String clientId = "user-" + UUID.randomUUID().toString().substring(0, 8);

        UserEntity user = UserEntity.builder()
                .email(email)
                .passwordHash(passwordHash)
                .clientId(clientId)
                .plan("free")
                .build();
        userRepository.save(user);

        ClientEntity clientEntity = ClientEntity.builder()
                .clientId(clientId)
                .apiKeyHash(keyHash)
                .plan("free")
                .rateLimitPerSecond(10)
                .rateLimitPerMinute(100)
                .build();
        clientRepository.save(clientEntity);

        String redisKey = "client:hash:" + keyHash;
        redisTemplate.opsForHash().put(redisKey, "clientId", clientId);
        redisTemplate.opsForHash().put(redisKey, "plan", "free");
        redisTemplate.opsForHash().put(redisKey, "limitPerSecond", "10");

        String accessToken = jwtService.generateAccessToken(clientId, "free", keyHash);
        String refreshToken = jwtService.generateRefreshToken(clientId, keyHash);

        return ResponseEntity.ok(Map.of(
                "accessToken", accessToken,
                "refreshToken", refreshToken,
                "apiKey", apiKey,
                "plan", "free",
                "email", email
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String password = request.get("password");

        if (email == null || password == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "email and password required"));
        }

        var user = userRepository.findByEmail(email);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "invalid email or password"));
        }

        String passwordHash = keyHashingService.hashKey(password);
        if (!passwordHash.equals(user.get().getPasswordHash())) {
            return ResponseEntity.status(401).body(Map.of("error", "invalid email or password"));
        }

        String clientId = user.get().getClientId();
        var clientEntity = clientRepository.findByClientId(clientId);
        if (clientEntity.isEmpty()) {
            return ResponseEntity.status(500).body(Map.of("error", "account configuration error"));
        }

        String keyHash = clientEntity.get().getApiKeyHash();
        String plan = user.get().getPlan();

        String accessToken = jwtService.generateAccessToken(clientId, plan, keyHash);
        String refreshToken = jwtService.generateRefreshToken(clientId, keyHash);

        return ResponseEntity.ok(Map.of(
                "accessToken", accessToken,
                "refreshToken", refreshToken,
                "plan", plan,
                "email", email
        ));
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

        if (clientId == null) {
            var entity = clientRepository.findByApiKeyHash(keyHash);
            if (entity.isPresent()) {
                clientId = entity.get().getClientId();
                plan = entity.get().getPlan();
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