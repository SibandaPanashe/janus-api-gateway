package com.sibanda.co.zw.janusgateway.service;

import com.sibanda.co.zw.janusgateway.entity.EventLogEntity;
import com.sibanda.co.zw.janusgateway.repository.EventLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

@Service
public class EventLogService {

    private static final Logger log = LoggerFactory.getLogger(EventLogService.class);
    private final EventLogRepository eventLogRepository;

    public EventLogService(EventLogRepository eventLogRepository) {
        this.eventLogRepository = eventLogRepository;
    }

    /**
     * Log a gateway event to both the structured log and PostgreSQL.
     */
    public void logEvent(String clientId, String eventType, String endpoint,
                         int responseCode, String plan, Integer requestCount, String metadata) {
        // Structured log entry
        MDC.put("clientId", clientId);
        MDC.put("plan", plan);
        MDC.put("responseCode", String.valueOf(responseCode));

        switch (eventType) {
            case "RATE_LIMITED" ->
                    log.warn("[AUDIT] RATE_LIMITED client={} plan={} count={} endpoint={}",
                            clientId, plan, requestCount, endpoint);
            case "REQUEST_ALLOWED" ->
                    log.debug("[AUDIT] REQUEST_ALLOWED client={} plan={} endpoint={}",
                            clientId, plan, endpoint);
            default ->
                    log.info("[AUDIT] {} client={} plan={} endpoint={}",
                            eventType, clientId, plan, endpoint);
        }

        // Persist to PostgreSQL for audit trail
        EventLogEntity entity = EventLogEntity.builder()
                .clientId(clientId)
                .eventType(eventType)
                .endpoint(endpoint)
                .responseCode(responseCode)
                .planAtTime(plan)
                .requestCount(requestCount)
                .metadata(metadata)
                .build();
        eventLogRepository.save(entity);
    }
}