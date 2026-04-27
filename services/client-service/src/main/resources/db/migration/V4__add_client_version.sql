-- Add optimistic locking version column to clients table
ALTER TABLE client_schema.clients
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
