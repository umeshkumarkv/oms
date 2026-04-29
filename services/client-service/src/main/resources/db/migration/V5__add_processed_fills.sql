-- Idempotency table for execution.completed Kafka consumer
CREATE TABLE IF NOT EXISTS client_schema.processed_fills (
    execution_id UUID        NOT NULL PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
