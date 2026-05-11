package com.sibanda.co.zw.janusgateway.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "event_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id")
    private String clientId;

    @Column(name = "event_type", nullable = false)
    private String eventType;  // "REQUEST_ALLOWED", "RATE_LIMITED", "KEY_CREATED", "KEY_REVOKED"

    @Column(name = "endpoint")
    private String endpoint;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "response_code")
    private int responseCode;

    @Column(name = "plan_at_time")
    private String planAtTime;

    @Column(name = "request_count")
    private Integer requestCount;

    @Column(columnDefinition = "TEXT")
    private String metadata;  // JSON blob for extra context

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}