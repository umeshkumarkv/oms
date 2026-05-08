package com.brokerdealer.orderservice.dto.response;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class FieldErrorResponse {
    String field;
    String code;
    String message;
}
