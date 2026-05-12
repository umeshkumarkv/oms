-- Add risk.manager user with ADMIN role (can view all orders and configure risk rules)
-- Password: Demo@1234 (BCrypt strength 10)
INSERT INTO auth_schema.users (id, username, password_hash, role, status)
VALUES (
    gen_random_uuid(),
    'risk.manager',
    '$2b$10$RZqbdGxbVlkbWNIDIHd9x.GIQB5frOvhzhWHcksVh.MxA/h64f7X6',
    'ADMIN',
    'ACTIVE'
)
ON CONFLICT (username) DO NOTHING;
