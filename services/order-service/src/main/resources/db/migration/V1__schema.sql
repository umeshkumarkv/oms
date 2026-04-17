-- order-service schema DDL
-- Schema: order_schema

CREATE TABLE IF NOT EXISTS order_schema.orders (
    id               UUID          NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    client_order_id  VARCHAR(100),
    client_id        UUID          NOT NULL,
    symbol           VARCHAR(20)   NOT NULL,
    side             VARCHAR(10)   NOT NULL CHECK (side IN ('BUY', 'SELL')),
    order_type       VARCHAR(10)   NOT NULL CHECK (order_type IN ('MARKET', 'LIMIT')),
    time_in_force    VARCHAR(10)   NOT NULL CHECK (time_in_force IN ('DAY', 'GTC')),
    quantity         INTEGER       NOT NULL CHECK (quantity > 0),
    limit_price      NUMERIC(18,4),
    status           VARCHAR(30)   NOT NULL CHECK (status IN (
                       'NEW', 'VALIDATED', 'RISK_CHECK_PASSED', 'RISK_REJECTED',
                       'ACCEPTED', 'SENT_TO_EXECUTION', 'PARTIALLY_FILLED',
                       'FILLED', 'CANCELLED', 'REJECTED', 'EXPIRED')),
    rejection_reason VARCHAR(500),
    expiry_date      DATE,
    expiry_applied   VARCHAR(10)   CHECK (expiry_applied IN ('DEFAULT', 'TRADER')),
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by       UUID          NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_orders_client_id  ON order_schema.orders (client_id);
CREATE INDEX IF NOT EXISTS idx_orders_status     ON order_schema.orders (status);
CREATE INDEX IF NOT EXISTS idx_orders_symbol     ON order_schema.orders (symbol);
CREATE INDEX IF NOT EXISTS idx_orders_created_at ON order_schema.orders (created_at);