package com.brokerdealer.riskservice.repository;

import com.brokerdealer.riskservice.domain.PendingChangeStatus;
import com.brokerdealer.riskservice.domain.RiskRulePendingChange;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RiskRulePendingChangeRepository extends JpaRepository<RiskRulePendingChange, UUID> {

    List<RiskRulePendingChange> findAllByOrderBySubmittedAtDesc();

    List<RiskRulePendingChange> findByStatusOrderBySubmittedAtDesc(PendingChangeStatus status);
}
