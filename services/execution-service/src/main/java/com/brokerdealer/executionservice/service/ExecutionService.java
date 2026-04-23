package com.brokerdealer.executionservice.service;

import com.brokerdealer.executionservice.repository.ExecutionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExecutionService {

    private final ExecutionRepository executionRepository;

    public Optional<Object> getExecutionsByOrderId(UUID orderId) {
        // TODO: implement — JIRA story BD-XX
        return Optional.empty();
    }
}
