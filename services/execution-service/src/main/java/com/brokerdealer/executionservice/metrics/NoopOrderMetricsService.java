package com.brokerdealer.executionservice.metrics;

import org.springframework.stereotype.Service;

@Service
public class NoopOrderMetricsService implements OrderMetricsService {
    @Override
    public void recordOrderOutcome(String outcome) {
        // no-op default implementation; swaps to real Micrometer implementation in production if desired
    }
}
