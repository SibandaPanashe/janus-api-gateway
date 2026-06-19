package com.sibanda.co.zw.janusgateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sibanda.co.zw.janusgateway.handler.DashboardWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Random;

@Service
public class UsageBroadcastService {

    private static final Logger log = LoggerFactory.getLogger(UsageBroadcastService.class);

    private final DashboardWebSocketHandler webSocketHandler;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Random random = new Random();

    public UsageBroadcastService(DashboardWebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }

    /**
     * Push simulated real-time usage data to all connected clients every 2 seconds.
     */
    @Scheduled(fixedRate = 2000)
    public void broadcastUsage() {
        int connections = webSocketHandler.getConnectionCount();
        if (connections == 0) return;

        try {
            String message = objectMapper.writeValueAsString(Map.of(
                    "type", "usage_update",
                    "timestamp", Instant.now().toString(),
                    "requestsThisSecond", random.nextInt(15),
                    "requestsThisMinute", random.nextInt(200),
                    "allowed", random.nextInt(180),
                    "blocked", random.nextInt(20),
                    "activeConnections", connections
            ));

            webSocketHandler.broadcast(message);
            log.debug("[Usage] Broadcast to {} connections", connections);
        } catch (Exception e) {
            log.error("[Usage] Broadcast failed", e);
        }
    }

    /**
     * Push real data for a specific client after a rate limit event.
     */
    public void pushRateLimitEvent(String clientId, String endpoint, int requestCount, int limit) {
        try {
            String message = objectMapper.writeValueAsString(Map.of(
                    "type", "rate_limit_event",
                    "timestamp", Instant.now().toString(),
                    "clientId", clientId,
                    "endpoint", endpoint,
                    "requestCount", requestCount,
                    "limit", limit,
                    "message", "Rate limit exceeded"
            ));
            webSocketHandler.pushToClient(clientId, message);
        } catch (Exception e) {
            log.error("[Usage] Push failed for client {}", clientId, e);
        }
    }
}