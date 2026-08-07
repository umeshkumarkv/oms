-- Add fill_or_kill boolean column to orders table
ALTER TABLE order_schema.orders
ADD COLUMN IF NOT EXISTS fill_or_kill boolean NOT NULL DEFAULT false;
