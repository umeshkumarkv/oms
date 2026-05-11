package com.brokerdealer.auditservice.dto.response;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ResponseMeta {
    String timestamp;
    String requestId;
}
