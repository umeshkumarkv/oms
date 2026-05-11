-- Migrate tick_time from TIMESTAMP (ET local) to TIMESTAMPTZ (UTC)
-- tick_time was previously stored as ET local time without timezone info.
-- This migration converts existing values to UTC using AT TIME ZONE 'America/New_York'.

ALTER TABLE market_schema.ticks
    ALTER COLUMN tick_time TYPE TIMESTAMPTZ
    USING tick_time AT TIME ZONE 'America/New_York';
