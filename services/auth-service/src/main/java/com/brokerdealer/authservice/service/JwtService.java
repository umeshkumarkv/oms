package com.brokerdealer.authservice.service;

import com.brokerdealer.authservice.domain.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class JwtService {

    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_ROLE = "role";
    private static final String BLACKLIST_PREFIX = "auth:blacklist:";
    private static final long EXPIRY_HOURS = 8;

    private final RedisTemplate<String, Object> redisTemplate;
    private final String jwtSecret;

    public JwtService(RedisTemplate<String, Object> redisTemplate,
                      @Value("${JWT_SECRET}") String jwtSecret) {
        this.redisTemplate = redisTemplate;
        this.jwtSecret = jwtSecret;
    }

    public String generateToken(UUID userId, String username, UserRole role) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(EXPIRY_HOURS * 3600L);
        return Jwts.builder()
                .claim(CLAIM_USER_ID, userId.toString())
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_ROLE, role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    public Claims parseAndValidate(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public void blacklistToken(String token, Claims claims) {
        Instant expiry = claims.getExpiration().toInstant();
        long ttlSeconds = Duration.between(Instant.now(), expiry).getSeconds();
        if (ttlSeconds > 0) {
            redisTemplate.opsForValue().set(
                    BLACKLIST_PREFIX + token,
                    "blacklisted",
                    ttlSeconds,
                    TimeUnit.SECONDS
            );
            log.debug("Token blacklisted: ttlSeconds={}", ttlSeconds);
        }
    }

    public boolean isBlacklisted(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + token));
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }
}
