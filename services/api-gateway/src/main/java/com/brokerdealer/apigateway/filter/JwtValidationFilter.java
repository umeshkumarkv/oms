package com.brokerdealer.apigateway.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Slf4j
public class JwtValidationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String AUTH_LOGIN_PATH = "/api/v1/auth/login";
    private static final String ACTUATOR_PATH = "/actuator";

    @Value("${JWT_SECRET:dev-secret-key-change-in-production-min-32-chars}")
    private String jwtSecret;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        // Allow login and actuator endpoints without JWT
        if (path.startsWith(AUTH_LOGIN_PATH) || path.startsWith(ACTUATOR_PATH)) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            // TODO: implement JWT validation — JIRA story BD-XX
            log.warn("Stub: JWT validation not yet implemented — allowing request through");
            // For now, pass through. Replace with actual validation in feature story.
            filterChain.doFilter(request, response);
            return;
        }

        // TODO: validate token and extract claims — JIRA story BD-XX
        log.debug("Stub: JWT token present, bypassing validation");
        filterChain.doFilter(request, response);
    }
}