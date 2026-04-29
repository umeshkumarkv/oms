package com.brokerdealer.authservice.dto.response;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class LogoutResponse {
    String message;
}
