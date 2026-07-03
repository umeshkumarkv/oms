# CLAUDE.md — Broker Dealer OMS POC
# Principal Architect: Umesh Kumar KV
# Version: 1.4 | July 2026
# Status: Active — Sprint 4

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
Methodology: BMAD — Breakthrough Method of Agile AI-driven Development

### BMAD Agent Pipeline

Development follows the 9-agent BMAD eval-loop pipeline.
Each agent command is fully self-contained in `.claude/commands/`.

| Stage | Command | Gate label |
|-------|---------|------------|
| PM | `/pm-eval-loop "requirement"` | bmad-prd-approved |
| BA | `/ba-eval-loop epic:{key}` | bmad-dor-ready |
| Arch | `/arch-eval-loop SCRUM-{n}` | bmad-arch-approved |
| Dev | `/dev-eval-loop SCRUM-{n}` | git push + PR |
| Code Review | `/code-review-eval-loop SCRUM-{n}` | bmad-code-review-approved |
| QA | `/qa-eval-loop SCRUM-{n}` | JIRA → In Review |
| DevOps | `/devops-eval-loop pr:{n}` | CI green |
| SM | `/sm-eval-loop` | JIRA → Done |
| Doc | `/doc-eval-loop SCRUM-{n}` | docs PR raised |

See each command file for max attempts, evaluator criteria, and escalation rules.

---

## 2. Tech Stack — Locked. Do Not Change.

| Layer          | Technology                    | Version      |
|----------------|-------------------------------|--------------|
| Language       | Java                          | 21 LTS       |
| Framework      | Spring Boot Web MVC           | 3.2.x        |
| Build          | Maven multi-module            | 3.9+         |
| Frontend       | React + TypeScript + Vite     | 18.x         |
| Date Picker    | react-datepicker (frontend)   | 4.x          |
| Database       | PostgreSQL                    | 15           |
| ORM            | Spring Data JPA + Hibernate   | Via Boot     |
| Migrations     | Flyway                        | Via Boot     |
| Async          | Apache Kafka + Zookeeper      | Latest       |
| Cache          | Redis via Spring Data Redis   | Latest       |
| Concurrency    | Java 21 Virtual Threads       | JDK 21       |
| Gateway        | Spring Cloud Gateway MVC      | Via Boot     |
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
| notification-service | 8088 | WebSocket push, Kafka fan-out to UI      | Persist data, call services directly    |

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
- side — must be BUY, SELL, or SHORT_SELL
- orderType — must be MARKET or LIMIT
- timeInForce — must be DAY or GTC
- quantity — must be Integer greater than 0
- limitPrice — required and greater than 0 if orderType is LIMIT
- limitPrice — must be null if orderType is MARKET
- symbol — 1–6 uppercase letters (A–Z) only
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
20. SELL only — holdings >= quantity for symbol
21. SELL only — holdings > 0
22. SELL and SHORT_SELL — dailyOrderCount < maxDailyOrderCount
23. SELL and SHORT_SELL — dailyOrderValue + orderValue <= dailyOrderLimit
24. SELL and SHORT_SELL — fat finger checks same as BUY
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
| order.state.changed           | order-service     | audit-service, client-service, notification-service        |
| risk.evaluation.requested     | order-service     | risk-service                                               |
| risk.evaluation.result        | risk-service      | order-service, notification-service                        |
| order.execution.requested     | order-service     | execution-service                                          |
| execution.completed           | execution-service | order-service, client-service, audit-service, notification-service |

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
- NEVER use WebFlux or reactive programming (Mono, Flux, Reactor) — use virtual threads
- NEVER introduce a new dependency without updating this file
- Market hours: 09:30–16:00 ET Monday–Friday
- Timezone: always America/New_York — never UTC for market hours
- Order cancellation by user: OUT OF SCOPE for POC
- Order modification: OUT OF SCOPE for POC

---

## 11. Concurrency Model — Virtual Threads Mandatory

