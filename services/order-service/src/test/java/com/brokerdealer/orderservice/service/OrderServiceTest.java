package com.brokerdealer.orderservice.service;

import com.brokerdealer.orderservice.client.ClientServiceClient;
import com.brokerdealer.orderservice.client.ExecutionServiceClient;
import com.brokerdealer.orderservice.client.MarketDataServiceClient;
import com.brokerdealer.orderservice.domain.ExpiryApplied;
import com.brokerdealer.orderservice.domain.Order;
import com.brokerdealer.orderservice.domain.OrderSide;
import com.brokerdealer.orderservice.domain.OrderStatus;
import com.brokerdealer.orderservice.domain.OrderType;
import com.brokerdealer.orderservice.domain.TimeInForce;
import com.brokerdealer.orderservice.dto.client.ClientResponse;
import com.brokerdealer.orderservice.dto.execution.ExecutionItem;
import com.brokerdealer.orderservice.dto.kafka.OrderStateChangedEvent;
import com.brokerdealer.orderservice.dto.kafka.RiskEvaluationRequestedEvent;
import com.brokerdealer.orderservice.dto.market.InstrumentResponse;
import com.brokerdealer.orderservice.dto.market.MarketHoursResponse;
import com.brokerdealer.orderservice.dto.request.SubmitOrderRequest;
import com.brokerdealer.orderservice.dto.response.OrderDetailResponse;
import com.brokerdealer.orderservice.dto.response.OrderListResponse;
import com.brokerdealer.orderservice.dto.response.SubmitOrderResponse;
import com.brokerdealer.orderservice.exception.ClientInactiveException;
import com.brokerdealer.orderservice.exception.ClientNotFoundException;
import com.brokerdealer.orderservice.exception.InsufficientBuyingPowerException;
import com.brokerdealer.orderservice.exception.InvalidExpiryDateException;
import com.brokerdealer.orderservice.exception.MarketClosedException;
import com.brokerdealer.orderservice.exception.OrderNotFoundException;
import com.brokerdealer.orderservice.exception.ServiceUnavailableException;
import com.brokerdealer.orderservice.exception.SymbolHaltedException;
import com.brokerdealer.orderservice.exception.SymbolNotFoundException;
import com.brokerdealer.orderservice.exception.ValidationException;
import com.brokerdealer.orderservice.kafka.producer.OrderEventProducer;
import com.brokerdealer.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ClientServiceClient clientServiceClient;
    @Mock
    private MarketDataServiceClient marketDataServiceClient;
    @Mock
    private OrderEventProducer orderEventProducer;
    @Mock
    private ExecutionServiceClient executionServiceClient;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private OrderService orderService;

    private final UUID clientId = UUID.fromString("a1000000-0000-0000-0000-000000000001");
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // Default: market open
        MarketHoursResponse.MarketHoursData hoursData = new MarketHoursResponse.MarketHoursData();
        hoursData.setMarketOpen(true);
        MarketHoursResponse hoursResponse = new MarketHoursResponse();
        hoursResponse.setData(hoursData);
        when(marketDataServiceClient.getMarketHours()).thenReturn(hoursResponse);

        // Default: client active
        ClientResponse.ClientData clientData = new ClientResponse.ClientData();
        clientData.setClientId(clientId);
        clientData.setStatus("ACTIVE");
        clientData.setTradingEnabled(true);
        ClientResponse clientResponse = new ClientResponse();
        clientResponse.setData(clientData);
        when(clientServiceClient.getClient(clientId)).thenReturn(clientResponse);

        // Default: symbol tradable, not halted
        InstrumentResponse.InstrumentData instrData = new InstrumentResponse.InstrumentData();
        instrData.setSymbol("AAPL");
        instrData.setTradable(true);
        instrData.setHalted(false);
        instrData.setMinQuantity(1);
        instrData.setMaxQuantity(10000);
        instrData.setLastPrice("150.00");
        InstrumentResponse instrResponse = new InstrumentResponse();
        instrResponse.setData(instrData);
        when(marketDataServiceClient.getInstrument("AAPL")).thenReturn(instrResponse);

        // Default: no holidays (so any future weekday is valid)
        when(marketDataServiceClient.getHolidays()).thenReturn(Collections.emptyList());

        // Default: Redis returns nothing (cache miss)
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(any())).thenReturn(null);

        // Default: execution-service returns no fills
        when(executionServiceClient.getFills(any())).thenReturn(Collections.emptyList());

        // Default repository: save passes through with generated ID
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            return Order.builder()
                    .id(UUID.randomUUID())
                    .clientOrderId(o.getClientOrderId())
                    .clientId(o.getClientId())
                    .symbol(o.getSymbol())
                    .side(o.getSide())
                    .orderType(o.getOrderType())
                    .timeInForce(o.getTimeInForce())
                    .quantity(o.getQuantity())
                    .limitPrice(o.getLimitPrice())
                    .status(o.getStatus())
                    .expiryDate(o.getExpiryDate())
                    .expiryApplied(o.getExpiryApplied())
                    .createdBy(o.getCreatedBy())
                    .build();
        });
    }

    // --- Happy path ---

    @Test
    void shouldSubmitMarketBuyOrderSuccessfully() {
        SubmitOrderResponse response = orderService.submitOrder(marketBuyRequest(), userId);

        assertThat(response.getData().getOrderId()).isNotNull();
        assertThat(response.getData().getStatus()).isEqualTo(OrderStatus.NEW.name());
        assertThat(response.getData().getExpiryDate()).isNull();
        assertThat(response.getMeta().getTimestamp()).isNotNull();
        assertThat(response.getMeta().getRequestId()).isNotNull();
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    void shouldSubmitLimitBuyOrderSuccessfully() {
        SubmitOrderResponse response = orderService.submitOrder(limitBuyRequest(), userId);

        assertThat(response.getData().getStatus()).isEqualTo(OrderStatus.NEW.name());
        assertThat(response.getData().getExpiryDate()).isNull();
    }

    @Test
    void shouldApplyDefaultExpiryForGtdOrder() {
        SubmitOrderRequest request = marketBuyRequest();
        request.setTimeInForce("GTD");

        SubmitOrderResponse response = orderService.submitOrder(request, userId);

        assertThat(response.getData().getExpiryDate()).isEqualTo(LocalDate.now().plusDays(180));
        assertThat(response.getData().getExpiryApplied()).isEqualTo(ExpiryApplied.DEFAULT.name());
    }

    @Test
    void shouldApplyTraderExpiryForGtdOrderWithDate() {
        SubmitOrderRequest request = marketBuyRequest();
        request.setTimeInForce("GTD");
        LocalDate futureDate = nextWeekday(LocalDate.now().plusDays(10));
        request.setExpiryDate(futureDate);

        SubmitOrderResponse response = orderService.submitOrder(request, userId);

        assertThat(response.getData().getExpiryDate()).isEqualTo(futureDate);
        assertThat(response.getData().getExpiryApplied()).isEqualTo(ExpiryApplied.TRADER.name());
    }

    @Test
    void shouldNotSetExpiryForDayOrder() {
        SubmitOrderResponse response = orderService.submitOrder(marketBuyRequest(), userId);

        assertThat(response.getData().getExpiryDate()).isNull();
        assertThat(response.getData().getExpiryApplied()).isNull();
    }

    // --- Kafka publishing ---

    @Test
    void shouldPublishOrderStateChangedAfterOrderPersisted() {
        orderService.submitOrder(marketBuyRequest(), userId);

        ArgumentCaptor<OrderStateChangedEvent> captor = ArgumentCaptor.forClass(OrderStateChangedEvent.class);
        verify(orderEventProducer).publishOrderStateChanged(captor.capture());

        OrderStateChangedEvent event = captor.getValue();
        assertThat(event.getToState()).isEqualTo(OrderStatus.NEW.name());
        assertThat(event.getFromState()).isNull();
        assertThat(event.getTriggeredBy()).isEqualTo("USER:" + userId);
        assertThat(event.getOrderId()).isNotNull();
        assertThat(event.getTimestamp()).isNotNull();
    }

    @Test
    void shouldPublishRiskEvaluationRequestedAfterOrderPersisted() {
        orderService.submitOrder(marketBuyRequest(), userId);

        ArgumentCaptor<RiskEvaluationRequestedEvent> captor = ArgumentCaptor.forClass(RiskEvaluationRequestedEvent.class);
        verify(orderEventProducer).publishRiskEvaluationRequested(captor.capture());

        RiskEvaluationRequestedEvent event = captor.getValue();
        assertThat(event.getSymbol()).isEqualTo("AAPL");
        assertThat(event.getSide()).isEqualTo("BUY");
        assertThat(event.getOrderType()).isEqualTo("MARKET");
        assertThat(event.getQuantity()).isEqualTo(10);
        assertThat(event.getOrderValue()).isNotNull();
        assertThat(event.getOrderId()).isNotNull();
    }

    @Test
    void shouldComputeOrderValueFromLimitPriceForLimitOrders() {
        orderService.submitOrder(limitBuyRequest(), userId);

        ArgumentCaptor<RiskEvaluationRequestedEvent> captor = ArgumentCaptor.forClass(RiskEvaluationRequestedEvent.class);
        verify(orderEventProducer).publishRiskEvaluationRequested(captor.capture());

        // limitPrice=150.00, qty=10 → orderValue=1500.00
        assertThat(captor.getValue().getOrderValue()).isEqualTo("1500.00");
    }

    @Test
    void shouldComputeOrderValueFromLastPriceForMarketOrders() {
        // lastPrice=150.00 (from setUp), qty=10 → orderValue=1500.00
        orderService.submitOrder(marketBuyRequest(), userId);

        ArgumentCaptor<RiskEvaluationRequestedEvent> captor = ArgumentCaptor.forClass(RiskEvaluationRequestedEvent.class);
        verify(orderEventProducer).publishRiskEvaluationRequested(captor.capture());

        assertThat(captor.getValue().getOrderValue()).isEqualTo("1500.00");
    }

    // --- L1 validation failures (no Kafka) ---

    @Test
    void shouldThrowValidationExceptionWhenLimitPriceMissingForLimitOrder() {
        SubmitOrderRequest request = marketBuyRequest();
        request.setOrderType("LIMIT");
        request.setLimitPrice(null);

        assertThatThrownBy(() -> orderService.submitOrder(request, userId))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("limitPrice is required");
        verify(orderRepository, never()).save(any());
        verify(orderEventProducer, never()).publishOrderStateChanged(any());
    }

    @Test
    void shouldThrowValidationExceptionWhenLimitPricePresentForMarketOrder() {
        SubmitOrderRequest request = marketBuyRequest();
        request.setLimitPrice("100.00");

        assertThatThrownBy(() -> orderService.submitOrder(request, userId))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("limitPrice must not be provided");
        verify(orderRepository, never()).save(any());
    }

    @Test
    void shouldThrowValidationExceptionForInvalidSide() {
        SubmitOrderRequest request = marketBuyRequest();
        request.setSide("INVALID");

        assertThatThrownBy(() -> orderService.submitOrder(request, userId))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Invalid side");
    }

    @Test
    void shouldThrowValidationExceptionForInvalidTimeInForce() {
        SubmitOrderRequest request = marketBuyRequest();
        request.setTimeInForce("INVALID");

        assertThatThrownBy(() -> orderService.submitOrder(request, userId))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Invalid timeInForce");
    }

    @Test
    void shouldIncludeTifAndExpiresAtInGtdResponse() {
        SubmitOrderRequest request = marketBuyRequest();
        request.setTimeInForce("GTD");
        LocalDate futureDate = nextWeekday(LocalDate.now().plusDays(10));
        request.setExpiryDate(futureDate);
        String expectedExpiresAt = ZonedDateTime.of(futureDate, LocalTime.of(16, 0),
                ZoneId.of("America/New_York")).toInstant().toString();

        SubmitOrderResponse response = orderService.submitOrder(request, userId);

        assertThat(response.getData().getTif()).isEqualTo("GTD");
        assertThat(response.getData().getExpiresAt()).isEqualTo(expectedExpiresAt);
    }

    @Test
    void shouldIncludeTifInDayOrderResponse() {
        SubmitOrderResponse response = orderService.submitOrder(marketBuyRequest(), userId);

        assertThat(response.getData().getTif()).isEqualTo("DAY");
        assertThat(response.getData().getExpiresAt()).isNull();
    }

    @Test
    void shouldRejectGtcTimeInForceWithValidationException() {
        SubmitOrderRequest request = marketBuyRequest();
        request.setTimeInForce("GTC");

        assertThatThrownBy(() -> orderService.submitOrder(request, userId))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Invalid timeInForce");
        verify(orderRepository, never()).save(any());
    }

    @Test
    void shouldThrowInvalidExpiryDateExceptionForTodayDate() {
        SubmitOrderRequest request = marketBuyRequest();
        request.setTimeInForce("GTD");
        request.setExpiryDate(LocalDate.now());

        assertThatThrownBy(() -> orderService.submitOrder(request, userId))
                .isInstanceOf(InvalidExpiryDateException.class)
                .hasMessageContaining("future date");
        verify(orderRepository, never()).save(any());
    }

    @Test
    void shouldThrowInvalidExpiryDateExceptionForPastDate() {
        SubmitOrderRequest request = marketBuyRequest();
        request.setTimeInForce("GTD");
        request.setExpiryDate(LocalDate.now().minusDays(1));

        assertThatThrownBy(() -> orderService.submitOrder(request, userId))
                .isInstanceOf(InvalidExpiryDateException.class);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void shouldThrowInvalidExpiryDateExceptionForWeekendDate() {
        SubmitOrderRequest request = marketBuyRequest();
        request.setTimeInForce("GTD");
        // Find next Saturday
        LocalDate saturday = LocalDate.now().plusDays(1);
        while (saturday.getDayOfWeek().getValue() != 6) {
            saturday = saturday.plusDays(1);
        }
        request.setExpiryDate(saturday);

        assertThatThrownBy(() -> orderService.submitOrder(request, userId))
                .isInstanceOf(InvalidExpiryDateException.class);
    }

    @Test
    void shouldThrowInvalidExpiryDateExceptionForHoliday() {
        LocalDate holiday = nextWeekday(LocalDate.now().plusDays(10));
        when(marketDataServiceClient.getHolidays()).thenReturn(List.of(holiday.toString()));

        SubmitOrderRequest request = marketBuyRequest();
        request.setTimeInForce("GTD");
        request.setExpiryDate(holiday);

        assertThatThrownBy(() -> orderService.submitOrder(request, userId))
                .isInstanceOf(InvalidExpiryDateException.class)
                .hasMessageContaining("holiday");
        verify(orderRepository, never()).save(any());
    }

    @Test
    void shouldThrowServiceUnavailableWhenHolidaysCircuitBreakerOpen() {
        SubmitOrderRequest request = marketBuyRequest();
        request.setTimeInForce("GTD");
        request.setExpiryDate(nextWeekday(LocalDate.now().plusDays(10)));
        when(marketDataServiceClient.getHolidays())
                .thenThrow(new ServiceUnavailableException("marketdata-service"));

        assertThatThrownBy(() -> orderService.submitOrder(request, userId))
                .isInstanceOf(ServiceUnavailableException.class);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void shouldThrowValidationExceptionForInvalidTifFilter() {
        assertThatThrownBy(() -> orderService.getOrders(null, null, null, "GTC", 0, 50))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Invalid tif filter value");
    }

    // --- L2 validation failures ---

    @Test
    void shouldThrowMarketClosedExceptionWhenMarketClosed() {
        MarketHoursResponse.MarketHoursData data = new MarketHoursResponse.MarketHoursData();
        data.setMarketOpen(false);
        MarketHoursResponse closed = new MarketHoursResponse();
        closed.setData(data);
        when(marketDataServiceClient.getMarketHours()).thenReturn(closed);

        assertThatThrownBy(() -> orderService.submitOrder(marketBuyRequest(), userId))
                .isInstanceOf(MarketClosedException.class);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void shouldThrowClientNotFoundExceptionWhenClientReturnsNull() {
        when(clientServiceClient.getClient(clientId)).thenReturn(null);

        assertThatThrownBy(() -> orderService.submitOrder(marketBuyRequest(), userId))
                .isInstanceOf(ClientNotFoundException.class);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void shouldThrowClientInactiveExceptionWhenClientIsBlocked() {
        ClientResponse.ClientData clientData = new ClientResponse.ClientData();
        clientData.setStatus("BLOCKED");
        ClientResponse clientResponse = new ClientResponse();
        clientResponse.setData(clientData);
        when(clientServiceClient.getClient(clientId)).thenReturn(clientResponse);

        assertThatThrownBy(() -> orderService.submitOrder(marketBuyRequest(), userId))
                .isInstanceOf(ClientInactiveException.class);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void shouldThrowSymbolNotFoundExceptionWhenSymbolNotTradable() {
        InstrumentResponse.InstrumentData instrData = new InstrumentResponse.InstrumentData();
        instrData.setTradable(false);
        instrData.setHalted(false);
        InstrumentResponse instrResponse = new InstrumentResponse();
        instrResponse.setData(instrData);
        when(marketDataServiceClient.getInstrument("AAPL")).thenReturn(instrResponse);

        assertThatThrownBy(() -> orderService.submitOrder(marketBuyRequest(), userId))
                .isInstanceOf(SymbolNotFoundException.class);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void shouldThrowSymbolNotFoundExceptionWhenSymbolReturnsNull() {
        when(marketDataServiceClient.getInstrument("AAPL")).thenReturn(null);

        assertThatThrownBy(() -> orderService.submitOrder(marketBuyRequest(), userId))
                .isInstanceOf(SymbolNotFoundException.class);
    }

    @Test
    void shouldThrowSymbolHaltedExceptionWhenSymbolHalted() {
        InstrumentResponse.InstrumentData instrData = new InstrumentResponse.InstrumentData();
        instrData.setTradable(true);
        instrData.setHalted(true);
        InstrumentResponse instrResponse = new InstrumentResponse();
        instrResponse.setData(instrData);
        when(marketDataServiceClient.getInstrument("AAPL")).thenReturn(instrResponse);

        assertThatThrownBy(() -> orderService.submitOrder(marketBuyRequest(), userId))
                .isInstanceOf(SymbolHaltedException.class);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void shouldThrowServiceUnavailableWhenMarketHoursCircuitBreakerOpen() {
        when(marketDataServiceClient.getMarketHours())
                .thenThrow(new ServiceUnavailableException("marketdata-service"));

        assertThatThrownBy(() -> orderService.submitOrder(marketBuyRequest(), userId))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    void shouldThrowServiceUnavailableWhenClientServiceCircuitBreakerOpen() {
        when(clientServiceClient.getClient(clientId))
                .thenThrow(new ServiceUnavailableException("client-service"));

        assertThatThrownBy(() -> orderService.submitOrder(marketBuyRequest(), userId))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    // --- Cash reservation ---

    @Test
    void shouldReserveCashBeforeOrderPersistOnBuyLimitOrder() {
        // limitPrice=150.00, qty=10 → orderValue=1500.00
        orderService.submitOrder(limitBuyRequest(), userId);

        org.mockito.InOrder inOrder = inOrder(clientServiceClient, orderRepository);
        inOrder.verify(clientServiceClient).reserveCash(eq(clientId), eq(new BigDecimal("1500.00")));
        inOrder.verify(orderRepository).save(any(Order.class));
    }

    @Test
    void shouldSkipCashReservationForSellOrder() {
        SubmitOrderRequest request = marketBuyRequest();
        request.setSide("SELL");

        orderService.submitOrder(request, userId);

        verify(clientServiceClient, never()).reserveCash(any(), any());
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    void shouldRejectBuyOrderWhenReserveCashReturns422() {
        doThrow(new InsufficientBuyingPowerException())
                .when(clientServiceClient).reserveCash(eq(clientId), any(BigDecimal.class));

        assertThatThrownBy(() -> orderService.submitOrder(marketBuyRequest(), userId))
                .isInstanceOf(InsufficientBuyingPowerException.class);
        verify(orderRepository, never()).save(any());
        verify(orderEventProducer, never()).publishOrderStateChanged(any());
    }

    @Test
    void shouldReturn503WhenClientServiceUnavailableOnCashReservation() {
        doThrow(new ServiceUnavailableException("client-service"))
                .when(clientServiceClient).reserveCash(eq(clientId), any(BigDecimal.class));

        assertThatThrownBy(() -> orderService.submitOrder(marketBuyRequest(), userId))
                .isInstanceOf(ServiceUnavailableException.class);
        verify(orderRepository, never()).save(any());
    }

    // --- GET /orders/{id} ---

    @Test
    void shouldReturnOrderDetailOnCacheMiss() {
        UUID orderId = UUID.randomUUID();
        Order order = buildOrder(orderId, OrderStatus.NEW);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        OrderDetailResponse response = orderService.getOrderById(orderId);

        assertThat(response.getData().getOrderId()).isEqualTo(orderId);
        assertThat(response.getData().getStatus()).isEqualTo("NEW");
        assertThat(response.getData().getFilledQuantity()).isEqualTo(0);
        assertThat(response.getData().getFills()).isEmpty();
        assertThat(response.getMeta().getTimestamp()).isNotNull();
        verify(valueOperations).set(eq("order:state:" + orderId), eq(order));
    }

    @Test
    void shouldReturnOrderDetailFromCacheOnCacheHit() {
        UUID orderId = UUID.randomUUID();
        Order order = buildOrder(orderId, OrderStatus.RISK_CHECK_PASSED);
        when(valueOperations.get("order:state:" + orderId)).thenReturn(order);

        OrderDetailResponse response = orderService.getOrderById(orderId);

        assertThat(response.getData().getStatus()).isEqualTo("RISK_CHECK_PASSED");
        verify(orderRepository, never()).findById(any());
    }

    @Test
    void shouldIncludeFillsAndComputeAggregates() {
        UUID orderId = UUID.randomUUID();
        Order order = buildOrder(orderId, OrderStatus.FILLED);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        ExecutionItem fill1 = new ExecutionItem();
        fill1.setExecutionId("ex-1");
        fill1.setExecutedQty(5);
        fill1.setExecutedPrice("150.00");
        fill1.setFillType("PARTIAL");
        fill1.setExecutedAt(Instant.now().toString());

        ExecutionItem fill2 = new ExecutionItem();
        fill2.setExecutionId("ex-2");
        fill2.setExecutedQty(5);
        fill2.setExecutedPrice("152.00");
        fill2.setFillType("FULL");
        fill2.setExecutedAt(Instant.now().toString());

        when(executionServiceClient.getFills(orderId)).thenReturn(List.of(fill1, fill2));

        OrderDetailResponse response = orderService.getOrderById(orderId);

        assertThat(response.getData().getFilledQuantity()).isEqualTo(10);
        assertThat(response.getData().getAvgFillPrice()).isEqualTo("151.0000");
        assertThat(response.getData().getFills()).hasSize(2);
    }

    @Test
    void shouldThrowOrderNotFoundWhenOrderDoesNotExist() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrderById(orderId))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void shouldReturnEmptyFillsWhenExecutionServiceUnavailable() {
        UUID orderId = UUID.randomUUID();
        Order order = buildOrder(orderId, OrderStatus.SENT_TO_EXECUTION);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(executionServiceClient.getFills(orderId)).thenReturn(Collections.emptyList());

        OrderDetailResponse response = orderService.getOrderById(orderId);

        assertThat(response.getData().getFills()).isEmpty();
        assertThat(response.getData().getFilledQuantity()).isEqualTo(0);
        assertThat(response.getData().getAvgFillPrice()).isNull();
    }

    // --- GET /orders ---

    @Test
    void shouldReturnPaginatedOrderList() {
        Order order = buildOrder(UUID.randomUUID(), OrderStatus.NEW);
        when(orderRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(order)));

        OrderListResponse response = orderService.getOrders(null, null, null, null, 0, 50);

        assertThat(response.getData().getOrders()).hasSize(1);
        assertThat(response.getData().getTotalCount()).isEqualTo(1);
        assertThat(response.getData().getPage()).isEqualTo(0);
        assertThat(response.getData().getPageSize()).isEqualTo(50);
        assertThat(response.getMeta().getTimestamp()).isNotNull();
    }

    @Test
    void shouldCapPageSizeAt200() {
        when(orderRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        OrderListResponse response = orderService.getOrders(null, null, null, null, 0, 500);

        assertThat(response.getData().getPageSize()).isEqualTo(200);
    }

    @Test
    void shouldReturnEmptyListWhenNoOrdersMatch() {
        when(orderRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 50), 0));

        OrderListResponse response = orderService.getOrders("FILLED", clientId, null, null, 0, 50);

        assertThat(response.getData().getOrders()).isEmpty();
        assertThat(response.getData().getTotalCount()).isEqualTo(0);
        assertThat(response.getData().getTotalPages()).isEqualTo(0);
    }

    // --- Helpers ---

    private Order buildOrder(UUID id, OrderStatus status) {
        Instant now = Instant.now();
        return Order.builder()
                .id(id)
                .clientId(clientId)
                .symbol("AAPL")
                .side(OrderSide.BUY)
                .orderType(OrderType.LIMIT)
                .timeInForce(TimeInForce.DAY)
                .quantity(10)
                .limitPrice(new BigDecimal("150.00"))
                .status(status)
                .createdBy(userId)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    // --- Helpers ---

    private SubmitOrderRequest marketBuyRequest() {
        SubmitOrderRequest r = new SubmitOrderRequest();
        r.setClientOrderId("CLT-ORD-001");
        r.setClientId(clientId);
        r.setSymbol("AAPL");
        r.setSide("BUY");
        r.setOrderType("MARKET");
        r.setTimeInForce("DAY");
        r.setQuantity(10);
        return r;
    }

    private SubmitOrderRequest limitBuyRequest() {
        SubmitOrderRequest r = marketBuyRequest();
        r.setOrderType("LIMIT");
        r.setLimitPrice("150.00");
        return r;
    }

    private LocalDate nextWeekday(LocalDate date) {
        while (date.getDayOfWeek().getValue() > 5) {
            date = date.plusDays(1);
        }
        return date;
    }
}
