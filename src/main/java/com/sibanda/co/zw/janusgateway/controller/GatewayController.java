package com.sibanda.co.zw.janusgateway.controller;

import com.sibanda.co.zw.janusgateway.service.EventLogService;
import com.sibanda.co.zw.janusgateway.service.GatewayService;
import com.sibanda.co.zw.janusgateway.service.JwtService;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class GatewayController {

    private final GatewayService gatewayService;
    private final JwtService jwtService;
    private final EventLogService eventLogService;

    public GatewayController(GatewayService gatewayService,
                             JwtService jwtService,
                             EventLogService eventLogService) {
        this.gatewayService = gatewayService;
        this.jwtService = jwtService;
        this.eventLogService = eventLogService;
    }

    @GetMapping("/proxy")
    public ResponseEntity<Map<String, Object>> handleRequest(
            @RequestHeader("X-API-Key") String apiKey
    ) {
        GatewayService.RequestResult result = gatewayService.processRequest(apiKey);

        if (!result.allowed()) {
            eventLogService.logEvent(
                    result.clientId(),
                    "RATE_LIMITED",
                    "/api/v1/proxy",
                    429,
                    result.plan(),
                    result.requestCount(),
                    null
            );
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(
                    Map.of(
                            "error", "rate_limit_exceeded",
                            "message", "You have exceeded your plan's rate limit. Upgrade at dashboard.janus.local"
                    )
            );
        }

        eventLogService.logEvent(
                result.clientId(),
                "REQUEST_ALLOWED",
                "/api/v1/proxy",
                200,
                result.plan(),
                result.requestCount(),
                null
        );
        return ResponseEntity.ok(
                Map.of(
                        "status", "allowed",
                        "message", "Request processed successfully"
                )
        );
    }

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
        GatewayService.RequestResult result = gatewayService.processRequest(clientId);

        if (!result.allowed()) {
            eventLogService.logEvent(
                    clientId,
                    "RATE_LIMITED",
                    "/api/v1/proxy/jwt",
                    429,
                    result.plan(),
                    result.requestCount(),
                    "auth=jwt"
            );
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(
                    Map.of(
                            "error", "rate_limit_exceeded",
                            "message", "You have exceeded your plan's rate limit. Upgrade at dashboard.janus.local"
                    )
            );
        }

        eventLogService.logEvent(
                clientId,
                "REQUEST_ALLOWED",
                "/api/v1/proxy/jwt",
                200,
                result.plan(),
                result.requestCount(),
                "auth=jwt"
        );
        return ResponseEntity.ok(
                Map.of(
                        "status", "allowed",
                        "message", "Request processed successfully",
                        "auth", "jwt"
                )
        );
    }
}