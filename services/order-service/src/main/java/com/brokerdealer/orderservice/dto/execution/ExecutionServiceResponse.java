package com.brokerdealer.orderservice.dto.execution;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class ExecutionServiceResponse {
    private List<ExecutionItem> data;
}
