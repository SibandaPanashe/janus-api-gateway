package com.sibanda.co.zw.janusgateway.controller;

import com.sibanda.co.zw.janusgateway.repository.ClientRepository;
import com.sibanda.co.zw.janusgateway.repository.EventLogRepository;
import com.sibanda.co.zw.janusgateway.service.JwtService;
import com.sibanda.co.zw.janusgateway.service.KeyHashingService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final EventLogRepository eventLogRepository;
    private final StringRedisTemplate redisTemplate;
    private final ClientRepository clientRepository;
    private final KeyHashingService keyHashingService;
    private final JwtService jwtService;

    public AnalyticsController(EventLogRepository eventLogRepository,
                               StringRedisTemplate redisTemplate,
                               ClientRepository clientRepository,
                               KeyHashingService keyHashingService,
                               JwtService jwtService) {
        this.eventLogRepository = eventLogRepository;
        this.redisTemplate = redisTemplate;
        this.clientRepository = clientRepository;
        this.keyHashingService = keyHashingService;
        this.jwtService = jwtService;
    }

    private String resolveClientId(String authHeader, String apiKey) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            var claims = jwtService.validateToken(authHeader.substring(7));
            if (claims != null) return claims.getSubject();
        }
        if (apiKey != null) {
            String keyHash = keyHashingService.hashKey(apiKey);
            String clientId = (String) redisTemplate.opsForHash().get("client:hash:" + keyHash, "clientId");
            if (clientId == null) {
                var entity = clientRepository.findByApiKeyHash(keyHash);
                if (entity.isPresent()) clientId = entity.get().getClientId();
            }
            return clientId != null ? clientId : "unknown";
        }
        return "unknown";
    }

    @GetMapping("/volume")
    public ResponseEntity<Map<String, Object>> getVolume(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {

        String clientId = resolveClientId(authHeader, apiKey);
        Instant now = Instant.now();
        List<Map<String, Object>> hourlyData = new ArrayList<>();

        for (int i = 23; i >= 0; i--) {
            Instant hourStart = now.minus(i + 1, ChronoUnit.HOURS);
            Instant hourEnd = now.minus(i, ChronoUnit.HOURS);
            long count = eventLogRepository.findAll().stream()
                    .filter(e -> clientId.equals(e.getClientId()))
                    .filter(e -> e.getCreatedAt().isAfter(hourStart) && e.getCreatedAt().isBefore(hourEnd))
                    .count();
            hourlyData.add(Map.of(
                    "hour", hourEnd.toString(),
                    "requests", count,
                    "allowed", count,
                    "blocked", 0
            ));
        }

        long totalRequests = hourlyData.stream().mapToLong(h -> (long) h.get("requests")).sum();

        return ResponseEntity.ok(Map.of(
                "totalRequests", totalRequests,
                "hourlyData", hourlyData
        ));
    }

    @GetMapping("/latency")
    public ResponseEntity<Map<String, Object>> getLatency(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {

        return ResponseEntity.ok(Map.of(
                "p50", 8,
                "p90", 15,
                "p99", 32,
                "p999", 45,
                "avg", 12,
                "samples", List.of(
                        Map.of("percentile", "P50", "value", 8),
                        Map.of("percentile", "P90", "value", 15),
                        Map.of("percentile", "P99", "value", 32),
                        Map.of("percentile", "P999", "value", 45)
                )
        ));
    }

    @GetMapping("/status-codes")
    public ResponseEntity<Map<String, Object>> getStatusCodes(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {

        String clientId = resolveClientId(authHeader, apiKey);
        Instant twentyFourHoursAgo = Instant.now().minus(24, ChronoUnit.HOURS);

        long allowed = eventLogRepository.findAll().stream()
                .filter(e -> clientId.equals(e.getClientId()))
                .filter(e -> e.getCreatedAt().isAfter(twentyFourHoursAgo))
                .filter(e -> e.getResponseCode() == 200)
                .count();

        long rateLimited = eventLogRepository.findAll().stream()
                .filter(e -> clientId.equals(e.getClientId()))
                .filter(e -> e.getCreatedAt().isAfter(twentyFourHoursAgo))
                .filter(e -> e.getResponseCode() == 429)
                .count();

        long total = allowed + rateLimited;

        return ResponseEntity.ok(Map.of(
                "total", total,
                "breakdown", List.of(
                        Map.of("code", 200, "label", "200 OK", "count", allowed,
                                "percentage", total > 0 ? Math.round((double) allowed / total * 1000) / 10.0 : 0),
                        Map.of("code", 429, "label", "429 Rate Limited", "count", rateLimited,
                                "percentage", total > 0 ? Math.round((double) rateLimited / total * 1000) / 10.0 : 0)
                )
        ));
    }

    @GetMapping("/rate-limits")
    public ResponseEntity<Map<String, Object>> getRateLimits(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {

        String clientId = resolveClientId(authHeader, apiKey);
        Instant twentyFourHoursAgo = Instant.now().minus(24, ChronoUnit.HOURS);

        var events = eventLogRepository.findAll().stream()
                .filter(e -> clientId.equals(e.getClientId()))
                .filter(e -> "RATE_LIMITED".equals(e.getEventType()))
                .filter(e -> e.getCreatedAt().isAfter(twentyFourHoursAgo))
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .limit(50)
                .map(e -> Map.of(
                        "timestamp", e.getCreatedAt().toString(),
                        "endpoint", e.getEndpoint() != null ? e.getEndpoint() : "/api/v1/proxy",
                        "plan", e.getPlanAtTime() != null ? e.getPlanAtTime() : "free",
                        "requestCount", e.getRequestCount() != null ? e.getRequestCount() : 0
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
                "total", events.size(),
                "events", events
        ));
    }
}