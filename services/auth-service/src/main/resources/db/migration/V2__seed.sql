-- auth-service seed data
-- Passwords are bcrypt hashed for "Demo@1234"
-- Hash: $2b$10$RZqbdGxbVlkbWNIDIHd9x.GIQB5frOvhzhWHcksVh.MxA/h64f7X6

INSERT INTO auth_schema.users (id, username, password_hash, role, created_at)
VALUES
  (gen_random_uuid(), 'trader.john',   '$2b$10$RZqbdGxbVlkbWNIDIHd9x.GIQB5frOvhzhWHcksVh.MxA/h64f7X6', 'TRADER',     now()),
  (gen_random_uuid(), 'trader.sarah',  '$2b$10$RZqbdGxbVlkbWNIDIHd9x.GIQB5frOvhzhWHcksVh.MxA/h64f7X6', 'TRADER',     now()),
  (gen_random_uuid(), 'ops.manager',   '$2b$10$RZqbdGxbVlkbWNIDIHd9x.GIQB5frOvhzhWHcksVh.MxA/h64f7X6', 'OPERATIONS', now()),
  (gen_random_uuid(), 'admin.user',    '$2b$10$RZqbdGxbVlkbWNIDIHd9x.GIQB5frOvhzhWHcksVh.MxA/h64f7X6', 'ADMIN',      now())
ON CONFLICT (username) DO NOTHING;