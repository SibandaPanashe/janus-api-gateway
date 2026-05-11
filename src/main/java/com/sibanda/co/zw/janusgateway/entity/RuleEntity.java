package com.sibanda.co.zw.janusgateway.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "rules")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleEntity {

    @Id
    private String id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "drl_content", nullable = false, columnDefinition = "TEXT")
    private String drlContent;

    @Column(nullable = false)
    private int priority;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(nullable = false)
    private int version;

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