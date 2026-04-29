package com.brokerdealer.marketdataservice.websocket;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class JwtValidatorTest {

    private static final String SECRET = "dev-secret-key-change-in-production-min-32-chars";
    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    private JwtValidator jwtValidator;

    @BeforeEach
    void setUp() {
        jwtValidator = new JwtValidator(SECRET);
    }

    @Test
    void shouldReturnTrueForValidJwt() {
        String token = buildToken(System.currentTimeMillis() + 3_600_000L);
        assertThat(jwtValidator.isValid(token)).isTrue();
    }

    @Test
    void shouldReturnFalseForExpiredJwt() {
        String token = buildToken(System.currentTimeMillis() - 1000L);
        assertThat(jwtValidator.isValid(token)).isFalse();
    }

    @Test
    void shouldReturnFalseForRandomString() {
        assertThat(jwtValidator.isValid("not.a.jwt")).isFalse();
    }

    @Test
    void shouldReturnFalseForNullToken() {
        assertThat(jwtValidator.isValid(null)).isFalse();
    }

    @Test
    void shouldReturnFalseForBlankToken() {
        assertThat(jwtValidator.isValid("   ")).isFalse();
    }

    @Test
    void shouldReturnFalseForTokenSignedWithDifferentSecret() {
        SecretKey wrongKey = Keys.hmacShaKeyFor("totally-different-secret-key-min-32-chars!".getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
                .subject("user-1")
                .expiration(new Date(System.currentTimeMillis() + 3_600_000L))
                .signWith(wrongKey)
                .compact();
        assertThat(jwtValidator.isValid(token)).isFalse();
    }

    private String buildToken(long expiryMillis) {
        return Jwts.builder()
                .subject("user-1")
                .claim("role", "TRADER")
                .expiration(new Date(expiryMillis))
                .signWith(KEY)
                .compact();
    }
}
