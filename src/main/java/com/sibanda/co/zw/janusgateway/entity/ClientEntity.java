package com.sibanda.co.zw.janusgateway.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "clients")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

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

    @Column(name = "surcharge_balance")
    private double surchargeBalance;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}