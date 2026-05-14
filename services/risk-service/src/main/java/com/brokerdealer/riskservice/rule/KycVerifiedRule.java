package com.brokerdealer.riskservice.rule;

import com.brokerdealer.riskservice.domain.RiskResult;
import com.brokerdealer.riskservice.domain.RiskRule;
import com.brokerdealer.riskservice.dto.RiskContext;
import com.brokerdealer.riskservice.dto.RiskEvaluationRequest;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;

@RequiredArgsConstructor
public class KycVerifiedRule implements RiskRule {

    private final boolean enabled;

    @Override
    public String getRuleCode() {
        return "RULE_KYC_VERIFIED";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public RiskResult evaluate(RiskEvaluationRequest request, RiskContext context) {
        if (request.getKycStatus() == null || !"VERIFIED".equals(request.getKycStatus())) {
            return RiskResult.rejected(getRuleCode(),
                    "KYC_NOT_VERIFIED: KYC status is " + request.getKycStatus());
        }
        if (request.getKycExpiryDate() != null) {
            LocalDate expiry = LocalDate.parse(request.getKycExpiryDate());
            if (expiry.isBefore(LocalDate.now())) {
                return RiskResult.rejected(getRuleCode(),
                        "KYC_NOT_VERIFIED: KYC expired on " + request.getKycExpiryDate());
            }
        }
        return RiskResult.passed();
    }
}
