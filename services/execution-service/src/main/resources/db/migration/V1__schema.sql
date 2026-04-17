-- execution-service schema DDL
-- Schema: execution_schema

CREATE TABLE IF NOT EXISTS execution_schema.executions (
    id             UUID          NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    order_id       UUID          NOT NULL,
    symbol         VARCHAR(20)   NOT NULL,
    side           VARCHAR(10)   NOT NULL CHECK (side IN ('BUY', 'SELL')),
    executed_qty   INTEGER       NOT NULL CHECK (executed_qty > 0),
    executed_price NUMERIC(18,4) NOT NULL,
    executed_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_executions_order_id    ON execution_schema.executions (order_id);
CREATE INDEX IF NOT EXISTS idx_executions_executed_at ON execution_schema.executions (executed_at);