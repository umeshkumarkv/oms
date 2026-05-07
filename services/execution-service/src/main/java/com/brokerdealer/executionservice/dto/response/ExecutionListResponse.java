package com.brokerdealer.executionservice.dto.response;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class ExecutionListResponse {
    private List<ExecutionResponse> data;
    private ResponseMeta meta;
}
