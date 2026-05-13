package com.brokerdealer.authservice.dto.response;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.UUID;

@Value
@Builder
public class UserStatusResponse {

    UUID userId;
    String status;
    Instant updatedAt;
}
