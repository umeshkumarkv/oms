package com.brokerdealer.executionservice.controller;

import com.brokerdealer.executionservice.service.ExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/executions")
@RequiredArgsConstructor
public class ExecutionController {

    private final ExecutionService executionService;

    @GetMapping("/{orderId}")
    public ResponseEntity<?> getExecutionsByOrderId(@PathVariable UUID orderId) {
        // TODO: implement — JIRA story BD-XX
        return ResponseEntity.ok().build();
    }
}