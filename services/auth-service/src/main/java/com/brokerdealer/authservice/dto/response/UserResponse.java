package com.brokerdealer.authservice.dto.response;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.UUID;

@Value
@Builder
public class UserResponse {

    UUID userId;
    String username;
    String displayName;
    String role;
    String status;
    Instant createdAt;
    Instant lastLoginAt;
}
