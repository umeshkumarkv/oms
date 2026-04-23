package com.brokerdealer.riskservice.controller;

import com.brokerdealer.riskservice.service.RiskRuleService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@WebMvcTest(RiskRuleController.class)
class RiskRuleControllerTest {

    @MockBean
    RiskRuleService riskRuleService;

    @Test
    void contextLoads() {
        // TODO: implement tests — JIRA story BD-XX
    }
}
