package com.brokerdealer.auditservice.service;

import com.brokerdealer.auditservice.domain.ActorType;
import com.brokerdealer.auditservice.domain.AuditLog;
import com.brokerdealer.auditservice.dto.response.AuditTrailResponse;
import com.brokerdealer.auditservice.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditService auditService;

    private UUID clientId;
    private UUID orderId;
    private AuditLog sampleLog;

    @BeforeEach
    void setUp() {
        clientId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        sampleLog = AuditLog.builder()
                .id(UUID.randomUUID())
                .entityType("ORDER")
                .entityId(orderId)
                .clientId(clientId)
                .previousState("NEW")
                .newState("VALIDATED")
                .actorType(ActorType.SYSTEM)
                .eventTimestamp(Instant.now())
                .metadata("{}")
                .build();
    }

    @Test
    void shouldReturnAuditTrailByClientId() {
        Page<AuditLog> page = new PageImpl<>(List.of(sampleLog));
        when(auditLogRepository.findByClientIdOrderByEventTimestampDesc(eq(clientId), any(PageRequest.class)))
                .thenReturn(page);

        AuditTrailResponse result = auditService.getAuditTrail(clientId, 0, 20);

        assertThat(result).isNotNull();
        assertThat(result.getData().getRecords()).hasSize(1);
        assertThat(result.getData().getTotalElements()).isEqualTo(1);
        assertThat(result.getMeta().getTimestamp()).isNotNull();
        assertThat(result.getMeta().getRequestId()).isNotNull();
    }

    @Test
    void shouldReturnEmptyListWhenNoRecordsFound() {
        Page<AuditLog> emptyPage = new PageImpl<>(List.of());
        when(auditLogRepository.findByClientIdOrderByEventTimestampDesc(eq(clientId), any(PageRequest.class)))
                .thenReturn(emptyPage);

        AuditTrailResponse result = auditService.getAuditTrail(clientId, 0, 20);

        assertThat(result.getData().getRecords()).isEmpty();
        assertThat(result.getData().getTotalElements()).isZero();
    }

    @Test
    void shouldReturnAllRecordsWhenClientIdIsNull() {
        Page<AuditLog> page = new PageImpl<>(List.of(sampleLog));
        when(auditLogRepository.findAllByOrderByEventTimestampDesc(any(PageRequest.class)))
                .thenReturn(page);

        AuditTrailResponse result = auditService.getAuditTrail(null, 0, 20);

        assertThat(result.getData().getRecords()).hasSize(1);
    }

    @Test
    void shouldReturnOrderAuditTrail() {
        when(auditLogRepository.findByEntityIdOrderByEventTimestampDesc(orderId))
                .thenReturn(List.of(sampleLog));

        AuditTrailResponse result = auditService.getOrderAuditTrail(orderId);

        assertThat(result.getData().getRecords()).hasSize(1);
        assertThat(result.getData().getRecords().get(0).getEntityType()).isEqualTo("ORDER");
        assertThat(result.getData().getRecords().get(0).getNewState()).isEqualTo("VALIDATED");
    }

    @Test
    void shouldMapAuditLogFieldsCorrectly() {
        Page<AuditLog> page = new PageImpl<>(List.of(sampleLog));
        when(auditLogRepository.findByClientIdOrderByEventTimestampDesc(eq(clientId), any(PageRequest.class)))
                .thenReturn(page);

        AuditTrailResponse result = auditService.getAuditTrail(clientId, 0, 20);

        var record = result.getData().getRecords().get(0);
        assertThat(record.getEntityType()).isEqualTo("ORDER");
        assertThat(record.getPreviousState()).isEqualTo("NEW");
        assertThat(record.getNewState()).isEqualTo("VALIDATED");
        assertThat(record.getActorType()).isEqualTo("SYSTEM");
        assertThat(record.getEntityId()).isEqualTo(orderId.toString());
    }

    @Test
    void shouldHandleNullActorIdGracefully() {
        AuditLog logWithNullActor = AuditLog.builder()
                .id(UUID.randomUUID())
                .entityType("EXECUTION")
                .entityId(orderId)
                .clientId(clientId)
                .newState("FULL")
                .actorId(null)
                .actorType(ActorType.SYSTEM)
                .eventTimestamp(Instant.now())
                .build();

        Page<AuditLog> page = new PageImpl<>(List.of(logWithNullActor));
        when(auditLogRepository.findByClientIdOrderByEventTimestampDesc(eq(clientId), any(PageRequest.class)))
                .thenReturn(page);

        AuditTrailResponse result = auditService.getAuditTrail(clientId, 0, 20);

        assertThat(result.getData().getRecords().get(0).getActorId()).isNull();
    }
}
