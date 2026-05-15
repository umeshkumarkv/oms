-- Reset all instrument prices to realistic May 2026 values.
-- Previous simulation had 0.1-0.5% per second which compounded into millions.
-- Fixed volatility to 0.01-0.10% per tick in PriceTickService.

UPDATE market_schema.instruments SET
    last_price = 196.45, bid_price = 196.35, ask_price = 196.55,
    previous_close = 194.82, day_high = 197.10, day_low = 194.30,
    seed_last_price = 196.45, seed_bid_price = 196.35, seed_ask_price = 196.55,
    data_timestamp = now()
WHERE symbol = 'AAPL';

UPDATE market_schema.instruments SET
    last_price = 421.30, bid_price = 421.20, ask_price = 421.40,
    previous_close = 418.75, day_high = 422.80, day_low = 417.90,
    seed_last_price = 421.30, seed_bid_price = 421.20, seed_ask_price = 421.40,
    data_timestamp = now()
WHERE symbol = 'MSFT';

UPDATE market_schema.instruments SET
    last_price = 172.80, bid_price = 172.70, ask_price = 172.90,
    previous_close = 170.55, day_high = 173.45, day_low = 170.20,
    seed_last_price = 172.80, seed_bid_price = 172.70, seed_ask_price = 172.90,
    data_timestamp = now()
WHERE symbol = 'GOOGL';

UPDATE market_schema.instruments SET
    last_price = 248.60, bid_price = 248.45, ask_price = 248.75,
    previous_close = 243.90, day_high = 250.10, day_low = 242.80,
    seed_last_price = 248.60, seed_bid_price = 248.45, seed_ask_price = 248.75,
    data_timestamp = now()
WHERE symbol = 'TSLA';

UPDATE market_schema.instruments SET
    last_price = 203.15, bid_price = 203.05, ask_price = 203.25,
    previous_close = 201.40, day_high = 204.20, day_low = 200.85,
    seed_last_price = 203.15, seed_bid_price = 203.05, seed_ask_price = 203.25,
    data_timestamp = now()
WHERE symbol = 'AMZN';

UPDATE market_schema.instruments SET
    last_price = 956.70, bid_price = 956.20, ask_price = 957.20,
    previous_close = 941.85, day_high = 960.50, day_low = 940.10,
    seed_last_price = 956.70, seed_bid_price = 956.20, seed_ask_price = 957.20,
    data_timestamp = now()
WHERE symbol = 'NVDA';

UPDATE market_schema.instruments SET
    last_price = 578.40, bid_price = 578.20, ask_price = 578.60,
    previous_close = 571.30, day_high = 580.90, day_low = 570.50,
    seed_last_price = 578.40, seed_bid_price = 578.20, seed_ask_price = 578.60,
    data_timestamp = now()
WHERE symbol = 'META';

UPDATE market_schema.instruments SET
    last_price = 218.90, bid_price = 218.80, ask_price = 219.00,
    previous_close = 216.45, day_high = 219.80, day_low = 215.90,
    seed_last_price = 218.90, seed_bid_price = 218.80, seed_ask_price = 219.00,
    data_timestamp = now()
WHERE symbol = 'JPM';

UPDATE market_schema.instruments SET
    last_price = 43.25, bid_price = 43.20, ask_price = 43.30,
    previous_close = 42.80, day_high = 43.55, day_low = 42.60,
    seed_last_price = 43.25, seed_bid_price = 43.20, seed_ask_price = 43.30,
    data_timestamp = now()
WHERE symbol = 'BAC';

UPDATE market_schema.instruments SET
    last_price = 50.00, bid_price = 49.90, ask_price = 50.10,
    previous_close = 51.00, day_high = 50.00, day_low = 50.00,
    seed_last_price = 50.00, seed_bid_price = 49.90, seed_ask_price = 50.10,
    data_timestamp = now()
WHERE symbol = 'HALT';