Java 21 virtual threads replace reactive programming for all I/O-bound work.
This is a project-wide standard. No exceptions.

### Mandatory configuration — every service application.yml

spring:
  threads:
    virtual:
      enabled: true

This single flag makes Spring Boot 3.2 dispatch all request-handling threads,
@Async tasks, and scheduled tasks on virtual threads automatically.

### Why virtual threads — not WebFlux

| Concern           | WebFlux (Reactor)         | Virtual Threads (Java 21)     |
|-------------------|---------------------------|-------------------------------|
| Programming model | Reactive — Mono/Flux      | Blocking — standard Java      |
| Debugging         | Async stack traces        | Normal stack traces           |
| Consistency       | Different from MVC        | Same model across all services|
| I/O throughput    | High                      | Equivalent at this scale      |
| Complexity        | High — operators, context | None — write blocking code    |

### Rules

- All services set spring.threads.virtual.enabled: true — no exceptions
- Write blocking code — virtual threads handle the I/O scheduling
- Do NOT use CompletableFuture chains to avoid blocking — just block
- @Async methods benefit automatically — no Executor configuration needed
- Spring Data JPA, RestTemplate, WebClient (if used) all work without changes
- NEVER introduce Mono, Flux, or any Reactor type — treat as a build error
- api-gateway uses Spring Cloud Gateway MVC (servlet) — not reactive Gateway
- NEVER use a connection pool for RestTemplate — use SimpleClientHttpRequestFactory
  with no pool. Pool limits defeat virtual threads. Each virtual thread blocks its
  own connection without consuming an OS thread.

---

## 12. REST API Standards

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

## 13. Coding Conventions — Mandatory for All Services

These apply to every line of code written by any developer or Claude Code session.
Violations must be caught in code review and not merged.

---

### 13a. Java Type Rules

| Concept              | Correct Type          | NEVER Use                        |
|----------------------|-----------------------|----------------------------------|
| Money / price        | `BigDecimal`          | `double`, `float`, `long`, `int` |
| Order / trade quantity | `Integer`           | `int`, `long`, `double`          |
| Primary keys         | `UUID`                | `Long`, `Integer`, `String`      |
| UTC timestamps       | `Instant`             | `Date`, `Calendar`, `long`       |
| Date only (no time)  | `LocalDate`           | `Date`, `String`                 |
| Boolean entity fields | `Boolean`            | `boolean` (primitives allow no null in JPA) |
| Enums in JSON/DB     | stored as `STRING`    | `ORDINAL` — breaks on reorder    |

BigDecimal arithmetic:
- Always use `BigDecimal.valueOf(string)` or constructor from String — NEVER from double
- Use `compareTo()` for equality — NEVER `equals()` (scale differs: 1.0 ≠ 1.00)
- Scale for money: 2 decimal places via `setScale(2, RoundingMode.HALF_UP)`

---

### 13b. Naming Conventions

#### Java
| Element            | Convention       | Example                          |
|--------------------|------------------|----------------------------------|
| Class              | PascalCase       | `OrderService`, `ClientController` |
| Interface          | PascalCase       | `RiskRule`, `OrderRepository`    |
| Method             | camelCase        | `submitOrder()`, `getClientById()` |
| Variable           | camelCase        | `clientId`, `limitPrice`         |
| Constant / static final | UPPER_SNAKE_CASE | `MAX_ORDER_QUANTITY`, `DEFAULT_EXPIRY_DAYS` |
| Enum type          | PascalCase       | `OrderStatus`, `OrderSide`       |
| Enum value         | UPPER_SNAKE_CASE | `RISK_CHECK_PASSED`, `PARTIALLY_FILLED` |
| Package            | lowercase        | `com.brokerdealer.orderservice`  |
| Test class         | PascalCase + Test | `OrderServiceTest`              |
| Test method        | camelCase        | `shouldRejectOrderWhenMarketClosed()` |

