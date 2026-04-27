package com.brokerdealer.clientservice.controller;

import com.brokerdealer.clientservice.dto.response.ClientProfileResponse;
import com.brokerdealer.clientservice.dto.response.PortfolioResponse;
import com.brokerdealer.clientservice.service.ClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clients")
@RequiredArgsConstructor
@Slf4j
public class ClientController {

    private final ClientService clientService;

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

    @PatchMapping("/{id}/reserved-cash")
    public ResponseEntity<?> adjustReservedCash(
            @PathVariable UUID id,
            @RequestBody Object request) {
        // TODO: implement — JIRA story SCRUM-34
        return ResponseEntity.ok().build();
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
