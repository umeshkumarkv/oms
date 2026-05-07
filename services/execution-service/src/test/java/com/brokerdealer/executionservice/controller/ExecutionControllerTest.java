package com.brokerdealer.executionservice.controller;

import com.brokerdealer.executionservice.dto.response.ExecutionListResponse;
import com.brokerdealer.executionservice.dto.response.ExecutionResponse;
import com.brokerdealer.executionservice.dto.response.ResponseMeta;
import com.brokerdealer.executionservice.exception.ResourceNotFoundException;
import com.brokerdealer.executionservice.service.ExecutionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ExecutionController.class)
class ExecutionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ExecutionService executionService;

    private static final String ORDER_ID = "a1000000-0000-0000-0000-000000000001";

    @Test
    void shouldReturn200WithExecutionListResponse() throws Exception {
        ExecutionListResponse response = buildResponse();
        when(executionService.getExecutionsByOrderId(UUID.fromString(ORDER_ID))).thenReturn(response);

        mockMvc.perform(get("/api/v1/executions/{orderId}", ORDER_ID)
                        .header("X-User-Role", "TRADER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].fillType").value("FULL"))
                .andExpect(jsonPath("$.data[0].executionVenue").value("MOCK_ECN"))
                .andExpect(jsonPath("$.data[0].executedPrice").value("174.50"))
                .andExpect(jsonPath("$.meta.timestamp").exists())
                .andExpect(jsonPath("$.meta.requestId").exists());
    }

    @Test
    void shouldReturn404WhenOrderNotFound() throws Exception {
        when(executionService.getExecutionsByOrderId(UUID.fromString(ORDER_ID)))
                .thenThrow(new ResourceNotFoundException("ORDER_NOT_FOUND",
                        "No executions found for order: " + ORDER_ID));

        mockMvc.perform(get("/api/v1/executions/{orderId}", ORDER_ID)
                        .header("X-User-Role", "TRADER"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ORDER_NOT_FOUND"))
                .andExpect(jsonPath("$.meta.timestamp").exists())
                .andExpect(jsonPath("$.meta.requestId").exists());
    }

    @Test
    void shouldReturn401WhenRoleHeaderMissing() throws Exception {
        mockMvc.perform(get("/api/v1/executions/{orderId}", ORDER_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void shouldReturnExecutedPriceAsString() throws Exception {
        ExecutionListResponse response = buildResponse();
        when(executionService.getExecutionsByOrderId(UUID.fromString(ORDER_ID))).thenReturn(response);

        mockMvc.perform(get("/api/v1/executions/{orderId}", ORDER_ID)
                        .header("X-User-Role", "OPERATIONS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].executedPrice").isString())
                .andExpect(jsonPath("$.data[0].fillValue").isString())
                .andExpect(jsonPath("$.data[0].marketPriceAtFill").isString());
    }

    private ExecutionListResponse buildResponse() {
        ExecutionResponse exec = ExecutionResponse.builder()
                .executionId(UUID.randomUUID().toString())
                .orderId(ORDER_ID)
                .symbol("AAPL")
                .side("BUY")
                .executedQty(100)
                .executedPrice("174.50")
                .fillType("FULL")
                .fillValue("17450.00")
                .remainingQuantity(0)
                .marketPriceAtFill("174.50")
                .executionVenue("MOCK_ECN")
                .executedAt(Instant.now().toString())
                .build();

        return ExecutionListResponse.builder()
                .data(List.of(exec))
                .meta(ResponseMeta.builder()
                        .timestamp(Instant.now().toString())
                        .requestId(UUID.randomUUID().toString())
                        .build())
                .build();
    }
}
