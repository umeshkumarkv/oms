package com.brokerdealer.orderservice.exception;

import com.brokerdealer.orderservice.controller.OrderController;
import com.brokerdealer.orderservice.dto.request.SubmitOrderRequest;
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

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@Import({GlobalExceptionHandler.class, RoleAuthorizationFilter.class})
class GlobalExceptionHandlerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    OrderService orderService;

    private final UUID clientId = UUID.fromString("a1000000-0000-0000-0000-000000000001");

    @Test
    void shouldReturnErrorDetailObjectsForBeanValidationErrors() throws Exception {
        SubmitOrderRequest body = validBody();
        body.setSymbol("aapl!!");  // fails @Pattern(regexp = "^[A-Z0-9]+$")

        mockMvc.perform(post("/api/v1/orders")
                        .header("X-User-Role", "TRADER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.error.details").isArray())
                .andExpect(jsonPath("$.error.details", hasSize(1)))
                .andExpect(jsonPath("$.error.details[0].field", is("symbol")))
                .andExpect(jsonPath("$.error.details[0].code", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.error.details[0].message").isString())
                .andExpect(jsonPath("$.meta.requestId").isString())
                .andExpect(jsonPath("$.meta.timestamp").isString());
    }

    @Test
    void shouldReturnDetailObjectForQuantityBeanValidation() throws Exception {
        SubmitOrderRequest body = validBody();
        body.setQuantity(0);  // fails @Min(value = 1)

        mockMvc.perform(post("/api/v1/orders")
                        .header("X-User-Role", "TRADER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.error.details").isArray())
                .andExpect(jsonPath("$.error.details", hasSize(1)))
                .andExpect(jsonPath("$.error.details[0].field", is("quantity")))
                .andExpect(jsonPath("$.error.details[0].code", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.error.details[0].message", is("quantity must be greater than 0")));
    }

    @Test
    void shouldReturnFieldDetailForFieldScopedValidationException() throws Exception {
        SubmitOrderRequest body = validBody();
        when(orderService.submitOrder(any(SubmitOrderRequest.class), any()))
                .thenThrow(new ValidationException("limitPrice", "LIMIT_PRICE_REQUIRED", "limitPrice is required for LIMIT orders"));

        mockMvc.perform(post("/api/v1/orders")
                        .header("X-User-Role", "TRADER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("LIMIT_PRICE_REQUIRED")))
                .andExpect(jsonPath("$.error.details").isArray())
                .andExpect(jsonPath("$.error.details", hasSize(1)))
                .andExpect(jsonPath("$.error.details[0].field", is("limitPrice")))
                .andExpect(jsonPath("$.error.details[0].code", is("LIMIT_PRICE_REQUIRED")))
                .andExpect(jsonPath("$.error.details[0].message", is("limitPrice is required for LIMIT orders")));
    }

    @Test
    void shouldReturnEmptyDetailsForNonFieldScopedValidationException() throws Exception {
        SubmitOrderRequest body = validBody();
        when(orderService.submitOrder(any(SubmitOrderRequest.class), any()))
                .thenThrow(new ValidationException("VALIDATION_ERROR", "Invalid side: must be BUY or SELL"));

        mockMvc.perform(post("/api/v1/orders")
                        .header("X-User-Role", "TRADER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.error.details").isArray())
                .andExpect(jsonPath("$.error.details", hasSize(0)));
    }

    @Test
    void shouldReturn422ForSemanticValidationException() throws Exception {
        SubmitOrderRequest body = validBody();
        when(orderService.submitOrder(any(SubmitOrderRequest.class), any()))
                .thenThrow(new SemanticValidationException("CLIENT_NOT_FOUND", "Client not found"));

        mockMvc.perform(post("/api/v1/orders")
                        .header("X-User-Role", "TRADER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code", is("CLIENT_NOT_FOUND")))
                .andExpect(jsonPath("$.error.details").isArray())
                .andExpect(jsonPath("$.error.details", hasSize(0)));
    }

    private SubmitOrderRequest validBody() {
        SubmitOrderRequest r = new SubmitOrderRequest();
        r.setClientOrderId("ORD-TEST-001");
        r.setClientId(clientId);
        r.setSymbol("AAPL");
        r.setSide("BUY");
        r.setOrderType("MARKET");
        r.setTimeInForce("DAY");
        r.setQuantity(100);
        return r;
    }
}
