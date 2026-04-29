package com.brokerdealer.marketdataservice.websocket;

import com.brokerdealer.marketdataservice.domain.Instrument;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MarketDataWebSocketHandlerTest {

    @Mock
    private JwtValidator jwtValidator;

    @Mock
    private WebSocketSession session;

    private MarketDataWebSocketHandler handler;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        handler = new MarketDataWebSocketHandler(jwtValidator, objectMapper);
        lenient().when(session.getId()).thenReturn("test-session-1");
        lenient().when(session.isOpen()).thenReturn(true);
    }

    // ── afterConnectionEstablished ────────────────────────────────────────────

    @Test
    void shouldAddSessionToPendingOnConnect() {
        handler.afterConnectionEstablished(session);

        ConcurrentHashMap<String, ?> pending = (ConcurrentHashMap<String, ?>) ReflectionTestUtils.getField(handler, "pendingSessions");
        assertThat(pending).containsKey("test-session-1");
    }

    // ── AUTH — valid JWT ───────────────────────────────────────────────────────

    @Test
    void shouldMoveSessionToAuthenticatedOnValidAuth() throws Exception {
        when(jwtValidator.isValid("valid-token")).thenReturn(true);
        handler.afterConnectionEstablished(session);

        handler.handleTextMessage(session, new TextMessage("{\"type\":\"AUTH\",\"token\":\"Bearer valid-token\"}"));

        ConcurrentHashMap<String, ?> authenticated = (ConcurrentHashMap<String, ?>) ReflectionTestUtils.getField(handler, "authenticatedSessions");
        ConcurrentHashMap<String, ?> pending = (ConcurrentHashMap<String, ?>) ReflectionTestUtils.getField(handler, "pendingSessions");
        assertThat(authenticated).containsKey("test-session-1");
        assertThat(pending).doesNotContainKey("test-session-1");

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());
        assertThat(captor.getValue().getPayload()).contains("AUTH_OK");
    }

    @Test
    void shouldStripBearerPrefixBeforeValidation() throws Exception {
        when(jwtValidator.isValid("raw-token")).thenReturn(true);
        handler.afterConnectionEstablished(session);

        handler.handleTextMessage(session, new TextMessage("{\"type\":\"AUTH\",\"token\":\"Bearer raw-token\"}"));

        verify(jwtValidator).isValid("raw-token");
    }

    // ── AUTH — invalid JWT ────────────────────────────────────────────────────

    @Test
    void shouldCloseWithCode4001OnInvalidJwt() throws Exception {
        when(jwtValidator.isValid(any())).thenReturn(false);
        handler.afterConnectionEstablished(session);

        handler.handleTextMessage(session, new TextMessage("{\"type\":\"AUTH\",\"token\":\"Bearer bad-token\"}"));

        ArgumentCaptor<CloseStatus> captor = ArgumentCaptor.forClass(CloseStatus.class);
        verify(session).close(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo(4001);

        ConcurrentHashMap<String, ?> authenticated = (ConcurrentHashMap<String, ?>) ReflectionTestUtils.getField(handler, "authenticatedSessions");
        assertThat(authenticated).doesNotContainKey("test-session-1");
    }

    // ── Auth timeout ──────────────────────────────────────────────────────────

    @Test
    void shouldCloseUnauthenticatedSessionAfterFiveSeconds() throws Exception {
        handler.afterConnectionEstablished(session);

        // Backdate the connect time by 6 seconds to simulate timeout
        ConcurrentHashMap<String, Instant> connectTimes = (ConcurrentHashMap<String, Instant>)
                ReflectionTestUtils.getField(handler, "pendingConnectTimes");
        connectTimes.put("test-session-1", Instant.now().minusSeconds(6));

        handler.closeUnauthenticatedSessions();

        ArgumentCaptor<CloseStatus> captor = ArgumentCaptor.forClass(CloseStatus.class);
        verify(session).close(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo(4001);

        ConcurrentHashMap<String, ?> pending = (ConcurrentHashMap<String, ?>) ReflectionTestUtils.getField(handler, "pendingSessions");
        assertThat(pending).doesNotContainKey("test-session-1");
    }

    @Test
    void shouldNotCloseSessionThatAuthenticatedWithinFiveSeconds() throws Exception {
        handler.afterConnectionEstablished(session);

        // connect time is NOW — not timed out
        handler.closeUnauthenticatedSessions();

        verify(session, never()).close(any(CloseStatus.class));
    }

    // ── broadcastTick ─────────────────────────────────────────────────────────

    @Test
    void shouldBroadcastTickToAuthenticatedSessions() throws Exception {
        // Manually put into authenticated
        ConcurrentHashMap<String, WebSocketSession> authenticated = (ConcurrentHashMap<String, WebSocketSession>)
                ReflectionTestUtils.getField(handler, "authenticatedSessions");
        authenticated.put("test-session-1", session);

        Instrument instrument = buildInstrument("AAPL");

        handler.broadcastTick(instrument);

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());
        String payload = captor.getValue().getPayload();
        assertThat(payload).contains("MARKET_DATA_TICK");
        assertThat(payload).contains("AAPL");
        assertThat(payload).contains("lastPrice");
        assertThat(payload).contains("bidPrice");
        assertThat(payload).contains("askPrice");
        assertThat(payload).contains("change");
        assertThat(payload).contains("changePct");
        assertThat(payload).contains("volume");
    }

    @Test
    void shouldNotSendWhenNoAuthenticatedSessions() throws Exception {
        handler.broadcastTick(buildInstrument("AAPL"));
        verify(session, never()).sendMessage(any());
    }

    // ── broadcastMarketStatus ─────────────────────────────────────────────────

    @Test
    void shouldBroadcastMarketOpenEvent() throws Exception {
        ConcurrentHashMap<String, WebSocketSession> authenticated = (ConcurrentHashMap<String, WebSocketSession>)
                ReflectionTestUtils.getField(handler, "authenticatedSessions");
        authenticated.put("test-session-1", session);

        handler.broadcastMarketStatus("MARKET_OPEN", "OPEN");

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());
        String payload = captor.getValue().getPayload();
        assertThat(payload).contains("MARKET_OPEN");
        assertThat(payload).contains("OPEN");
    }

    // ── ping / pong ───────────────────────────────────────────────────────────

    @Test
    void shouldSendPingToAuthenticatedSessions() throws Exception {
        ConcurrentHashMap<String, WebSocketSession> authenticated = (ConcurrentHashMap<String, WebSocketSession>)
                ReflectionTestUtils.getField(handler, "authenticatedSessions");
        authenticated.put("test-session-1", session);

        ConcurrentHashMap<String, Instant> pongTimes = (ConcurrentHashMap<String, Instant>)
                ReflectionTestUtils.getField(handler, "lastPongTime");
        pongTimes.put("test-session-1", Instant.now()); // recent pong — no timeout

        handler.pingAndCheckPong();

        verify(session).sendMessage(any(PingMessage.class));
    }

    @Test
    void shouldCloseSessionOnPongTimeout() throws Exception {
        ConcurrentHashMap<String, WebSocketSession> authenticated = (ConcurrentHashMap<String, WebSocketSession>)
                ReflectionTestUtils.getField(handler, "authenticatedSessions");
        authenticated.put("test-session-1", session);

        ConcurrentHashMap<String, Instant> pongTimes = (ConcurrentHashMap<String, Instant>)
                ReflectionTestUtils.getField(handler, "lastPongTime");
        pongTimes.put("test-session-1", Instant.now().minusSeconds(50)); // 50s ago — timeout

        handler.pingAndCheckPong();

        verify(session).close(any(CloseStatus.class));
        verify(session, never()).sendMessage(any(PingMessage.class));

        assertThat(authenticated).doesNotContainKey("test-session-1");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Instrument buildInstrument(String symbol) {
        return Instrument.builder()
                .symbol(symbol)
                .lastPrice(new BigDecimal("175.00"))
                .bidPrice(new BigDecimal("174.95"))
                .askPrice(new BigDecimal("175.05"))
                .previousClose(new BigDecimal("174.00"))
                .volume(1_000_000L)
                .tradable(true)
                .tradingHalted(false)
                .dataTimestamp(Instant.now())
                .build();
    }
}
