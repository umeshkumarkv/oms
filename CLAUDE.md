# CLAUDE.md — Broker Dealer OMS POC
# Principal Architect: Umesh Kumar KV
# Version: 1.0 | April 2026
# Status: Active — Sprint 1

---

## CRITICAL — READ THIS FIRST

This file is read by Claude Code at the start of every session.
It is the single source of truth for architecture, standards, and
domain rules. Never deviate from these rules without updating this
file first and getting Principal Architect approval.

---

## 1. Project Overview

System: Sell-Side Broker Order Management System (OMS)
Domain: US Equities only — Stocks
Scope: Fully mocked simulation — no real exchange connectivity
Goal: End-to-end order flow — trader submits equity order →
      passes compliance → executes → appears in audit trail
Methodology: Agentic AI SDLC — Claude Code executes JIRA stories,
             humans review and govern

---

## 2. Tech Stack — Locked. Do Not Change.

| Layer          | Technology                    | Version      |
|----------------|-------------------------------|--------------|
| Language       | Java                          | 21 LTS       |
| Framework      | Spring Boot Web MVC           | 3.2.x        |
| Build          | Maven multi-module            | 3.9+         |
| Frontend       | React + TypeScript + Vite     | 18.x         |
| Database       | PostgreSQL                    | 15           |
| ORM            | Spring Data JPA + Hibernate   | Via Boot     |
| Migrations     | Flyway                        | Via Boot     |
| Async          | Apache Kafka + Zookeeper      | Latest       |
| Cache          | Redis via Spring Data Redis   | Latest       |
| Gateway        | Spring Cloud Gateway          | Via Boot     |
| Auth           | JWT via jjwt                  | 0.12.x       |
| Resilience     | Resilience4j                  | Via Boot     |
| API Docs       | SpringDoc OpenAPI             | 2.x          |
| Testing        | JUnit 5 + Mockito + MockMvc   | Via Boot     |
| Coverage       | JaCoCo                        | Via Maven    |
| Containers     | Docker + Docker Compose       | Latest       |
| Orchestration  | Minikube                      | Latest       |
| CI/CD          | GitHub Actions + Docker Hub   | Latest       |

---

## 3. Microservices — Boundaries Are Absolute

| Service            | Port  | Owns                                      | Must Never                              |
|--------------------|-------|-------------------------------------------|-----------------------------------------|
| auth-service       | 8081  | JWT, login, roles                         | Access trading data                     |
| order-service      | 8082  | Order entry, state machine, lifecycle     | Call risk rules directly                |
| client-service     | 8083  | Client profiles, cash, holdings           | Execute orders or apply risk rules      |
| marketdata-service | 8084  | Mock prices, market hours                 | Store orders or client data             |
| risk-service       | 8085  | Configurable rule engine                  | Modify orders or portfolios             |
| execution-service  | 8086  | Mock fill engine, execution records       | Update portfolio directly               |
| audit-service      | 8087  | Immutable audit log                       | Be called synchronously                 |
| api-gateway        | 8080  | JWT validation, routing                   | Contain business logic                  |

One service per domain boundary.
One PostgreSQL schema per service.
No service reads or writes another service's schema directly.
Cross-service data access via REST or Kafka only.

---

## 4. Package Structure — Mandatory for All Services

com.brokerdealer.{servicename}
  .controller      — REST endpoints only. No business logic.
  .service         — All business logic and orchestration.
  .repository      — Spring Data JPA repositories only.
  .domain          — JPA entities and enums.
  .dto             — Request and Response DTOs. Never expose entities.
    .request       — Inbound DTOs
    .response      — Outbound DTOs
  .config          — Spring configuration classes.
  .kafka
    .producer      — Kafka producers.
    .consumer      — Kafka consumers.
  .exception       — Custom exceptions and global handler.
  .mapper          — MapStruct mappers between entity and DTO.

---

## 5. Order Lifecycle — State Machine

States and valid transitions are fixed. Never add, remove, or
rename states without updating this file and getting approval.

