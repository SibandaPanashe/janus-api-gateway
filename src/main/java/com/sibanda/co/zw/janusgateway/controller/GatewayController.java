package com.sibanda.co.zw.janusgateway.controller;

import com.sibanda.co.zw.janusgateway.service.GatewayService;
import com.sibanda.co.zw.janusgateway.service.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class GatewayController {

    private final GatewayService gatewayService;
    private final JwtService jwtService;

    public GatewayController(GatewayService gatewayService, JwtService jwtService) {
        this.gatewayService = gatewayService;
        this.jwtService = jwtService;
    }

    /**
     * API Key authenticated endpoint.
     * Clients pass their API key in the X-API-Key header.
     */
    @GetMapping("/proxy")
    public ResponseEntity<Map<String, Object>> handleRequest(
            @RequestHeader("X-API-Key") String apiKey
    ) {
        boolean allowed = gatewayService.processRequest(apiKey);

        if (!allowed) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(
                    Map.of(
                            "error", "rate_limit_exceeded",
                            "message", "You have exceeded your plan's rate limit. Upgrade at dashboard.janus.local"
                    )
            );
        }

        return ResponseEntity.ok(
                Map.of(
                        "status", "allowed",
                        "message", "Request processed successfully"
                )
        );
    }

    /**
     * JWT-authenticated endpoint.
     * Clients pass Authorization: Bearer <token> header.
     */
    @GetMapping("/proxy/jwt")
    public ResponseEntity<Map<String, Object>> handleJwtRequest(
            @RequestHeader("Authorization") String authHeader
    ) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    Map.of("error", "Missing or invalid Authorization header")
            );
        }

        String token = authHeader.substring(7);
        var clientInfo = jwtService.extractClientInfo(token);

        if (clientInfo == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    Map.of("error", "Invalid or expired token")
            );
        }

        String clientId = clientInfo.get("clientId");
        boolean allowed = gatewayService.processRequest(clientId);

        if (!allowed) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(
                    Map.of(
                            "error", "rate_limit_exceeded",
                            "message", "You have exceeded your plan's rate limit. Upgrade at dashboard.janus.local"
                    )
            );
        }

        return ResponseEntity.ok(
                Map.of(
                        "status", "allowed",
                        "message", "Request processed successfully",
                        "auth", "jwt"
                )
        );
    }
}