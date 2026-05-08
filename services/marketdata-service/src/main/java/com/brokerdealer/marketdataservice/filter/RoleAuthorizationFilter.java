package com.brokerdealer.marketdataservice.filter;

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
import java.util.UUID;

/**
 * Enforces role-based access control for marketdata-service using the X-User-Role header
 * forwarded by the API gateway. No Spring Security dependency required.
 *
 * Permission matrix:
 *   GET /api/v1/market/**  -- VIEWER, TRADER, OPERATIONS (ADMIN excluded per BRD 12.2)
 *   Requests without X-User-Role header are internal system calls -- passed through.
 */
@Component
@Slf4j
public class RoleAuthorizationFilter extends OncePerRequestFilter {

    private static final String ROLE_HEADER = "X-User-Role";

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

        // No role header means internal system call (e.g. from order-service or client-service) — pass through
        if (role == null || role.isBlank()) {
            chain.doFilter(request, response);
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
        // /actuator, OPTIONS, and /api/v1/admin are excluded — admin endpoints use RoleGuard directly
        return path.startsWith("/actuator")
                || path.startsWith("/api/v1/admin")
                || "OPTIONS".equalsIgnoreCase(method);
    }

    private boolean isAllowed(String role, String method, String path) {
        if ("GET".equalsIgnoreCase(method) && path.startsWith("/api/v1/market")) {
            return !"ADMIN".equals(role);
        }
        return true;
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
