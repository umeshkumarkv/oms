-- Add default_route column to clients table
ALTER TABLE client_schema.clients
    ADD COLUMN default_route VARCHAR(20) NOT NULL DEFAULT 'NASDAQ';
