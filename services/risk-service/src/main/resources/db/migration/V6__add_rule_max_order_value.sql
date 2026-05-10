-- SCRUM-76: Add RULE_MAX_ORDER_VALUE — absolute per-order value ceiling ($500,000 USD)
INSERT INTO risk_schema.risk_rule_config
    (rule_code, rule_name, rule_category, description, enabled, threshold_value, threshold_unit, action, priority, updated_at)
VALUES
    ('RULE_MAX_ORDER_VALUE',
     'Max Single Order Value',
     'PRE_TRADE',
     'Reject if single order notional value (qty x price) exceeds threshold in USD. Absolute ceiling check.',
     true,
     500000.0000,
     'USD',
     'REJECT',
     10,
     NOW())
ON CONFLICT (rule_code) DO NOTHING;
