package com.brokerdealer.marketdataservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "market.scenarios")
@Data
public class ScenarioConfig {

    private Map<String, ScenarioDefinition> items = new HashMap<>();

    @Data
    public static class ScenarioDefinition {
        private String symbol;
        private BigDecimal directionPct;
        private int ticks;
    }
}
