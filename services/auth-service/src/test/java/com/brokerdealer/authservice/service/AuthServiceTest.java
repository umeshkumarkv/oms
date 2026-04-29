package com.brokerdealer.authservice.service;

import com.brokerdealer.authservice.domain.User;
import com.brokerdealer.authservice.domain.UserRole;
import com.brokerdealer.authservice.domain.UserStatus;
import com.brokerdealer.authservice.dto.request.LoginRequest;
import com.brokerdealer.authservice.dto.response.LoginData;
import com.brokerdealer.authservice.dto.response.LogoutResponse;
import com.brokerdealer.authservice.dto.response.MeResponse;
import com.brokerdealer.authservice.exception.AuthException;
import com.brokerdealer.authservice.exception.InvalidTokenException;
import com.brokerdealer.authservice.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.BeforeEach;
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

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(UUID.randomUUID())
                .username("trader.john")
                .passwordHash("$2a$10$hashedpassword")
                .role(UserRole.TRADER)
                .status(UserStatus.ACTIVE)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void shouldReturnLoginDataOnSuccessfulLogin() {
        LoginRequest request = new LoginRequest();
        request.setUsername("trader.john");
        request.setPassword("Demo@1234");

        when(userRepository.findByUsernameIgnoreCase("trader.john")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("Demo@1234", testUser.getPasswordHash())).thenReturn(true);
        when(jwtService.generateToken(testUser.getId(), testUser.getUsername(), testUser.getRole()))
                .thenReturn("jwt-token");

        LoginData result = authService.login(request);

        assertThat(result.getToken()).isEqualTo("jwt-token");
        assertThat(result.getRole()).isEqualTo("TRADER");
        assertThat(result.getUserId()).isEqualTo(testUser.getId().toString());
        assertThat(result.getDisplayName()).isEqualTo("trader.john");
        assertThat(result.getExpiresAt()).isNotBlank();
    }

    @Test
    void shouldThrowAuthExceptionWhenUserNotFound() {
        LoginRequest request = new LoginRequest();
        request.setUsername("unknown.user");
        request.setPassword("Demo@1234");

        when(userRepository.findByUsernameIgnoreCase("unknown.user")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(AuthException.class)
                .hasFieldOrPropertyWithValue("errorCode", "UNAUTHORIZED")
                .hasMessage("Username or password is incorrect");
    }

    @Test
    void shouldThrowAuthExceptionOnWrongPassword() {
        LoginRequest request = new LoginRequest();
        request.setUsername("trader.john");
        request.setPassword("WrongPassword");

        when(userRepository.findByUsernameIgnoreCase("trader.john")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("WrongPassword", testUser.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(AuthException.class)
                .hasFieldOrPropertyWithValue("errorCode", "UNAUTHORIZED")
                .hasMessage("Username or password is incorrect");
    }

    @Test
    void shouldThrowAccountInactiveForInactiveUser() {
        User inactiveUser = User.builder()
                .id(UUID.randomUUID())
                .username("trader.john")
                .passwordHash("$2a$10$hashedpassword")
                .role(UserRole.TRADER)
                .status(UserStatus.INACTIVE)
                .createdAt(Instant.now())
                .build();

        LoginRequest request = new LoginRequest();
        request.setUsername("trader.john");
        request.setPassword("Demo@1234");

        when(userRepository.findByUsernameIgnoreCase("trader.john")).thenReturn(Optional.of(inactiveUser));
        when(passwordEncoder.matches("Demo@1234", inactiveUser.getPasswordHash())).thenReturn(true);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(AuthException.class)
                .hasFieldOrPropertyWithValue("errorCode", "ACCOUNT_INACTIVE")
                .hasMessage("Account is inactive");
    }

    @Test
    void shouldPerformCaseInsensitiveUsernameLookup() {
        LoginRequest request = new LoginRequest();
        request.setUsername("TRADER.JOHN");
        request.setPassword("Demo@1234");

        when(userRepository.findByUsernameIgnoreCase("TRADER.JOHN")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("Demo@1234", testUser.getPasswordHash())).thenReturn(true);
        when(jwtService.generateToken(any(), any(), any())).thenReturn("jwt-token");

        LoginData result = authService.login(request);

        assertThat(result).isNotNull();
        verify(userRepository).findByUsernameIgnoreCase("TRADER.JOHN");
    }

    @Test
    void shouldSetExpiryEightHoursFromNow() {
        LoginRequest request = new LoginRequest();
        request.setUsername("trader.john");
        request.setPassword("Demo@1234");

        when(userRepository.findByUsernameIgnoreCase("trader.john")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("Demo@1234", testUser.getPasswordHash())).thenReturn(true);
        when(jwtService.generateToken(any(), any(), any())).thenReturn("jwt-token");

        Instant before = Instant.now();
        LoginData result = authService.login(request);
        Instant after = Instant.now();

        Instant expiresAt = Instant.parse(result.getExpiresAt());
        assertThat(expiresAt).isAfter(before.plusSeconds(8 * 3600 - 5));
        assertThat(expiresAt).isBefore(after.plusSeconds(8 * 3600 + 5));
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
