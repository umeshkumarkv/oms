package com.brokerdealer.orderservice.dto.response;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.util.UUID;

@Value
@Builder
public class SubmitOrderData {
    UUID orderId;
    String status;
    LocalDate expiryDate;
    String expiryApplied;
}
