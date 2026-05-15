INSERT INTO risk_schema.risk_rule_config
    (rule_code, rule_name, rule_category, description, enabled, threshold_value, threshold_unit, action, priority, updated_at)
VALUES
    ('PRICE_DEVIATION_CHECK',
     'Price Deviation Check',
     'FAT_FINGER',
     'WARN when limitPrice deviates more than 10% from lastPrice; skipped for MARKET orders',
     false, 0.10, 'PERCENT', 'WARN', 30, NOW()),

    ('NOTIONAL_VALUE_CHECK',
     'Notional Value Check',
     'FAT_FINGER',
     'WARN when orderValue exceeds $1,000,000',
     false, 1000000.00, 'USD', 'WARN', 31, NOW()),

    ('ORDER_QTY_SPIKE_CHECK',
     'Order Quantity Spike Check',
     'FAT_FINGER',
     'WARN when quantity exceeds 10x avgOrderQty; skipped when avgOrderQty is 0 or null',
     false, 10.00, 'MULTIPLIER', 'WARN', 32, NOW()),

    ('ORDER_VALUE_SPIKE_CHECK',
     'Order Value Spike Check',
     'FAT_FINGER',
     'WARN when orderValue exceeds 10x avgOrderValue',
     true, 10.00, 'MULTIPLIER', 'WARN', 33, NOW()),

    ('RULE_DAILY_ORDER_COUNT',
     'Daily Order Count Warning',
     'FAT_FINGER',
     'WARN when dailyOrderCount reaches maxDailyOrders limit',
     true, null, null, 'WARN', 34, NOW())

ON CONFLICT (rule_code) DO NOTHING;
