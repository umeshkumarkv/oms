package com.brokerdealer.authservice.service;

import com.brokerdealer.authservice.domain.User;
import com.brokerdealer.authservice.dto.request.LoginRequest;
import com.brokerdealer.authservice.dto.response.LoginData;
import com.brokerdealer.authservice.exception.AuthException;
import com.brokerdealer.authservice.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${JWT_SECRET:dev-secret-key-change-in-production-min-32-chars}")
    private String jwtSecret;

    public LoginData login(LoginRequest request) {
        User user = userRepository.findByUsernameIgnoreCase(request.getUsername())
                .orElseThrow(() -> {
                    log.info("Login attempt: username={} result=failure reason=user_not_found",
                            request.getUsername());
                    return new AuthException("UNAUTHORIZED", "Username or password is incorrect");
                });

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            log.info("Login attempt: username={} role={} result=failure reason=bad_credentials",
                    user.getUsername(), user.getRole());
            throw new AuthException("UNAUTHORIZED", "Username or password is incorrect");
        }

        Instant now = Instant.now();
        Instant expiresAt = now.plus(8, ChronoUnit.HOURS);

        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));

        String token = Jwts.builder()
                .subject(user.getId().toString())
                .claim("username", user.getUsername())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();

        log.info("Login attempt: username={} role={} result=success", user.getUsername(), user.getRole());

        return LoginData.builder()
                .token(token)
                .expiresAt(expiresAt.toString())
                .role(user.getRole().name())
                .userId(user.getId().toString())
                .displayName(user.getUsername())
                .build();
    }

    public void logout(String token) {
        // TODO: implement — JIRA story SCRUM-17
    }

    public Object getCurrentUser(String token) {
        // TODO: implement — JIRA story SCRUM-17
        return null;
    }
}
