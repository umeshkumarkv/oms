-- auth-service seed data
-- Passwords are bcrypt hashed for "Demo@1234"
-- Hash: $2a$10$TKh8H1.PfQx37YgCzwiKb.KjNyWgaHb9cbcoQgdIXFlpb7pIgSkb2

INSERT INTO auth_schema.users (id, username, password_hash, role, created_at)
VALUES
  (gen_random_uuid(), 'trader.john',   '$2a$10$TKh8H1.PfQx37YgCzwiKb.KjNyWgaHb9cbcoQgdIXFlpb7pIgSkb2', 'TRADER',     now()),
  (gen_random_uuid(), 'trader.sarah',  '$2a$10$TKh8H1.PfQx37YgCzwiKb.KjNyWgaHb9cbcoQgdIXFlpb7pIgSkb2', 'TRADER',     now()),
  (gen_random_uuid(), 'ops.manager',   '$2a$10$TKh8H1.PfQx37YgCzwiKb.KjNyWgaHb9cbcoQgdIXFlpb7pIgSkb2', 'OPERATIONS', now()),
  (gen_random_uuid(), 'admin.user',    '$2a$10$TKh8H1.PfQx37YgCzwiKb.KjNyWgaHb9cbcoQgdIXFlpb7pIgSkb2', 'ADMIN',      now())
ON CONFLICT (username) DO NOTHING;