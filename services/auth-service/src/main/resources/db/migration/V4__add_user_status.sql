-- Add status column to auth_schema.users
-- All existing users default to ACTIVE

ALTER TABLE auth_schema.users
    ADD COLUMN IF NOT EXISTS status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'INACTIVE'));
