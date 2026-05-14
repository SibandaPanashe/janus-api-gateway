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
@RequestMapping("/api/billing")
public class BillingController {

    private final StringRedisTemplate redisTemplate;
    private final ClientRepository clientRepository;
    private final EventLogRepository eventLogRepository;
    private final KeyHashingService keyHashingService;
    private final JwtService jwtService;

    public BillingController(StringRedisTemplate redisTemplate,
                             ClientRepository clientRepository,
                             EventLogRepository eventLogRepository,
                             KeyHashingService keyHashingService,
                             JwtService jwtService) {
        this.redisTemplate = redisTemplate;
        this.clientRepository = clientRepository;
        this.eventLogRepository = eventLogRepository;
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

    @GetMapping("/plan")
    public ResponseEntity<Map<String, Object>> getPlan(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {

        String clientId = resolveClientId(authHeader, apiKey);
        String redisKey = "client:hash:" + (apiKey != null ? keyHashingService.hashKey(apiKey) : "");
        String plan = (String) redisTemplate.opsForHash().get(redisKey, "plan");
        if (plan == null) plan = "free";

        var entity = clientRepository.findByClientId(clientId);
        String currentPlan = entity.map(e -> e.getPlan()).orElse(plan);

        return ResponseEntity.ok(Map.of(
                "currentPlan", currentPlan,
                "plans", List.of(
                        Map.of("name", "Free", "price", "$0/month", "rateLimit", "10 req/s", "apiKeys", 1,
                                "features", List.of("Community support", "Basic analytics")),
                        Map.of("name", "Pro", "price", "$49/month", "rateLimit", "100 req/s", "apiKeys", 10,
                                "features", List.of("Email support", "Advanced analytics", "Custom rules")),
                        Map.of("name", "Enterprise", "price", "Custom", "rateLimit", "1,000 req/s", "apiKeys", -1,
                                "features", List.of("Priority support", "Unlimited keys", "SLA guarantee", "Custom integrations"))
                )
        ));
    }

    @GetMapping("/history")
    public ResponseEntity<Map<String, Object>> getHistory(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {

        return ResponseEntity.ok(Map.of(
                "invoices", List.of(
                        Map.of("date", "2026-05-01", "description", "Free Plan - Monthly", "amount", "$0.00", "status", "active"),
                        Map.of("date", "2026-04-01", "description", "Free Plan - Monthly", "amount", "$0.00", "status", "paid")
                ),
                "totalSpent", "$0.00"
        ));
    }
}