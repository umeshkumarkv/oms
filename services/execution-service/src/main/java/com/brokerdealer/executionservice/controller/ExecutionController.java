package com.brokerdealer.executionservice.controller;

import com.brokerdealer.executionservice.dto.response.ExecutionListResponse;
import com.brokerdealer.executionservice.service.ExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/executions")
@RequiredArgsConstructor
@Slf4j
public class ExecutionController {

    private final ExecutionService executionService;

    @GetMapping("/{orderId}")
    public ResponseEntity<ExecutionListResponse> getExecutionsByOrderId(@PathVariable UUID orderId) {
        log.debug("GET /api/v1/executions/{}", orderId);
        ExecutionListResponse response = executionService.getExecutionsByOrderId(orderId);
        return ResponseEntity.ok(response);
    }
}
