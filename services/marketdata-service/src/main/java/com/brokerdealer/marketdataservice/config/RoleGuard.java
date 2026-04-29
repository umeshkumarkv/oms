package com.brokerdealer.marketdataservice.config;

import com.brokerdealer.marketdataservice.exception.AccessForbiddenException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Component
@Slf4j
public class RoleGuard {

    @Value("${JWT_SECRET:#{null}}")
    private String jwtSecret;

    public void requireRole(String bearerHeader, String requiredRole) {
        if (bearerHeader == null || !bearerHeader.startsWith("Bearer ")) {
            throw new AccessForbiddenException("UNAUTHORIZED", "Authorization header missing or invalid");
        }
        if (jwtSecret == null) {
            log.warn("JWT_SECRET not configured — cannot validate role");
            throw new AccessForbiddenException("FORBIDDEN", "Role validation not configured");
        }
        String token = bearerHeader.substring(7);
        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String role = claims.get("role", String.class);
            if (!requiredRole.equals(role)) {
                log.warn("Role check failed: required={} actual={}", requiredRole, role);
                throw new AccessForbiddenException("FORBIDDEN", "Insufficient role: ADMIN required");
            }
        } catch (AccessForbiddenException e) {
            throw e;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            throw new AccessForbiddenException("UNAUTHORIZED", "Invalid or expired token");
        }
    }
}
