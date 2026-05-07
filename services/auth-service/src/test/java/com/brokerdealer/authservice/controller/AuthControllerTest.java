package com.brokerdealer.authservice.controller;

import com.brokerdealer.authservice.config.SecurityConfig;
import com.brokerdealer.authservice.dto.request.LoginRequest;
import com.brokerdealer.authservice.dto.response.LoginData;
import com.brokerdealer.authservice.dto.response.LogoutResponse;
import com.brokerdealer.authservice.dto.response.MeResponse;
import com.brokerdealer.authservice.exception.AuthException;
import com.brokerdealer.authservice.exception.InvalidTokenException;
import com.brokerdealer.authservice.exception.ValidationException;
import com.brokerdealer.authservice.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldReturn200WithTokenOnSuccessfulLogin() throws Exception {
        LoginData loginData = LoginData.builder()
                .token("eyJhbGciOiJIUzI1NiJ9.test.sig")
                .expiresAt(Instant.now().plus(30, ChronoUnit.MINUTES).toString())
                .role("TRADER")
                .userId(UUID.randomUUID().toString())
                .displayName("trader.john")
                .build();

        when(authService.login(any())).thenReturn(loginData);

        LoginRequest request = new LoginRequest();
        request.setUsername("trader.john");
        request.setPassword("Demo@1234");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").value("eyJhbGciOiJIUzI1NiJ9.test.sig"))
                .andExpect(jsonPath("$.data.role").value("TRADER"))
                .andExpect(jsonPath("$.data.userId").isNotEmpty())
                .andExpect(jsonPath("$.data.displayName").value("trader.john"))
                .andExpect(jsonPath("$.data.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.meta.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.meta.requestId").isNotEmpty());
    }

    @Test
    void shouldReturn401OnBadCredentials() throws Exception {
        when(authService.login(any()))
                .thenThrow(new AuthException("UNAUTHORIZED", "Username or password is incorrect"));

        LoginRequest request = new LoginRequest();
        request.setUsername("trader.john");
        request.setPassword("WrongPassword");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.error.message").value("Username or password is incorrect"))
                .andExpect(jsonPath("$.meta.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.meta.requestId").isNotEmpty());
    }

    @Test
    void shouldReturn401OnInactiveAccount() throws Exception {
        when(authService.login(any()))
                .thenThrow(new AuthException("ACCOUNT_INACTIVE", "Account is inactive"));

        LoginRequest request = new LoginRequest();
        request.setUsername("trader.john");
        request.setPassword("Demo@1234");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_INACTIVE"))
                .andExpect(jsonPath("$.error.message").value("Account is inactive"));
    }

    @Test
    void shouldReturn400WhenUsernameIsMissing() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setPassword("Demo@1234");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldReturn400WhenPasswordIsMissing() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername("trader.john");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldReturn400OnValidationException() throws Exception {
        when(authService.login(any()))
                .thenThrow(new ValidationException("VALIDATION_FAILED", "Validation failed"));

        LoginRequest request = new LoginRequest();
        request.setUsername("trader.john");
        request.setPassword("Demo@1234");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.meta.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.meta.requestId").isNotEmpty());
    }

    @Test
    void shouldReturn500OnUnexpectedException() throws Exception {
        when(authService.login(any()))
                .thenThrow(new RuntimeException("Unexpected error"));

        LoginRequest request = new LoginRequest();
        request.setUsername("trader.john");
        request.setPassword("Demo@1234");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.meta.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.meta.requestId").isNotEmpty());
    }

    @Test
    void shouldReturnOkOnSuccessfulLogout() throws Exception {
        LogoutResponse logoutResponse = LogoutResponse.builder()
                .message("Logged out successfully")
                .build();

        when(authService.logout(eq("Bearer valid-token"))).thenReturn(logoutResponse);

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("Logged out successfully"))
                .andExpect(jsonPath("$.meta.requestId").exists());
    }

    @Test
    void shouldReturn401WhenTokenInvalidOnLogout() throws Exception {
        when(authService.logout(any()))
                .thenThrow(new InvalidTokenException("UNAUTHORIZED", "Token is missing or invalid"));

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer bad-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void shouldReturn401WhenNoAuthHeaderOnLogout() throws Exception {
        when(authService.logout(isNull(String.class)))
                .thenThrow(new InvalidTokenException("UNAUTHORIZED", "Token is missing or invalid"));

        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void shouldReturnOkOnMeRequest() throws Exception {
        UUID userId = UUID.randomUUID();
        MeResponse meResponse = MeResponse.builder()
                .userId(userId)
                .username("trader.john")
                .role("TRADER")
                .displayName("trader.john")
                .build();

        when(authService.getCurrentUser(eq("Bearer valid-token"))).thenReturn(meResponse);

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(userId.toString()))
                .andExpect(jsonPath("$.data.username").value("trader.john"))
                .andExpect(jsonPath("$.data.role").value("TRADER"))
                .andExpect(jsonPath("$.data.displayName").value("trader.john"))
                .andExpect(jsonPath("$.meta.requestId").exists());
    }

    @Test
    void shouldReturn401WhenTokenBlacklistedOnMe() throws Exception {
        when(authService.getCurrentUser(any()))
                .thenThrow(new InvalidTokenException("UNAUTHORIZED", "Token has been invalidated"));

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer blacklisted-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void shouldReturn401WhenNoAuthHeaderOnMe() throws Exception {
        when(authService.getCurrentUser(isNull(String.class)))
                .thenThrow(new InvalidTokenException("UNAUTHORIZED", "Token is missing or invalid"));

        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }
}
