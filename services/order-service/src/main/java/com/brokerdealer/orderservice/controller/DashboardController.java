package com.brokerdealer.orderservice.controller;

import com.brokerdealer.orderservice.dto.response.HealthResponse;
import com.brokerdealer.orderservice.dto.response.RiskAlertsResponse;
import com.brokerdealer.orderservice.dto.response.TradingSummaryResponse;
import com.brokerdealer.orderservice.service.DashboardService;
import com.brokerdealer.orderservice.service.HealthAggregatorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final HealthAggregatorService healthAggregatorService;

    @GetMapping("/risk-alerts")
    public ResponseEntity<RiskAlertsResponse> getRiskAlerts() {
        return ResponseEntity.ok(dashboardService.getRiskAlerts());
    }

    @GetMapping("/summary")
    public ResponseEntity<TradingSummaryResponse> getTradingSummary() {
        return ResponseEntity.ok(dashboardService.getTradingSummary());
    }

    @GetMapping("/health")
    public ResponseEntity<HealthResponse> getSystemHealth() {
        return ResponseEntity.ok(healthAggregatorService.getSystemHealth());
    }
}
