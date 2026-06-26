package com.brokerdealer.auditservice.kafka.consumer;

import com.brokerdealer.auditservice.domain.ActorType;
import com.brokerdealer.auditservice.domain.AuditLog;
import com.brokerdealer.auditservice.repository.AuditLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuditEventConsumer {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "order.state.changed",
                   groupId = "audit-service-order.state.changed-group")
    public void consumeOrderStateChanged(Map<String, Object> event) {
        try {
            UUID orderId = parseUuid(event.get("orderId"));
            if (orderId == null) {
                log.warn("order.state.changed skipped — missing orderId");
                return;
            }

            String toState = (String) event.get("toState");
            if (toState == null) {
                log.warn("order.state.changed skipped — missing toState: orderId={}", orderId);
                return;
            }

            UUID clientId = parseUuid(event.get("clientId"));
            UUID userId = parseUuid(event.get("userId"));
            String fromState = (String) event.get("fromState");
            String triggeredBy = (String) event.get("triggeredBy");
            String timestamp = (String) event.get("timestamp");

            Instant eventTs = parseTimestamp(timestamp);

            if (auditLogRepository.existsByEntityIdAndNewStateAndEventTimestamp(orderId, toState, eventTs)) {
                log.info("Duplicate order.state.changed skipped: orderId={} toState={}", orderId, toState);
                return;
            }

            ActorType actorType = (triggeredBy != null && triggeredBy.startsWith("USER:"))
                    ? ActorType.USER : ActorType.SYSTEM;

            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("clientId",    clientId != null ? clientId.toString() : "");
            meta.put("triggeredBy", triggeredBy != null ? triggeredBy : "");

            if ("RISK_REJECTED".equals(toState)) {
                @SuppressWarnings("unchecked")
                Map<String, Object> eventMetadata = event.get("metadata") instanceof Map
                        ? (Map<String, Object>) event.get("metadata")
                        : Collections.emptyMap();
                meta.put("rejectionReason",  eventMetadata.getOrDefault("rejectionReason", ""));
                meta.put("violatedRuleCode", eventMetadata.getOrDefault("violatedRuleCode", ""));
            }

            AuditLog auditLog = AuditLog.builder()
                    .entityType("ORDER")
                    .entityId(orderId)
                    .clientId(clientId)
                    .previousState(fromState)
                    .newState(toState)
                    .actorId(userId)
                    .actorType(actorType)
                    .eventTimestamp(eventTs)
                    .metadata(toJson(meta))
                    .build();

            auditLogRepository.save(auditLog);
            log.info("AuditLog saved: entityType=ORDER orderId={} {} -> {}", orderId, fromState, toState);
        } catch (Exception e) {
            log.error("Failed to process order.state.changed audit event: {}", e.getMessage(), e);
            throw e;
        }
    }

    @KafkaListener(topics = "execution.completed",
                   groupId = "audit-service-execution.completed-group")
    public void consumeExecutionCompleted(Map<String, Object> event) {
        try {
            UUID orderId = parseUuid(event.get("orderId"));
            if (orderId == null) {
                log.warn("execution.completed skipped — missing orderId");
                return;
            }

            String fillType = (String) event.get("fillType");
            if (fillType == null) {
                log.warn("execution.completed skipped — missing fillType: orderId={}", orderId);
                return;
            }

            UUID clientId = parseUuid(event.get("clientId"));
            String symbol = (String) event.get("symbol");
            String side = (String) event.get("side");
            Object executedQty = event.get("executedQty");
            String executedPrice = (String) event.get("executedPrice");
            String fillValue = (String) event.get("fillValue");
            String executedAt = (String) event.get("executedAt");

            Map<String, Object> meta = Map.of(
                    "orderId", orderId.toString(),
                    "symbol", symbol != null ? symbol : "",
                    "side", side != null ? side : "",
                    "executedQty", executedQty != null ? executedQty : 0,
                    "executedPrice", executedPrice != null ? executedPrice : "0",
                    "fillValue", fillValue != null ? fillValue : "0",
                    "fillType", fillType
            );

            AuditLog auditLog = AuditLog.builder()
                    .entityType("EXECUTION")
                    .entityId(orderId)
                    .clientId(clientId)
                    .previousState(null)
                    .newState(fillType)
                    .actorId(null)
                    .actorType(ActorType.SYSTEM)
                    .eventTimestamp(parseTimestamp(executedAt))
                    .metadata(toJson(meta))
                    .build();

            auditLogRepository.save(auditLog);
            log.info("AuditLog saved: entityType=EXECUTION orderId={} fillType={}", orderId, fillType);
        } catch (Exception e) {
            log.error("Failed to process execution.completed audit event: {}", e.getMessage(), e);
            throw e;
        }
    }

    private UUID parseUuid(Object value) {
        if (value == null) return null;
        try {
            return UUID.fromString(value.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Instant parseTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) return Instant.now();
        try {
            return Instant.parse(timestamp);
        } catch (Exception e) {
            return Instant.now();
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
