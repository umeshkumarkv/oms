-- Add account_type column to clients table
-- Iteration 1: all clients are CASH accounts

ALTER TABLE client_schema.clients
    ADD COLUMN account_type VARCHAR(20) NOT NULL DEFAULT 'CASH'
        CHECK (account_type IN ('CASH', 'MARGIN', 'SHORT_SELLING'));
