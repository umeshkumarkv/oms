package com.brokerdealer.authservice.controller;

import com.brokerdealer.authservice.dto.request.LoginRequest;
import com.brokerdealer.authservice.dto.response.LoginData;
import com.brokerdealer.authservice.dto.response.LogoutResponse;
import com.brokerdealer.authservice.dto.response.MeResponse;
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
        LoginData loginData = authService.login(request);
        return ResponseEntity.ok(buildResponse(loginData));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(
            @RequestHeader(value = "Authorization", required = false) String bearerToken) {
        LogoutResponse logoutResponse = authService.logout(bearerToken);
        return ResponseEntity.ok(buildResponse(Map.of("message", logoutResponse.getMessage())));
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(
            @RequestHeader(value = "Authorization", required = false) String bearerToken) {
        MeResponse meResponse = authService.getCurrentUser(bearerToken);
        return ResponseEntity.ok(buildResponse(Map.of(
                "userId", meResponse.getUserId().toString(),
                "username", meResponse.getUsername(),
                "role", meResponse.getRole(),
                "displayName", meResponse.getDisplayName()
        )));
    }

    private Map<String, Object> buildResponse(Object data) {
        return Map.of(
                "data", data,
                "meta", Map.of(
                        "timestamp", Instant.now().toString(),
                        "requestId", UUID.randomUUID().toString()
                )
        );
    }
}
