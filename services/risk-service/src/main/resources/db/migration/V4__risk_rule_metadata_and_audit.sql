-- V4: Add rule metadata columns to risk_rule_config and create risk_rule_audit table
-- Service: risk-service | SCRUM-47

-- Step 1: Add metadata columns (nullable first to allow UPDATE)
ALTER TABLE risk_schema.risk_rule_config
    ADD COLUMN IF NOT EXISTS rule_name      VARCHAR(200),
    ADD COLUMN IF NOT EXISTS rule_category  VARCHAR(50),
    ADD COLUMN IF NOT EXISTS threshold_unit VARCHAR(50),
    ADD COLUMN IF NOT EXISTS action         VARCHAR(20) DEFAULT 'REJECT';

-- Step 2: Populate values for all 13 rules
UPDATE risk_schema.risk_rule_config SET rule_name = 'Single Order Quantity Limit',   rule_category = 'FINANCIAL_LIMIT', threshold_unit = 'QUANTITY',   action = 'REJECT' WHERE rule_code = 'SINGLE_ORDER_QTY_LIMIT';
UPDATE risk_schema.risk_rule_config SET rule_name = 'Order Value Limit',              rule_category = 'FINANCIAL_LIMIT', threshold_unit = 'USD',        action = 'REJECT' WHERE rule_code = 'ORDER_VALUE_LIMIT';
UPDATE risk_schema.risk_rule_config SET rule_name = 'Price Deviation Check',          rule_category = 'FAT_FINGER',      threshold_unit = 'PERCENT',    action = 'REJECT' WHERE rule_code = 'PRICE_DEVIATION_CHECK';
UPDATE risk_schema.risk_rule_config SET rule_name = 'Notional Value Check',           rule_category = 'FAT_FINGER',      threshold_unit = 'USD',        action = 'REJECT' WHERE rule_code = 'NOTIONAL_VALUE_CHECK';
UPDATE risk_schema.risk_rule_config SET rule_name = 'Order Quantity Spike Check',     rule_category = 'FAT_FINGER',      threshold_unit = 'MULTIPLIER', action = 'REJECT' WHERE rule_code = 'ORDER_QTY_SPIKE_CHECK';
UPDATE risk_schema.risk_rule_config SET rule_name = 'Client Active Check',            rule_category = 'REDUNDANT',       threshold_unit = NULL,         action = 'REJECT' WHERE rule_code = 'CLIENT_ACTIVE_CHECK';
UPDATE risk_schema.risk_rule_config SET rule_name = 'Instrument Tradable Check',      rule_category = 'REDUNDANT',       threshold_unit = NULL,         action = 'REJECT' WHERE rule_code = 'INSTRUMENT_TRADABLE_CHECK';
UPDATE risk_schema.risk_rule_config SET rule_name = 'Market Hours Check',             rule_category = 'REDUNDANT',       threshold_unit = NULL,         action = 'REJECT' WHERE rule_code = 'MARKET_HOURS_CHECK';
UPDATE risk_schema.risk_rule_config SET rule_name = 'Quantity Positive Check',        rule_category = 'REDUNDANT',       threshold_unit = NULL,         action = 'REJECT' WHERE rule_code = 'QUANTITY_POSITIVE_CHECK';
UPDATE risk_schema.risk_rule_config SET rule_name = 'Limit Price Check',              rule_category = 'REDUNDANT',       threshold_unit = NULL,         action = 'REJECT' WHERE rule_code = 'LIMIT_PRICE_CHECK';
UPDATE risk_schema.risk_rule_config SET rule_name = 'Cash Balance Check',             rule_category = 'REDUNDANT',       threshold_unit = NULL,         action = 'REJECT' WHERE rule_code = 'CASH_BALANCE_CHECK';
UPDATE risk_schema.risk_rule_config SET rule_name = 'Holdings Check',                 rule_category = 'REDUNDANT',       threshold_unit = NULL,         action = 'REJECT' WHERE rule_code = 'HOLDINGS_CHECK';
UPDATE risk_schema.risk_rule_config SET rule_name = 'Order Value Spike Check',        rule_category = 'REDUNDANT',       threshold_unit = NULL,         action = 'REJECT' WHERE rule_code = 'ORDER_VALUE_SPIKE_CHECK';

-- Step 3: Enforce NOT NULL on required metadata columns
ALTER TABLE risk_schema.risk_rule_config
    ALTER COLUMN rule_name     SET NOT NULL,
    ALTER COLUMN rule_category SET NOT NULL,
    ALTER COLUMN action        SET NOT NULL;

-- Step 4: Create risk_rule_audit table (append-only — no UPDATE or DELETE)
CREATE TABLE IF NOT EXISTS risk_schema.risk_rule_audit (
    id            UUID          NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    rule_code     VARCHAR(100)  NOT NULL,
    field_changed VARCHAR(100)  NOT NULL,
    old_value     TEXT,
    new_value     TEXT,
    changed_by    VARCHAR(255)  NOT NULL,
    change_reason VARCHAR(1000) NOT NULL,
    changed_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_risk_rule_audit_rule_code  ON risk_schema.risk_rule_audit(rule_code);
CREATE INDEX IF NOT EXISTS idx_risk_rule_audit_changed_at ON risk_schema.risk_rule_audit(changed_at DESC);
