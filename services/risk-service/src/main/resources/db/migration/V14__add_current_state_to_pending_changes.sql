ALTER TABLE risk_schema.risk_rule_pending_changes
    ADD COLUMN IF NOT EXISTS current_enabled   BOOLEAN      NULL,
    ADD COLUMN IF NOT EXISTS current_threshold VARCHAR(255) NULL;
