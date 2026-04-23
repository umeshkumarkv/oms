package com.brokerdealer.auditservice.controller;

import com.brokerdealer.auditservice.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    @GetMapping("/orders/{id}")
    public ResponseEntity<?> getOrderAuditTrail(@PathVariable UUID id) {
        // TODO: implement — JIRA story BD-XX
        return ResponseEntity.ok().build();
    }

    @GetMapping("/trail")
    public ResponseEntity<?> getAuditTrail(
            @RequestParam(required = false) UUID clientId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String entityType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // TODO: implement — JIRA story BD-XX
        return ResponseEntity.ok().build();
    }
}