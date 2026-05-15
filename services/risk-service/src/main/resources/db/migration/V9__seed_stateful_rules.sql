-- SCRUM-121: Add stateful risk rules — RULE_DUPLICATE_ORDER, RULE_SELF_TRADE
INSERT INTO risk_schema.risk_rule_config
    (rule_code, rule_name, rule_category, description, enabled, threshold_value, threshold_unit, action, priority, updated_at)
VALUES
    ('RULE_DUPLICATE_ORDER',
     'Duplicate Order Check',
     'STATEFUL',
     'Reject orders identical to one submitted by the same client within the last 30 seconds. Uses Redis SET NX with 30s TTL. Key: dup:{clientId}:{symbol}:{side}:{quantity}:{limitPrice|MARKET}.',
     true,
     NULL,
     NULL,
     'REJECT',
     20,
     NOW()),
    ('RULE_SELF_TRADE',
     'Self-Trade Prevention',
     'STATEFUL',
     'Reject orders that would cross with an existing open order on the opposite side for the same client and symbol.',
     true,
     NULL,
     NULL,
     'REJECT',
     21,
     NOW())
ON CONFLICT (rule_code) DO NOTHING;
