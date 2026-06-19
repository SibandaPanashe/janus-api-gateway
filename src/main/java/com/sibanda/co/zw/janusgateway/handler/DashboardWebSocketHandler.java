package com.sibanda.co.zw.janusgateway.handler;

import com.sibanda.co.zw.janusgateway.service.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DashboardWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(DashboardWebSocketHandler.class);

    private final JwtService jwtService;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public DashboardWebSocketHandler(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String clientId = extractClientId(session);
        if (clientId == null) {
            log.warn("[WS] Connection rejected: no valid token");
            try { session.close(CloseStatus.POLICY_VIOLATION); } catch (IOException ignored) {}
            return;
        }

        sessions.put(clientId, session);
        log.info("[WS] Client connected: {} (total sessions: {})", clientId, sessions.size());

        try {
            session.sendMessage(new TextMessage(
                    "{\"type\":\"connected\",\"message\":\"Connected to Janus real-time stream\"}"
            ));
        } catch (IOException e) {
            log.error("[WS] Failed to send welcome message", e);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        log.debug("[WS] Received: {}", payload);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String clientId = extractClientId(session);
        if (clientId != null) {
            sessions.remove(clientId);
            log.info("[WS] Client disconnected: {} (total sessions: {})", clientId, sessions.size());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("[WS] Transport error for session {}", session.getId(), exception);
    }

    /**
     * Push a usage update to a specific client.
     */
    public void pushToClient(String clientId, String message) {
        WebSocketSession session = sessions.get(clientId);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(message));
            } catch (IOException e) {
                log.error("[WS] Failed to push to client {}", clientId, e);
            }
        }
    }

    /**
     * Broadcast to all connected clients.
     */
    public void broadcast(String message) {
        sessions.forEach((clientId, session) -> {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(message));
                } catch (IOException e) {
                    log.error("[WS] Broadcast failed for client {}", clientId);
                }
            }
        });
    }

    /**
     * Get count of connected sessions.
     */
    public int getConnectionCount() {
        return sessions.size();
    }

    private String extractClientId(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) return null;

        String query = uri.getQuery();
        if (query != null && query.contains("token=")) {
            String token = query.substring(query.indexOf("token=") + 6);
            if (token.contains("&")) token = token.substring(0, token.indexOf("&"));

            var claims = jwtService.validateToken(token);
            if (claims != null) {
                return claims.getSubject();
            }
        }
        return null;
    }
}