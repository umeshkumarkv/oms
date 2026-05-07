-- Add priority column for ordered rule evaluation
-- Lower priority number = evaluated first (fail-fast)
ALTER TABLE risk_schema.risk_rule_config
    ADD COLUMN IF NOT EXISTS priority INTEGER NOT NULL DEFAULT 99;

UPDATE risk_schema.risk_rule_config SET priority = 1 WHERE rule_code = 'SINGLE_ORDER_QTY_LIMIT';
UPDATE risk_schema.risk_rule_config SET priority = 2 WHERE rule_code = 'ORDER_VALUE_LIMIT';
