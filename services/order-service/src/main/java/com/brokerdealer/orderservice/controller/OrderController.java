package com.brokerdealer.orderservice.controller;

import com.brokerdealer.orderservice.dto.request.SubmitOrderRequest;
import com.brokerdealer.orderservice.dto.response.OrderDetailResponse;
import com.brokerdealer.orderservice.dto.response.OrderListResponse;
import com.brokerdealer.orderservice.dto.response.StatusCountsResponse;
import com.brokerdealer.orderservice.dto.response.SubmitOrderResponse;
import com.brokerdealer.orderservice.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<SubmitOrderResponse> submitOrder(
            @Valid @RequestBody SubmitOrderRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        UUID userUuid = null;
        if (userId != null && !userId.isBlank()) {
            try {
                userUuid = UUID.fromString(userId);
            } catch (IllegalArgumentException ignored) {
                // gateway may not send this header in all cases
            }
        }
        SubmitOrderResponse response = orderService.submitOrder(request, userUuid);
        return ResponseEntity.accepted().body(response);
    }

    @GetMapping
    public ResponseEntity<OrderListResponse> getOrders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID clientId,
            @RequestParam(required = false) String side,
            @RequestParam(required = false) String timeInForce,
            @RequestParam(required = false) String createdAfter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int pageSize) {
        OrderListResponse response = orderService.getOrders(status, clientId, side, timeInForce, createdAfter, page, pageSize);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/status-counts")
    public ResponseEntity<StatusCountsResponse> getStatusCounts() {
        return ResponseEntity.ok(orderService.getStatusCountsForToday());
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderDetailResponse> getOrderById(@PathVariable UUID id) {
        OrderDetailResponse response = orderService.getOrderById(id);
        return ResponseEntity.ok(response);
    }
}
