-- Add updated_at column to auth_schema.users — SCRUM-79
ALTER TABLE auth_schema.users
  ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ;

UPDATE auth_schema.users
  SET updated_at = created_at
  WHERE updated_at IS NULL;
