package com.brokerdealer.apigateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
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
import java.util.UUID;

@Component
@Slf4j
public class JwtValidationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String AUTH_LOGIN_PATH = "/api/v1/auth/login";
    private static final String ACTUATOR_PATH = "/actuator";
    private static final String BLACKLIST_PREFIX = "auth:blacklist:";

    private final String jwtSecret;
    private final RedisTemplate<String, Object> redisTemplate;

    public JwtValidationFilter(
            @Value("${JWT_SECRET:dev-secret-key-change-in-production-min-32-chars}") String jwtSecret,
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
            writeUnauthorizedResponse(response, "UNAUTHORIZED", "Token is missing or invalid");
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
                writeUnauthorizedResponse(response, "UNAUTHORIZED", "Token has been invalidated");
                return;
            }

            log.debug("JWT validated: userId={}", claims.get("userId"));
            filterChain.doFilter(request, response);

        } catch (ExpiredJwtException e) {
            log.warn("Expired JWT token: {}", e.getMessage());
            writeUnauthorizedResponse(response, "UNAUTHORIZED", "Token has expired");
        } catch (JwtException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            writeUnauthorizedResponse(response, "UNAUTHORIZED", "Token is missing or invalid");
        }
    }

    private void writeUnauthorizedResponse(HttpServletResponse response, String code, String message)
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
}
