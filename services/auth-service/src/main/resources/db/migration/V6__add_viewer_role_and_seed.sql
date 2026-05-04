-- Add VIEWER to role check constraint and insert viewer seed user
-- VIEWER role: read-only access across all services

-- Drop old constraint and recreate with VIEWER included
ALTER TABLE auth_schema.users
    DROP CONSTRAINT IF EXISTS users_role_check;

ALTER TABLE auth_schema.users
    ADD CONSTRAINT users_role_check
        CHECK (role IN ('TRADER', 'OPERATIONS', 'ADMIN', 'VIEWER'));

-- Insert VIEWER seed user
-- Password: Demo@1234 (BCrypt strength 10)
INSERT INTO auth_schema.users (id, username, password_hash, role, status)
VALUES (
    gen_random_uuid(),
    'viewer.user',
    '$2b$10$RZqbdGxbVlkbWNIDIHd9x.GIQB5frOvhzhWHcksVh.MxA/h64f7X6',
    'VIEWER',
    'ACTIVE'
)
ON CONFLICT (username) DO NOTHING;
