package com.brokerdealer.orderservice.service;

import com.brokerdealer.orderservice.client.ClientServiceClient;
import com.brokerdealer.orderservice.client.MarketDataServiceClient;
import com.brokerdealer.orderservice.domain.ExpiryApplied;
import com.brokerdealer.orderservice.domain.Order;
import com.brokerdealer.orderservice.domain.OrderStatus;
import com.brokerdealer.orderservice.dto.client.ClientResponse;
import com.brokerdealer.orderservice.dto.market.InstrumentResponse;
import com.brokerdealer.orderservice.dto.market.MarketHoursResponse;
import com.brokerdealer.orderservice.dto.request.SubmitOrderRequest;
import com.brokerdealer.orderservice.dto.response.SubmitOrderResponse;
import com.brokerdealer.orderservice.exception.ClientInactiveException;
import com.brokerdealer.orderservice.exception.ClientNotFoundException;
import com.brokerdealer.orderservice.exception.InvalidExpiryDateException;
import com.brokerdealer.orderservice.exception.MarketClosedException;
import com.brokerdealer.orderservice.exception.ServiceUnavailableException;
import com.brokerdealer.orderservice.exception.SymbolHaltedException;
import com.brokerdealer.orderservice.exception.SymbolNotFoundException;
import com.brokerdealer.orderservice.exception.ValidationException;
import com.brokerdealer.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
    void shouldApplyDefaultExpiryForGtcOrder() {
        SubmitOrderRequest request = marketBuyRequest();
        request.setTimeInForce("GTC");

        SubmitOrderResponse response = orderService.submitOrder(request, userId);

        assertThat(response.getData().getExpiryDate()).isEqualTo(LocalDate.now().plusDays(180));
        assertThat(response.getData().getExpiryApplied()).isEqualTo(ExpiryApplied.DEFAULT.name());
    }

    @Test
    void shouldApplyTraderExpiryForGtcOrderWithDate() {
        SubmitOrderRequest request = marketBuyRequest();
        request.setTimeInForce("GTC");
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

    // --- L1 validation failures ---

    @Test
    void shouldThrowValidationExceptionWhenLimitPriceMissingForLimitOrder() {
        SubmitOrderRequest request = marketBuyRequest();
        request.setOrderType("LIMIT");
        request.setLimitPrice(null);

        assertThatThrownBy(() -> orderService.submitOrder(request, userId))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("limitPrice is required");
        verify(orderRepository, never()).save(any());
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
        request.setTimeInForce("GTD");

        assertThatThrownBy(() -> orderService.submitOrder(request, userId))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Invalid timeInForce");
    }

    @Test
    void shouldThrowInvalidExpiryDateExceptionForPastDate() {
        SubmitOrderRequest request = marketBuyRequest();
        request.setTimeInForce("GTC");
        request.setExpiryDate(LocalDate.now().minusDays(1));

        assertThatThrownBy(() -> orderService.submitOrder(request, userId))
                .isInstanceOf(InvalidExpiryDateException.class);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void shouldThrowInvalidExpiryDateExceptionForWeekendDate() {
        SubmitOrderRequest request = marketBuyRequest();
        request.setTimeInForce("GTC");
        // Find next Saturday
        LocalDate saturday = LocalDate.now().plusDays(1);
        while (saturday.getDayOfWeek().getValue() != 6) {
            saturday = saturday.plusDays(1);
        }
        request.setExpiryDate(saturday);

        assertThatThrownBy(() -> orderService.submitOrder(request, userId))
                .isInstanceOf(InvalidExpiryDateException.class);
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
