-- marketdata-service schema DDL
-- Schema: market_schema

CREATE TABLE IF NOT EXISTS market_schema.instruments (
    symbol          VARCHAR(20)   NOT NULL PRIMARY KEY,
    bid_price       NUMERIC(18,4),
    ask_price       NUMERIC(18,4),
    last_price      NUMERIC(18,4),
    tradable        BOOLEAN       NOT NULL DEFAULT true,
    trading_halted  BOOLEAN       NOT NULL DEFAULT false,
    min_quantity    INTEGER,
    max_quantity    INTEGER,
    data_timestamp  TIMESTAMPTZ
);