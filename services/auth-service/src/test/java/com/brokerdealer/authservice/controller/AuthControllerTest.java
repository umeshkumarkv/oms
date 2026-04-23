package com.brokerdealer.authservice.controller;

import com.brokerdealer.authservice.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@WebMvcTest(AuthController.class)
class AuthControllerTest {

    @MockBean
    AuthService authService;

    @Test
    void contextLoads() {
        // TODO: implement tests — JIRA story BD-XX
    }
}