| State                | Owned By          | Terminal | Triggered By                        |
|----------------------|-------------------|----------|-------------------------------------|
| NEW                  | order-service     | No       | Level 1 + Level 2 validation pass   |
| VALIDATED            | order-service     | No       | Both validation levels pass         |
| RISK_CHECK_PASSED    | order-service     | No       | risk-service returns PASSED         |
| RISK_REJECTED        | order-service     | YES      | risk-service returns REJECTED       |
| ACCEPTED             | order-service     | No       | OMS accepts after risk pass         |
| SENT_TO_EXECUTION    | order-service     | No       | order.execution.requested published |
| PARTIALLY_FILLED     | execution-service | No       | Partial fill received               |
| FILLED               | execution-service | YES      | Full fill received                  |
| CANCELLED            | order-service     | YES      | System expiry only — not user       |
| REJECTED             | execution-service | YES      | Execution engine rejects            |
| EXPIRED              | order-service     | YES      | DAY or GTC expiry job at 16:00 ET   |

Valid transitions only:
NEW → VALIDATED → RISK_CHECK_PASSED → ACCEPTED → SENT_TO_EXECUTION
SENT_TO_EXECUTION → PARTIALLY_FILLED → FILLED
SENT_TO_EXECUTION → FILLED
SENT_TO_EXECUTION → REJECTED
PARTIALLY_FILLED → EXPIRED
SENT_TO_EXECUTION → EXPIRED
NEW → REJECTED (validation failure)
VALIDATED → RISK_REJECTED

Every state transition must:
- Be persisted to PostgreSQL immediately
- Publish event to order.state.changed Kafka topic
- Be timestamped in UTC
- Record actor ID and actor type (USER or SYSTEM)

---

## 6. Two-Level Validation — Before Anything Is Written or Published

Nothing is persisted. Nothing is published to Kafka until both
levels pass. This is non-negotiable.

### Level 1 — Syntactic (order-service internal — no external calls)
- All required fields present and non-null
- side — must be BUY or SELL
- orderType — must be MARKET or LIMIT
- timeInForce — must be DAY or GTC
- quantity — must be Integer greater than 0
- limitPrice — required and greater than 0 if orderType is LIMIT
- limitPrice — must be null if orderType is MARKET
- symbol — non-empty, max 20 chars, alphanumeric only
Fail → 400 Bad Request

### Level 2 — Semantic (synchronous REST calls — use Resilience4j circuit breaker)
In this exact order:
1.  Client exists — client-service GET /api/v1/clients/{id}/portfolio
2.  Client status is ACTIVE
3.  trading_enabled is true
4.  kyc_status is VERIFIED and kyc_expiry_date is future
5.  Symbol exists and tradable — marketdata-service GET /api/v1/market/{symbol}
6.  Market is open — marketdata-service GET /api/v1/market/hours
7.  Symbol not halted
8.  quantity >= minQuantity
9.  quantity <= maxQuantity
10. Duplicate check — same clientId + symbol + side + quantity +
    orderType within last 30 seconds
11. Open orders count < maxOpenOrders
12. Self trade check — no open opposite order at crossing price
13. BUY — buyingPower >= orderValue
14. BUY — marginCallActive is false
15. BUY — dailyOrderCount < maxDailyOrderCount
16. BUY — dailyOrderValue + orderValue <= dailyOrderLimit
17. BUY — fat finger price deviation <= 10% from lastPrice
18. BUY — fat finger notional value <= 1000000
19. BUY — fat finger quantity spike <= 10x avgOrderQty
20. SELL — holdings >= quantity for symbol
21. SELL — holdings > 0 (no short selling)
22. SELL — dailyOrderCount < maxDailyOrderCount
23. SELL — dailyOrderValue + orderValue <= dailyOrderLimit
24. SELL — fat finger checks same as BUY
Fail → 422 Unprocessable Entity with specific error code

### After both levels pass:
- GTC orders — set expiryDate to trader value or default 180 days
- Set expiryApplied to TRADER or DEFAULT
- Create order entity — state NEW
- Persist to PostgreSQL
- Reserve cash for BUY — PATCH /clients/{id}/reserved-cash
- Publish order.state.changed (NEW) to Kafka
- Return 202 Accepted with orderId, expiryDate, expiryApplied

