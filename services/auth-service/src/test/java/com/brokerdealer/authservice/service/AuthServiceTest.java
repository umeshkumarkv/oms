package com.brokerdealer.authservice.service;

import com.brokerdealer.authservice.domain.User;
import com.brokerdealer.authservice.domain.UserRole;
import com.brokerdealer.authservice.dto.request.LoginRequest;
import com.brokerdealer.authservice.dto.response.LoginResponse;
import com.brokerdealer.authservice.dto.response.LogoutResponse;
import com.brokerdealer.authservice.dto.response.MeResponse;
import com.brokerdealer.authservice.exception.InvalidTokenException;
import com.brokerdealer.authservice.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    @Test
    void shouldLoginSuccessfully() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .username("trader.john")
                .passwordHash("hashed")
                .role(UserRole.TRADER)
                .build();

        LoginRequest request = new LoginRequest();
        request.setUsername("trader.john");
        request.setPassword("Demo@1234");

        when(userRepository.findByUsername("trader.john")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Demo@1234", "hashed")).thenReturn(true);
        when(jwtService.generateToken(userId, "trader.john", UserRole.TRADER)).thenReturn("jwt-token");

        LoginResponse response = authService.login(request);

        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getRole()).isEqualTo("TRADER");
        assertThat(response.getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void shouldThrowInvalidTokenExceptionWhenUserNotFound() {
        LoginRequest request = new LoginRequest();
        request.setUsername("unknown");
        request.setPassword("pass");

        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("incorrect");
    }

    @Test
    void shouldThrowInvalidTokenExceptionWhenPasswordDoesNotMatch() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .username("trader.john")
                .passwordHash("hashed")
                .role(UserRole.TRADER)
                .build();

        LoginRequest request = new LoginRequest();
        request.setUsername("trader.john");
        request.setPassword("wrong");

        when(userRepository.findByUsername("trader.john")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("incorrect");
    }

    @Test
    void shouldLogoutSuccessfully() {
        Claims claims = mock(Claims.class);
        when(jwtService.parseAndValidate("valid-token")).thenReturn(claims);

        LogoutResponse response = authService.logout("Bearer valid-token");

        assertThat(response.getMessage()).isEqualTo("Logged out successfully");
        verify(jwtService).blacklistToken("valid-token", claims);
    }

    @Test
    void shouldThrowInvalidTokenExceptionWhenBearerPrefixMissingOnLogout() {
        assertThatThrownBy(() -> authService.logout("invalid-header"))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("missing or invalid");
    }

    @Test
    void shouldThrowInvalidTokenExceptionWhenNullHeaderOnLogout() {
        assertThatThrownBy(() -> authService.logout(null))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("missing or invalid");
    }

    @Test
    void shouldThrowInvalidTokenExceptionWhenTokenExpiredOnLogout() {
        when(jwtService.parseAndValidate("expired-token"))
                .thenThrow(new ExpiredJwtException(null, null, "Token expired"));

        assertThatThrownBy(() -> authService.logout("Bearer expired-token"))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("missing or invalid");
    }

    @Test
    void shouldGetCurrentUserSuccessfully() {
        UUID userId = UUID.randomUUID();
        Claims claims = mock(Claims.class);
        when(claims.get("userId", String.class)).thenReturn(userId.toString());
        when(claims.get("username", String.class)).thenReturn("trader.john");
        when(claims.get("role", String.class)).thenReturn("TRADER");
        when(jwtService.parseAndValidate("valid-token")).thenReturn(claims);
        when(jwtService.isBlacklisted("valid-token")).thenReturn(false);

        MeResponse response = authService.getCurrentUser("Bearer valid-token");

        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getUsername()).isEqualTo("trader.john");
        assertThat(response.getRole()).isEqualTo("TRADER");
        assertThat(response.getDisplayName()).isEqualTo("trader.john");
    }

    @Test
    void shouldThrowInvalidTokenExceptionWhenTokenIsBlacklisted() {
        Claims claims = mock(Claims.class);
        when(jwtService.parseAndValidate("blacklisted-token")).thenReturn(claims);
        when(jwtService.isBlacklisted("blacklisted-token")).thenReturn(true);

        assertThatThrownBy(() -> authService.getCurrentUser("Bearer blacklisted-token"))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("invalidated");
    }

    @Test
    void shouldThrowInvalidTokenExceptionWhenBearerPrefixMissingOnMe() {
        assertThatThrownBy(() -> authService.getCurrentUser("no-bearer"))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("missing or invalid");
    }

    @Test
    void shouldThrowInvalidTokenExceptionWhenNullHeaderOnMe() {
        assertThatThrownBy(() -> authService.getCurrentUser(null))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("missing or invalid");
    }
}
