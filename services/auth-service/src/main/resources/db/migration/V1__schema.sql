-- auth-service schema DDL
-- Schema: auth_schema

CREATE TABLE IF NOT EXISTS auth_schema.users (
    id            UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    username      VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('TRADER', 'OPERATIONS', 'ADMIN')),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_users_username ON auth_schema.users (username);