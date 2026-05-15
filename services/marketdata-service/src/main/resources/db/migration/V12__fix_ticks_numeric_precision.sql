-- Increase price/bid/ask precision in ticks table from NUMERIC(12,4) to NUMERIC(19,4)
-- to prevent numeric overflow when the random-walk simulation runs in bypass-market-hours
-- mode for extended periods. V11 already reset instrument prices to realistic values.

ALTER TABLE market_schema.ticks
    ALTER COLUMN price TYPE NUMERIC(19,4),
    ALTER COLUMN bid   TYPE NUMERIC(19,4),
    ALTER COLUMN ask   TYPE NUMERIC(19,4);

-- Clear any overflow ticks so history restarts clean from the V11 price reset
TRUNCATE TABLE market_schema.ticks;
