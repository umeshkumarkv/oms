-- Add display_name and last_login_at columns to support GET /api/v1/users — SCRUM-78

ALTER TABLE auth_schema.users ADD COLUMN IF NOT EXISTS display_name VARCHAR(200) NULL;
ALTER TABLE auth_schema.users ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMPTZ NULL;

-- Backfill display names for existing seed users
UPDATE auth_schema.users SET display_name = 'John Trader'        WHERE username = 'trader.john';
UPDATE auth_schema.users SET display_name = 'Sarah Trader'       WHERE username = 'trader.sarah';
UPDATE auth_schema.users SET display_name = 'Operations Manager' WHERE username = 'ops.manager';
UPDATE auth_schema.users SET display_name = 'Admin User'         WHERE username = 'admin.user';
UPDATE auth_schema.users SET display_name = 'Viewer User'        WHERE username = 'viewer.user';
UPDATE auth_schema.users SET display_name = 'Risk Manager'       WHERE username = 'risk.manager';
