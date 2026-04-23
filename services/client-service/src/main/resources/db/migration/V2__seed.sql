-- client-service seed data

-- CLIENT-001: John Smith — active trader with holdings
INSERT INTO client_schema.clients (id, name, status, trading_enabled, kyc_status, kyc_expiry_date,
    margin_call_active, cash_balance, reserved_cash, trading_limit, daily_order_count,
    daily_order_value, daily_order_limit, max_open_orders, avg_order_qty)
VALUES ('a1000000-0000-0000-0000-000000000001', 'John Smith', 'ACTIVE', true, 'VERIFIED',
    '2027-12-31', false, 500000.00, 0.00, 500000.00, 0, 0.00, 1000000.00, 10, 100);

INSERT INTO client_schema.holdings (id, client_id, symbol, quantity, avg_price)
VALUES
  (gen_random_uuid(), 'a1000000-0000-0000-0000-000000000001', 'AAPL', 100, 150.00),
  (gen_random_uuid(), 'a1000000-0000-0000-0000-000000000001', 'MSFT', 50, 400.00);

-- CLIENT-002: Sarah Johnson — active trader with TSLA holding
INSERT INTO client_schema.clients (id, name, status, trading_enabled, kyc_status, kyc_expiry_date,
    margin_call_active, cash_balance, reserved_cash, trading_limit, daily_order_count,
    daily_order_value, daily_order_limit, max_open_orders, avg_order_qty)
VALUES ('a2000000-0000-0000-0000-000000000002', 'Sarah Johnson', 'ACTIVE', true, 'VERIFIED',
    '2027-12-31', false, 50000.00, 0.00, 50000.00, 0, 0.00, 100000.00, 5, 20);

INSERT INTO client_schema.holdings (id, client_id, symbol, quantity, avg_price)
VALUES
  (gen_random_uuid(), 'a2000000-0000-0000-0000-000000000002', 'TSLA', 20, 170.00);

-- CLIENT-003: Mike Davis — active but trading disabled
INSERT INTO client_schema.clients (id, name, status, trading_enabled, kyc_status, kyc_expiry_date,
    margin_call_active, cash_balance, reserved_cash, trading_limit, daily_order_count,
    daily_order_value, daily_order_limit, max_open_orders, avg_order_qty)
VALUES ('a3000000-0000-0000-0000-000000000003', 'Mike Davis', 'ACTIVE', false, 'VERIFIED',
    '2027-12-31', false, 200000.00, 0.00, 200000.00, 0, 0.00, 500000.00, 10, 50);

-- CLIENT-004: Emily Chen — blocked with GOOGL holding
INSERT INTO client_schema.clients (id, name, status, trading_enabled, kyc_status, kyc_expiry_date,
    margin_call_active, cash_balance, reserved_cash, trading_limit, daily_order_count,
    daily_order_value, daily_order_limit, max_open_orders, avg_order_qty)
VALUES ('a4000000-0000-0000-0000-000000000004', 'Emily Chen', 'BLOCKED', true, 'VERIFIED',
    '2027-12-31', false, 100000.00, 0.00, 100000.00, 0, 0.00, 250000.00, 10, 30);

INSERT INTO client_schema.holdings (id, client_id, symbol, quantity, avg_price)
VALUES
  (gen_random_uuid(), 'a4000000-0000-0000-0000-000000000004', 'GOOGL', 10, 160.00);

-- CLIENT-005: Robert Wilson — active but expired KYC
INSERT INTO client_schema.clients (id, name, status, trading_enabled, kyc_status, kyc_expiry_date,
    margin_call_active, cash_balance, reserved_cash, trading_limit, daily_order_count,
    daily_order_value, daily_order_limit, max_open_orders, avg_order_qty)
VALUES ('a5000000-0000-0000-0000-000000000005', 'Robert Wilson', 'ACTIVE', true, 'EXPIRED',
    '2025-01-01', false, 75000.00, 0.00, 75000.00, 0, 0.00, 150000.00, 5, 25);