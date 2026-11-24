package com.sibanda.co.zw.janusgateway.controller;

import com.sibanda.co.zw.janusgateway.entity.ClientEntity;
import com.sibanda.co.zw.janusgateway.entity.ServiceEntity;
import com.sibanda.co.zw.janusgateway.repository.ClientRepository;
import com.sibanda.co.zw.janusgateway.repository.ServiceRepository;
import com.sibanda.co.zw.janusgateway.service.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class GatewayController {

    private static final Logger log = LoggerFactory.getLogger(GatewayController.class);

    private final GatewayService gatewayService;
    private final JwtService jwtService;
    private final EventLogService eventLogService;
    private final StringRedisTemplate redisTemplate;
    private final ClientRepository clientRepository;
    private final ServiceRepository serviceRepository; // <--- ADDED THIS
    private final KeyHashingService keyHashingService;
    private final UsageBroadcastService usageBroadcastService;
    private final RestTemplate restTemplate = new RestTemplate();

    public GatewayController(GatewayService gatewayService,
                             JwtService jwtService,
                             EventLogService eventLogService,
                             StringRedisTemplate redisTemplate,
                             ClientRepository clientRepository,
                             ServiceRepository serviceRepository, // <--- ADDED THIS
                             KeyHashingService keyHashingService,
                             UsageBroadcastService usageBroadcastService) {
        this.gatewayService = gatewayService;
        this.jwtService = jwtService;
        this.eventLogService = eventLogService;
        this.redisTemplate = redisTemplate;
        this.clientRepository = clientRepository;
        this.serviceRepository = serviceRepository; // <--- ADDED THIS
        this.keyHashingService = keyHashingService;
        this.usageBroadcastService = usageBroadcastService;
    }

    @GetMapping("/proxy/**")
    public ResponseEntity<?> handleProxyRequest(
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            HttpServletRequest request) {

        GatewayService.RequestResult result = gatewayService.processRequest(apiKey);

        if (!result.allowed()) {
            eventLogService.logEvent(result.clientId(), "RATE_LIMITED",
                    request.getRequestURI(), 429, result.plan(), result.requestCount(), null);
            usageBroadcastService.pushRateLimitEvent(
                    result.clientId(), request.getRequestURI(),
                    result.requestCount(),
                    result.plan().equals("free") ? 10 : 100);
            return ResponseEntity.status(429).body(Map.of(
                    "error", "rate_limit_exceeded",
                    "message", "You have exceeded your plan's rate limit. Upgrade at dashboard.janus.local"
            ));
        }

        // --- FIX: Fetch the backend URL from the Services table ---
        String backendUrl = null;
        List<ServiceEntity> services = serviceRepository.findByClientId(result.clientId());
        if (!services.isEmpty()) {
            backendUrl = services.get(0).getBackendUrl(); // Get the first registered service
        }

        if (backendUrl == null || backendUrl.isBlank()) {
            eventLogService.logEvent(result.clientId(), "REQUEST_ALLOWED",
                    request.getRequestURI(), 200, result.plan(), result.requestCount(), null);
            return ResponseEntity.ok(Map.of(
                    "status", "allowed",
                    "message", "Request processed successfully",
                    "note", "No backend configured for this client. Set your API URL in the dashboard."
            ));
        }

        String path = request.getRequestURI().replace("/api/v1/proxy", "");
        String queryString = request.getQueryString();
        String targetUrl = backendUrl + path + (queryString != null ? "?" + queryString : "");

        try {
            log.info("[Proxy] Forwarding to: {}", targetUrl);
            ResponseEntity<String> response = restTemplate.getForEntity(targetUrl, String.class);
            eventLogService.logEvent(result.clientId(), "REQUEST_ALLOWED",
                    request.getRequestURI(), response.getStatusCode().value(),
                    result.plan(), result.requestCount(), "proxied=true");
            return ResponseEntity.status(response.getStatusCode())
                    .headers(response.getHeaders())
                    .body(response.getBody());
        } catch (Exception e) {
            log.warn("[Proxy] Backend unreachable: {}", targetUrl, e);
            return ResponseEntity.status(502).body(Map.of(
                    "error", "backend_unreachable",
                    "message", "Could not reach your backend at " + backendUrl
            ));
        }
    }

    @GetMapping("/proxy/jwt")
    public ResponseEntity<Map<String, Object>> handleJwtRequest(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    Map.of("error", "Missing or invalid Authorization header"));
        }

        String token = authHeader.substring(7);
        var clientInfo = jwtService.extractClientInfo(token);

        if (clientInfo == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    Map.of("error", "Invalid or expired token"));
        }

        String clientId = clientInfo.get("clientId");
        GatewayService.RequestResult result = gatewayService.processJwtRequest(clientId);

        if (!result.allowed()) {
            eventLogService.logEvent(clientId, "RATE_LIMITED",
                    "/api/v1/proxy/jwt", 429, result.plan(), result.requestCount(), "auth=jwt");
            usageBroadcastService.pushRateLimitEvent(
                    clientId, "/api/v1/proxy/jwt",
                    result.requestCount(),
                    result.plan().equals("free") ? 10 : 100);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
                    "error", "rate_limit_exceeded",
                    "message", "You have exceeded your plan's rate limit. Upgrade at dashboard.janus.local"));
        }

        eventLogService.logEvent(clientId, "REQUEST_ALLOWED",
                "/api/v1/proxy/jwt", 200, result.plan(), result.requestCount(), "auth=jwt");
        return ResponseEntity.ok(Map.of(
                "status", "allowed",
                "message", "Request processed successfully",
                "auth", "jwt"));
    }
}