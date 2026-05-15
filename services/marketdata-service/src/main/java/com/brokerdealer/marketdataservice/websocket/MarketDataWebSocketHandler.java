package com.brokerdealer.marketdataservice.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class MarketDataWebSocketHandler extends TextWebSocketHandler {

    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    /** sessionId → set of symbols the session has subscribed to */
    private final ConcurrentHashMap<String, Set<String>> sessionSymbols = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), session);
        sessionSymbols.put(session.getId(), ConcurrentHashMap.newKeySet());
        log.debug("WS connected: sessionId={} total={}", session.getId(), sessions.size());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Map<?, ?> payload = objectMapper.readValue(message.getPayload(), Map.class);
        String type   = (String) payload.get("type");
        String action = (String) payload.get("action");
        String symbol = (String) payload.get("symbol");

        if ("AUTH".equals(type)) {
            // Market data is public — accept all connections, send AUTH_OK to start live feed
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of("type", "AUTH_OK"))));
            log.debug("WS AUTH_OK: sessionId={}", session.getId());
        } else if ("subscribe".equals(action) && symbol != null) {
            sessionSymbols.computeIfAbsent(session.getId(), k -> ConcurrentHashMap.newKeySet()).add(symbol);
            log.debug("WS subscribe: sessionId={} symbol={}", session.getId(), symbol);
        } else if ("unsubscribe".equals(action) && symbol != null) {
            Set<String> syms = sessionSymbols.get(session.getId());
            if (syms != null) syms.remove(symbol);
            log.debug("WS unsubscribe: sessionId={} symbol={}", session.getId(), symbol);
        }
        // PONG — no-op
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        sessionSymbols.remove(session.getId());
        log.debug("WS disconnected: sessionId={} status={} total={}", session.getId(), status, sessions.size());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        sessions.remove(session.getId());
        sessionSymbols.remove(session.getId());
        log.warn("WS transport error: sessionId={} error={}", session.getId(), exception.getMessage());
    }

    public boolean hasActiveSessions() {
        return !sessions.isEmpty();
    }

    /** Send a tick only to sessions that have subscribed to this symbol. */
    public void broadcastToSymbol(String symbol, String json) {
        TextMessage message = new TextMessage(json);
        sessions.forEach((id, session) -> {
            Set<String> syms = sessionSymbols.get(id);
            if (syms == null || !syms.contains(symbol)) return;
            if (!session.isOpen()) {
                sessions.remove(id);
                sessionSymbols.remove(id);
                return;
            }
            try {
                synchronized (session) {
                    session.sendMessage(message);
                }
            } catch (IOException e) {
                log.warn("WS send failed, removing session: sessionId={}", id);
                sessions.remove(id);
                sessionSymbols.remove(id);
            }
        });
    }

    /** Broadcast to ALL sessions (used for PING and market-status events). */
    public void broadcast(String json) {
        if (sessions.isEmpty()) return;
        TextMessage message = new TextMessage(json);
        sessions.values().removeIf(session -> {
            if (!session.isOpen()) return true;
            try {
                synchronized (session) {
                    session.sendMessage(message);
                }
                return false;
            } catch (IOException e) {
                log.warn("WS send failed, removing session: sessionId={}", session.getId());
                return true;
            }
        });
    }

    @Scheduled(fixedDelay = 30_000)
    public void ping() {
        if (sessions.isEmpty()) return;
        try {
            broadcast(objectMapper.writeValueAsString(Map.of("type", "PING")));
        } catch (Exception e) {
            log.warn("WS ping failed: {}", e.getMessage());
        }
    }
}
