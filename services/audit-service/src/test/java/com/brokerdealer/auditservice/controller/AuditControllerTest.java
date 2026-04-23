package com.brokerdealer.auditservice.controller;

import com.brokerdealer.auditservice.service.AuditService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@WebMvcTest(AuditController.class)
class AuditControllerTest {

    @MockBean
    AuditService auditService;

    @Test
    void contextLoads() {
        // TODO: implement tests — JIRA story BD-XX
    }
}
