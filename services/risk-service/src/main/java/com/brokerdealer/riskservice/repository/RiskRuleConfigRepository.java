package com.brokerdealer.riskservice.repository;

import com.brokerdealer.riskservice.domain.RiskRuleConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RiskRuleConfigRepository extends JpaRepository<RiskRuleConfig, String> {

    List<RiskRuleConfig> findByEnabledTrueOrderByPriority();
}
