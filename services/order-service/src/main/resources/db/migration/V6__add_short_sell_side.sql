-- order-service V6: extend side CHECK constraint to include SHORT_SELL
-- The inline CHECK from V1 has a pg-generated name; drop it dynamically and re-add with SHORT_SELL.

DO $$
DECLARE
    v_constraint text;
BEGIN
    SELECT conname INTO v_constraint
    FROM pg_constraint
    WHERE conrelid = 'order_schema.orders'::regclass
      AND contype = 'c'
      AND pg_get_constraintdef(oid) LIKE '%side%';

    IF v_constraint IS NOT NULL THEN
        EXECUTE 'ALTER TABLE order_schema.orders DROP CONSTRAINT ' || quote_ident(v_constraint);
    END IF;
END $$;

ALTER TABLE order_schema.orders
    ADD CONSTRAINT orders_side_check CHECK (side IN ('BUY', 'SELL', 'SHORT_SELL'));
