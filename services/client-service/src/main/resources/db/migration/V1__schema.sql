-- client-service schema DDL
-- Schema: client_schema

CREATE TABLE IF NOT EXISTS client_schema.clients (
    id                UUID          NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    name              VARCHAR(200)  NOT NULL,
    status            VARCHAR(20)   NOT NULL CHECK (status IN ('ACTIVE', 'BLOCKED')),
    trading_enabled   BOOLEAN       NOT NULL DEFAULT true,
    kyc_status        VARCHAR(20)   NOT NULL CHECK (kyc_status IN ('VERIFIED', 'PENDING', 'EXPIRED')),
    kyc_expiry_date   DATE,
    margin_call_active BOOLEAN      NOT NULL DEFAULT false,
    cash_balance      NUMERIC(18,2) NOT NULL DEFAULT 0,
    reserved_cash     NUMERIC(18,2) NOT NULL DEFAULT 0,
    trading_limit     NUMERIC(18,2),
    daily_order_count INTEGER       NOT NULL DEFAULT 0,
    daily_order_value NUMERIC(18,2) NOT NULL DEFAULT 0,
    daily_order_limit NUMERIC(18,2),
    max_open_orders   INTEGER,
    avg_order_qty     INTEGER
);

CREATE TABLE IF NOT EXISTS client_schema.holdings (
    id        UUID          NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    client_id UUID          NOT NULL REFERENCES client_schema.clients(id),
    symbol    VARCHAR(20)   NOT NULL,
    quantity  INTEGER       NOT NULL CHECK (quantity >= 0),
    avg_price NUMERIC(18,4) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_holdings_client_id ON client_schema.holdings (client_id);
CREATE UNIQUE INDEX IF NOT EXISTS uidx_holdings_client_symbol ON client_schema.holdings (client_id, symbol);