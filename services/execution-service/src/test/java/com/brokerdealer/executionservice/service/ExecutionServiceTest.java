package com.brokerdealer.executionservice.service;

import com.brokerdealer.executionservice.domain.*;
import com.brokerdealer.executionservice.dto.request.OrderExecutionRequest;
import com.brokerdealer.executionservice.dto.response.ExecutionListResponse;
import com.brokerdealer.executionservice.dto.response.ExecutionResponse;
import com.brokerdealer.executionservice.exception.ResourceNotFoundException;
import com.brokerdealer.executionservice.mapper.ExecutionMapper;
import com.brokerdealer.executionservice.repository.ExecutionRepository;
import com.brokerdealer.executionservice.repository.PendingOrderExecutionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExecutionServiceTest {

    @Mock
    private ExecutionRepository executionRepository;
    @Mock
    private PendingOrderExecutionRepository pendingRepository;
    @Mock
    private FillSimulationService fillSimulationService;
    @Mock
    private ExecutionMapper executionMapper;

    @InjectMocks
    private ExecutionService executionService;

    private static final UUID ORDER_ID = UUID.fromString("a1000000-0000-0000-0000-000000000001");
    private static final UUID CLIENT_ID = UUID.fromString("b2000000-0000-0000-0000-000000000001");

    @Test
    void shouldReturnExecutionListForKnownOrderId() {
        Execution execution = buildExecution(ORDER_ID);
        ExecutionResponse response = buildExecutionResponse();
        when(executionRepository.findByOrderIdOrderByExecutedAtAsc(ORDER_ID)).thenReturn(List.of(execution));
        when(executionMapper.toResponse(execution)).thenReturn(response);

        ExecutionListResponse result = executionService.getExecutionsByOrderId(ORDER_ID);

        assertThat(result.getData()).hasSize(1);
        assertThat(result.getData().get(0).getFillType()).isEqualTo("FULL");
        assertThat(result.getMeta()).isNotNull();
        assertThat(result.getMeta().getTimestamp()).isNotNull();
        assertThat(result.getMeta().getRequestId()).isNotNull();
    }

    @Test
    void shouldThrowResourceNotFoundForUnknownOrderId() {
        when(executionRepository.findByOrderIdOrderByExecutedAtAsc(ORDER_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> executionService.getExecutionsByOrderId(ORDER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(ORDER_ID.toString());
    }

    @Test
    void shouldSkipProcessingWhenExecutionAlreadyExistsForOrderId() {
        OrderExecutionRequest request = buildRequest(OrderType.MARKET);
        when(executionRepository.existsByOrderId(ORDER_ID)).thenReturn(true);

        executionService.processFill(request);

        verifyNoInteractions(fillSimulationService);
    }

    @Test
    void shouldSkipProcessingWhenPendingAlreadyExistsForOrderId() {
        OrderExecutionRequest request = buildRequest(OrderType.LIMIT);
        when(executionRepository.existsByOrderId(ORDER_ID)).thenReturn(false);
        when(pendingRepository.existsByOrderId(ORDER_ID)).thenReturn(true);

        executionService.processFill(request);

        verifyNoInteractions(fillSimulationService);
    }

    @Test
    void shouldRouteMarketOrderToMarketFill() {
        OrderExecutionRequest request = buildRequest(OrderType.MARKET);
        when(executionRepository.existsByOrderId(ORDER_ID)).thenReturn(false);
        when(pendingRepository.existsByOrderId(ORDER_ID)).thenReturn(false);

        executionService.processFill(request);

        verify(fillSimulationService).executeMarketFill(request);
        verify(fillSimulationService, never()).initiateLimitFill(any());
    }

    @Test
    void shouldRouteLimitOrderToLimitFill() {
        OrderExecutionRequest request = buildRequest(OrderType.LIMIT);
        when(executionRepository.existsByOrderId(ORDER_ID)).thenReturn(false);
        when(pendingRepository.existsByOrderId(ORDER_ID)).thenReturn(false);

        executionService.processFill(request);

        verify(fillSimulationService).initiateLimitFill(request);
        verify(fillSimulationService, never()).executeMarketFill(any());
    }

    private OrderExecutionRequest buildRequest(OrderType orderType) {
        OrderExecutionRequest req = new OrderExecutionRequest();
        req.setOrderId(ORDER_ID);
        req.setClientId(CLIENT_ID);
        req.setSymbol("AAPL");
        req.setSide(OrderSide.BUY);
        req.setOrderType(orderType);
        req.setQuantity(100);
        req.setLimitPrice(new BigDecimal("175.00"));
        req.setOrderValue(new BigDecimal("17500.00"));
        req.setLastPrice(new BigDecimal("174.50"));
        return req;
    }

    private Execution buildExecution(UUID orderId) {
        return Execution.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .clientId(CLIENT_ID)
                .symbol("AAPL")
                .side(OrderSide.BUY)
                .executedQty(100)
                .executedPrice(new BigDecimal("174.50"))
                .fillType(FillType.FULL)
                .fillValue(new BigDecimal("17450.00"))
                .remainingQuantity(0)
                .marketPriceAtFill(new BigDecimal("174.50"))
                .executionVenue("MOCK_ECN")
                .executedAt(Instant.now())
                .build();
    }

    private ExecutionResponse buildExecutionResponse() {
        return ExecutionResponse.builder()
                .executionId(UUID.randomUUID().toString())
                .orderId(ORDER_ID.toString())
                .symbol("AAPL")
                .side("BUY")
                .executedQty(100)
                .executedPrice("174.50")
                .fillType("FULL")
                .fillValue("17450.00")
                .remainingQuantity(0)
                .marketPriceAtFill("174.50")
                .executionVenue("MOCK_ECN")
                .executedAt(Instant.now().toString())
                .build();
    }
}
