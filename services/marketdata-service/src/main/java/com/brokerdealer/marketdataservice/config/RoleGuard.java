package com.brokerdealer.marketdataservice.config;

import com.brokerdealer.marketdataservice.exception.AccessForbiddenException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Checks the X-User-Role header forwarded by the API gateway.
 * The gateway is trusted to have validated the JWT; services read the header directly.
 */
@Component
@Slf4j
public class RoleGuard {

    private static final String ROLE_HEADER = "X-User-Role";

    public void requireRole(String roleHeader, String requiredRole) {
        if (roleHeader == null || roleHeader.isBlank()) {
            log.warn("X-User-Role header missing");
            throw new AccessForbiddenException("UNAUTHORIZED", "X-User-Role header is required");
        }
        if (!requiredRole.equals(roleHeader)) {
            log.warn("Role check failed: required={} actual={}", requiredRole, roleHeader);
            throw new AccessForbiddenException("FORBIDDEN", "Insufficient role: " + requiredRole + " required");
        }
    }
}
