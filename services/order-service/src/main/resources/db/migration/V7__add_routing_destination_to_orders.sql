ALTER TABLE order_schema.orders
    ADD COLUMN IF NOT EXISTS routing_destination VARCHAR(50);
