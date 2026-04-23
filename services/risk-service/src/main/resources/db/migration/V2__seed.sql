-- risk-service seed data — 13 risk rules

INSERT INTO risk_schema.risk_rule_config (rule_code, enabled, threshold_value, description, updated_at)
VALUES
  ('SINGLE_ORDER_QTY_LIMIT',     true,  10000.00,    'Maximum quantity allowed in a single order',        now()),
  ('ORDER_VALUE_LIMIT',          true,  500000.00,   'Maximum notional value allowed in a single order',  now()),
  ('PRICE_DEVIATION_CHECK',      false, 10.00,       'Maximum % price deviation from last price',         now()),
  ('NOTIONAL_VALUE_CHECK',       false, 1000000.00,  'Maximum notional value fat finger check',           now()),
  ('ORDER_QTY_SPIKE_CHECK',      false, 10.00,       'Maximum multiplier vs avg order qty',               now()),
  ('CLIENT_ACTIVE_CHECK',        false, null,        'Redundant — handled by Level 2 validation',         now()),
  ('INSTRUMENT_TRADABLE_CHECK',  false, null,        'Redundant — handled by Level 2 validation',         now()),
  ('MARKET_HOURS_CHECK',         false, null,        'Redundant — handled by Level 2 validation',         now()),
  ('QUANTITY_POSITIVE_CHECK',    false, null,        'Redundant — handled by Level 1 validation',         now()),
  ('LIMIT_PRICE_CHECK',          false, null,        'Redundant — handled by Level 1 validation',         now()),
  ('CASH_BALANCE_CHECK',         false, null,        'Redundant — handled by Level 2 validation',         now()),
  ('HOLDINGS_CHECK',             false, null,        'Redundant — handled by Level 2 validation',         now()),
  ('ORDER_VALUE_SPIKE_CHECK',    false, null,        'Redundant — handled by Level 2 validation',         now())
ON CONFLICT (rule_code) DO NOTHING;