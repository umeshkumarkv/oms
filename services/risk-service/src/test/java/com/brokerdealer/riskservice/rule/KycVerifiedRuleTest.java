package com.brokerdealer.riskservice.rule;

import com.brokerdealer.riskservice.domain.RiskResult;
import com.brokerdealer.riskservice.dto.RiskContext;
import com.brokerdealer.riskservice.dto.RiskEvaluationRequest;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class KycVerifiedRuleTest {

    private final RiskContext context = RiskContext.builder()
            .orderId(UUID.randomUUID())
            .clientId(UUID.randomUUID())
            .build();

    private RiskEvaluationRequest requestWith(String kycStatus, String kycExpiryDate) {
        RiskEvaluationRequest req = new RiskEvaluationRequest();
        req.setKycStatus(kycStatus);
        req.setKycExpiryDate(kycExpiryDate);
        req.setSide("BUY");
        return req;
    }

    @Test
    void shouldPassWhenKycVerifiedAndFutureExpiry() {
        KycVerifiedRule rule = new KycVerifiedRule(true);
        RiskResult result = rule.evaluate(
                requestWith("VERIFIED", LocalDate.now().plusYears(1).toString()), context);
        assertThat(result.getResult()).isEqualTo("PASSED");
    }

    @Test
    void shouldRejectWhenKycStatusIsPending() {
        KycVerifiedRule rule = new KycVerifiedRule(true);
        RiskResult result = rule.evaluate(requestWith("PENDING", "2027-01-01"), context);
        assertThat(result.getResult()).isEqualTo("REJECTED");
        assertThat(result.getFailedRule()).isEqualTo("RULE_KYC_VERIFIED");
        assertThat(result.getReason()).contains("KYC_NOT_VERIFIED");
        assertThat(result.getReason()).contains("PENDING");
    }

    @Test
    void shouldRejectWhenKycStatusIsExpiredString() {
        KycVerifiedRule rule = new KycVerifiedRule(true);
        RiskResult result = rule.evaluate(requestWith("EXPIRED", "2025-01-01"), context);
        assertThat(result.getResult()).isEqualTo("REJECTED");
        assertThat(result.getReason()).contains("KYC_NOT_VERIFIED");
    }

    @Test
    void shouldRejectWhenKycExpiryDateIsInPast() {
        KycVerifiedRule rule = new KycVerifiedRule(true);
        String yesterday = LocalDate.now().minusDays(1).toString();
        RiskResult result = rule.evaluate(requestWith("VERIFIED", yesterday), context);
        assertThat(result.getResult()).isEqualTo("REJECTED");
        assertThat(result.getFailedRule()).isEqualTo("RULE_KYC_VERIFIED");
        assertThat(result.getReason()).contains("KYC_NOT_VERIFIED");
        assertThat(result.getReason()).contains(yesterday);
    }

    @Test
    void shouldPassWhenKycExpiryDateIsToday() {
        KycVerifiedRule rule = new KycVerifiedRule(true);
        String today = LocalDate.now().toString();
        RiskResult result = rule.evaluate(requestWith("VERIFIED", today), context);
        // today.isBefore(today) == false → pass
        assertThat(result.getResult()).isEqualTo("PASSED");
    }

    @Test
    void shouldRejectWhenKycStatusIsNull() {
        KycVerifiedRule rule = new KycVerifiedRule(true);
        RiskResult result = rule.evaluate(requestWith(null, "2027-01-01"), context);
        assertThat(result.getResult()).isEqualTo("REJECTED");
        assertThat(result.getReason()).contains("KYC_NOT_VERIFIED");
    }

    @Test
    void shouldPassWhenKycExpiryDateIsNull() {
        // kycStatus=VERIFIED, no expiry date → pass (no expiry check performed)
        KycVerifiedRule rule = new KycVerifiedRule(true);
        RiskResult result = rule.evaluate(requestWith("VERIFIED", null), context);
        assertThat(result.getResult()).isEqualTo("PASSED");
    }

    @Test
    void shouldReturnCorrectRuleCode() {
        KycVerifiedRule rule = new KycVerifiedRule(true);
        assertThat(rule.getRuleCode()).isEqualTo("RULE_KYC_VERIFIED");
    }

    @Test
    void shouldReportDisabledCorrectly() {
        KycVerifiedRule rule = new KycVerifiedRule(false);
        assertThat(rule.isEnabled()).isFalse();
    }
}
