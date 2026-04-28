package com.brokerdealer.authservice.service;

import com.brokerdealer.authservice.domain.User;
import com.brokerdealer.authservice.domain.UserRole;
import com.brokerdealer.authservice.dto.request.LoginRequest;
import com.brokerdealer.authservice.dto.response.LoginData;
import com.brokerdealer.authservice.exception.AuthException;
import com.brokerdealer.authservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    private User testUser;

    // Must be >= 32 bytes for HS256
    private static final String TEST_JWT_SECRET = "test-secret-key-that-is-at-least-32-bytes-long!";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "jwtSecret", TEST_JWT_SECRET);

        testUser = User.builder()
                .id(UUID.randomUUID())
                .username("trader.john")
                .passwordHash("$2a$10$hashedpassword")
                .role(UserRole.TRADER)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void shouldReturnTokenOnSuccessfulLogin() {
        LoginRequest request = new LoginRequest();
        request.setUsername("trader.john");
        request.setPassword("Demo@1234");

        when(userRepository.findByUsernameIgnoreCase("trader.john")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("Demo@1234", testUser.getPasswordHash())).thenReturn(true);

        LoginData result = authService.login(request);

        assertThat(result.getToken()).isNotBlank();
        assertThat(result.getRole()).isEqualTo("TRADER");
        assertThat(result.getUserId()).isEqualTo(testUser.getId().toString());
        assertThat(result.getDisplayName()).isEqualTo("trader.john");
        assertThat(result.getExpiresAt()).isNotBlank();
    }

    @Test
    void shouldThrowUnauthorizedOnWrongPassword() {
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
    void shouldThrowUnauthorizedWhenUserNotFound() {
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
    void shouldPerformCaseInsensitiveUsernameLookup() {
        LoginRequest request = new LoginRequest();
        request.setUsername("TRADER.JOHN");
        request.setPassword("Demo@1234");

        when(userRepository.findByUsernameIgnoreCase("TRADER.JOHN")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("Demo@1234", testUser.getPasswordHash())).thenReturn(true);

        LoginData result = authService.login(request);

        assertThat(result).isNotNull();
        verify(userRepository).findByUsernameIgnoreCase("TRADER.JOHN");
    }

    @Test
    void shouldReturnNullForGetCurrentUser() {
        Object result = authService.getCurrentUser("some-token");
        assertThat(result).isNull();
    }

    @Test
    void shouldCompleteLogoutWithoutError() {
        authService.logout("some-token");
        // stub — no exception expected
    }

    @Test
    void shouldSetExpiryEightHoursFromNow() {
        LoginRequest request = new LoginRequest();
        request.setUsername("trader.john");
        request.setPassword("Demo@1234");

        when(userRepository.findByUsernameIgnoreCase("trader.john")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("Demo@1234", testUser.getPasswordHash())).thenReturn(true);

        Instant before = Instant.now();
        LoginData result = authService.login(request);
        Instant after = Instant.now();

        Instant expiresAt = Instant.parse(result.getExpiresAt());
        assertThat(expiresAt).isAfter(before.plusSeconds(8 * 3600 - 5));
        assertThat(expiresAt).isBefore(after.plusSeconds(8 * 3600 + 5));
    }
}
