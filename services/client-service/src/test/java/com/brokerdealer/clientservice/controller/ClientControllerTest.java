package com.brokerdealer.clientservice.controller;

import com.brokerdealer.clientservice.service.ClientService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@WebMvcTest(ClientController.class)
class ClientControllerTest {

    @MockBean
    ClientService clientService;

    @Test
    void contextLoads() {
        // TODO: implement tests — JIRA story BD-XX
    }
}
