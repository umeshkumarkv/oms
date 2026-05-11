ALTER TABLE audit_schema.audit_logs
    ADD COLUMN IF NOT EXISTS client_id UUID;

CREATE INDEX IF NOT EXISTS idx_audit_client_id
    ON audit_schema.audit_logs (client_id);
