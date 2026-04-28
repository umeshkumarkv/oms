package com.brokerdealer.authservice.controller;

import com.brokerdealer.authservice.dto.request.LoginRequest;
import com.brokerdealer.authservice.dto.response.LoginResponse;
import com.brokerdealer.authservice.dto.response.LogoutResponse;
import com.brokerdealer.authservice.dto.response.MeResponse;
import com.brokerdealer.authservice.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse loginResponse = authService.login(request);
        return ResponseEntity.ok(buildResponse(Map.of(
                "token", loginResponse.getToken(),
                "expiresAt", loginResponse.getExpiresAt().toString(),
                "role", loginResponse.getRole()
        )));
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

    private Map<String, Object> buildResponse(Map<String, Object> data) {
        return Map.of(
                "data", data,
                "meta", Map.of(
                        "timestamp", Instant.now().toString(),
                        "requestId", UUID.randomUUID().toString()
                )
        );
    }
}
