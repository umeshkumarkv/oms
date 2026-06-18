package com.brokerdealer.notificationservice.controller;

import com.brokerdealer.notificationservice.service.SseEmitterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Slf4j
public class SseController {

    private final SseEmitterRegistry sseEmitterRegistry;

    /**
     * Opens a long-lived SSE stream for the authenticated user.
     * TRADER role → receives pushes targeted to their userId.
     * OPERATIONS/ADMIN role → receives broadcast events.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        String role = authentication.getAuthorities().iterator().next()
                .getAuthority().replace("ROLE_", "");

        log.info("SseController: SSE connection opened userId={} role={}", userId, role);

        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        sseEmitterRegistry.register(userId, role, emitter);

        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(Map.of("userId", userId.toString(), "role", role)));
        } catch (IOException e) {
            log.warn("SseController: failed to send initial connected event userId={}", userId);
        }

        return emitter;
    }
}
