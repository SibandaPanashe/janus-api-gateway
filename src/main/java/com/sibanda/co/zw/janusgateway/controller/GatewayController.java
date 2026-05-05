package com.sibanda.co.zw.janusgateway.controller;

import com.sibanda.co.zw.janusgateway.service.GatewayService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class GatewayController {

    private final GatewayService gatewayService;

    public GatewayController(GatewayService gatewayService) {
        this.gatewayService = gatewayService;
    }

    /**
     * This is the endpoint your Nginx edge will proxy to.
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
}