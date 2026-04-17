package com.brokerdealer.clientservice.controller;

import com.brokerdealer.clientservice.service.ClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clients")
@RequiredArgsConstructor
public class ClientController {

    private final ClientService clientService;

    @GetMapping("/{id}")
    public ResponseEntity<?> getClient(@PathVariable UUID id) {
        // TODO: implement — JIRA story BD-XX
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/portfolio")
    public ResponseEntity<?> getPortfolio(@PathVariable UUID id) {
        // TODO: implement — JIRA story BD-XX
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{id}/reserved-cash")
    public ResponseEntity<?> adjustReservedCash(
            @PathVariable UUID id,
            @RequestBody Object request) {
        // TODO: implement — JIRA story BD-XX
        return ResponseEntity.ok().build();
    }
}