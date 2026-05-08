package com.brokerdealer.riskservice.dto.request;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateRiskRuleRequest {

    private Boolean enabled;
    private BigDecimal thresholdValue;
    private String changeReason;
}
