# CLAUDE.md — client-service
# Part of Broker Dealer OMS POC
# Read root CLAUDE.md first. This file adds service-specific detail.

---

## 1. Service Purpose

The client-service owns all client profiles, cash balances, reserved cash, holdings, and daily
trading limits. It serves as the source of truth for client financial state consulted by
order-service during Level 2 validation. It listens to order.state.changed and
execution.completed Kafka events to update reserved cash and holdings. It must never execute
orders, apply risk rules, or directly call order-service.

---

## 2. Port

8083

---

## 3. Database Schema

client_schema

---

## 4. Kafka Topics Produced

None — client-service does not publish to any Kafka topic.

---

## 5. Kafka Topics Consumed

| Topic                 | Consumer Group ID                             | Action on Receive                    |
|-----------------------|-----------------------------------------------|--------------------------------------|
| order.state.changed   | client-service-order.state.changed-group      | Update reserved cash on state changes|
| execution.completed   | client-service-execution.completed-group      | Update holdings and cash balance     |

Idempotent consumers — check current state before applying updates.

---

## 6. Redis Cache Keys Owned

| Key Pattern                    | TTL        | Purpose                              |
|--------------------------------|------------|--------------------------------------|
| client:portfolio:{clientId}    | 30 seconds | Portfolio for Level 2 validation     |
| client:balance:{clientId}      | 60 seconds | Cash balance for fast lookup         |

Cache-aside pattern. Invalidate on execution.completed and order.state.changed events.

---

## 7. REST Endpoints Exposed

See openapi.yaml for full schema.

| Method | Path                            | Auth Required | Role                    | Description              |
|--------|---------------------------------|---------------|-------------------------|--------------------------|
| GET    | /api/v1/clients/{id}            | Yes           | Any                     | Client profile           |
| GET    | /api/v1/clients/{id}/portfolio  | Yes           | Any                     | Portfolio + holdings     |
| PATCH  | /api/v1/clients/{id}/reserved-cash | Yes        | SYSTEM (order-service)  | Reserve/release cash     |

---

## 8. REST Endpoints Called (Downstream Dependencies)

None — client-service has no downstream service dependencies.

---

## 9. Entities Owned

Client:
- id: UUID (PK, auto-generated)
- name: String (non-null, max 200)
- status: ClientStatus enum (ACTIVE / BLOCKED)
- tradingEnabled: Boolean (non-null)
- kycStatus: KycStatus enum (VERIFIED / PENDING / EXPIRED)
- kycExpiryDate: LocalDate (nullable)
- marginCallActive: Boolean (non-null, default false)
- cashBalance: BigDecimal (non-null, precision 18 scale 2)
- reservedCash: BigDecimal (non-null, default 0)
- tradingLimit: BigDecimal (nullable)
- dailyOrderCount: Integer (non-null, default 0)
- dailyOrderValue: BigDecimal (non-null, default 0)
- dailyOrderLimit: BigDecimal (nullable)
- maxOpenOrders: Integer (nullable)
- avgOrderQty: Integer (nullable)

Holding:
- id: UUID (PK, auto-generated)
- clientId: UUID (FK → clients.id, non-null)
- symbol: String (non-null, max 20, unique with clientId)
- quantity: Integer (non-null, >= 0)
- avgPrice: BigDecimal (non-null, precision 18 scale 4)

---

## 10. What This Service Must NEVER Do

- Never access order_schema, market_schema, risk_schema, execution_schema, audit_schema
- Never call order-service, marketdata-service, risk-service, execution-service, or audit-service
- Never execute orders or modify order state
- Never apply risk rules
- Never allow negative cash balance after reservation
- Never allow negative holdings quantity
- Never publish to Kafka

---

## 11. Key Business Rules

- buyingPower = cashBalance - reservedCash
- On BUY order NEW: PATCH /reserved-cash with positive adjustment = order notional value
- On BUY order FILLED: release reserved cash, deduct from cashBalance, add to holdings
- On BUY order REJECTED/EXPIRED/RISK_REJECTED/CANCELLED: release reserved cash
- On SELL order FILLED: deduct from holdings, add to cashBalance
- Daily limits reset at midnight ET — not implemented in POC (Sprint 2)
- Seed data: 5 clients with CLIENT-001 through CLIENT-005 IDs as in V2__seed.sql