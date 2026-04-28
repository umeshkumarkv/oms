-- marketdata-service — fix precision on price simulation columns to NUMERIC(19,4) per CLAUDE.md Section 13f

ALTER TABLE market_schema.instruments
    ALTER COLUMN day_high       TYPE NUMERIC(19, 4),
    ALTER COLUMN day_low        TYPE NUMERIC(19, 4),
    ALTER COLUMN previous_close TYPE NUMERIC(19, 4);