---

## 7. Kafka Topics

| Topic                         | Producer          | Consumers                                          |
|-------------------------------|-------------------|----------------------------------------------------|
| order.state.changed           | order-service     | audit-service, client-service                      |
| risk.evaluation.requested     | order-service     | risk-service                                       |
| risk.evaluation.result        | risk-service      | order-service                                      |
| order.execution.requested     | order-service     | execution-service                                  |
| execution.completed           | execution-service | order-service, client-service, audit-service       |

### Kafka Rules
- Use Spring Kafka @KafkaListener for all consumers
- Consumer group ID format: {service-name}-{topic-name}-group
- All messages serialized as JSON using Jackson
- Idempotent consumers — check current entity state before processing
- If already in target state — ignore the message
- Retry 3 times with exponential backoff on failure — 1s, 2s, 4s
- After 3 failures — send to Dead Letter Topic {topic}.DLT
- At least once delivery — consumers must be idempotent

---

## 8. Redis Cache Strategy

| Cache Key                      | Service            | TTL          | Invalidation                          |
|--------------------------------|--------------------|--------------|---------------------------------------|
| market:price:{symbol}          | marketdata-service | 30 seconds   | Auto expiry                           |
| client:portfolio:{clientId}    | client-service     | 30 seconds   | On order state change or fill         |
| client:balance:{clientId}      | client-service     | 60 seconds   | On execution.completed                |
| auth:blacklist:{token}         | auth-service       | Token expiry | On logout                             |
| order:state:{orderId}          | order-service      | Session      | On state transition                   |

Cache-aside pattern for all entries:
1. Read from Redis
2. On miss — read from PostgreSQL
3. Populate Redis
4. Return data

---

## 9. Risk Engine — Pluggable Rule Pattern

Rules are NEVER hard-coded.
Each rule implements the RiskRule interface.
Rules are stored in risk_rule_config table with ON/OFF toggle.
Rules are loaded from DB at startup and on config change.

Interface contract:
  String getRuleCode();
  RiskResult evaluate(Order order, RiskContext context);
  boolean isEnabled();

| Rule Code                  | Default | Category              |
|----------------------------|---------|-----------------------|
| SINGLE_ORDER_QTY_LIMIT     | ON      | Financial limit       |
| ORDER_VALUE_LIMIT          | ON      | Financial limit       |
| CLIENT_ACTIVE_CHECK        | OFF     | Redundant — Level 2   |
| INSTRUMENT_TRADABLE_CHECK  | OFF     | Redundant — Level 2   |
| MARKET_HOURS_CHECK         | OFF     | Redundant — Level 2   |
| QUANTITY_POSITIVE_CHECK    | OFF     | Redundant — Level 1   |
| LIMIT_PRICE_CHECK          | OFF     | Redundant — Level 1   |
| CASH_BALANCE_CHECK         | OFF     | Redundant — Level 2   |
| HOLDINGS_CHECK             | OFF     | Redundant — Level 2   |
| ORDER_QTY_SPIKE_CHECK      | OFF     | Redundant — Level 2   |
| ORDER_VALUE_SPIKE_CHECK    | OFF     | Redundant — Level 2   |
| PRICE_DEVIATION_CHECK      | OFF     | Redundant — Level 2   |
| NOTIONAL_VALUE_CHECK       | OFF     | Redundant — Level 2   |

---

## 10. Domain Rules — Non-Negotiable

- NEVER use float or double for monetary values — always BigDecimal
- NEVER execute a trade without RISK_CHECK_PASSED state
- NEVER allow order state to move backwards
- NEVER modify another service's database schema
- NEVER hardcode credentials, API keys, or secrets
- NEVER skip unit tests for business logic
- NEVER log sensitive client data — log IDs and states only
- NEVER push directly to develop or main — always via PR
- NEVER use Spring ApplicationEvent — use Kafka only
- NEVER introduce a new dependency without updating this file
- Market hours: 09:30–16:00 ET Monday–Friday
- Timezone: always America/New_York — never UTC for market hours
- Order cancellation by user: OUT OF SCOPE for POC
- Order modification: OUT OF SCOPE for POC
- Short selling: OUT OF SCOPE for POC

