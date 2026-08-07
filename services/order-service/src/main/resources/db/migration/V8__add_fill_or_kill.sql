ALTER TABLE order_schema.orders
    ADD COLUMN IF NOT EXISTS fill_or_kill BOOLEAN NOT NULL DEFAULT false;
