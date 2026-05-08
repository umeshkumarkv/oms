package com.brokerdealer.orderservice.dto.response;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Value
@Builder
public class OrderResponse {
    UUID orderId;
    UUID clientId;
    String clientOrderId;
    String symbol;
    String side;
    String orderType;
    String timeInForce;
    Integer quantity;
    String limitPrice;
    String status;
    String rejectionReason;
    LocalDate expiryDate;
    String expiryApplied;
    String createdAt;
    String updatedAt;
    UUID createdBy;
    Integer filledQuantity;
    String avgFillPrice;
    List<FillResponse> fills;
}
