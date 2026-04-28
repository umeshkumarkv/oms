-- marketdata-service — add price simulation fields to instruments table

ALTER TABLE market_schema.instruments
    ADD COLUMN IF NOT EXISTS day_high        NUMERIC(18, 4),
    ADD COLUMN IF NOT EXISTS day_low         NUMERIC(18, 4),
    ADD COLUMN IF NOT EXISTS previous_close  NUMERIC(18, 4),
    ADD COLUMN IF NOT EXISTS volume          BIGINT DEFAULT 0;
