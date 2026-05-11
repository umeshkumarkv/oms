-- Tick storage for intraday price history
-- Schema: market_schema
-- All tick_time values are stored in America/New_York (ET) local time

CREATE TABLE IF NOT EXISTS market_schema.ticks (
    id          BIGSERIAL     PRIMARY KEY,
    symbol      VARCHAR(10)   NOT NULL,
    price       NUMERIC(12,4) NOT NULL,
    bid         NUMERIC(12,4),
    ask         NUMERIC(12,4),
    volume      BIGINT,
    tick_time   TIMESTAMP     NOT NULL,
    CONSTRAINT uq_ticks_symbol_time UNIQUE (symbol, tick_time)
);

CREATE INDEX IF NOT EXISTS idx_ticks_symbol_time ON market_schema.ticks (symbol, tick_time);
