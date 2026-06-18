package com.brokerdealer.notificationservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.UUID;

import static org.mockito.Mockito.*;

class SseEmitterRegistryTest {

    private SseEmitterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SseEmitterRegistry();
    }

    @Test
    void shouldRegisterTraderEmitterAndSendEvent() throws IOException {
        UUID userId = UUID.randomUUID();
        SseEmitter emitter = mock(SseEmitter.class);
        SseEmitter.SseEventBuilder event = SseEmitter.event().name("test").data("payload");

        registry.register(userId, "TRADER", emitter);
        registry.pushToUser(userId, event);

        verify(emitter).send(event);
    }

    @Test
    void shouldNotThrowWhenPushingToUnregisteredTrader() {
        UUID userId = UUID.randomUUID();
        SseEmitter.SseEventBuilder event = SseEmitter.event().name("test").data("payload");

        // No exception should be thrown
        registry.pushToUser(userId, event);
    }

    @Test
    void shouldRegisterBroadcastEmitterForOperations() throws IOException {
        UUID userId = UUID.randomUUID();
        SseEmitter emitter = mock(SseEmitter.class);
        SseEmitter.SseEventBuilder event = SseEmitter.event().name("test").data("payload");

        registry.register(userId, "OPERATIONS", emitter);
        registry.broadcast(event);

        verify(emitter).send(event);
    }

    @Test
    void shouldRegisterBroadcastEmitterForAdmin() throws IOException {
        UUID userId = UUID.randomUUID();
        SseEmitter emitter = mock(SseEmitter.class);
        SseEmitter.SseEventBuilder event = SseEmitter.event().name("test").data("payload");

        registry.register(userId, "ADMIN", emitter);
        registry.broadcast(event);

        verify(emitter).send(event);
    }

    @Test
    void shouldBroadcastToMultipleEmitters() throws IOException {
        SseEmitter emitter1 = mock(SseEmitter.class);
        SseEmitter emitter2 = mock(SseEmitter.class);
        SseEmitter.SseEventBuilder event = SseEmitter.event().name("test").data("payload");

        registry.register(UUID.randomUUID(), "OPERATIONS", emitter1);
        registry.register(UUID.randomUUID(), "OPERATIONS", emitter2);
        registry.broadcast(event);

        verify(emitter1).send(event);
        verify(emitter2).send(event);
    }

    @Test
    void shouldRemoveTraderEmitterOnSendFailure() throws IOException {
        UUID userId = UUID.randomUUID();
        SseEmitter emitter = mock(SseEmitter.class);
        SseEmitter.SseEventBuilder event = SseEmitter.event().name("test").data("payload");

        doThrow(new IOException("connection closed")).when(emitter).send(event);

        registry.register(userId, "TRADER", emitter);
        registry.pushToUser(userId, event); // should not throw
        registry.pushToUser(userId, event); // emitter removed — should be no-op

        // send called only once (second call is no-op because emitter was removed)
        verify(emitter, times(1)).send(event);
    }

    @Test
    void shouldRemoveBroadcastEmitterOnSendFailure() throws IOException {
        SseEmitter emitter = mock(SseEmitter.class);
        SseEmitter.SseEventBuilder event = SseEmitter.event().name("test").data("payload");

        doThrow(new IOException("connection closed")).when(emitter).send(event);

        registry.register(UUID.randomUUID(), "OPERATIONS", emitter);
        registry.broadcast(event); // should not throw

        verify(emitter, times(1)).send(event);
    }

    @Test
    void shouldSendHeartbeatToAllEmitters() throws IOException {
        UUID traderId = UUID.randomUUID();
        SseEmitter traderEmitter = mock(SseEmitter.class);
        SseEmitter broadcastEmitter = mock(SseEmitter.class);

        registry.register(traderId, "TRADER", traderEmitter);
        registry.register(UUID.randomUUID(), "OPERATIONS", broadcastEmitter);

        registry.sendHeartbeat();

        verify(traderEmitter).send(any(SseEmitter.SseEventBuilder.class));
        verify(broadcastEmitter).send(any(SseEmitter.SseEventBuilder.class));
    }
}
