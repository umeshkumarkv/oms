package com.brokerdealer.authservice.dto.response;

import lombok.Builder;
import lombok.Value;

import java.util.UUID;

@Value
@Builder
public class MeResponse {
    UUID userId;
    String username;
    String role;
    String displayName;
}
