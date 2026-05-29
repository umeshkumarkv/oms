-- Add admin02 user to support maker-checker two-admin workflow
-- Password: Demo@1234
-- Hash: $2b$10$RZqbdGxbVlkbWNIDIHd9x.GIQB5frOvhzhWHcksVh.MxA/h64f7X6

INSERT INTO auth_schema.users (id, username, password_hash, role, status, display_name, created_at, updated_at)
VALUES (
    gen_random_uuid(),
    'admin02',
    '$2b$10$RZqbdGxbVlkbWNIDIHd9x.GIQB5frOvhzhWHcksVh.MxA/h64f7X6',
    'ADMIN',
    'ACTIVE',
    'Admin User 2',
    now(),
    now()
)
ON CONFLICT (username) DO NOTHING;
