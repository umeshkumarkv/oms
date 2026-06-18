package com.brokerdealer.notificationservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
@Slf4j
public class SseEmitterRegistry {

    private final ConcurrentHashMap<UUID, SseEmitter> traderEmitters     = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<SseEmitter>    broadcastEmitters   = new CopyOnWriteArrayList<>();

    /**
     * Register an emitter for a connected client.
     * TRADER role → keyed by userId in traderEmitters.
     * OPERATIONS / ADMIN → added to broadcastEmitters.
     */
    public void register(UUID userId, String role, SseEmitter emitter) {
        if ("TRADER".equals(role)) {
            traderEmitters.put(userId, emitter);
            emitter.onCompletion(() -> {
                traderEmitters.remove(userId);
                log.debug("SseEmitterRegistry: TRADER emitter completed userId={}", userId);
            });
            emitter.onTimeout(() -> {
                traderEmitters.remove(userId);
                log.debug("SseEmitterRegistry: TRADER emitter timed out userId={}", userId);
            });
            emitter.onError(e -> {
                traderEmitters.remove(userId);
                log.debug("SseEmitterRegistry: TRADER emitter error userId={}", userId);
            });
            log.debug("SseEmitterRegistry: registered TRADER emitter userId={}", userId);
        } else {
            broadcastEmitters.add(emitter);
            emitter.onCompletion(() -> {
                broadcastEmitters.remove(emitter);
                log.debug("SseEmitterRegistry: broadcast emitter completed role={} userId={}", role, userId);
            });
            emitter.onTimeout(() -> {
                broadcastEmitters.remove(emitter);
                log.debug("SseEmitterRegistry: broadcast emitter timed out role={} userId={}", role, userId);
            });
            emitter.onError(e -> {
                broadcastEmitters.remove(emitter);
                log.debug("SseEmitterRegistry: broadcast emitter error role={} userId={}", role, userId);
            });
            log.debug("SseEmitterRegistry: registered broadcast emitter role={} userId={}", role, userId);
        }
    }

    /**
     * Push an SSE event to a specific TRADER by userId.
     * No-op if the trader has no active emitter.
     */
    public void pushToUser(UUID userId, SseEmitter.SseEventBuilder event) {
        SseEmitter emitter = traderEmitters.get(userId);
        if (emitter == null) {
            return;
        }
        try {
            emitter.send(event);
        } catch (IOException e) {
            log.warn("SseEmitterRegistry: send failed for userId={}, removing emitter", userId);
            traderEmitters.remove(userId);
        }
    }

    /**
     * Broadcast an SSE event to all OPERATIONS/ADMIN emitters.
     */
    public void broadcast(SseEmitter.SseEventBuilder event) {
        for (SseEmitter emitter : broadcastEmitters) {
            try {
                emitter.send(event);
            } catch (IOException e) {
                log.warn("SseEmitterRegistry: broadcast send failed, removing dead emitter");
                broadcastEmitters.remove(emitter);
            }
        }
    }

    /**
     * Send a keepalive SSE comment every 20 s to all active emitters
     * to prevent proxy / load-balancer idle-connection timeouts.
     */
    @Scheduled(fixedRate = 20_000)
    public void sendHeartbeat() {
        SseEmitter.SseEventBuilder heartbeat = SseEmitter.event().comment("keepalive");

        traderEmitters.forEach((userId, emitter) -> {
            try {
                emitter.send(heartbeat);
            } catch (IOException e) {
                log.warn("SseEmitterRegistry: heartbeat failed for userId={}, removing", userId);
                traderEmitters.remove(userId);
            }
        });

        for (SseEmitter emitter : broadcastEmitters) {
            try {
                emitter.send(heartbeat);
            } catch (IOException e) {
                log.warn("SseEmitterRegistry: heartbeat broadcast failed, removing dead emitter");
                broadcastEmitters.remove(emitter);
            }
        }
    }
}
