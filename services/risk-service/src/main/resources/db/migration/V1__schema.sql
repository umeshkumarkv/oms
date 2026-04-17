-- risk-service schema DDL
-- Schema: risk_schema

CREATE TABLE IF NOT EXISTS risk_schema.risk_rule_config (
    rule_code       VARCHAR(100)  NOT NULL PRIMARY KEY,
    enabled         BOOLEAN       NOT NULL DEFAULT false,
    threshold_value NUMERIC(18,4),
    description     VARCHAR(500),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);