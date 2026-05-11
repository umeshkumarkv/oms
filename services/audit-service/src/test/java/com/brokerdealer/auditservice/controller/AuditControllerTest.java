package com.brokerdealer.auditservice.controller;

import com.brokerdealer.auditservice.dto.response.AuditLogResponse;
import com.brokerdealer.auditservice.dto.response.AuditTrailResponse;
import com.brokerdealer.auditservice.dto.response.AuditTrailResponse.AuditTrailData;
import com.brokerdealer.auditservice.dto.response.ResponseMeta;
import com.brokerdealer.auditservice.service.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuditController.class)
class AuditControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuditService auditService;

    private AuditTrailResponse sampleResponse;

    @BeforeEach
    void setUp() {
        AuditLogResponse record = AuditLogResponse.builder()
                .id(UUID.randomUUID().toString())
                .entityType("ORDER")
                .entityId(UUID.randomUUID().toString())
                .previousState("NEW")
                .newState("VALIDATED")
                .actorType("SYSTEM")
                .eventTimestamp("2026-01-01T10:00:00Z")
                .build();

        sampleResponse = AuditTrailResponse.builder()
                .data(AuditTrailData.builder()
                        .records(List.of(record))
                        .totalElements(1)
                        .build())
                .meta(ResponseMeta.builder()
                        .timestamp("2026-01-01T10:00:00Z")
                        .requestId(UUID.randomUUID().toString())
                        .build())
                .build();

        when(auditService.getAuditTrail(any(), anyInt(), anyInt())).thenReturn(sampleResponse);
        when(auditService.getOrderAuditTrail(any())).thenReturn(sampleResponse);
    }

    @Test
    void shouldReturn200WithEnvelopeForAuditTrail() throws Exception {
        mockMvc.perform(get("/api/v1/audit/trail")
                        .header("X-User-Role", "OPERATIONS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.meta.timestamp").exists())
                .andExpect(jsonPath("$.meta.requestId").exists());
    }

    @Test
    void shouldReturn200WithClientIdFilter() throws Exception {
        mockMvc.perform(get("/api/v1/audit/trail")
                        .param("clientId", UUID.randomUUID().toString())
                        .header("X-User-Role", "OPERATIONS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records").isArray());
    }

    @Test
    void shouldReturn200ForOrderAuditTrail() throws Exception {
        mockMvc.perform(get("/api/v1/audit/orders/" + UUID.randomUUID())
                        .header("X-User-Role", "OPERATIONS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records").isArray());
    }

    @Test
    void shouldReturn401WhenRoleHeaderMissing() throws Exception {
        mockMvc.perform(get("/api/v1/audit/trail"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAcceptTraderRole() throws Exception {
        mockMvc.perform(get("/api/v1/audit/trail")
                        .header("X-User-Role", "TRADER"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldAcceptAdminRole() throws Exception {
        mockMvc.perform(get("/api/v1/audit/trail")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk());
    }
}
