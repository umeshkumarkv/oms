-- Seed BRD Section 16.3 users — SCRUM-78
-- Passwords are bcrypt hashed for "Demo@1234"
-- Hash: $2a$10$TKh8H1.PfQx37YgCzwiKb.KjNyWgaHb9cbcoQgdIXFlpb7pIgSkb2

INSERT INTO auth_schema.users (id, username, password_hash, role, status, display_name, created_at)
VALUES
  (gen_random_uuid(), 'trader01', '$2a$10$TKh8H1.PfQx37YgCzwiKb.KjNyWgaHb9cbcoQgdIXFlpb7pIgSkb2', 'TRADER', 'ACTIVE', 'Alice Johnson', now()),
  (gen_random_uuid(), 'trader02', '$2a$10$TKh8H1.PfQx37YgCzwiKb.KjNyWgaHb9cbcoQgdIXFlpb7pIgSkb2', 'TRADER', 'ACTIVE', 'Bob Smith',     now()),
  (gen_random_uuid(), 'admin01',  '$2a$10$TKh8H1.PfQx37YgCzwiKb.KjNyWgaHb9cbcoQgdIXFlpb7pIgSkb2', 'ADMIN',  'ACTIVE', 'Carol White',    now()),
  (gen_random_uuid(), 'viewer01', '$2a$10$TKh8H1.PfQx37YgCzwiKb.KjNyWgaHb9cbcoQgdIXFlpb7pIgSkb2', 'VIEWER', 'ACTIVE', 'David Brown',    now())
ON CONFLICT (username) DO NOTHING;
