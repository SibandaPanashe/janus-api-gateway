package com.sibanda.co.zw.janusgateway.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "services")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ServiceEntity {
    @Id
    private UUID id;

    @Column(name = "client_id", nullable = false)
    private String clientId;

    @Column(nullable = false)
    private String name;

    @Column(name = "backend_url", nullable = false)
    private String backendUrl;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID(); // Generate ID in Java
        if (createdAt == null) createdAt = Instant.now();
    }
}