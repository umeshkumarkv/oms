package com.brokerdealer.authservice.controller;

import com.brokerdealer.authservice.domain.UserRole;
import com.brokerdealer.authservice.domain.UserStatus;
import com.brokerdealer.authservice.dto.request.CreateUserRequest;
import com.brokerdealer.authservice.dto.request.UpdateUserStatusRequest;
import com.brokerdealer.authservice.dto.response.UserResponse;
import com.brokerdealer.authservice.dto.response.UserStatusResponse;
import com.brokerdealer.authservice.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> listUsers(
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) UserStatus status) {

        log.debug("GET /api/v1/users: role={} status={}", role, status);

        List<UserResponse> users = userService.listUsers(role, status);

        Map<String, Object> meta = Map.of(
                "total", users.size(),
                "page", 1,
                "pageSize", 50,
                "timestamp", Instant.now().toString(),
                "requestId", UUID.randomUUID().toString()
        );

        return ResponseEntity.ok(Map.of("data", users, "meta", meta));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> createUser(
            @Valid @RequestBody CreateUserRequest request) {

        log.debug("POST /api/v1/users: username={} role={}", request.getUsername(), request.getRole());

        UserResponse created = userService.createUser(request);

        Map<String, Object> meta = Map.of(
                "timestamp", Instant.now().toString(),
                "requestId", UUID.randomUUID().toString()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", created, "meta", meta));
    }

    @PatchMapping("/{userId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> updateUserStatus(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserStatusRequest request,
            HttpServletRequest httpRequest) {

        log.debug("PATCH /api/v1/users/{}/status: status={}", userId, request.getStatus());

        UUID callerAdminId = resolveCallerId(httpRequest.getHeader("X-User-Id"));
        UserStatusResponse result = userService.updateUserStatus(userId, callerAdminId, request);

        Map<String, Object> meta = Map.of(
                "timestamp", Instant.now().toString(),
                "requestId", UUID.randomUUID().toString()
        );

        return ResponseEntity.ok(Map.of("data", result, "meta", meta));
    }

    private UUID resolveCallerId(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(header);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid X-User-Id header value: {}", header);
            return null;
        }
    }
}
