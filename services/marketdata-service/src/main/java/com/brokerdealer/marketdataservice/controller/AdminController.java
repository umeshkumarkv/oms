package com.brokerdealer.marketdataservice.controller;

import com.brokerdealer.marketdataservice.config.RoleGuard;
import com.brokerdealer.marketdataservice.dto.request.ScenarioRequest;
import com.brokerdealer.marketdataservice.dto.response.ScenarioResponse;
import com.brokerdealer.marketdataservice.service.ScenarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final ScenarioService scenarioService;
    private final RoleGuard roleGuard;

    @PostMapping("/scenario")
    public ResponseEntity<Map<String, Object>> startScenario(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody @Valid ScenarioRequest request) {
        roleGuard.requireRole(authHeader, "ADMIN");
        log.info("Admin scenario request: scenarioId={} symbol={}", request.getScenarioId(), request.getSymbol());
        ScenarioResponse data = scenarioService.startScenario(request);
        return ResponseEntity.ok(buildSuccessResponse(data));
    }

    private Map<String, Object> buildSuccessResponse(Object data) {
        return Map.of(
                "data", data,
                "meta", Map.of(
                        "timestamp", Instant.now().toString(),
                        "requestId", UUID.randomUUID().toString()
                )
        );
    }
}
