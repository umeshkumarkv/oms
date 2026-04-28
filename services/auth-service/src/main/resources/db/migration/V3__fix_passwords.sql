-- Fix: V2__seed.sql contained an incorrect bcrypt hash
-- Regenerate all seed user passwords as Demo@1234 using pgcrypto
-- pgcrypto crypt() with gen_salt('bf', 10) is compatible with Spring BCryptPasswordEncoder

CREATE EXTENSION IF NOT EXISTS pgcrypto;

UPDATE auth_schema.users
SET password_hash = crypt('Demo@1234', gen_salt('bf', 10))
WHERE username IN ('trader.john', 'trader.sarah', 'ops.manager', 'admin.user');
