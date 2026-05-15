package com.brokerdealer.riskservice.domain;

import lombok.Value;

@Value
public class RiskResult {

    String result;
    String failedRule;
    String reason;

    public static RiskResult passed() {
        return new RiskResult("PASSED", null, null);
    }

    public static RiskResult rejected(String ruleCode, String reason) {
        return new RiskResult("REJECTED", ruleCode, reason);
    }

    public static RiskResult warned(String ruleCode, String reason) {
        return new RiskResult("WARNED", ruleCode, reason);
    }
}
