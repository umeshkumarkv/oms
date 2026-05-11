package com.brokerdealer.orderservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class HttpClientConfig {

    /**
     * No connection pool — one connection per virtual thread.
     * Uses Java 21's built-in HttpClient via JdkClientHttpRequestFactory.
     * Supports all HTTP methods including PATCH.
     * Virtual threads (Java 21) make blocking I/O cheap, so there is no need
     * to cap concurrency with a pool. Each virtual thread blocks independently
     * without consuming an OS thread while waiting.
     */
    @Bean
    public RestTemplate restTemplate() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(2000))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(2000));
        return new RestTemplate(factory);
    }
}