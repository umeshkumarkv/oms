package com.brokerdealer.apigateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

@Configuration
public class GatewayConfig {

    @Value("${notification.stream.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    @Bean
    public ClientHttpRequestFactory sseRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(0); // 0 = no timeout — SSE streams are infinite
        return factory;
    }
}
