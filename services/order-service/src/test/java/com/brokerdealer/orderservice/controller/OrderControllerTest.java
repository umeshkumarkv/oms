package com.brokerdealer.orderservice.controller;

import com.brokerdealer.orderservice.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @MockBean
    OrderService orderService;

    @Test
    void contextLoads() {
        // TODO: implement tests — JIRA story BD-XX
    }
}
