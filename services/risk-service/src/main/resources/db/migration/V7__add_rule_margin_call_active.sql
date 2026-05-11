-- SCRUM-77: Add RULE_MARGIN_CALL_ACTIVE — reject BUY orders when client has active margin call
INSERT INTO risk_schema.risk_rule_config
    (rule_code, rule_name, rule_category, description, enabled, threshold_value, threshold_unit, action, priority, updated_at)
VALUES
    ('RULE_MARGIN_CALL_ACTIVE',
     'Margin Call Active Check',
     'CLIENT',
     'Reject BUY orders when client has an active margin call. Only SELL orders are permitted during margin call.',
     true,
     NULL,
     NULL,
     'REJECT',
     24,
     NOW())
ON CONFLICT (rule_code) DO NOTHING;
