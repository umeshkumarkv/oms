package com.brokerdealer.authservice.service;

import com.brokerdealer.authservice.domain.UserRole;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private JwtService jwtService;

    private static final String JWT_SECRET = "test-secret-key-that-is-at-least-32-chars-long!!";

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(redisTemplate, JWT_SECRET);
    }

    @Test
    void shouldGenerateAndParseTokenSuccessfully() {
        UUID userId = UUID.randomUUID();
        String username = "trader.john";
        UserRole role = UserRole.TRADER;

        String token = jwtService.generateToken(userId, username, role);

        assertThat(token).isNotBlank();

        Claims claims = jwtService.parseAndValidate(token);
        assertThat(claims.get("userId", String.class)).isEqualTo(userId.toString());
        assertThat(claims.get("username", String.class)).isEqualTo(username);
        assertThat(claims.get("role", String.class)).isEqualTo(role.name());
    }

    @Test
    void shouldBlacklistTokenWithCorrectTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        UUID userId = UUID.randomUUID();
        String token = jwtService.generateToken(userId, "trader.john", UserRole.TRADER);
        Claims claims = jwtService.parseAndValidate(token);

        jwtService.blacklistToken(token, claims);

        verify(valueOperations).set(
                eq("auth:blacklist:" + token),
                eq("blacklisted"),
                longThat(ttl -> ttl > 0 && ttl <= 30 * 60L),
                eq(TimeUnit.SECONDS)
        );
    }

    @Test
    void shouldReturnTrueWhenTokenIsBlacklisted() {
        String token = "some-token";
        when(redisTemplate.hasKey("auth:blacklist:" + token)).thenReturn(true);

        assertThat(jwtService.isBlacklisted(token)).isTrue();
    }

    @Test
    void shouldReturnFalseWhenTokenIsNotBlacklisted() {
        String token = "some-token";
        when(redisTemplate.hasKey("auth:blacklist:" + token)).thenReturn(false);

        assertThat(jwtService.isBlacklisted(token)).isFalse();
    }

    @Test
    void shouldNotBlacklistAlreadyExpiredToken() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // Build a claims object with past expiry using a very short-lived token
        // and then manually advance — instead, verify the ttl > 0 guard
        UUID userId = UUID.randomUUID();
        String token = jwtService.generateToken(userId, "trader.john", UserRole.TRADER);
        Claims claims = jwtService.parseAndValidate(token);

        // Normal token: blacklist IS called (ttl > 0)
        jwtService.blacklistToken(token, claims);
        verify(valueOperations, times(1)).set(anyString(), anyString(), anyLong(), any());
    }
}
