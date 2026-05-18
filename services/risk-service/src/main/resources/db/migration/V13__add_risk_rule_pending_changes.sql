CREATE TABLE IF NOT EXISTS risk_schema.risk_rule_pending_changes (
    id                UUID         PRIMARY KEY,
    rule_code         VARCHAR(100) NOT NULL REFERENCES risk_schema.risk_rule_config(rule_code),
    change_type       VARCHAR(30)  NOT NULL,
    proposed_enabled  BOOLEAN,
    proposed_threshold NUMERIC(19, 4),
    maker_user_id     VARCHAR(200) NOT NULL,
    maker_username    VARCHAR(200) NOT NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    maker_note        VARCHAR(500),
    rejection_reason  VARCHAR(500),
    checker_user_id   VARCHAR(200),
    submitted_at      TIMESTAMPTZ  NOT NULL,
    reviewed_at       TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_risk_rule_pending_changes_status
    ON risk_schema.risk_rule_pending_changes (status);

CREATE INDEX IF NOT EXISTS idx_risk_rule_pending_changes_rule_code
    ON risk_schema.risk_rule_pending_changes (rule_code);
