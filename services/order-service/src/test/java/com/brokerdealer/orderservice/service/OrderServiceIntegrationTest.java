package com.brokerdealer.orderservice.service;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Integration test placeholder for end-to-end flow from order-service -> execution-service.
 * Requires a running test environment (Docker Compose or testcontainers) and is intentionally
 * disabled in CI until the environment is available.
 */
@SpringBootTest
@Disabled("Requires integration test environment: Docker Compose or Testcontainers")
public class OrderServiceIntegrationTest {

    @Test
    void shouldProcessFillOrKillOrderEndToEnd() {
        // TODO: Implement integration test wiring order-service, execution-service, and a test Kafka broker.
        // Steps:
        // 1) Start services via docker-compose or Testcontainers
        // 2) POST /api/v1/orders with fillOrKill=true
        // 3) Wait for order state terminal and assert status is FILLED or CANCELLED and no partial fills
    }
}
