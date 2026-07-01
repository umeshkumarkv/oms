package com.brokerdealer.auditservice.kafka.consumer;

import com.brokerdealer.auditservice.domain.ActorType;
import com.brokerdealer.auditservice.domain.AuditLog;
import com.brokerdealer.auditservice.repository.AuditLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditEventConsumerTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private AuditEventConsumer auditEventConsumer;

    private UUID orderId;
    private UUID clientId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        orderId  = UUID.randomUUID();
        clientId = UUID.randomUUID();
        userId   = UUID.randomUUID();
    }

    // ─── consumeOrderStateChanged — happy path ────────────────────────────────

    @Test
    void shouldSaveAuditLogForOrderStateChangedWithSystemActor() {
        Map<String, Object> event = new HashMap<>();
        event.put("orderId",     orderId.toString());
        event.put("toState",     "VALIDATED");
        event.put("fromState",   "NEW");
        event.put("clientId",    clientId.toString());
        event.put("userId",      userId.toString());
        event.put("triggeredBy", "SYSTEM");
        event.put("timestamp",   "2026-05-01T10:00:00Z");

        auditEventConsumer.consumeOrderStateChanged(event);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertThat(saved.getEntityType()).isEqualTo("ORDER");
        assertThat(saved.getEntityId()).isEqualTo(orderId);
        assertThat(saved.getClientId()).isEqualTo(clientId);
        assertThat(saved.getPreviousState()).isEqualTo("NEW");
        assertThat(saved.getNewState()).isEqualTo("VALIDATED");
        assertThat(saved.getActorType()).isEqualTo(ActorType.SYSTEM);
        assertThat(saved.getEventTimestamp()).isNotNull();
        assertThat(saved.getMetadata()).isNotNull();
    }

    @Test
    void shouldSetActorTypeUserWhenTriggeredByStartsWithUser() {
        Map<String, Object> event = new HashMap<>();
        event.put("orderId",     orderId.toString());
        event.put("toState",     "ACCEPTED");
        event.put("triggeredBy", "USER:trader.john");
        event.put("timestamp",   "2026-05-01T10:00:00Z");

        auditEventConsumer.consumeOrderStateChanged(event);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getActorType()).isEqualTo(ActorType.USER);
    }

    @Test
    void shouldHandleNullOptionalFieldsInOrderStateChanged() {
        Map<String, Object> event = new HashMap<>();
        event.put("orderId",   orderId.toString());
        event.put("toState",   "NEW");
        // clientId, userId, fromState, triggeredBy, timestamp all null

        auditEventConsumer.consumeOrderStateChanged(event);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertThat(saved.getClientId()).isNull();
        assertThat(saved.getActorId()).isNull();
        assertThat(saved.getPreviousState()).isNull();
        assertThat(saved.getActorType()).isEqualTo(ActorType.SYSTEM);
    }

    @Test
    void shouldHandleInvalidTimestampInOrderStateChanged() {
        Map<String, Object> event = new HashMap<>();
        event.put("orderId",   orderId.toString());
        event.put("toState",   "VALIDATED");
        event.put("timestamp", "not-a-timestamp");

        auditEventConsumer.consumeOrderStateChanged(event);

        verify(auditLogRepository).save(any(AuditLog.class));
    }

    @Test
    void shouldHandleBlankTimestampInOrderStateChanged() {
        Map<String, Object> event = new HashMap<>();
        event.put("orderId",   orderId.toString());
        event.put("toState",   "VALIDATED");
        event.put("timestamp", "");

        auditEventConsumer.consumeOrderStateChanged(event);

        verify(auditLogRepository).save(any(AuditLog.class));
    }

    // ─── consumeOrderStateChanged — missing required fields ──────────────────

    @Test
    void shouldSkipWhenOrderIdMissingFromOrderStateChanged() {
        Map<String, Object> event = new HashMap<>();
        event.put("toState", "VALIDATED");

        auditEventConsumer.consumeOrderStateChanged(event);

        verifyNoInteractions(auditLogRepository);
    }

    @Test
    void shouldSkipWhenOrderIdIsInvalidUuid() {
        Map<String, Object> event = new HashMap<>();
        event.put("orderId", "not-a-uuid");
        event.put("toState", "VALIDATED");

        auditEventConsumer.consumeOrderStateChanged(event);

        verifyNoInteractions(auditLogRepository);
    }

    @Test
    void shouldSkipWhenToStateMissingFromOrderStateChanged() {
        Map<String, Object> event = new HashMap<>();
        event.put("orderId", orderId.toString());

        auditEventConsumer.consumeOrderStateChanged(event);

        verifyNoInteractions(auditLogRepository);
    }

    @Test
    void shouldRethrowExceptionFromOrderStateChangedConsumer() {
        Map<String, Object> event = new HashMap<>();
        event.put("orderId",  orderId.toString());
        event.put("toState",  "VALIDATED");
        event.put("timestamp", "2026-05-01T10:00:00Z");
        when(auditLogRepository.existsByEntityIdAndNewStateAndEventTimestamp(any(), any(), any())).thenReturn(false);
        when(auditLogRepository.save(any())).thenThrow(new RuntimeException("DB error"));

        assertThatThrownBy(() -> auditEventConsumer.consumeOrderStateChanged(event))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB error");
    }

    // ─── SCRUM-178: RISK_REJECTED metadata enrichment + idempotency ──────────

    @Test
    void shouldSaveAuditLogWithRejectionMetadataWhenRiskRejected() {
        Map<String, Object> eventMetadata = new HashMap<>();
        eventMetadata.put("rejectionReason",  "Single order quantity limit exceeded — SINGLE_ORDER_QTY_LIMIT");
        eventMetadata.put("violatedRuleCode", "SINGLE_ORDER_QTY_LIMIT");

        Map<String, Object> event = new HashMap<>();
        event.put("orderId",     orderId.toString());
        event.put("toState",     "RISK_REJECTED");
        event.put("fromState",   "VALIDATED");
        event.put("clientId",    clientId.toString());
        event.put("triggeredBy", "SYSTEM:RISK_ENGINE");
        event.put("timestamp",   "2026-05-01T10:00:00Z");
        event.put("metadata",    eventMetadata);

        when(auditLogRepository.existsByEntityIdAndNewStateAndEventTimestamp(any(), any(), any())).thenReturn(false);

        auditEventConsumer.consumeOrderStateChanged(event);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertThat(saved.getNewState()).isEqualTo("RISK_REJECTED");
        assertThat(saved.getActorType()).isEqualTo(ActorType.SYSTEM);
        assertThat(saved.getMetadata()).contains("rejectionReason");
        assertThat(saved.getMetadata()).contains("SINGLE_ORDER_QTY_LIMIT");
        assertThat(saved.getMetadata()).contains("violatedRuleCode");
        assertThat(saved.getMetadata()).doesNotContain("buyingPower");
        assertThat(saved.getMetadata()).doesNotContain("balance");
    }

    @Test
    void shouldNotIncludeRejectionMetadataForNonRiskRejectedTransition() {
        Map<String, Object> event = new HashMap<>();
        event.put("orderId",     orderId.toString());
        event.put("toState",     "VALIDATED");
        event.put("fromState",   "NEW");
        event.put("clientId",    clientId.toString());
        event.put("triggeredBy", "SYSTEM:OMS");
        event.put("timestamp",   "2026-05-01T10:00:00Z");

        when(auditLogRepository.existsByEntityIdAndNewStateAndEventTimestamp(any(), any(), any())).thenReturn(false);

        auditEventConsumer.consumeOrderStateChanged(event);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertThat(saved.getNewState()).isEqualTo("VALIDATED");
        assertThat(saved.getMetadata()).doesNotContain("rejectionReason");
        assertThat(saved.getMetadata()).doesNotContain("violatedRuleCode");
    }

    @Test
    void shouldSkipDuplicateEventForIdempotency() {
        Map<String, Object> event = new HashMap<>();
        event.put("orderId",   orderId.toString());
        event.put("toState",   "RISK_REJECTED");
        event.put("timestamp", "2026-05-01T10:00:00Z");

        when(auditLogRepository.existsByEntityIdAndNewStateAndEventTimestamp(any(), any(), any())).thenReturn(true);

        auditEventConsumer.consumeOrderStateChanged(event);

        verify(auditLogRepository, never()).save(any());
    }

    // ─── consumeExecutionCompleted — happy path ───────────────────────────────

    @Test
    void shouldSaveAuditLogForExecutionCompleted() {
        Map<String, Object> event = new HashMap<>();
        event.put("orderId",       orderId.toString());
        event.put("fillType",      "FULL");
        event.put("clientId",      clientId.toString());
        event.put("symbol",        "AAPL");
        event.put("side",          "BUY");
        event.put("executedQty",   100);
        event.put("executedPrice", "175.50");
        event.put("fillValue",     "17550.00");
        event.put("executedAt",    "2026-05-01T14:30:00Z");

        auditEventConsumer.consumeExecutionCompleted(event);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertThat(saved.getEntityType()).isEqualTo("EXECUTION");
        assertThat(saved.getEntityId()).isEqualTo(orderId);
        assertThat(saved.getClientId()).isEqualTo(clientId);
        assertThat(saved.getNewState()).isEqualTo("FULL");
        assertThat(saved.getActorType()).isEqualTo(ActorType.SYSTEM);
        assertThat(saved.getPreviousState()).isNull();
        assertThat(saved.getActorId()).isNull();
        assertThat(saved.getMetadata()).contains("AAPL");
    }

    @Test
    void shouldHandleNullOptionalFieldsInExecutionCompleted() {
        Map<String, Object> event = new HashMap<>();
        event.put("orderId",   orderId.toString());
        event.put("fillType",  "PARTIAL");
        // symbol, side, executedQty, executedPrice, fillValue, executedAt all null

        auditEventConsumer.consumeExecutionCompleted(event);

        verify(auditLogRepository).save(any(AuditLog.class));
    }

    @Test
    void shouldHandleInvalidTimestampInExecutionCompleted() {
        Map<String, Object> event = new HashMap<>();
        event.put("orderId",    orderId.toString());
        event.put("fillType",   "FULL");
        event.put("executedAt", "bad-timestamp");

        auditEventConsumer.consumeExecutionCompleted(event);

        verify(auditLogRepository).save(any(AuditLog.class));
    }

    // ─── consumeExecutionCompleted — missing required fields ─────────────────

    @Test
    void shouldSkipWhenOrderIdMissingFromExecutionCompleted() {
        Map<String, Object> event = new HashMap<>();
        event.put("fillType", "FULL");

        auditEventConsumer.consumeExecutionCompleted(event);

        verifyNoInteractions(auditLogRepository);
    }

    @Test
    void shouldSkipWhenOrderIdIsInvalidUuidInExecutionCompleted() {
        Map<String, Object> event = new HashMap<>();
        event.put("orderId",  "bad-uuid");
        event.put("fillType", "FULL");

        auditEventConsumer.consumeExecutionCompleted(event);

        verifyNoInteractions(auditLogRepository);
    }

    @Test
    void shouldSkipWhenFillTypeMissingFromExecutionCompleted() {
        Map<String, Object> event = new HashMap<>();
        event.put("orderId", orderId.toString());

        auditEventConsumer.consumeExecutionCompleted(event);

        verifyNoInteractions(auditLogRepository);
    }

    @Test
    void shouldRethrowExceptionFromExecutionCompletedConsumer() {
        Map<String, Object> event = new HashMap<>();
        event.put("orderId",  orderId.toString());
        event.put("fillType", "FULL");
        when(auditLogRepository.save(any())).thenThrow(new RuntimeException("DB down"));

        assertThatThrownBy(() -> auditEventConsumer.consumeExecutionCompleted(event))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB down");
    }
}
