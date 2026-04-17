package com.brokerdealer.marketdataservice.controller;

import com.brokerdealer.marketdataservice.service.MarketDataService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@WebMvcTest(MarketDataController.class)
class MarketDataControllerTest {

    @MockBean
    MarketDataService marketDataService;

    @Test
    void contextLoads() {
        // TODO: implement tests — JIRA story BD-XX
    }
}
