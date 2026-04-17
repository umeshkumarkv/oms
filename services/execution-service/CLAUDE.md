# CLAUDE.md — execution-service
# Part of Broker Dealer OMS POC
# Read root CLAUDE.md first. This file adds service-specific detail.

---

## 1. Service Purpose

The execution-service is the mock fill engine. It receives order execution requests via Kafka,
simulates fills (full or partial), records execution records, and publishes execution.completed
events. All fills are mocked — there is no real exchange connectivity. It must never update
portfolios directly (that is client-service responsibility on receiving execution.completed).
It is the sole owner of execution records and is responsible for order state transitions to
PARTIALLY_FILLED, FILLED, and REJECTED (execution failure).

---

## 2. Port

8086

---

## 3. Database Schema

execution_schema

---

## 4. Kafka Topics Produced

| Topic               | Trigger              | Payload                                                    |
|---------------------|----------------------|------------------------------------------------------------|
| execution.completed | After each fill      | orderId, symbol, side, executedQty, executedPrice, executedAt, fillType (PARTIAL/FULL/REJECTED) |

---

## 5. Kafka Topics Consumed

| Topic                      | Consumer Group ID                                  | Action on Receive         |
|----------------------------|----------------------------------------------------|---------------------------|
| order.execution.requested  | execution-service-order.execution.requested-group  | Simulate fill and publish result |

Idempotent consumers — check if execution already exists for orderId before processing.

---

## 6. Redis Cache Keys Owned

None — execution-service does not use Redis caching.

---

## 7. REST Endpoints Exposed

See openapi.yaml for full schema.

| Method | Path                          | Auth Required | Role                    | Description                  |
|--------|-------------------------------|---------------|-------------------------|------------------------------|
| GET    | /api/v1/executions/{orderId}  | Yes           | TRADER/OPERATIONS/ADMIN | Get executions for an order  |

---

## 8. REST Endpoints Called (Downstream Dependencies)

None — execution-service has no synchronous downstream dependencies.

---

## 9. Entities Owned

Execution:
- id: UUID (PK, auto-generated)
- orderId: UUID (non-null)
- symbol: String (non-null, max 20)
- side: OrderSide enum (BUY / SELL)
- executedQty: Integer (non-null, > 0)
- executedPrice: BigDecimal (non-null, precision 18 scale 4)
- executedAt: Instant (non-null)

---

## 10. What This Service Must NEVER Do

- Never access order_schema, client_schema, market_schema, risk_schema, auth_schema, audit_schema
- Never call order-service, client-service, marketdata-service, risk-service, auth-service, or audit-service
- Never update portfolios or holdings directly
- Never execute orders that have not reached SENT_TO_EXECUTION state
- Never use real exchange prices — mock fills only

---

## 11. Key Business Rules

- Mock fill strategy: simulate immediate full fill at lastPrice from order data (Sprint 1)
- Partial fills may be introduced in Sprint 2
- On fill: persist Execution record, publish execution.completed
- execution.completed consumers: order-service (state transition), client-service (portfolio update), audit-service (log)
- executedPrice must be BigDecimal — never float or double
- One Execution record per fill event (multiple records possible for partial fills of same order)