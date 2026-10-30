<!-- plumbline:template claude-unit v1 -->
# order-service

<!-- plumbline:begin unit -->
Last updated: 4369a80 (2026-10-30, /pl-onboard, claude-unit v1)

## Purpose
The order-service is the core order lifecycle engine for the Broker-Dealer OMS. It accepts order submissions from traders via REST, runs two-level pre-trade validation (bean + semantic/external), publishes events to Kafka for risk evaluation and execution, and drives all order state transitions through a state machine. It owns the sole authoritative `orders` table in PostgreSQL and caches hot reads in Redis.

## Build & run
Build and test: `mvn test` (from `services/order-service/`)
Start: `mvn spring-boot:run`
Port: `8082`
No named Spring profiles; configuration driven by environment variables (see Configuration in CLAUDE.reference.md).

Test recipe: `mvn test` · Java 21 required (root pom.xml `<java.version>21</java.version>`) · Java 24 available (openjdk version "24") · **FAILED** — `Fatal error compiling: java.lang.ExceptionInInitializerError: com.sun.tools.javac.code.TypeTag :: UNKNOWN` — Lombok/MapStruct annotation processors are incompatible with Java 24; tests cannot run until Java 21 is used.

## Architecture & structure
- **Stack:** Java 21, Spring Boot 3 (Web, Data JPA, Data Redis, Validation, Actuator, Kafka, Scheduling), PostgreSQL 15 (`order_schema`), Redis, Flyway 7, Lombok, MapStruct, Resilience4j, springdoc-openapi, jjwt.
- **Layer pattern:** `controller` → `service` → `repository` (JPA + Specifications), with `client` adapters for outbound HTTP and `kafka` sub-packages.
- `controller` — `OrderController` (orders CRUD), `DashboardController` (risk alerts, summary, health).
- `service` — `OrderService`, `OrderStateMachineService`, `DashboardService`, `HealthAggregatorService`, `MarketCloseScheduler`.
- `kafka/producer` — `OrderEventProducer` (3 topics); `kafka/consumer` — `RiskResultConsumer` (2 topics).
- `client` — `ClientServiceClient`, `MarketDataServiceClient`, `ExecutionServiceClient` (all Resilience4j circuit-broken).
- `repository` — `OrderRepository` (JpaRepository + JpaSpecificationExecutor + 12 custom queries).
- `domain` — `Order` entity; enums: `OrderStatus` (11 values), `OrderSide`, `OrderType`, `TimeInForce`, `ExpiryApplied`.
- `filter` — `RoleAuthorizationFilter` (servlet RBAC); `exception` — `GlobalExceptionHandler` + 14 domain exceptions.
- **Virtual threads** enabled (`spring.threads.virtual.enabled: true`); RestTemplate uses `JdkClientHttpRequestFactory`.
- **Kafka error handling:** exponential backoff (1 s, 2 s, 4 s, 3 attempts) then dead-letter topic.

## Security
- No Spring Security. RBAC enforced by `RoleAuthorizationFilter` reading `X-User-Role` header from the gateway. [code: filter/RoleAuthorizationFilter.java]
- `POST /api/v1/orders` — TRADER only; `GET /api/v1/orders/**` — VIEWER, TRADER, OPERATIONS, ADMIN; `GET /api/v1/dashboard/**` — TRADER, OPERATIONS, ADMIN.
- `/actuator/**` and `OPTIONS` requests bypass the filter.
- TRADER data isolation: `getOrderById` returns 403 if caller's `X-User-Id` does not match the order's `clientId`. [code: service/OrderService.java]
- Outbound calls: `ClientServiceClient` injects `X-User-Role: SYSTEM`; token verification happens at the gateway.