#### Database (PostgreSQL)
| Element       | Convention    | Example                           |
|---------------|---------------|-----------------------------------|
| Table         | snake_case    | `order_events`, `risk_rule_config` |
| Column        | snake_case    | `client_id`, `limit_price`        |
| Primary key   | `id`          | `id UUID PRIMARY KEY`             |
| Foreign key   | `{entity}_id` | `client_id`, `order_id`           |
| Schema        | snake_case    | `order_schema`, `auth_schema`     |
| Index         | `idx_{table}_{column}` | `idx_orders_client_id`  |

#### Kafka
| Element        | Convention          | Example                            |
|----------------|---------------------|------------------------------------|
| Topic name     | dot.separated.lower | `order.state.changed`              |
| DLT topic      | topic + `.DLT`      | `order.state.changed.DLT`          |
| Consumer group | `{service}-{topic}-group` | `order-service-risk.evaluation.result-group` |

#### REST
| Element         | Convention        | Example                           |
|-----------------|-------------------|-----------------------------------|
| Resource path   | kebab-case plural | `/api/v1/orders`, `/api/v1/risk-rules` |
| Path variable   | camelCase         | `{orderId}`, `{clientId}`         |
| Query parameter | camelCase         | `?pageSize=10&clientId=uuid`      |
| JSON field      | camelCase         | `"limitPrice"`, `"expiryDate"`    |

#### Redis keys
| Convention              | Example                          |
|-------------------------|----------------------------------|
| `{domain}:{entity}:{id}` | `order:state:{orderId}`         |
| `{domain}:{list}:{id}`   | `client:portfolio:{clientId}`   |
| `{domain}:{type}:{value}` | `auth:blacklist:{token}`       |

---

### 13c. Class Suffix Conventions

Every class name must end with its layer suffix. No exceptions.

| Layer            | Suffix        | Example                          |
|------------------|---------------|----------------------------------|
| REST controller  | `Controller`  | `OrderController`                |
| Business logic   | `Service`     | `OrderService`                   |
| JPA repository   | `Repository`  | `OrderRepository`                |
| JPA entity       | (none)        | `Order`, `Client`, `Holding`     |
| Inbound DTO      | `Request`     | `SubmitOrderRequest`             |
| Outbound DTO     | `Response`    | `OrderResponse`, `PortfolioResponse` |
| MapStruct mapper | `Mapper`      | `OrderMapper`                    |
| Custom exception | `Exception`   | `InsufficientBuyingPowerException` |
| Kafka producer   | `Producer`    | `OrderEventProducer`             |
| Kafka consumer   | `Consumer`    | `RiskResultConsumer`             |
| Spring config    | `Config`      | `KafkaConfig`, `RedisConfig`     |
| Spring filter    | `Filter`      | `JwtValidationFilter`            |

---

### 13d. Lombok — Mandatory Annotations

Entities:
```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "orders", schema = "order_schema")
public class Order { }
```

Services / Consumers / Producers:
```java
@Service          // or @Component
@RequiredArgsConstructor
@Slf4j
public class OrderService { }
```

Controllers:
```java
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController { }
```

DTOs — use `@Value` (immutable) for Responses, `@Data` for Requests:
```java
@Value  // immutable response
@Builder
public class OrderResponse { }

@Data   // mutable, Bean Validation annotations
public class SubmitOrderRequest { }
```

---

### 13e. Dependency Injection

ALWAYS constructor injection via `@RequiredArgsConstructor`.
NEVER `@Autowired` on fields — it hides dependencies and breaks testability.

```java
// CORRECT
@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
}

// WRONG — never do this
@Service
public class OrderService {
    @Autowired
    private OrderRepository orderRepository;
}
```

---

### 13f. Entity Column Conventions

```java
@Column(nullable = false)               // all required fields
@Column(nullable = false, unique = true) // unique required fields
@Enumerated(EnumType.STRING)            // ALL enums — never ORDINAL
@Column(precision = 19, scale = 4)      // ALL BigDecimal monetary fields
```

