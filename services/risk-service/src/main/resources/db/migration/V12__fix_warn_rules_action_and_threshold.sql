-- V12: Correct WARN rule metadata and enforce CLAUDE.md §9 default OFF
-- Context: V2 seeded fat-finger rules with action=REJECT (before SCRUM-122).
-- V4 set action=REJECT and category for these rules.
-- V10 ON CONFLICT DO NOTHING left existing rows unchanged for all except RULE_DAILY_ORDER_COUNT.
-- This migration corrects action, category, threshold and priority for all 5 WARN rules,
-- and sets enabled=false per CLAUDE.md §9 (all rules default OFF; ADMIN must enable explicitly).

UPDATE risk_schema.risk_rule_config
SET rule_name      = 'Price Deviation Check',
    rule_category  = 'FAT_FINGER',
    description    = 'WARN when limitPrice deviates more than threshold % from lastPrice; skipped for MARKET orders',
    action         = 'WARN',
    priority       = 30,
    enabled        = false,
    updated_at     = NOW()
WHERE rule_code = 'PRICE_DEVIATION_CHECK';

UPDATE risk_schema.risk_rule_config
SET rule_name      = 'Notional Value Check',
    rule_category  = 'FAT_FINGER',
    description    = 'WARN when orderValue exceeds notional threshold (default $1,000,000)',
    action         = 'WARN',
    priority       = 31,
    enabled        = false,
    updated_at     = NOW()
WHERE rule_code = 'NOTIONAL_VALUE_CHECK';

UPDATE risk_schema.risk_rule_config
SET rule_name      = 'Order Quantity Spike Check',
    rule_category  = 'FAT_FINGER',
    description    = 'WARN when quantity exceeds multiplier x avgOrderQty; skipped when avgOrderQty is 0 or null',
    action         = 'WARN',
    priority       = 32,
    enabled        = false,
    updated_at     = NOW()
WHERE rule_code = 'ORDER_QTY_SPIKE_CHECK';

-- ORDER_VALUE_SPIKE_CHECK: was seeded with threshold=null (V2) — fix threshold so rule is functional
UPDATE risk_schema.risk_rule_config
SET rule_name       = 'Order Value Spike Check',
    rule_category   = 'FAT_FINGER',
    description     = 'WARN when orderValue exceeds multiplier x avgOrderValue',
    action          = 'WARN',
    priority        = 33,
    threshold_value = 10.00,
    threshold_unit  = 'MULTIPLIER',
    enabled         = false,
    updated_at      = NOW()
WHERE rule_code = 'ORDER_VALUE_SPIKE_CHECK';

UPDATE risk_schema.risk_rule_config
SET enabled    = false,
    updated_at = NOW()
WHERE rule_code = 'RULE_DAILY_ORDER_COUNT';
