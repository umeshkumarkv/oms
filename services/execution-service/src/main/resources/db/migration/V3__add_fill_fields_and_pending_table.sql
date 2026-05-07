-- execution-service V3 migration — SCRUM-48
-- Adds fill detail columns to executions and creates pending_order_executions table

ALTER TABLE execution_schema.executions
    ADD COLUMN fill_type            VARCHAR(10)   NOT NULL DEFAULT 'FULL',
    ADD COLUMN fill_value           NUMERIC(19,4) NOT NULL DEFAULT 0,
    ADD COLUMN remaining_quantity   INTEGER       NOT NULL DEFAULT 0,
    ADD COLUMN market_price_at_fill NUMERIC(19,4) NOT NULL DEFAULT 0,
    ADD COLUMN execution_venue      VARCHAR(50)   NOT NULL DEFAULT 'MOCK_ECN',
    ADD COLUMN client_id            UUID          NOT NULL DEFAULT gen_random_uuid();

CREATE TABLE IF NOT EXISTS execution_schema.pending_order_executions (
    id          UUID          NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    order_id    UUID          NOT NULL,
    client_id   UUID          NOT NULL,
    symbol      VARCHAR(20)   NOT NULL,
    side        VARCHAR(10)   NOT NULL,
    order_type  VARCHAR(10)   NOT NULL,
    quantity    INTEGER       NOT NULL CHECK (quantity > 0),
    limit_price NUMERIC(19,4) NOT NULL,
    last_price  NUMERIC(19,4) NOT NULL,
    order_value NUMERIC(19,4) NOT NULL,
    status      VARCHAR(10)   NOT NULL DEFAULT 'PENDING',
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_pending_order_id UNIQUE (order_id)
);

CREATE INDEX IF NOT EXISTS idx_pending_executions_status   ON execution_schema.pending_order_executions (status);
CREATE INDEX IF NOT EXISTS idx_pending_executions_order_id ON execution_schema.pending_order_executions (order_id);
