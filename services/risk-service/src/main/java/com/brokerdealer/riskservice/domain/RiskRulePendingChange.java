package com.brokerdealer.riskservice.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "risk_rule_pending_changes", schema = "risk_schema")
public class RiskRulePendingChange {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "rule_code", nullable = false, length = 100)
    private String ruleCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 30)
    private PendingChangeType changeType;

    @Column(name = "proposed_enabled")
    private Boolean proposedEnabled;

    @Column(name = "proposed_threshold", precision = 19, scale = 4)
    private BigDecimal proposedThreshold;

    @Column(name = "maker_user_id", nullable = false, length = 200)
    private String makerUserId;

    @Column(name = "maker_username", nullable = false, length = 200)
    private String makerUsername;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PendingChangeStatus status;

    @Column(name = "maker_note", length = 500)
    private String makerNote;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "checker_user_id", length = 200)
    private String checkerUserId;

    @Column(name = "current_enabled")
    private Boolean currentEnabled;

    @Column(name = "current_threshold", length = 255)
    private String currentThreshold;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;
}
