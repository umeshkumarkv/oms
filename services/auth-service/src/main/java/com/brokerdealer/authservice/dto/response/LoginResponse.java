package com.brokerdealer.authservice.dto.response;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class LoginResponse {
    String token;
    Instant expiresAt;
    String role;
}