Audit fields on every entity that tracks changes:
```java
@Column(nullable = false, updatable = false)
private Instant createdAt;

@Column(nullable = false)
private Instant updatedAt;
```

---

### 13g. Service Layer Return Types

- Return `Optional<T>` when the entity may not exist — NEVER return `null`
- Return `List<T>` (never null list) — return `Collections.emptyList()` if empty
- Throw domain exceptions for business failures — NEVER return null to signal error
- NEVER expose JPA entities from service methods — always map to DTO first

```java
// CORRECT
public Optional<OrderResponse> getOrderById(UUID id) {
    return orderRepository.findById(id).map(orderMapper::toResponse);
}

// WRONG
public OrderResponse getOrderById(UUID id) {
    return null; // caller has no idea what happened
}
```

---

### 13h. Logging Conventions

Use `@Slf4j` (Lombok). All log statements must follow these rules:

```java
// Service entry — DEBUG level, log IDs only, never data
log.debug("Submitting order: orderId={}, clientId={}, symbol={}", orderId, clientId, symbol);

// State transitions — INFO level
log.info("Order state transition: orderId={} {} -> {}", orderId, previousState, newState);

// External call failures — WARN level
log.warn("Circuit breaker open for client-service: clientId={}", clientId);

// Unexpected errors — ERROR level with context
log.error("Failed to process order: orderId={}, error={}", orderId, e.getMessage(), e);
```

NEVER log:
- Passwords, tokens, secrets
- Full request/response bodies containing client PII
- BigDecimal monetary values at INFO or above — use IDs and states only

---

### 13i. Exception Handling

Every service must have `GlobalExceptionHandler` annotated `@RestControllerAdvice`.

Custom exceptions must:
- Extend `RuntimeException`
- Carry a machine-readable error code (`String errorCode`)
- Have a human-readable message
- Map to the standard error envelope in the handler

```java
// CORRECT
throw new InsufficientBuyingPowerException("INSUFFICIENT_BUYING_POWER",
    "Buying power " + buyingPower + " is less than order value " + orderValue);

// WRONG — never throw generic exceptions from business logic
throw new RuntimeException("not enough money");
```

NEVER expose stack traces in API responses. Log them server-side only.

---

## 14. Security

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

## 15. Testing Requirements

- Unit tests: all business logic, all risk rules, all state transitions
- Integration tests: all REST endpoints — happy path + error cases
- Coverage: minimum 80% line coverage per service
- Tools: JUnit 5 + Mockito + MockMvc + JaCoCo
- JaCoCo enforced in Maven build — build fails below 80%

---

## 16. GitHub Workflow

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

## 17. CLAUDE.md Hierarchy

This root file covers all services.
Each service has its own CLAUDE.md at services/{name}/CLAUDE.md.
Service CLAUDE.md covers service-specific endpoints, entities,
Kafka topics produced and consumed, and Redis keys used.

Read root CLAUDE.md first.
Then read service CLAUDE.md.
Both apply. Neither overrides the other.

---

## 18. Out of Scope for POC

- Real exchange connectivity
- Clearing and settlement
- FIX protocol
- Options and fixed income
- Cloud deployment — Minikube only
- Regulatory reporting
- Order cancellation by user
- Order modification
- Pre and post market trading
- Margin trading
- Corporate actions
- Mobile application

---

## 19. Document Control

| Field          | Value                                               |
|----------------|-----------------------------------------------------|
| Version        | 1.4                                                 |
| Owner          | Umesh Kumar KV — Principal Architect                |
| Last Updated   | July 2026 — extended eval loop pattern to all 9 BMAD agents; pm-eval-loop, ba-eval-loop, code-review-eval-loop, sm-eval-loop, devops-eval-loop, doc-eval-loop added; single-shot variants deprecated |
| GitHub         | github.com/umeshkumarkv/broker-dealer               |
| Confluence     | BD-POC > AI SDLC Standards > CLAUDE.md              |
| Update Rule    | Update this file before introducing any new pattern |
