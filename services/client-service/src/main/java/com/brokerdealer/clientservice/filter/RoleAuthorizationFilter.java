package com.brokerdealer.clientservice.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Enforces role-based access control for client-service using the X-User-Role header
 * forwarded by the API gateway. No Spring Security dependency required.
 *
 * Permission matrix:
 *   GET   /api/v1/clients/**                   -- VIEWER, TRADER, OPERATIONS, ADMIN
 *   GET   /api/v1/clients/{id}/portfolio       -- VIEWER, TRADER, OPERATIONS (ADMIN excluded per BRD 12.2)
 *   PATCH /api/v1/clients/{id}/reserved-cash   -- system call (order-service), no X-User-Role required
 */
@Component
@Slf4j
public class RoleAuthorizationFilter extends OncePerRequestFilter {

    private static final String ROLE_HEADER = "X-User-Role";
    private static final Set<String> ALL_ROLES = Set.of("VIEWER", "TRADER", "OPERATIONS", "ADMIN", "SYSTEM");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();

        if (isExcluded(path, method)) {
            chain.doFilter(request, response);
            return;
        }

        String role = request.getHeader(ROLE_HEADER);
        if (role == null || role.isBlank()) {
            log.warn("Missing X-User-Role header: method={} path={}", method, path);
            writeError(response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "X-User-Role header is required");
            return;
        }

        if (!isAllowed(role, method, path)) {
            log.warn("Access denied: role={} method={} path={}", role, method, path);
            writeError(response, HttpStatus.FORBIDDEN, "FORBIDDEN",
                    "Role " + role + " is not permitted to " + method + " " + path);
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean isExcluded(String path, String method) {
        if (path.startsWith("/actuator") || "OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }
        // PATCH /reserved-cash is a system-to-system call from order-service, not through gateway
        if ("PATCH".equalsIgnoreCase(method) && path.contains("/reserved-cash")) {
            return true;
        }
        return false;
    }

    private boolean isAllowed(String role, String method, String path) {
        if (!ALL_ROLES.contains(role)) {
            return false;
        }
        if ("GET".equalsIgnoreCase(method) && path.startsWith("/api/v1/clients")) {
            // ADMIN cannot access portfolio data per BRD Section 12.2
            // SYSTEM (order-service service-to-service) requires portfolio for L2 validation — allowed
            if (path.contains("/portfolio") && "ADMIN".equals(role)) {
                return false;
            }
            return true;
        }
        return false;
    }

    private void writeError(HttpServletResponse response, HttpStatus status,
                            String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Map<String, Object> body = Map.of(
                "error", Map.of("code", code, "message", message, "details", List.of()),
                "meta", Map.of("timestamp", Instant.now().toString(), "requestId", UUID.randomUUID().toString())
        );
        objectMapper.writeValue(response.getWriter(), body);
    }
}
