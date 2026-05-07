package com.brokerdealer.riskservice.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "risk_rule_config", schema = "risk_schema")
public class RiskRuleConfig {

    @Id
    @Column(name = "rule_code", nullable = false, length = 100)
    private String ruleCode;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    @Column(name = "threshold_value", precision = 18, scale = 4)
    private BigDecimal thresholdValue;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "priority", nullable = false)
    private Integer priority;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}