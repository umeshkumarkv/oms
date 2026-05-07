-- order-service V3 migration — SCRUM-41
-- Fix limit_price column precision from NUMERIC(18,4) to NUMERIC(19,4) per CLAUDE.md §13f

ALTER TABLE order_schema.orders
    ALTER COLUMN limit_price TYPE NUMERIC(19,4);
