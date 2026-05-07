package com.brokerdealer.executionservice.dto.response;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ResponseMeta {
    private String timestamp;
    private String requestId;
}
