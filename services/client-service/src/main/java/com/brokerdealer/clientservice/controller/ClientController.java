package com.brokerdealer.clientservice.controller;

import com.brokerdealer.clientservice.dto.request.UpdateDefaultRouteRequest;
import com.brokerdealer.clientservice.dto.response.ActiveClientResponse;
import com.brokerdealer.clientservice.dto.response.ClientProfileResponse;
import com.brokerdealer.clientservice.dto.response.PortfolioResponse;
import com.brokerdealer.clientservice.dto.response.UpdateDefaultRouteResponse;
import com.brokerdealer.clientservice.service.ClientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clients")
@RequiredArgsConstructor
@Slf4j
public class ClientController {

    private final ClientService clientService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllClients() {
        log.debug("GET /api/v1/clients");
        List<ClientProfileResponse> data = clientService.getAllClients();
        return ResponseEntity.ok(buildSuccessResponse(data));
    }

    @GetMapping("/active")
    public ResponseEntity<Map<String, Object>> getActiveClients(
            @RequestParam(defaultValue = "10") int limit) {
        log.debug("GET /api/v1/clients/active limit={}", limit);
        List<ActiveClientResponse> data = clientService.getActiveClients(limit);
        return ResponseEntity.ok(buildSuccessResponse(data));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getClient(@PathVariable UUID id) {
        log.debug("GET /api/v1/clients/{}", id);
        ClientProfileResponse data = clientService.getClient(id);
        return ResponseEntity.ok(buildSuccessResponse(data));
    }

    @GetMapping("/{id}/portfolio")
    public ResponseEntity<Map<String, Object>> getPortfolio(@PathVariable UUID id) {
        log.debug("GET /api/v1/clients/{}/portfolio", id);
        PortfolioResponse data = clientService.getPortfolio(id);
        return ResponseEntity.ok(buildSuccessResponse(data));
    }

    @PatchMapping("/{id}/default-route")
    public ResponseEntity<Map<String, Object>> updateDefaultRoute(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDefaultRouteRequest request) {
        log.debug("PATCH /api/v1/clients/{}/default-route", id);
        UpdateDefaultRouteResponse data = clientService.updateDefaultRoute(id, request);
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
