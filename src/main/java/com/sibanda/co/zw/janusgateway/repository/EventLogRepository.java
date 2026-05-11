package com.sibanda.co.zw.janusgateway.repository;

import com.sibanda.co.zw.janusgateway.entity.EventLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface EventLogRepository extends JpaRepository<EventLogEntity, Long> {
    List<EventLogEntity> findByClientIdAndCreatedAtAfterOrderByCreatedAtDesc(
            String clientId, Instant since);
    long countByClientIdAndEventTypeAndCreatedAtAfter(
            String clientId, String eventType, Instant since);
    List<EventLogEntity> findByEventTypeAndCreatedAtAfterOrderByCreatedAtDesc(
            String eventType, Instant since);
}