## Rules
- LIMIT orders must supply `limitPrice` > 0; MARKET orders must not. [code: service/OrderService.java]
- Valid sides: BUY, SELL, SHORT_SELL; types: MARKET, LIMIT; TIF: DAY, GTD. [code: domain/OrderSide.java, OrderType.java, TimeInForce.java]
- GTD orders default to today + 180 days if no `gtdDate`; supplied `gtdDate` must be a future weekday non-holiday. [code: service/OrderService.java]
- Order rejected at submission if market is closed (from market-data-service). [code: service/OrderService.java]
- Client must be ACTIVE, trading-enabled, KYC=VERIFIED and not expired, or order is rejected. [code: service/OrderService.java]
- Symbol must be tradable and not halted, or order is rejected. [code: service/OrderService.java]
- SELL: `availableToSell = holdings - open SELL commitments`; rejected if insufficient. [code: service/OrderService.java, repository/OrderRepository.java]
- BUY: `availableCash = settledCash - open BUY order values`; rejected if insufficient. [code: service/OrderService.java, repository/OrderRepository.java]
- Routing destination defaults to "NYSE" when absent or blank. [code: service/OrderService.java]
- SHORT_SELL bypasses holdings check. [code: service/OrderService.java]
- GTC renamed to GTD; `time_in_force` column allows only DAY or GTD. [decided V4__rename_gtc_to_gtd.sql, BRD Section 14]
- `limit_price` precision is NUMERIC(19,4). [decided V3__fix_limitprice_precision.sql, SCRUM-41]
- DAY orders expire at 16:00 ET Mon–Fri non-holiday; GTD orders expire when `expiryDate == today`. [code: service/MarketCloseScheduler.java]
- Market-open recovery at 09:30 ET expires stale GTD orders missed by prior scheduler run. [code: service/MarketCloseScheduler.java]
- POST /api/v1/orders is TRADER-only per BRD 12.2; ADMIN excluded from order write. [code: filter/RoleAuthorizationFilter.java]
- Status-counts and dashboard metrics cached in Redis with 15 s TTL; Redis failures silently swallowed. [code: service/OrderService.java, service/DashboardService.java]
- Page size capped at 200 for order list queries. [code: service/OrderService.java]
- Kafka consumer failures retry (1 s → 2 s → 4 s, 3 attempts) then route to DLT. [code: config/KafkaConfig.java]
- Resilience4j circuit breakers (sliding window 10, failure threshold 50 %, open wait 10 s) protect all three downstream HTTP calls. [code: application.yml]
- `InsufficientBuyingPowerException` excluded from circuit-breaker failure counting. [code: application.yml]

## Must never
- Remove or skip two-level pre-trade validation (L1 bean + L2 semantic/external) before persisting an order.
- Allow a TRADER to read another client's orders (breaks TRADER data isolation in `OrderService.getOrderById`).
- Allow any role except TRADER to submit orders.
- Bypass the market-hours check; orders must not be accepted when the market is closed.
- Accept a `limitPrice` on a MARKET order or omit it on a LIMIT order.
- Run DB schema changes outside Flyway migrations.
- Delete the market-open recovery scheduler without a replacement for stale GTD cleanup.
- Change `time_in_force` to accept GTC (renamed to GTD per BRD Section 14).

## Decisions & trade-offs
- No Spring Security: lightweight header-based RBAC avoids full stack overhead behind a trusted gateway. [code: filter/RoleAuthorizationFilter.java — comment block]
- No RestTemplate pool: one connection per virtual thread via `JdkClientHttpRequestFactory`; blocking I/O is cheap on virtual threads. [code: config/HttpClientConfig.java — Javadoc comment]
- In-flight cash/sell commitments read from own DB to remove cross-service state coupling. [code: service/OrderService.java — comment "Replaces the PATCH /reserved-cash call"]
- Risk event carries full portfolio snapshot so risk-service avoids separate data pulls (SCRUM-119). [code: service/OrderService.java — comment "Compute portfolio snapshot fields for risk.evaluation.requested (SCRUM-119)"]
- Kafka consumers deserialize to Map then convert to avoid class-name type-header coupling across services. [code: config/KafkaConfig.java — comment]

## Reference
Open CLAUDE.reference.md only for the section your task needs.
- API — 7 endpoints, 2 controllers.
- Data — 1 table (`orders`, 18 columns), 7 Flyway migrations.
- Integrations — 3 outbound HTTP clients, 3 Kafka topics produced, 2 consumed, 1 Redis cache.
- Configuration — 12 config keys / env vars.
- Tech debt — 0 TODO/FIXME items found in source.
<!-- plumbline:end unit -->
