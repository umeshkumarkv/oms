package com.brokerdealer.apigateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
@Slf4j
public class JwtValidationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String AUTH_LOGIN_PATH = "/api/v1/auth/login";
    private static final String ACTUATOR_PATH = "/actuator";
    private static final String BLACKLIST_PREFIX = "auth:blacklist:";

    static final String HEADER_USER_ID = "X-User-Id";
    static final String HEADER_USERNAME = "X-Username";
    static final String HEADER_USER_ROLE = "X-User-Role";

    private final String jwtSecret;
    private final RedisTemplate<String, Object> redisTemplate;

    public JwtValidationFilter(
            @Value("${JWT_SECRET}") String jwtSecret,
            RedisTemplate<String, Object> redisTemplate) {
        this.jwtSecret = jwtSecret;
        this.redisTemplate = redisTemplate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        if (path.startsWith(AUTH_LOGIN_PATH) || path.startsWith(ACTUATOR_PATH)) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            log.warn("Missing or invalid Authorization header for path: {}", path);
            writeErrorResponse(response, "UNAUTHORIZED", "Token is missing or invalid");
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            if (Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + token))) {
                log.warn("Blacklisted token attempt: userId={}", claims.get("userId"));
                writeErrorResponse(response, "TOKEN_REVOKED", "Token has been revoked");
                return;
            }

            String userId = claims.get("userId", String.class);
            String username = claims.get("username", String.class);
            String role = claims.get("role", String.class);

            log.debug("JWT validated: userId={} role={}", userId, role);

            MutableHttpServletRequest mutableRequest = new MutableHttpServletRequest(request);
            mutableRequest.putHeader(HEADER_USER_ID, userId);
            mutableRequest.putHeader(HEADER_USERNAME, username);
            mutableRequest.putHeader(HEADER_USER_ROLE, role);

            filterChain.doFilter(mutableRequest, response);

        } catch (ExpiredJwtException e) {
            log.warn("Expired JWT token for path: {}", path);
            writeErrorResponse(response, "TOKEN_EXPIRED", "Token has expired");
        } catch (JwtException e) {
            log.warn("Invalid JWT token for path: {}", path);
            writeErrorResponse(response, "UNAUTHORIZED", "Token is missing or invalid");
        }
    }

    private void writeErrorResponse(HttpServletResponse response, String code, String message)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        String body = String.format(
                "{\"error\":{\"code\":\"%s\",\"message\":\"%s\",\"details\":[]}," +
                "\"meta\":{\"timestamp\":\"%s\",\"requestId\":\"%s\"}}",
                code, message, Instant.now(), UUID.randomUUID());
        response.getWriter().write(body);
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Wraps HttpServletRequest to allow adding headers for downstream services.
     * Required because HttpServletRequest headers are immutable by the Servlet spec.
     */
    static class MutableHttpServletRequest extends HttpServletRequestWrapper {

        private final Map<String, String> extraHeaders = new HashMap<>();

        MutableHttpServletRequest(HttpServletRequest request) {
            super(request);
        }

        void putHeader(String name, String value) {
            extraHeaders.put(name, value);
        }

        @Override
        public String getHeader(String name) {
            String extra = extraHeaders.get(name);
            return extra != null ? extra : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            String extra = extraHeaders.get(name);
            if (extra != null) {
                return Collections.enumeration(Collections.singletonList(extra));
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            Set<String> names = new HashSet<>(Collections.list(super.getHeaderNames()));
            names.addAll(extraHeaders.keySet());
            return Collections.enumeration(names);
        }
    }
}
