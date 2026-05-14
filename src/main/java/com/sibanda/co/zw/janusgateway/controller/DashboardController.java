package com.sibanda.co.zw.janusgateway.controller;

import com.sibanda.co.zw.janusgateway.repository.ClientRepository;
import com.sibanda.co.zw.janusgateway.repository.EventLogRepository;
import com.sibanda.co.zw.janusgateway.service.JwtService;
import com.sibanda.co.zw.janusgateway.service.KeyHashingService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final StringRedisTemplate redisTemplate;
    private final EventLogRepository eventLogRepository;
    private final ClientRepository clientRepository;
    private final KeyHashingService keyHashingService;
    private final JwtService jwtService;

    public DashboardController(StringRedisTemplate redisTemplate,
                               EventLogRepository eventLogRepository,
                               ClientRepository clientRepository,
                               KeyHashingService keyHashingService,
                               JwtService jwtService) {
        this.redisTemplate = redisTemplate;
        this.eventLogRepository = eventLogRepository;
        this.clientRepository = clientRepository;
        this.keyHashingService = keyHashingService;
        this.jwtService = jwtService;
    }

    /**
     * Resolve client ID from either Authorization: Bearer (JWT) or X-API-Key header.
     */
    private String resolveClientId(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {

        // Try JWT first
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            var claims = jwtService.validateToken(token);
            if (claims != null) {
                return claims.getSubject();
            }
        }

        // Fall back to API key
        if (apiKey != null && !apiKey.isBlank()) {
            String keyHash = keyHashingService.hashKey(apiKey);
            String redisKey = "client:hash:" + keyHash;
            String clientId = (String) redisTemplate.opsForHash().get(redisKey, "clientId");
            if (clientId == null) {
                var entity = clientRepository.findByApiKeyHash(keyHash);
                if (entity.isPresent()) {
                    clientId = entity.get().getClientId();
                }
            }
            return clientId != null ? clientId : "unknown";
        }

        return "unknown";
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {

        String clientId = resolveClientId(authHeader, apiKey);

        long requestsToday = eventLogRepository.findAll().stream()
                .filter(e -> clientId.equals(e.getClientId()))
                .filter(e -> e.getCreatedAt().isAfter(Instant.now().minusSeconds(86400)))
                .count();

        long rateLimited = eventLogRepository.findAll().stream()
                .filter(e -> clientId.equals(e.getClientId()))
                .filter(e -> "RATE_LIMITED".equals(e.getEventType()))
                .filter(e -> e.getCreatedAt().isAfter(Instant.now().minusSeconds(86400)))
                .count();

        double errorRate = requestsToday > 0
                ? (double) rateLimited / requestsToday * 100
                : 0;

        return ResponseEntity.ok(Map.of(
                "requestsToday", requestsToday,
                "avgLatency", 78,
                "errorRate", Math.round(errorRate * 100.0) / 100.0,
                "requestsTrend", "+4.5%",
                "latencyTrend", "+1.9%",
                "errorTrend", "+0.11%"
        ));
    }

    @GetMapping("/usage")
    public ResponseEntity<Map<String, Object>> getUsage(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {

        String clientId = resolveClientId(authHeader, apiKey);

        long monthlyRequests = eventLogRepository.findAll().stream()
                .filter(e -> clientId.equals(e.getClientId()))
                .filter(e -> "REQUEST_ALLOWED".equals(e.getEventType()))
                .filter(e -> e.getCreatedAt().isAfter(Instant.now().minusSeconds(2592000)))
                .count();

        return ResponseEntity.ok(Map.of(
                "requestsThisMonth", monthlyRequests,
                "limit", 10000,
                "percentUsed", Math.round((double) monthlyRequests / 10000 * 1000) / 10.0
        ));
    }

    @GetMapping("/endpoints")
    public ResponseEntity<List<Map<String, Object>>> getTopEndpoints(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {

        return ResponseEntity.ok(List.of(
                Map.of("endpoint", "/api/v1/chat", "requests", 2341),
                Map.of("endpoint", "/api/v1/embed", "requests", 1823),
                Map.of("endpoint", "/api/v1/complete", "requests", 987),
                Map.of("endpoint", "/api/v1/models", "requests", 654),
                Map.of("endpoint", "/api/v1/files", "requests", 321)
        ));
    }

    @GetMapping("/keys")
    public ResponseEntity<List<Map<String, Object>>> getApiKeys(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {

        String clientId = resolveClientId(authHeader, apiKey);

        // In production: query clientRepository for keys belonging to this user
        return ResponseEntity.ok(List.of(
                Map.of(
                        "name", "Production",
                        "maskedKey", "sk-prod...xyz",
                        "created", "12 days ago",
                        "environment", "production"
                ),
                Map.of(
                        "name", "Staging",
                        "maskedKey", "sk-stg...abc",
                        "created", "3 days ago",
                        "environment", "staging"
                )
        ));
    }
}