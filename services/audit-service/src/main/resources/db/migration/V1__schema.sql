-- audit-service schema DDL
-- Schema: audit_schema

CREATE TABLE IF NOT EXISTS audit_schema.audit_logs (
    id              UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    entity_type     VARCHAR(100) NOT NULL,
    entity_id       UUID         NOT NULL,
    previous_state  VARCHAR(100),
    new_state       VARCHAR(100) NOT NULL,
    actor_id        UUID,
    actor_type      VARCHAR(20)  NOT NULL CHECK (actor_type IN ('USER', 'SYSTEM')),
    event_timestamp TIMESTAMPTZ  NOT NULL DEFAULT now(),
    metadata        TEXT
);

CREATE INDEX IF NOT EXISTS idx_audit_entity_id       ON audit_schema.audit_logs (entity_id);
CREATE INDEX IF NOT EXISTS idx_audit_entity_type     ON audit_schema.audit_logs (entity_type);
CREATE INDEX IF NOT EXISTS idx_audit_event_timestamp ON audit_schema.audit_logs (event_timestamp);