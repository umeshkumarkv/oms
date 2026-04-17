package com.brokerdealer.apigateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class JwtValidationFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String AUTH_LOGIN_PATH = "/api/v1/auth/login";
    private static final String ACTUATOR_PATH = "/actuator";

    @Value("${JWT_SECRET:dev-secret-key-change-in-production-min-32-chars}")
    private String jwtSecret;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().toString();

        // Allow login and actuator endpoints without JWT
        if (path.startsWith(AUTH_LOGIN_PATH) || path.startsWith(ACTUATOR_PATH)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            // TODO: implement JWT validation — JIRA story BD-XX
            log.warn("Stub: JWT validation not yet implemented — allowing request through");
            // For now, pass through. Replace with actual validation in feature story.
            return chain.filter(exchange);
        }

        // TODO: validate token and extract claims — JIRA story BD-XX
        log.debug("Stub: JWT token present, bypassing validation");
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -1;
    }
}