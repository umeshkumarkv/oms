package com.brokerdealer.riskservice.controller;

import com.brokerdealer.riskservice.service.RiskRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/risk")
@RequiredArgsConstructor
public class RiskRuleController {

    private final RiskRuleService riskRuleService;

    @GetMapping("/rules")
    public ResponseEntity<?> getRiskRules() {
        // TODO: implement — JIRA story BD-XX
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/rules/{ruleCode}")
    public ResponseEntity<?> updateRiskRule(
            @PathVariable String ruleCode,
            @RequestBody Object request) {
        // TODO: implement — JIRA story BD-XX
        return ResponseEntity.ok().build();
    }
}