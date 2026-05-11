package com.sibanda.co.zw.janusgateway.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long accessTokenTtl;
    private final long refreshTokenTtl;

    public JwtService(
            @Value("${janus.jwt.secret}") String secret,
            @Value("${janus.jwt.access-token-ttl}") long accessTokenTtl,
            @Value("${janus.jwt.refresh-token-ttl}") long refreshTokenTtl) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtl = accessTokenTtl;
        this.refreshTokenTtl = refreshTokenTtl;
    }

    /**
     * Generate an access token after validating the API key.
     */
    public String generateAccessToken(String clientId, String plan, String keyHash) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenTtl);

        String safeKeyHash = keyHash != null ? keyHash.substring(0, Math.min(16, keyHash.length())) : "unknown";

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(clientId)
                .claim("plan", plan)
                .claim("keyHash", safeKeyHash)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    /**
     * Generate a refresh token (long-lived, stores keyHash for lookup during refresh).
     */
    public String generateRefreshToken(String clientId, String keyHash) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + refreshTokenTtl);

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(clientId)
                .claim("type", "refresh")
                .claim("keyHash", keyHash)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    /**
     * Validate and parse a JWT. Returns null if invalid.
     */
    public Claims validateToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Extract client profile from a validated JWT.
     */
    public Map<String, String> extractClientInfo(String token) {
        Claims claims = validateToken(token);
        if (claims == null) {
            return null;
        }
        return Map.of(
                "clientId", claims.getSubject(),
                "plan", claims.get("plan", String.class),
                "jti", claims.getId()
        );
    }
}