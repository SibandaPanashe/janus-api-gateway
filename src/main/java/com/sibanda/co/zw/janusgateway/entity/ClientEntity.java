package com.sibanda.co.zw.janusgateway.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "clients")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientEntity {

    @Id
    private UUID id; // Changed to UUID, removed @GeneratedValue

    @Column(name = "client_id", unique = true, nullable = false)
    private String clientId;

    @Column(name = "api_key_hash", unique = true, nullable = false, length = 64)
    private String apiKeyHash;

    @Column(nullable = false)
    private String plan;

    @Column(name = "rate_limit_per_second")
    private int rateLimitPerSecond;

    @Column(name = "rate_limit_per_minute")
    private int rateLimitPerMinute;

    @Column(name = "is_blocked")
    private boolean blocked;

    @Column(name = "backend_url", length = 500)
    private String backendUrl;

    @Column(name = "backend_name")
    private String backendName;

    @Column(name = "surcharge_balance")
    private double surchargeBalance;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID(); // Generate ID in Java
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}