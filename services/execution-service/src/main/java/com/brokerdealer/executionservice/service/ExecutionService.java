package com.brokerdealer.executionservice.service;

import com.brokerdealer.executionservice.domain.OrderType;
import com.brokerdealer.executionservice.dto.request.OrderExecutionRequest;
import com.brokerdealer.executionservice.dto.response.ExecutionListResponse;
import com.brokerdealer.executionservice.dto.response.ExecutionResponse;
import com.brokerdealer.executionservice.dto.response.ResponseMeta;
import com.brokerdealer.executionservice.exception.ResourceNotFoundException;
import com.brokerdealer.executionservice.mapper.ExecutionMapper;
import com.brokerdealer.executionservice.repository.ExecutionRepository;
import com.brokerdealer.executionservice.repository.PendingOrderExecutionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExecutionService {

    private final ExecutionRepository executionRepository;
    private final PendingOrderExecutionRepository pendingRepository;
    private final FillSimulationService fillSimulationService;
    private final ExecutionMapper executionMapper;

    public ExecutionListResponse getExecutionsByOrderId(UUID orderId) {
        List<ExecutionResponse> executions = executionRepository
                .findByOrderIdOrderByExecutedAtAsc(orderId)
                .stream()
                .map(executionMapper::toResponse)
                .toList();

        if (executions.isEmpty()) {
            throw new ResourceNotFoundException("ORDER_NOT_FOUND",
                    "No executions found for order: " + orderId);
        }

        return ExecutionListResponse.builder()
                .data(executions)
                .meta(ResponseMeta.builder()
                        .timestamp(Instant.now().toString())
                        .requestId(UUID.randomUUID().toString())
                        .build())
                .build();
    }

    public void processFill(OrderExecutionRequest request) {
        if (executionRepository.existsByOrderId(request.getOrderId())) {
            log.warn("Idempotency: execution already exists for orderId={}", request.getOrderId());
            return;
        }
        if (pendingRepository.existsByOrderId(request.getOrderId())) {
            log.warn("Idempotency: pending execution already exists for orderId={}", request.getOrderId());
            return;
        }

        log.info("Processing fill: orderId={} orderType={} symbol={} fillOrKill={}",
                request.getOrderId(), request.getOrderType(), request.getSymbol(), request.getFillOrKill());

        if (Boolean.TRUE.equals(request.getFillOrKill())) {
            fillSimulationService.executeFillOrKill(request);
        } else if (request.getOrderType() == OrderType.MARKET) {
            fillSimulationService.executeMarketFill(request);
        } else {
            fillSimulationService.initiateLimitFill(request);
        }
    }
}
