package com.brokerdealer.authservice.service;

import com.brokerdealer.authservice.domain.User;
import com.brokerdealer.authservice.dto.request.LoginRequest;
import com.brokerdealer.authservice.dto.response.LoginResponse;
import com.brokerdealer.authservice.dto.response.LogoutResponse;
import com.brokerdealer.authservice.dto.response.MeResponse;
import com.brokerdealer.authservice.exception.InvalidTokenException;
import com.brokerdealer.authservice.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new InvalidTokenException(
                        "INVALID_CREDENTIALS", "Username or password is incorrect"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new InvalidTokenException("INVALID_CREDENTIALS", "Username or password is incorrect");
        }

        String token = jwtService.generateToken(user.getId(), user.getUsername(), user.getRole());
        Instant expiresAt = Instant.now().plusSeconds(8 * 3600L);

        log.info("User login: userId={} role={}", user.getId(), user.getRole());

        return LoginResponse.builder()
                .token(token)
                .expiresAt(expiresAt)
                .role(user.getRole().name())
                .build();
    }

    public LogoutResponse logout(String bearerToken) {
        String token = extractRawToken(bearerToken);
        Claims claims = parseToken(token);
        jwtService.blacklistToken(token, claims);
        log.info("User logout: userId={}", claims.get(CLAIM_USER_ID));
        return LogoutResponse.builder().message("Logged out successfully").build();
    }

    public MeResponse getCurrentUser(String bearerToken) {
        String token = extractRawToken(bearerToken);
        Claims claims = parseToken(token);

        if (jwtService.isBlacklisted(token)) {
            throw new InvalidTokenException("UNAUTHORIZED", "Token has been invalidated");
        }

        UUID userId = UUID.fromString(claims.get(CLAIM_USER_ID, String.class));
        String username = claims.get(CLAIM_USERNAME, String.class);
        String role = claims.get(CLAIM_ROLE, String.class);

        log.debug("ME request: userId={}", userId);

        return MeResponse.builder()
                .userId(userId)
                .username(username)
                .role(role)
                .displayName(username)
                .build();
    }

    private String extractRawToken(String bearerToken) {
        if (bearerToken == null || !bearerToken.startsWith("Bearer ")) {
            throw new InvalidTokenException("UNAUTHORIZED", "Token is missing or invalid");
        }
        return bearerToken.substring(7);
    }

    private Claims parseToken(String token) {
        try {
            return jwtService.parseAndValidate(token);
        } catch (JwtException e) {
            log.warn("JWT validation failed: {}", e.getMessage());
            throw new InvalidTokenException("UNAUTHORIZED", "Token is missing or invalid");
        }
    }

    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_ROLE = "role";
}
