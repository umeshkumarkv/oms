package com.brokerdealer.marketdataservice.websocket;

import com.brokerdealer.marketdataservice.domain.Instrument;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class MarketDataWebSocketHandler extends TextWebSocketHandler {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtValidator jwtValidator;
    private final ObjectMapper objectMapper;

    // sessions connected but not yet authenticated
    private final ConcurrentHashMap<String, WebSocketSession> pendingSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> pendingConnectTimes = new ConcurrentHashMap<>();

    // sessions that have passed AUTH
    private final ConcurrentHashMap<String, WebSocketSession> authenticatedSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> lastPongTime = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        pendingSessions.put(session.getId(), session);
        pendingConnectTimes.put(session.getId(), Instant.now());
        log.debug("WS connected: sessionId={}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode node = objectMapper.readTree(message.getPayload());
        String type = node.path("type").asText();
        if ("AUTH".equals(type)) {
            handleAuth(session, node.path("token").asText());
        }
    }

    @Override
    protected void handlePongMessage(WebSocketSession session, PongMessage message) {
        lastPongTime.put(session.getId(), Instant.now());
        log.debug("Pong received: sessionId={}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        removeSession(session.getId());
        log.debug("WS closed: sessionId={} status={}", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        removeSession(session.getId());
        log.warn("WS transport error: sessionId={} error={}", session.getId(), exception.getMessage());
    }

    // ── Auth ──────────────────────────────────────────────────────────────────

    private void handleAuth(WebSocketSession session, String tokenValue) throws IOException {
        String token = tokenValue.startsWith(BEARER_PREFIX)
                ? tokenValue.substring(BEARER_PREFIX.length())
                : tokenValue;

        if (!jwtValidator.isValid(token)) {
            log.warn("WS auth failed: sessionId={}", session.getId());
            safeClose(session, new CloseStatus(4001, "Invalid token"));
            removeSession(session.getId());
            return;
        }

        pendingSessions.remove(session.getId());
        pendingConnectTimes.remove(session.getId());
        authenticatedSessions.put(session.getId(), session);
        lastPongTime.put(session.getId(), Instant.now());

        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of("type", "AUTH_OK"))));
        log.info("WS auth OK: sessionId={}", session.getId());
    }

    // ── Broadcasts ────────────────────────────────────────────────────────────

    public void broadcastTick(Instrument instrument) {
        if (authenticatedSessions.isEmpty()) {
            return;
        }
        BigDecimal lastPrice = instrument.getLastPrice();
        BigDecimal previousClose = instrument.getPreviousClose();
        BigDecimal change = BigDecimal.ZERO;
        BigDecimal changePct = BigDecimal.ZERO;
        if (previousClose != null && previousClose.compareTo(BigDecimal.ZERO) != 0) {
            change = lastPrice.subtract(previousClose).setScale(4, RoundingMode.HALF_UP);
            changePct = change
                    .divide(previousClose, 6, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"))
                    .setScale(4, RoundingMode.HALF_UP);
        }

        Map<String, Object> tick = new LinkedHashMap<>();
        tick.put("type", "MARKET_DATA_TICK");
        tick.put("symbol", instrument.getSymbol());
        tick.put("lastPrice", lastPrice.toPlainString());
        tick.put("bidPrice", instrument.getBidPrice().toPlainString());
        tick.put("askPrice", instrument.getAskPrice().toPlainString());
        tick.put("change", change.toPlainString());
        tick.put("changePct", changePct.toPlainString());
        tick.put("volume", instrument.getVolume());
        tick.put("timestamp", Instant.now().toString());

        broadcast(tick);
    }

    public void broadcastMarketStatus(String type, String status) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", type);
        event.put("marketStatus", status);
        event.put("timestamp", Instant.now().toString());
        broadcast(event);
    }

    private void broadcast(Map<String, Object> event) {
        String json;
        try {
            json = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize WS event: {}", e.getMessage());
            return;
        }
        TextMessage message = new TextMessage(json);
        authenticatedSessions.forEach((id, session) -> {
            try {
                if (session.isOpen()) {
                    session.sendMessage(message);
                }
            } catch (IOException e) {
                log.warn("Failed to send WS message to sessionId={}: {}", id, e.getMessage());
                removeSession(id);
            }
        });
    }

    // ── Scheduled maintenance ─────────────────────────────────────────────────

    // Close unauthenticated sessions that have not sent AUTH within 5 seconds
    @Scheduled(fixedDelay = 1000)
    public void closeUnauthenticatedSessions() {
        Instant cutoff = Instant.now().minusSeconds(5);
        pendingConnectTimes.forEach((id, connectTime) -> {
            if (connectTime.isBefore(cutoff)) {
                WebSocketSession session = pendingSessions.get(id);
                if (session != null) {
                    safeClose(session, new CloseStatus(4001, "Authentication timeout"));
                }
                removeSession(id);
                log.info("WS auth timeout: sessionId={}", id);
            }
        });
    }

    // Send ping every 30s; disconnect if no pong received within 40s (30s + 10s grace)
    @Scheduled(fixedDelay = 30000)
    public void pingAndCheckPong() {
        Instant cutoff = Instant.now().minusSeconds(40);
        authenticatedSessions.forEach((id, session) -> {
            Instant lastPong = lastPongTime.getOrDefault(id, Instant.MIN);
            if (lastPong.isBefore(cutoff)) {
                log.warn("WS pong timeout: sessionId={}", id);
                safeClose(session, CloseStatus.SESSION_NOT_RELIABLE);
                removeSession(id);
                return; // skip ping for this session
            }
            try {
                if (session.isOpen()) {
                    session.sendMessage(new PingMessage());
                }
            } catch (IOException e) {
                log.warn("Failed to send ping to sessionId={}: {}", id, e.getMessage());
                removeSession(id);
            }
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void removeSession(String id) {
        pendingSessions.remove(id);
        pendingConnectTimes.remove(id);
        authenticatedSessions.remove(id);
        lastPongTime.remove(id);
    }

    private void safeClose(WebSocketSession session, CloseStatus status) {
        try {
            if (session.isOpen()) {
                session.close(status);
            }
        } catch (IOException e) {
            log.warn("Error closing WS session: sessionId={} error={}", session.getId(), e.getMessage());
        }
    }
}
