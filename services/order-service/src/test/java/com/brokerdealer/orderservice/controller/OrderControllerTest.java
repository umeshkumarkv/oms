package com.brokerdealer.orderservice.controller;

import com.brokerdealer.orderservice.dto.request.SubmitOrderRequest;
import com.brokerdealer.orderservice.dto.response.FillResponse;
import com.brokerdealer.orderservice.dto.response.OrderDetailResponse;
import com.brokerdealer.orderservice.dto.response.OrderListData;
import com.brokerdealer.orderservice.dto.response.OrderListResponse;
import com.brokerdealer.orderservice.dto.response.OrderResponse;
import com.brokerdealer.orderservice.dto.response.ResponseMeta;
import com.brokerdealer.orderservice.dto.response.SubmitOrderData;
import com.brokerdealer.orderservice.dto.response.SubmitOrderResponse;
import com.brokerdealer.orderservice.exception.ClientInactiveException;
import com.brokerdealer.orderservice.exception.ClientNotFoundException;
import com.brokerdealer.orderservice.exception.GlobalExceptionHandler;
import com.brokerdealer.orderservice.exception.MarketClosedException;
import com.brokerdealer.orderservice.exception.OrderNotFoundException;
import com.brokerdealer.orderservice.exception.ServiceUnavailableException;
import com.brokerdealer.orderservice.exception.SymbolHaltedException;
import com.brokerdealer.orderservice.exception.SymbolNotFoundException;
import com.brokerdealer.orderservice.exception.ValidationException;
import com.brokerdealer.orderservice.filter.RoleAuthorizationFilter;
import com.brokerdealer.orderservice.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@Import({GlobalExceptionHandler.class, RoleAuthorizationFilter.class})
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;

    private final UUID orderId = UUID.randomUUID();
    private final UUID clientId = UUID.fromString("a1000000-0000-0000-0000-000000000001");

    @Test
    void shouldReturn202WhenValidMarketBuyOrderSubmitted() throws Exception {
        when(orderService.submitOrder(any(), any())).thenReturn(buildSuccessResponse());

        mockMvc.perform(post("/api/v1/orders")
                        .header("X-User-Role", "TRADER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validMarketBuyBody())))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.data.status").value("NEW"))
                .andExpect(jsonPath("$.meta.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.meta.requestId").isNotEmpty());
    }

    @Test
    void shouldReturn400WhenRequiredFieldMissing() throws Exception {
        SubmitOrderRequest body = validMarketBuyBody();
        body.setClientId(null);

        mockMvc.perform(post("/api/v1/orders")
                        .header("X-User-Role", "TRADER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldReturn400WhenQuantityIsZero() throws Exception {
        SubmitOrderRequest body = validMarketBuyBody();
        body.setQuantity(0);

        mockMvc.perform(post("/api/v1/orders")
                        .header("X-User-Role", "TRADER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldReturn400WhenLimitPriceMissingForLimitOrder() throws Exception {
        when(orderService.submitOrder(any(), any()))
                .thenThrow(new ValidationException("LIMIT_PRICE_REQUIRED", "limitPrice is required for LIMIT orders"));

        SubmitOrderRequest body = validMarketBuyBody();
        body.setOrderType("LIMIT");

        mockMvc.perform(post("/api/v1/orders")
                        .header("X-User-Role", "TRADER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("LIMIT_PRICE_REQUIRED"));
    }

    @Test
    void shouldReturn422WhenClientNotFound() throws Exception {
        when(orderService.submitOrder(any(), any()))
                .thenThrow(new ClientNotFoundException(clientId.toString()));

        mockMvc.perform(post("/api/v1/orders")
                        .header("X-User-Role", "TRADER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validMarketBuyBody())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("CLIENT_NOT_FOUND"));
    }

    @Test
    void shouldReturn422WhenClientInactive() throws Exception {
        when(orderService.submitOrder(any(), any()))
                .thenThrow(new ClientInactiveException(clientId.toString()));

        mockMvc.perform(post("/api/v1/orders")
                        .header("X-User-Role", "TRADER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validMarketBuyBody())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("CLIENT_INACTIVE"));
    }

    @Test
    void shouldReturn422WhenMarketClosed() throws Exception {
        when(orderService.submitOrder(any(), any()))
                .thenThrow(new MarketClosedException());

        mockMvc.perform(post("/api/v1/orders")
                        .header("X-User-Role", "TRADER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validMarketBuyBody())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("MARKET_CLOSED"));
    }

    @Test
    void shouldReturn422WhenSymbolNotFound() throws Exception {
        when(orderService.submitOrder(any(), any()))
                .thenThrow(new SymbolNotFoundException("BADTICKER"));

        mockMvc.perform(post("/api/v1/orders")
                        .header("X-User-Role", "TRADER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validMarketBuyBody())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("SYMBOL_NOT_FOUND"));
    }

    @Test
    void shouldReturn422WhenSymbolHalted() throws Exception {
        when(orderService.submitOrder(any(), any()))
                .thenThrow(new SymbolHaltedException("AAPL"));

        mockMvc.perform(post("/api/v1/orders")
                        .header("X-User-Role", "TRADER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validMarketBuyBody())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("SYMBOL_HALTED"));
    }

    @Test
    void shouldReturn503WhenServiceUnavailable() throws Exception {
        when(orderService.submitOrder(any(), any()))
                .thenThrow(new ServiceUnavailableException("client-service"));

        mockMvc.perform(post("/api/v1/orders")
                        .header("X-User-Role", "TRADER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validMarketBuyBody())))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("SERVICE_UNAVAILABLE"));
    }

    @Test
    void shouldReturn403WhenRoleIsNotTrader() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .header("X-User-Role", "OPERATIONS")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validMarketBuyBody())))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturn401WhenRoleHeaderMissing() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validMarketBuyBody())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturnStandardEnvelopeWithDataAndMeta() throws Exception {
        when(orderService.submitOrder(any(), any())).thenReturn(buildSuccessResponse());

        mockMvc.perform(post("/api/v1/orders")
                        .header("X-User-Role", "TRADER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validMarketBuyBody())))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.meta").exists())
                .andExpect(jsonPath("$.meta.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.meta.requestId").isNotEmpty());
    }

    // --- GET /orders/{id} ---

    @Test
    void shouldReturn200WithOrderDetailForKnownId() throws Exception {
        when(orderService.getOrderById(orderId)).thenReturn(buildDetailResponse());

        mockMvc.perform(get("/api/v1/orders/" + orderId)
                        .header("X-User-Role", "TRADER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(orderId.toString()))
                .andExpect(jsonPath("$.data.status").value("NEW"))
                .andExpect(jsonPath("$.data.fills").isArray())
                .andExpect(jsonPath("$.meta.timestamp").isNotEmpty());
    }

    @Test
    void shouldReturn404ForUnknownOrderId() throws Exception {
        when(orderService.getOrderById(orderId))
                .thenThrow(new OrderNotFoundException(orderId));

        mockMvc.perform(get("/api/v1/orders/" + orderId)
                        .header("X-User-Role", "TRADER"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ORDER_NOT_FOUND"));
    }

    // --- GET /orders ---

    @Test
    void shouldReturn200WithPaginatedOrderList() throws Exception {
        when(orderService.getOrders(isNull(), isNull(), isNull(), isNull(), isNull(), anyInt(), anyInt()))
                .thenReturn(buildListResponse());

        mockMvc.perform(get("/api/v1/orders")
                        .header("X-User-Role", "TRADER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orders").isArray())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.meta.timestamp").isNotEmpty());
    }

    @Test
    void shouldReturn200WithFilteredOrderList() throws Exception {
        when(orderService.getOrders(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(buildListResponse());

        mockMvc.perform(get("/api/v1/orders")
                        .param("status", "NEW")
                        .param("clientId", clientId.toString())
                        .header("X-User-Role", "OPERATIONS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orders").isArray());
    }

    @Test
    void shouldReturn200WithCreatedAfterParam() throws Exception {
        when(orderService.getOrders(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(buildListResponse());

        mockMvc.perform(get("/api/v1/orders")
                        .param("status", "FILLED")
                        .param("createdAfter", "2026-05-18T00:00:00Z")
                        .header("X-User-Role", "TRADER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orders").isArray())
                .andExpect(jsonPath("$.data.totalCount").value(1));
    }

    @Test
    void shouldReturn400WhenCreatedAfterIsInvalidIso8601() throws Exception {
        when(orderService.getOrders(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenThrow(new ValidationException("VALIDATION_ERROR",
                        "Invalid createdAfter format: must be ISO 8601 UTC (e.g. 2026-05-18T00:00:00Z)"));

        mockMvc.perform(get("/api/v1/orders")
                        .param("createdAfter", "2026-05-18")
                        .header("X-User-Role", "TRADER"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldReturnRoutingDestinationInOrderDetailResponse() throws Exception {
        when(orderService.getOrderById(orderId)).thenReturn(buildDetailResponseWithVenue("NASDAQ"));

        mockMvc.perform(get("/api/v1/orders/" + orderId)
                        .header("X-User-Role", "TRADER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.routingDestination").value("NASDAQ"));
    }

    // --- Helpers ---

    private OrderDetailResponse buildDetailResponse() {
        OrderResponse orderResponse = OrderResponse.builder()
                .id(orderId)
                .clientId(clientId)
                .symbol("AAPL")
                .side("BUY")
                .orderType("MARKET")
                .timeInForce("DAY")
                .quantity(10)
                .status("NEW")
                .filledQuantity(0)
                .fills(Collections.emptyList())
                .createdAt(Instant.now().toString())
                .updatedAt(Instant.now().toString())
                .routingDestination("NYSE")
                .build();
        return OrderDetailResponse.builder()
                .data(orderResponse)
                .meta(ResponseMeta.builder()
                        .timestamp(Instant.now().toString())
                        .requestId(UUID.randomUUID().toString())
                        .build())
                .build();
    }

    private OrderDetailResponse buildDetailResponseWithVenue(String venue) {
        OrderResponse orderResponse = OrderResponse.builder()
                .id(orderId)
                .clientId(clientId)
                .symbol("AAPL")
                .side("BUY")
                .orderType("MARKET")
                .timeInForce("DAY")
                .quantity(10)
                .status("NEW")
                .filledQuantity(0)
                .fills(Collections.emptyList())
                .createdAt(Instant.now().toString())
                .updatedAt(Instant.now().toString())
                .routingDestination(venue)
                .build();
        return OrderDetailResponse.builder()
                .data(orderResponse)
                .meta(ResponseMeta.builder()
                        .timestamp(Instant.now().toString())
                        .requestId(UUID.randomUUID().toString())
                        .build())
                .build();
    }

    private OrderListResponse buildListResponse() {
        OrderResponse orderResponse = OrderResponse.builder()
                .id(orderId)
                .clientId(clientId)
                .symbol("AAPL")
                .side("BUY")
                .orderType("MARKET")
                .timeInForce("DAY")
                .quantity(10)
                .status("NEW")
                .filledQuantity(0)
                .fills(Collections.emptyList())
                .createdAt(Instant.now().toString())
                .updatedAt(Instant.now().toString())
                .build();
        return OrderListResponse.builder()
                .data(OrderListData.builder()
                        .orders(List.of(orderResponse))
                        .page(0)
                        .pageSize(50)
                        .totalCount(1)
                        .totalPages(1)
                        .build())
                .meta(ResponseMeta.builder()
                        .timestamp(Instant.now().toString())
                        .requestId(UUID.randomUUID().toString())
                        .build())
                .build();
    }

    private SubmitOrderRequest validMarketBuyBody() {
        SubmitOrderRequest r = new SubmitOrderRequest();
        r.setClientOrderId("CLT-ORD-001");
        r.setClientId(clientId);
        r.setSymbol("AAPL");
        r.setSide("BUY");
        r.setOrderType("MARKET");
        r.setTimeInForce("DAY");
        r.setQuantity(10);
        return r;
    }

    private SubmitOrderResponse buildSuccessResponse() {
        return SubmitOrderResponse.builder()
                .data(SubmitOrderData.builder()
                        .orderId(orderId)
                        .status("NEW")
                        .build())
                .meta(ResponseMeta.builder()
                        .timestamp(Instant.now().toString())
                        .requestId(UUID.randomUUID().toString())
                        .build())
                .build();
    }
}
