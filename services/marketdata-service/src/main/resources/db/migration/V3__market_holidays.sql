-- marketdata-service — market holidays table and seed data

CREATE TABLE IF NOT EXISTS market_schema.market_holidays (
    holiday_date  DATE         NOT NULL PRIMARY KEY,
    holiday_name  VARCHAR(100) NOT NULL
);

-- US equity market holidays for 2026
INSERT INTO market_schema.market_holidays (holiday_date, holiday_name)
VALUES
  ('2026-01-01', 'New Year''s Day'),
  ('2026-01-19', 'Martin Luther King Jr. Day'),
  ('2026-02-16', 'Presidents'' Day'),
  ('2026-04-03', 'Good Friday'),
  ('2026-05-25', 'Memorial Day'),
  ('2026-06-19', 'Juneteenth National Independence Day'),
  ('2026-07-03', 'Independence Day (observed)'),
  ('2026-09-07', 'Labor Day'),
  ('2026-11-26', 'Thanksgiving Day'),
  ('2026-12-25', 'Christmas Day')
ON CONFLICT (holiday_date) DO NOTHING;