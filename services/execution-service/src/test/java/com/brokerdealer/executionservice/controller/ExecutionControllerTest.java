package com.brokerdealer.executionservice.controller;

import com.brokerdealer.executionservice.service.ExecutionService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@WebMvcTest(ExecutionController.class)
class ExecutionControllerTest {

    @MockBean
    ExecutionService executionService;

    @Test
    void contextLoads() {
        // TODO: implement tests — JIRA story BD-XX
    }
}