---

## 11. REST API Standards

- Base path: /api/v1/{resource}
- Resources: plural nouns — /orders, /clients, /executions
- HTTP verbs: POST=create, GET=read, PATCH=partial update
- All monetary values in JSON: String representation of BigDecimal
- All timestamps: ISO 8601 UTC — 2026-04-16T09:30:00Z
- Order ID: UUID auto-generated by OMS
- Client Order ID: client-submitted string — stored not used internally

Success response envelope:
{
  "data": {},
  "meta": {
    "timestamp": "2026-04-16T09:30:00Z",
    "requestId": "uuid"
  }
}

Error response envelope:
{
  "error": {
    "code": "INSUFFICIENT_BUYING_POWER",
    "message": "Human readable message",
    "details": []
  },
  "meta": {
    "timestamp": "2026-04-16T09:30:00Z",
    "requestId": "uuid"
  }
}

---

## 12. Security

- JWT issued by auth-service on login
- Token payload: userId, role, expiry
- Algorithm: HS256
- Expiry: 8 hours
- Secret: environment variable JWT_SECRET — never in code
- API Gateway validates JWT on every inbound request
- Role enforcement: @PreAuthorize at controller level
- Roles: TRADER, OPERATIONS, ADMIN

| Role       | Submit Orders | View All Orders | Cancel Orders | Configure Risk |
|------------|---------------|-----------------|---------------|----------------|
| TRADER     | Own only      | No              | No            | No             |
| OPERATIONS | No            | Yes             | No            | No             |
| ADMIN      | No            | Yes             | No            | Yes            |

---

## 13. Testing Requirements

- Unit tests: all business logic, all risk rules, all state transitions
- Integration tests: all REST endpoints — happy path + error cases
- Coverage: minimum 80% line coverage per service
- Tools: JUnit 5 + Mockito + MockMvc + JaCoCo
- JaCoCo enforced in Maven build — build fails below 80%

---

## 14. GitHub Workflow

| Branch                        | Created By        | Merges To  | Approvals |
|-------------------------------|-------------------|------------|-----------|
| main                          | Never direct push | —          | 2         |
| develop                       | Never direct push | main       | 1         |
| ai/BD-{n}-{description}       | Claude Code       | develop    | 1         |
| bootstrap/{description}       | Architect         | develop    | 1         |
| infra/{description}           | Dev 1             | develop    | 1         |

Claude Code session opener — use this every session:
  Read CLAUDE.md and services/{service-name}/CLAUDE.md
  fully before writing any code.
  You are working on JIRA story BD-{number}.
  Story: {paste full story}
  The skeleton is already in place.
  Implement within the existing structure only.
  Follow openapi.yaml exactly for all endpoints.
  Create branch ai/BD-{number}-{short-description} from develop.

---

## 15. CLAUDE.md Hierarchy

This root file covers all services.
Each service has its own CLAUDE.md at services/{name}/CLAUDE.md.
Service CLAUDE.md covers service-specific endpoints, entities,
Kafka topics produced and consumed, and Redis keys used.

Read root CLAUDE.md first.
Then read service CLAUDE.md.
Both apply. Neither overrides the other.

---

## 16. Out of Scope for POC

- Real exchange connectivity
- Clearing and settlement
- FIX protocol
- Options and fixed income
- Cloud deployment — Minikube only
- Regulatory reporting
- Order cancellation by user
- Order modification
- Pre and post market trading
- Short selling
- Margin trading
- Corporate actions
- Mobile application

---

## 17. Document Control

| Field          | Value                                               |
|----------------|-----------------------------------------------------|
| Version        | 1.0                                                 |
| Owner          | Umesh Kumar KV — Principal Architect                |
| Last Updated   | April 2026                                          |
| GitHub         | github.com/umeshkumarkv/broker-dealer               |
| Confluence     | BD-POC > AI SDLC Standards > CLAUDE.md              |
| Update Rule    | Update this file before introducing any new pattern |
