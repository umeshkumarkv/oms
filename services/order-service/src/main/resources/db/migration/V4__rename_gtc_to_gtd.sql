-- Rename GTC (Good Till Cancelled) → GTD (Good Till Date) per BRD Section 14
-- GTC is out of scope; every non-DAY order must carry an explicit gtd_date (expiry_date)

ALTER TABLE order_schema.orders DROP CONSTRAINT IF EXISTS orders_time_in_force_check;

UPDATE order_schema.orders SET time_in_force = 'GTD' WHERE time_in_force = 'GTC';

ALTER TABLE order_schema.orders
    ADD CONSTRAINT orders_time_in_force_gtd_check CHECK (time_in_force IN ('DAY', 'GTD'));
