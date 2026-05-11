-- order-service V5: add last_price and order_value to orders table
-- Required for publishing order.execution.requested Kafka event

ALTER TABLE order_schema.orders
    ADD COLUMN IF NOT EXISTS last_price NUMERIC(19, 4),
    ADD COLUMN IF NOT EXISTS order_value NUMERIC(19, 4);
