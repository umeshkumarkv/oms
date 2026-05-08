-- SCRUM-75: Add RULE_MAX_ORDER_QTY — absolute ceiling check (50,000 shares)
INSERT INTO risk_schema.risk_rule_config
    (rule_code, rule_name, rule_category, description, enabled, threshold_value, threshold_unit, action, priority, updated_at)
VALUES
    ('RULE_MAX_ORDER_QTY',
     'Max Single Order Quantity',
     'PRE_TRADE',
     'Reject if single order quantity exceeds threshold. Absolute ceiling check.',
     true,
     50000.0000,
     'SHARES',
     'REJECT',
     9,
     NOW())
ON CONFLICT (rule_code) DO NOTHING;
