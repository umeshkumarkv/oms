-- marketdata-service seed data — 10 instruments

INSERT INTO market_schema.instruments (symbol, bid_price, ask_price, last_price, tradable, trading_halted, min_quantity, max_quantity, data_timestamp)
VALUES
  ('AAPL',  174.95, 175.05, 175.00, true,  false, 1, 10000, now()),
  ('MSFT',  414.90, 415.10, 415.00, true,  false, 1, 10000, now()),
  ('GOOGL', 164.90, 165.10, 165.00, true,  false, 1, 5000,  now()),
  ('TSLA',  174.80, 175.20, 175.00, true,  false, 1, 5000,  now()),
  ('AMZN',  184.90, 185.10, 185.00, true,  false, 1, 5000,  now()),
  ('NVDA',  874.50, 875.50, 875.00, true,  false, 1, 2000,  now()),
  ('META',  509.80, 510.20, 510.00, true,  false, 1, 5000,  now()),
  ('JPM',   194.90, 195.10, 195.00, true,  false, 1, 10000, now()),
  ('BAC',   37.95,  38.05,  38.00,  true,  false, 1, 50000, now()),
  ('HALT',  49.90,  50.10,  50.00,  true,  true,  1, 10000, now())
ON CONFLICT (symbol) DO NOTHING;