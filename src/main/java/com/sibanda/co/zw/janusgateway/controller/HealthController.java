package com.sibanda.co.zw.janusgateway.controller;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
public class HealthController {

    private final StringRedisTemplate redisTemplate;

    public HealthController(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        boolean redisAlive = checkRedis();

        return Map.of(
                "status", redisAlive ? "UP" : "DEGRADED",
                "timestamp", Instant.now().toString(),
                "service", "Janus Gateway",
                "version", "0.0.1",
                "dependencies", Map.of(
                        "redis", redisAlive ? "connected" : "disconnected"
                )
        );
    }

    private boolean checkRedis() {
        try {
            String pong = redisTemplate.getConnectionFactory()
                    .getConnection()
                    .ping();
            return "PONG".equals(pong);
        } catch (Exception e) {
            return false;
        }
    }
}