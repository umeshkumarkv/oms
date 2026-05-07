package com.brokerdealer.executionservice.kafka.consumer;

import com.brokerdealer.executionservice.dto.request.OrderExecutionRequest;
import com.brokerdealer.executionservice.domain.OrderSide;
import com.brokerdealer.executionservice.domain.OrderType;
import com.brokerdealer.executionservice.service.ExecutionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderExecutionConsumerTest {

    @Mock
    private ExecutionService executionService;

    @InjectMocks
    private OrderExecutionConsumer consumer;

    @Test
    void shouldDelegateToExecutionServiceOnValidMessage() {
        OrderExecutionRequest request = buildRequest();

        consumer.consumeOrderExecutionRequested(request);

        verify(executionService).processFill(request);
    }

    @Test
    void shouldNotThrowOnNullMessage() {
        consumer.consumeOrderExecutionRequested(null);

        verifyNoInteractions(executionService);
    }

    @Test
    void shouldNotThrowWhenOrderIdIsNull() {
        OrderExecutionRequest request = new OrderExecutionRequest();
        request.setOrderId(null);

        consumer.consumeOrderExecutionRequested(request);

        verifyNoInteractions(executionService);
    }

    private OrderExecutionRequest buildRequest() {
        OrderExecutionRequest req = new OrderExecutionRequest();
        req.setOrderId(UUID.fromString("a1000000-0000-0000-0000-000000000001"));
        req.setClientId(UUID.fromString("b2000000-0000-0000-0000-000000000001"));
        req.setSymbol("AAPL");
        req.setSide(OrderSide.BUY);
        req.setOrderType(OrderType.MARKET);
        req.setQuantity(100);
        req.setOrderValue(new BigDecimal("17550.00"));
        req.setLastPrice(new BigDecimal("175.50"));
        return req;
    }
}
