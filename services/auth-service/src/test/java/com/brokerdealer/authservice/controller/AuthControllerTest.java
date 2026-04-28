package com.brokerdealer.authservice.controller;

import com.brokerdealer.authservice.dto.response.LoginData;
import com.brokerdealer.authservice.exception.AuthException;
import com.brokerdealer.authservice.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    AuthService authService;

    @Test
    void shouldReturn200WithTokenOnSuccessfulLogin() throws Exception {
        LoginData loginData = LoginData.builder()
                .token("eyJhbGciOiJIUzI1NiJ9.test.sig")
                .expiresAt(Instant.now().plus(8, ChronoUnit.HOURS).toString())
                .role("TRADER")
                .userId(UUID.randomUUID().toString())
                .displayName("trader.john")
                .build();

        when(authService.login(any())).thenReturn(loginData);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"trader.john\",\"password\":\"Demo@1234\"}"))
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

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"trader.john\",\"password\":\"WrongPassword\"}"))
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

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"trader.john\",\"password\":\"Demo@1234\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_INACTIVE"))
                .andExpect(jsonPath("$.error.message").value("Account is inactive"));
    }

    @Test
    void shouldReturn400WhenUsernameIsMissing() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"Demo@1234\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldReturn400WhenPasswordIsMissing() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"trader.john\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}
