package com.brokerdealer.authservice.controller;

import com.brokerdealer.authservice.dto.request.LoginRequest;
import com.brokerdealer.authservice.dto.response.LoginData;
import com.brokerdealer.authservice.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@Slf4j
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        log.info("Login request received: username={}", request.getUsername());
        LoginData data = authService.login(request);
        log.info("Login successful: username={}", request.getUsername());
        return ResponseEntity.ok(buildSuccessResponse(data));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader("Authorization") String token) {
        // TODO: implement — JIRA story SCRUM-17
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    public ResponseEntity<?> me() {
        // TODO: implement — JIRA story SCRUM-17
        return ResponseEntity.ok().build();
    }

    private Map<String, Object> buildSuccessResponse(Object data) {
        return Map.of(
                "data", data,
                "meta", Map.of(
                        "timestamp", Instant.now().toString(),
                        "requestId", UUID.randomUUID().toString()
                )
        );
    }
}
