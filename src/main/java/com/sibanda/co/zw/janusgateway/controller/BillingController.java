package com.sibanda.co.zw.janusgateway.controller;

import com.sibanda.co.zw.janusgateway.repository.ClientRepository;
import com.sibanda.co.zw.janusgateway.service.JwtService;
import com.sibanda.co.zw.janusgateway.service.KeyHashingService;
import com.sibanda.co.zw.janusgateway.service.StripeService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/billing")
public class BillingController {

    private final StripeService stripeService;
    private final StringRedisTemplate redisTemplate;
    private final ClientRepository clientRepository;
    private final KeyHashingService keyHashingService;
    private final JwtService jwtService;

    public BillingController(StripeService stripeService,
                             StringRedisTemplate redisTemplate,
                             ClientRepository clientRepository,
                             KeyHashingService keyHashingService,
                             JwtService jwtService) {
        this.stripeService = stripeService;
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

    @PostMapping("/upgrade")
    public ResponseEntity<Map<String, Object>> createUpgradeSession(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestBody Map<String, String> body) {

        String clientId = resolveClientId(authHeader, apiKey);
        String email = body.getOrDefault("email", "customer@example.com");
        String plan = body.getOrDefault("plan", "pro");
        String successUrl = body.getOrDefault("successUrl", "http://localhost:5173/dashboard?upgraded=true");
        String cancelUrl = body.getOrDefault("cancelUrl", "http://localhost:5173/dashboard?cancelled=true");

        try {
            String checkoutUrl = stripeService.createCheckoutSession(clientId, email, successUrl, cancelUrl);
            return ResponseEntity.ok(Map.of("checkoutUrl", checkoutUrl));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Stripe session failed"));
        }
    }

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, String>> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {

        try {
            String clientId = stripeService.handleWebhook(payload, sigHeader);
            if (clientId != null) {
                // Upgrade the client to pro
                upgradeClientPlan(clientId, "pro");
            }
            return ResponseEntity.ok(Map.of("status", "ok"));
        } catch (Exception e) {
            return ResponseEntity.status(400).body(Map.of("error", "Webhook processing failed"));
        }
    }

    private void upgradeClientPlan(String clientId, String newPlan) {
        var entity = clientRepository.findByClientId(clientId);
        if (entity.isPresent()) {
            var client = entity.get();
            client.setPlan(newPlan);
            client.setRateLimitPerSecond(100);
            clientRepository.save(client);

            String redisKey = "client:hash:" + client.getApiKeyHash();
            redisTemplate.opsForHash().put(redisKey, "plan", newPlan);
            redisTemplate.opsForHash().put(redisKey, "limitPerSecond", "100");
        }
    }

    @GetMapping("/plan")
    public ResponseEntity<Map<String, Object>> getPlan(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {

        return ResponseEntity.ok(Map.of(
                "currentPlan", "free",
                "plans", java.util.List.of(
                        Map.of("name", "Free", "price", "$0/month", "rateLimit", "10 req/s"),
                        Map.of("name", "Pro", "price", "$49/month", "rateLimit", "100 req/s"),
                        Map.of("name", "Enterprise", "price", "Custom", "rateLimit", "1,000 req/s")
                )
        ));
    }
}