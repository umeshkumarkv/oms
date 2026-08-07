package com.brokerdealer.executionservice.metrics;

public interface OrderMetricsService {
    void recordOrderOutcome(String outcome);
}
