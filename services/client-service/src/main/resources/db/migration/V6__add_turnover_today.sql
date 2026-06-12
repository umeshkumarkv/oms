ALTER TABLE client_schema.clients
    ADD COLUMN turnover_today NUMERIC(18,2) NOT NULL DEFAULT 0;
