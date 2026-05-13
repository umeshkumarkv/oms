package com.brokerdealer.authservice.controller;

import com.brokerdealer.authservice.domain.UserRole;
import com.brokerdealer.authservice.domain.UserStatus;
import com.brokerdealer.authservice.dto.response.UserResponse;
import com.brokerdealer.authservice.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
}
