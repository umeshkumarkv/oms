-- execution-service V4 migration — SCRUM-99
-- Relax executed_qty > 0 constraint to allow 0 for REJECTED fills

ALTER TABLE execution_schema.executions
    DROP CONSTRAINT executions_executed_qty_check;

ALTER TABLE execution_schema.executions
    ADD CONSTRAINT executions_executed_qty_non_negative CHECK (executed_qty >= 0);
