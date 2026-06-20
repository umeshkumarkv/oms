package com.brokerdealer.orderservice.filter;

import com.brokerdealer.orderservice.controller.OrderController;
import com.brokerdealer.orderservice.dto.response.ResponseMeta;
import com.brokerdealer.orderservice.dto.response.SubmitOrderData;
import com.brokerdealer.orderservice.dto.response.SubmitOrderResponse;
import com.brokerdealer.orderservice.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
class RoleAuthorizationFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    // ─── Missing header → 401 ────────────────────────────────────────────────

    @Test
    void shouldReturn401WhenRoleHeaderMissingOnGet() throws Exception {
        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void shouldReturn401WhenRoleHeaderMissingOnPost() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    // ─── VIEWER role ──────────────────────────────────────────────────────────

    @Test
    void shouldReturn403WhenViewerSubmitsOrder() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .header("X-User-Role", "VIEWER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void shouldAllowViewerToGetOrders() throws Exception {
        mockMvc.perform(get("/api/v1/orders")
                        .header("X-User-Role", "VIEWER"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldAllowViewerToGetOrderById() throws Exception {
        mockMvc.perform(get("/api/v1/orders/550e8400-e29b-41d4-a716-446655440000")
                        .header("X-User-Role", "VIEWER"))
                .andExpect(status().isOk());
    }

    // ─── TRADER role ──────────────────────────────────────────────────────────

    @Test
    void shouldAllowTraderToSubmitOrder() throws Exception {
        when(orderService.submitOrder(any(), any())).thenReturn(SubmitOrderResponse.builder()
                .data(SubmitOrderData.builder().orderId(UUID.randomUUID()).status("NEW").build())
                .meta(ResponseMeta.builder().timestamp(Instant.now().toString())
                        .requestId(UUID.randomUUID().toString()).build())
                .build());

        String validBody = """
                {"clientOrderId":"ORD-001","clientId":"a1000000-0000-0000-0000-000000000001",
                 "symbol":"AAPL","side":"BUY","orderType":"MARKET","tif":"DAY","quantity":10}
                """;
        mockMvc.perform(post("/api/v1/orders")
                        .header("X-User-Role", "TRADER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody))
                .andExpect(status().isAccepted());
    }

    @Test
    void shouldAllowTraderToGetOrders() throws Exception {
        mockMvc.perform(get("/api/v1/orders")
                        .header("X-User-Role", "TRADER"))
                .andExpect(status().isOk());
    }

    // ─── OPERATIONS role ──────────────────────────────────────────────────────

    @Test
    void shouldReturn403WhenOperationsSubmitsOrder() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .header("X-User-Role", "OPERATIONS")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void shouldAllowOperationsToGetOrders() throws Exception {
        mockMvc.perform(get("/api/v1/orders")
                        .header("X-User-Role", "OPERATIONS"))
                .andExpect(status().isOk());
    }

    // ─── ADMIN role ───────────────────────────────────────────────────────────

    @Test
    void shouldReturn403WhenAdminSubmitsOrder() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void shouldAllowAdminToGetOrders() throws Exception {
        mockMvc.perform(get("/api/v1/orders")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldAllowAdminToGetOrderById() throws Exception {
        mockMvc.perform(get("/api/v1/orders/550e8400-e29b-41d4-a716-446655440000")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk());
    }

    // ─── Unknown role ─────────────────────────────────────────────────────────

    @Test
    void shouldReturn403WhenUnknownRoleSubmitsRequest() throws Exception {
        mockMvc.perform(get("/api/v1/orders")
                        .header("X-User-Role", "UNKNOWN_ROLE"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    // ─── Error envelope shape ─────────────────────────────────────────────────

    @Test
    void shouldReturnStandardErrorEnvelopeOn403() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .header("X-User-Role", "VIEWER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.error.message").exists())
                .andExpect(jsonPath("$.meta.timestamp").exists())
                .andExpect(jsonPath("$.meta.requestId").exists());
    }
}
