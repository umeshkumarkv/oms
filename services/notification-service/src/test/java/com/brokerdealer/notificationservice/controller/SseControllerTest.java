package com.brokerdealer.notificationservice.controller;

import com.brokerdealer.notificationservice.config.SecurityConfig;
import com.brokerdealer.notificationservice.service.SseEmitterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SseController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "app.jwt.secret=test-secret-key-for-unit-tests-32chars")
class SseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SseEmitterRegistry sseEmitterRegistry;

    @Test
    void shouldReturn401WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/stream")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "a1000000-0000-0000-0000-000000000001", roles = {"TRADER"})
    void shouldReturn200WhenTraderAuthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/stream")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "b2000000-0000-0000-0000-000000000002", roles = {"OPERATIONS"})
    void shouldReturn200WhenOperationsAuthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/stream")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk());
    }
}
