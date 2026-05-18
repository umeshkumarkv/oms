package com.brokerdealer.riskservice.controller;

import com.brokerdealer.riskservice.dto.request.RejectPendingChangeRequest;
import com.brokerdealer.riskservice.dto.request.UpdateRiskRuleRequest;
import com.brokerdealer.riskservice.dto.response.PendingChangeResponse;
import com.brokerdealer.riskservice.dto.response.RiskRuleAuditResponse;
import com.brokerdealer.riskservice.dto.response.RiskRuleResponse;
import com.brokerdealer.riskservice.service.PendingChangeService;
import com.brokerdealer.riskservice.service.RiskRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/risk")
@RequiredArgsConstructor
public class RiskRuleController {

    private final RiskRuleService riskRuleService;
    private final PendingChangeService pendingChangeService;

    @GetMapping("/rules")
    public ResponseEntity<Map<String, Object>> getRiskRules() {
        List<RiskRuleResponse> rules = riskRuleService.getAllRules();
        return ResponseEntity.ok(buildResponse(rules));
    }

    @PatchMapping("/rules/{ruleCode}")
    public ResponseEntity<Map<String, Object>> submitPendingChange(
            @PathVariable String ruleCode,
            @RequestBody UpdateRiskRuleRequest request,
            @RequestHeader(value = "X-User-Id", defaultValue = "SYSTEM") String userId,
            @RequestHeader(value = "X-Username", defaultValue = "SYSTEM") String username) {
        PendingChangeResponse response = pendingChangeService.submitPendingChange(ruleCode, request, userId, username);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(buildResponse(response));
    }

    @GetMapping("/rules/audit")
    public ResponseEntity<Map<String, Object>> getAuditLog() {
        List<RiskRuleAuditResponse> auditLog = riskRuleService.getAuditLog();
        return ResponseEntity.ok(buildResponse(auditLog));
    }

    @GetMapping("/rules/pending-changes")
    public ResponseEntity<Map<String, Object>> listPendingChanges(
            @RequestParam(required = false) String status) {
        List<PendingChangeResponse> changes = pendingChangeService.listPendingChanges(status);
        return ResponseEntity.ok(buildResponse(changes));
    }

    @GetMapping("/rules/pending-changes/{changeId}")
    public ResponseEntity<Map<String, Object>> getPendingChange(@PathVariable UUID changeId) {
        PendingChangeResponse change = pendingChangeService.getPendingChange(changeId);
        return ResponseEntity.ok(buildResponse(change));
    }

    @PatchMapping("/rules/pending-changes/{changeId}/approve")
    public ResponseEntity<Map<String, Object>> approveChange(
            @PathVariable UUID changeId,
            @RequestHeader(value = "X-User-Id", defaultValue = "SYSTEM") String userId) {
        PendingChangeResponse response = pendingChangeService.approveChange(changeId, userId);
        return ResponseEntity.ok(buildResponse(response));
    }

    @PatchMapping("/rules/pending-changes/{changeId}/reject")
    public ResponseEntity<Map<String, Object>> rejectChange(
            @PathVariable UUID changeId,
            @RequestBody RejectPendingChangeRequest request,
            @RequestHeader(value = "X-User-Id", defaultValue = "SYSTEM") String userId) {
        PendingChangeResponse response = pendingChangeService.rejectChange(changeId, userId, request.getRejectionReason());
        return ResponseEntity.ok(buildResponse(response));
    }

    private Map<String, Object> buildResponse(Object data) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("timestamp", Instant.now().toString());
        meta.put("requestId", UUID.randomUUID().toString());
        Map<String, Object> response = new HashMap<>();
        response.put("data", data);
        response.put("meta", meta);
        return response;
    }
}
