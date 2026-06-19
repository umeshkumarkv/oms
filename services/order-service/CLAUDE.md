# CLAUDE.md — order-service
# Part of Broker Dealer OMS POC
# Read root CLAUDE.md first. This file adds service-specific detail.

---

## 1. Service Purpose

The order-service is the core of the OMS. It owns the complete order lifecycle from submission
through final state (FILLED, REJECTED, EXPIRED, RISK_REJECTED, or CANCELLED). It enforces the
two-level validation framework, manages the state machine, orchestrates risk evaluation via Kafka,
routes to execution via Kafka, and maintains the authoritative order record. It calls
client-service and marketdata-service synchronously during Level 2 validation using Resilience4j
circuit breakers.

---

## 2. Port

8082

---

## 3. Database Schema

order_schema

---

## 4. Kafka Topics Produced

| Topic                      | Trigger                                  | Payload              |
|----------------------------|------------------------------------------|----------------------|
| order.state.changed        | Every state transition                   | orderId, newState, previousState, actorId, actorType, timestamp |
| risk.evaluation.requested  | After order reaches VALIDATED state      | orderId, clientId, symbol, side, orderType, quantity, limitPrice |
| order.execution.requested  | After order reaches ACCEPTED state       | orderId, clientId, symbol, side, orderType, quantity, limitPrice |

---

## 5. Kafka Topics Consumed

| Topic                   | Consumer Group ID                              | Action on Receive         |
|-------------------------|------------------------------------------------|---------------------------|
| risk.evaluation.result  | order-service-risk.evaluation.result-group     | Transition to RISK_CHECK_PASSED or RISK_REJECTED |
| execution.completed     | order-service-execution.completed-group        | Transition to PARTIALLY_FILLED, FILLED, or REJECTED |

Idempotent consumers — check order.status before processing. Ignore if already in terminal state.

---

## 6. Redis Cache Keys Owned

| Key Pattern              | TTL     | Purpose                              |
|--------------------------|---------|--------------------------------------|
| order:state:{orderId}    | Session | Current order state for fast lookup  |

---

## 7. REST Endpoints Exposed

See openapi.yaml for full schema.

| Method | Path               | Auth Required | Role              | Description            |
|--------|--------------------|---------------|-------------------|------------------------|
| POST   | /api/v1/orders     | Yes           | TRADER            | Submit new order       |
| GET    | /api/v1/orders     | Yes           | TRADER/OPERATIONS/ADMIN | List orders     |
| GET    | /api/v1/orders/{id}| Yes           | TRADER/OPERATIONS/ADMIN | Get order by ID |

---

## 8. REST Endpoints Called (Downstream Dependencies)

| Service            | Endpoint                               | Purpose                        | Circuit Breaker |
|--------------------|----------------------------------------|--------------------------------|-----------------|
| client-service     | GET /api/v1/clients/{id}/portfolio     | Level 2 validation steps 1-4   | Yes             |
| marketdata-service | GET /api/v1/market/{symbol}            | Level 2 validation steps 5,7-9 | Yes             |
| marketdata-service | GET /api/v1/market/hours               | Level 2 validation step 6      | Yes             |
| client-service     | PATCH /api/v1/clients/{id}/reserved-cash| Reserve cash after NEW         | Yes             |

All synchronous calls use Resilience4j circuit breaker. Failure = 503 ServiceUnavailableException.

---

## 9. Entities Owned

Order:
- id: UUID (PK, auto-generated)
- clientOrderId: String (client-supplied, stored not used internally)
- clientId: UUID (non-null)
- symbol: String (1–6 uppercase letters A–Z)
- side: OrderSide enum (BUY / SELL / SHORT_SELL)
- orderType: OrderType enum (MARKET / LIMIT)
- timeInForce: TimeInForce enum (DAY / GTC)
- quantity: Integer (> 0)
- limitPrice: BigDecimal (required for LIMIT, null for MARKET)
- status: OrderStatus enum (all 11 states)
- rejectionReason: String (nullable)
- expiryDate: LocalDate (set after validation pass)
- expiryApplied: ExpiryApplied enum (DEFAULT / TRADER)
- createdAt: Instant (immutable)
- updatedAt: Instant (updated on every state change)
- createdBy: UUID (userId from JWT)

---

## 10. What This Service Must NEVER Do

- Never call risk-service directly — communicate via Kafka only
- Never modify client_schema, market_schema, risk_schema, execution_schema, audit_schema
- Never skip Level 1 validation before writing anything
- Never skip Level 2 validation before persisting anything
- Never allow backward state transitions
- Never execute a trade without RISK_CHECK_PASSED state
- Never skip holdings check for SELL orders (Level 2 checks #20-21)
- SHORT_SELL orders intentionally bypass holdings checks #20-21 — this is by design (SCRUM-123)
- Never allow LIMIT orders without limitPrice
- Never allow MARKET orders with limitPrice

---

## 11. Key Business Rules

- Level 1 validation: syntactic only, no external calls, fail = 400
- Level 2 validation: 24 checks in exact order from CLAUDE.md, fail = 422
- Nothing persisted until both levels pass
- GTC expiry: use trader-supplied date or default 180 days from today
- After NEW: reserve cash via client-service PATCH /reserved-cash for BUY orders
- Every state transition: persist immediately + publish order.state.changed to Kafka
- Duplicate check window: 30 seconds (same clientId + symbol + side + quantity + orderType)
- Fat finger price deviation: <= 10% from lastPrice
- Fat finger notional: <= 1,000,000
- Fat finger quantity spike: <= 10x avgOrderQty
- Market hours: 09:30-16:00 ET Monday-Friday (America/New_York timezone)
- Expiry job: DAY orders expire at 16:00 ET daily, GTC orders expire at expiryDate 16:00 ET