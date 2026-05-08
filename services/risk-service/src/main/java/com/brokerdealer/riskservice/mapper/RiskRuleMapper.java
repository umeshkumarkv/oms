package com.brokerdealer.riskservice.mapper;

import com.brokerdealer.riskservice.domain.RiskRuleAudit;
import com.brokerdealer.riskservice.domain.RiskRuleConfig;
import com.brokerdealer.riskservice.dto.response.RiskRuleAuditResponse;
import com.brokerdealer.riskservice.dto.response.RiskRuleResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface RiskRuleMapper {

    @Mapping(target = "thresholdValue",
             expression = "java(config.getThresholdValue() != null ? config.getThresholdValue().toPlainString() : null)")
    RiskRuleResponse toResponse(RiskRuleConfig config);

    RiskRuleAuditResponse toAuditResponse(RiskRuleAudit audit);
}
