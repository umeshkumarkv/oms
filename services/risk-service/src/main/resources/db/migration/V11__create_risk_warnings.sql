CREATE TABLE IF NOT EXISTS risk_schema.risk_warnings (
    id          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id    UUID            NOT NULL,
    rule_code   VARCHAR(100)    NOT NULL,
    reason      TEXT            NOT NULL,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_risk_warnings_order_id ON risk_schema.risk_warnings(order_id);
