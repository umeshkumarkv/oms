package com.brokerdealer.orderservice.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.util.UUID;

@Value
@Builder
public class SubmitOrderData {
    UUID orderId;
    String status;
    String tif;
    @JsonProperty("gtdDate")
    LocalDate expiryDate;
    String expiryApplied;
    String expiresAt;
}
