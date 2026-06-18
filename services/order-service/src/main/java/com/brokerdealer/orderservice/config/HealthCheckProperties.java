package com.brokerdealer.orderservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "app.health")
@Getter
@Setter
public class HealthCheckProperties {
    private long timeoutMs = 500;
    private Map<String, String> services = new LinkedHashMap<>();
}
