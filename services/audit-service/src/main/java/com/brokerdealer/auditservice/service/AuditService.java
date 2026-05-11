package com.brokerdealer.auditservice.service;

import com.brokerdealer.auditservice.domain.AuditLog;
import com.brokerdealer.auditservice.dto.response.AuditLogResponse;
import com.brokerdealer.auditservice.dto.response.AuditTrailResponse;
import com.brokerdealer.auditservice.dto.response.AuditTrailResponse.AuditTrailData;
import com.brokerdealer.auditservice.dto.response.ResponseMeta;
import com.brokerdealer.auditservice.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditTrailResponse getAuditTrail(UUID clientId, int page, int size) {
        Page<AuditLog> auditPage;
        if (clientId != null) {
            auditPage = auditLogRepository.findByClientIdOrderByEventTimestampDesc(
                    clientId, PageRequest.of(page, size));
        } else {
            auditPage = auditLogRepository.findAllByOrderByEventTimestampDesc(
                    PageRequest.of(page, size));
        }

        List<AuditLogResponse> records = auditPage.getContent().stream()
                .map(this::toResponse)
                .toList();

        log.debug("getAuditTrail: clientId={} page={} size={} total={}",
                clientId, page, size, auditPage.getTotalElements());

        return AuditTrailResponse.builder()
                .data(AuditTrailData.builder()
                        .records(records)
                        .totalElements(auditPage.getTotalElements())
                        .build())
                .meta(ResponseMeta.builder()
                        .timestamp(Instant.now().toString())
                        .requestId(UUID.randomUUID().toString())
                        .build())
                .build();
    }

    public AuditTrailResponse getOrderAuditTrail(UUID orderId) {
        List<AuditLog> logs = auditLogRepository.findByEntityIdOrderByEventTimestampDesc(orderId);
        List<AuditLogResponse> records = logs.stream().map(this::toResponse).toList();

        return AuditTrailResponse.builder()
                .data(AuditTrailData.builder()
                        .records(records)
                        .totalElements(records.size())
                        .build())
                .meta(ResponseMeta.builder()
                        .timestamp(Instant.now().toString())
                        .requestId(UUID.randomUUID().toString())
                        .build())
                .build();
    }

    private AuditLogResponse toResponse(AuditLog log) {
        return AuditLogResponse.builder()
                .id(log.getId() != null ? log.getId().toString() : null)
                .entityType(log.getEntityType())
                .entityId(log.getEntityId() != null ? log.getEntityId().toString() : null)
                .previousState(log.getPreviousState())
                .newState(log.getNewState())
                .actorId(log.getActorId() != null ? log.getActorId().toString() : null)
                .actorType(log.getActorType() != null ? log.getActorType().name() : null)
                .eventTimestamp(log.getEventTimestamp() != null ? log.getEventTimestamp().toString() : null)
                .metadata(log.getMetadata())
                .build();
    }
}
