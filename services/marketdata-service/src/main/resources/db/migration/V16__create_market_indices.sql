-- marketdata-service — create market_indices table and seed 3 mocked index rows
-- percentChange = (currentValue - openValue) / openValue * 100 — computed on read, never stored

CREATE TABLE market_schema.market_indices (
    id            UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    name          VARCHAR(100)   NOT NULL UNIQUE,
    current_value NUMERIC(19, 4) NOT NULL,
    open_value    NUMERIC(19, 4) NOT NULL,
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_market_indices_name ON market_schema.market_indices (name);

-- Seed 3 mocked index rows with realistic June 2026 values
-- S&P 500  pct: (5321.84 - 5287.50) / 5287.50 * 100 = +0.65%
-- Nasdaq   pct: (16892.30 - 16745.80) / 16745.80 * 100 = +0.87%
-- Dow Jones pct: (41234.56 - 40987.20) / 40987.20 * 100 = +0.60%
INSERT INTO market_schema.market_indices (name, current_value, open_value) VALUES
    ('S&P 500',    5321.8400, 5287.5000),
    ('Nasdaq',    16892.3000, 16745.8000),
    ('Dow Jones', 41234.5600, 40987.2000);
