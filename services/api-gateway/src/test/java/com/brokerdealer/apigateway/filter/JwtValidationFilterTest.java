package com.brokerdealer.apigateway.filter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import java.util.UUID;

import static com.brokerdealer.apigateway.filter.JwtValidationFilter.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtValidationFilterTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    private JwtValidationFilter filter;

    private static final String JWT_SECRET = "test-secret-key-that-is-at-least-32-chars-long!!";

    private String testUserId;

    @BeforeEach
    void setUp() {
        filter = new JwtValidationFilter(JWT_SECRET, redisTemplate);
        testUserId = UUID.randomUUID().toString();
    }

    private String buildValidToken() {
        return Jwts.builder()
                .claim("userId", testUserId)
                .claim("username", "trader.john")
                .claim("role", "TRADER")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 8 * 3600 * 1000L))
                .signWith(signingKey(), Jwts.SIG.HS256)
                .compact();
    }

    private String buildExpiredToken() {
        return Jwts.builder()
                .claim("userId", UUID.randomUUID().toString())
                .claim("username", "trader.john")
                .claim("role", "TRADER")
                .issuedAt(new Date(System.currentTimeMillis() - 10000))
                .expiration(new Date(System.currentTimeMillis() - 5000))
                .signWith(signingKey(), Jwts.SIG.HS256)
                .compact();
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void shouldPassThroughLoginPath() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void shouldPassThroughActuatorPath() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void shouldReturn401WhenAuthorizationHeaderMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
        assertThat(response.getContentAsString()).contains("UNAUTHORIZED");
    }

    @Test
    void shouldReturn401WhenBearerPrefixMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/orders");
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
        assertThat(response.getContentAsString()).contains("UNAUTHORIZED");
    }

    @Test
    void shouldReturn401WithUnauthorizedWhenTokenIsMalformed() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/orders");
        request.addHeader("Authorization", "Bearer invalid.jwt.token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
        assertThat(response.getContentAsString()).contains("\"code\":\"UNAUTHORIZED\"");
    }

    @Test
    void shouldReturn401WithTokenExpiredWhenTokenIsExpired() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/orders");
        request.addHeader("Authorization", "Bearer " + buildExpiredToken());
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
        assertThat(response.getContentAsString()).contains("\"code\":\"TOKEN_EXPIRED\"");
        assertThat(response.getContentAsString()).contains("Token has expired");
    }

    @Test
    void shouldReturn401WithTokenRevokedWhenTokenIsBlacklisted() throws Exception {
        String token = buildValidToken();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/orders");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        when(redisTemplate.hasKey("auth:blacklist:" + token)).thenReturn(true);

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
        assertThat(response.getContentAsString()).contains("\"code\":\"TOKEN_REVOKED\"");
        assertThat(response.getContentAsString()).contains("Token has been revoked");
    }

    @Test
    void shouldPassThroughAndForwardHeadersWhenTokenIsValid() throws Exception {
        String token = buildValidToken();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/orders");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        when(redisTemplate.hasKey("auth:blacklist:" + token)).thenReturn(false);

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();

        jakarta.servlet.http.HttpServletRequest forwarded =
                (jakarta.servlet.http.HttpServletRequest) chain.getRequest();
        assertThat(forwarded.getHeader(HEADER_USER_ID)).isEqualTo(testUserId);
        assertThat(forwarded.getHeader(HEADER_USERNAME)).isEqualTo("trader.john");
        assertThat(forwarded.getHeader(HEADER_USER_ROLE)).isEqualTo("TRADER");
    }

    @Test
    void shouldIncludeClaimHeadersInForwardedHeaderNames() throws Exception {
        String token = buildValidToken();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/clients");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        when(redisTemplate.hasKey("auth:blacklist:" + token)).thenReturn(false);

        filter.doFilterInternal(request, response, chain);

        jakarta.servlet.http.HttpServletRequest forwarded =
                (jakarta.servlet.http.HttpServletRequest) chain.getRequest();
        assertThat(Collections.list(forwarded.getHeaderNames()))
                .contains(HEADER_USER_ID, HEADER_USERNAME, HEADER_USER_ROLE);
    }

    @Test
    void shouldReturnStandardErrorEnvelopeOnUnauthorized() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        String body = response.getContentAsString();
        assertThat(body).contains("\"error\"");
        assertThat(body).contains("\"code\"");
        assertThat(body).contains("\"message\"");
        assertThat(body).contains("\"meta\"");
        assertThat(body).contains("\"timestamp\"");
        assertThat(body).contains("\"requestId\"");
    }
}
