package com.brokerdealer.notificationservice.service;

import com.brokerdealer.notificationservice.config.WebSocketAuthInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest
class NotificationServiceTest {

    @MockBean
    private WebSocketAuthInterceptor webSocketAuthInterceptor;

    @Test
    void contextLoads() {
        // TODO: implement — JIRA story BD-XX
    }
}