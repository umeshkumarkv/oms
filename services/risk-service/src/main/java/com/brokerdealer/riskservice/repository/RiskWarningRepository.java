package com.brokerdealer.riskservice.repository;

import com.brokerdealer.riskservice.domain.RiskWarning;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RiskWarningRepository extends JpaRepository<RiskWarning, UUID> {
}
