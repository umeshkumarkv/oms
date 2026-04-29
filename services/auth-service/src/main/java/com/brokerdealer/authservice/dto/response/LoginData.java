package com.brokerdealer.authservice.dto.response;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class LoginData {
    String token;
    String expiresAt;
    String role;
    String userId;
    String displayName;
}
