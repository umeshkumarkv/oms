-- Seed realistic previous_close values so change % arrows are visible in the UI.
-- Values are set slightly different from last_price to simulate intraday movement.
UPDATE market_schema.instruments SET previous_close = 171.50 WHERE symbol = 'AAPL'  AND previous_close IS NULL;
UPDATE market_schema.instruments SET previous_close = 408.00 WHERE symbol = 'MSFT'  AND previous_close IS NULL;
UPDATE market_schema.instruments SET previous_close = 162.25 WHERE symbol = 'GOOGL' AND previous_close IS NULL;
UPDATE market_schema.instruments SET previous_close = 179.00 WHERE symbol = 'TSLA'  AND previous_close IS NULL;
UPDATE market_schema.instruments SET previous_close = 182.30 WHERE symbol = 'AMZN'  AND previous_close IS NULL;
UPDATE market_schema.instruments SET previous_close = 860.00 WHERE symbol = 'NVDA'  AND previous_close IS NULL;
UPDATE market_schema.instruments SET previous_close = 498.00 WHERE symbol = 'META'  AND previous_close IS NULL;
UPDATE market_schema.instruments SET previous_close = 193.50 WHERE symbol = 'JPM'   AND previous_close IS NULL;
UPDATE market_schema.instruments SET previous_close =  37.50 WHERE symbol = 'BAC'   AND previous_close IS NULL;
UPDATE market_schema.instruments SET previous_close =  51.00 WHERE symbol = 'HALT'  AND previous_close IS NULL;
