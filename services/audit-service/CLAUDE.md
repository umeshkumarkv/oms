# CLAUDE.md — audit-service
# Part of Broker Dealer OMS POC
# Read root CLAUDE.md first. This file adds service-specific detail.

---

## 1. Service Purpose

The audit-service maintains an immutable, append-only audit trail of all order state changes and
execution events. It is purely event-driven — it is never called synchronously to write records.
All audit log entries are created by consuming Kafka events from order.state.changed and
execution.completed topics. The audit trail is queryable via REST for OPERATIONS and ADMIN roles.
No audit record may ever be updated or deleted.

---

## 2. Port

8087

---

## 3. Database Schema

audit_schema

---

## 4. Kafka Topics Produced

None — audit-service does not publish to any Kafka topic.

---

## 5. Kafka Topics Consumed

| Topic               | Consumer Group ID                            | Action on Receive                  |
|---------------------|----------------------------------------------|------------------------------------|
| order.state.changed | audit-service-order.state.changed-group      | Create AuditLog for state change   |
| execution.completed | audit-service-execution.completed-group      | Create AuditLog for execution fill |

Idempotent consumers — check if audit record already exists for the event before inserting.

---

## 6. Redis Cache Keys Owned

None — audit-service does not use Redis caching. Audit records are always read from PostgreSQL.

---

## 7. REST Endpoints Exposed

See openapi.yaml for full schema.

| Method | Path                      | Auth Required | Role                 | Description                  |
|--------|---------------------------|---------------|----------------------|------------------------------|
| GET    | /api/v1/audit/orders/{id} | Yes           | OPERATIONS / ADMIN   | Audit trail for one order    |
| GET    | /api/v1/audit/trail       | Yes           | OPERATIONS / ADMIN   | Paginated audit trail search |

---

## 8. REST Endpoints Called (Downstream Dependencies)

None — audit-service has no downstream service dependencies.

---

## 9. Entities Owned

AuditLog:
- id: UUID (PK, auto-generated)
- entityType: String (non-null, max 100, e.g. "ORDER", "EXECUTION")
- entityId: UUID (non-null)
- previousState: String (nullable, max 100)
- newState: String (non-null, max 100)
- actorId: UUID (nullable — null for SYSTEM events)
- actorType: ActorType enum (USER / SYSTEM)
- eventTimestamp: Instant (non-null)
- metadata: String (nullable, JSON, stored as TEXT)

---

## 10. What This Service Must NEVER Do

- Never access order_schema, client_schema, market_schema, risk_schema, execution_schema, auth_schema
- Never call order-service, client-service, marketdata-service, risk-service, execution-service, or auth-service synchronously
- Never update or delete existing AuditLog records — immutable by design
- Never be called synchronously to write records — write is Kafka-only
- Never expose sensitive client financial data in metadata — log IDs and states only

---

## 11. Key Business Rules

- Audit records are append-only — no UPDATE or DELETE operations on audit_logs table
- Every state transition in order-service produces an order.state.changed event → one AuditLog record
- Every execution fill produces an execution.completed event → one AuditLog record
- entityType = "ORDER" for order state change events
- entityType = "EXECUTION" for execution completed events
- actorType = USER when triggered by trader action, SYSTEM when triggered by expiry job or risk engine
- All timestamps stored in UTC as TIMESTAMPTZ
- Metadata field stores any additional context as JSON string (never raw PII)