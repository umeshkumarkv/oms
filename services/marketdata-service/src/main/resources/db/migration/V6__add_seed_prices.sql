-- Add seed price columns to instruments so admin reset can restore original prices
ALTER TABLE market_schema.instruments
    ADD COLUMN IF NOT EXISTS seed_last_price NUMERIC(19, 4),
    ADD COLUMN IF NOT EXISTS seed_bid_price  NUMERIC(19, 4),
    ADD COLUMN IF NOT EXISTS seed_ask_price  NUMERIC(19, 4);

-- Populate seed prices from current (initial) prices — only where not yet set
UPDATE market_schema.instruments
SET seed_last_price = last_price,
    seed_bid_price  = bid_price,
    seed_ask_price  = ask_price
WHERE seed_last_price IS NULL;
