package com.brokerdealer.riskservice.repository;

import com.brokerdealer.riskservice.domain.RiskRuleAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RiskRuleAuditRepository extends JpaRepository<RiskRuleAudit, UUID> {

    List<RiskRuleAudit> findAllByOrderByChangedAtDesc();
}
