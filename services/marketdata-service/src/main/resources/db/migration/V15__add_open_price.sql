-- marketdata-service — add open_price column for market movers percent change calculation
-- percentChange = (lastPrice - openPrice) / openPrice * 100

ALTER TABLE market_schema.instruments
    ADD COLUMN IF NOT EXISTS open_price NUMERIC(19, 4);

-- Seed open_price values intentionally offset from lastPrice to produce a spread of
-- positive and negative percent changes across the mocked watchlist.
UPDATE market_schema.instruments SET open_price = 169.80  WHERE symbol = 'AAPL'  AND open_price IS NULL;
UPDATE market_schema.instruments SET open_price = 405.50  WHERE symbol = 'MSFT'  AND open_price IS NULL;
UPDATE market_schema.instruments SET open_price = 160.00  WHERE symbol = 'GOOGL' AND open_price IS NULL;
UPDATE market_schema.instruments SET open_price = 174.20  WHERE symbol = 'TSLA'  AND open_price IS NULL;
UPDATE market_schema.instruments SET open_price = 180.50  WHERE symbol = 'AMZN'  AND open_price IS NULL;
UPDATE market_schema.instruments SET open_price = 855.00  WHERE symbol = 'NVDA'  AND open_price IS NULL;
UPDATE market_schema.instruments SET open_price = 494.00  WHERE symbol = 'META'  AND open_price IS NULL;
UPDATE market_schema.instruments SET open_price = 192.00  WHERE symbol = 'JPM'   AND open_price IS NULL;
UPDATE market_schema.instruments SET open_price =  38.20  WHERE symbol = 'BAC'   AND open_price IS NULL;
UPDATE market_schema.instruments SET open_price =  50.00  WHERE symbol = 'HALT'  AND open_price IS NULL;
