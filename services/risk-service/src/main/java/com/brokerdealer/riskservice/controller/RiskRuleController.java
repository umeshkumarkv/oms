package com.brokerdealer.riskservice.controller;

import com.brokerdealer.riskservice.dto.request.UpdateRiskRuleRequest;
import com.brokerdealer.riskservice.dto.response.RiskRuleAuditResponse;
import com.brokerdealer.riskservice.dto.response.RiskRuleResponse;
import com.brokerdealer.riskservice.service.RiskRuleService;
import lombok.RequiredArgsConstructor;
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

    @GetMapping("/rules")
    public ResponseEntity<Map<String, Object>> getRiskRules() {
        List<RiskRuleResponse> rules = riskRuleService.getAllRules();
        return ResponseEntity.ok(buildResponse(rules));
    }

    @PatchMapping("/rules/{ruleCode}")
    public ResponseEntity<Map<String, Object>> updateRiskRule(
            @PathVariable String ruleCode,
            @RequestBody UpdateRiskRuleRequest request,
            @RequestHeader(value = "X-User-Id", defaultValue = "SYSTEM") String userId) {
        RiskRuleResponse updated = riskRuleService.updateRule(ruleCode, request, userId);
        return ResponseEntity.ok(buildResponse(updated));
    }

    @GetMapping("/rules/audit")
    public ResponseEntity<Map<String, Object>> getAuditLog() {
        List<RiskRuleAuditResponse> auditLog = riskRuleService.getAuditLog();
        return ResponseEntity.ok(buildResponse(auditLog));
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
