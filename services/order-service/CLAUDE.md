<!-- plumbline:template claude-unit v1 -->
# order-service

<!-- plumbline:begin unit -->
Last updated: 4586741 (2026-10-30, /pl-onboard, claude-unit v1)

## Purpose
The order-service is the core order lifecycle engine for the Broker-Dealer OMS. It accepts order submissions from traders via REST, runs two-level pre-trade validation (bean + semantic/external), publishes events to Kafka for risk evaluation and execution, and drives all order state transitions through a state machine. It owns the sole authoritative `orders` table in PostgreSQL (`order_schema`) and caches hot reads in Redis.

## Build & run
Build and test: `mvn test` (from `services/order-service/`)
Start: `mvn spring-boot:run`
Port: `8082`
No named Spring profiles; configuration driven by environment variables (see Configuration in CLAUDE.reference.md).

Test recipe: `mvn test` · Java 21 required (root pom.xml `<java.version>21</java.version>`) · **FAILED** — `ExceptionInInitializerError: TypeTag::UNKNOWN` — Lombok/MapStruct annotation processors incompatible with Java 24; run with Java 21. · 2026-10-30

## Architecture & structure
Stack: Java 21, Spring Boot 3, PostgreSQL 15 (`order_schema`), Redis, Flyway, Lombok, MapStruct, Resilience4j, springdoc-openapi, jjwt.

Layer pattern: `controller` → `service` → `repository` (JPA + Specifications), `client` adapters for outbound HTTP, `kafka` sub-packages.
- `controller` — `OrderController` (CRUD), `DashboardController` (alerts, summary, health).
- `service` — `OrderService`, `OrderStateMachineService`, `DashboardService`, `MarketCloseScheduler`.
- `kafka` — `OrderEventProducer` (3 topics), `RiskResultConsumer` (2 topics).
- `client` — 3 circuit-broken HTTP clients: `ClientServiceClient`, `MarketDataServiceClient`, `ExecutionServiceClient`.
- `repository` — `OrderRepository` (JPA + Specs + 12 custom queries).
- `domain` — `Order` entity; enums `OrderStatus` (11), `OrderSide`, `OrderType`, `TimeInForce`.
- `filter` + `exception` — `RoleAuthorizationFilter`, `GlobalExceptionHandler` + 14 exceptions.

Virtual threads enabled; `JdkClientHttpRequestFactory`. Kafka DLT: exponential backoff 1→2→4 s, 3 attempts.

## Security
- No Spring Security. `RoleAuthorizationFilter` reads `X-User-Role` from gateway. [code: filter/RoleAuthorizationFilter.java]
- `POST /api/v1/orders` TRADER only; `GET /api/v1/orders/**` VIEWER/TRADER/OPERATIONS/ADMIN; `GET /api/v1/dashboard/**` TRADER/OPERATIONS/ADMIN. `/actuator/**` and OPTIONS bypass.
- TRADER data isolation: `getOrderById` returns 403 if `X-User-Id` ≠ order's `clientId`. [code: service/OrderService.java]
- Outbound: `ClientServiceClient` injects `X-User-Role: SYSTEM`; gateway verifies tokens.

## Rules
- LIMIT orders require `limitPrice` > 0; MARKET orders must not supply it. [code: service/OrderService.java]
- Valid sides: BUY, SELL, SHORT_SELL; types: MARKET, LIMIT; TIF: DAY, GTD (GTC renamed per BRD §14). [decided: V4__rename_gtc_to_gtd.sql]
- GTD default = today + 180 days; supplied `gtdDate` must be a future weekday non-holiday. [code: service/OrderService.java]
- Order rejected at submission if market is closed. [code: service/OrderService.java]
- Client must be ACTIVE, trading-enabled, KYC=VERIFIED and not expired. [code: service/OrderService.java]
- Symbol must be tradable and not halted. [code: service/OrderService.java]
- SELL: `availableToSell = holdings − open SELL commitments`; rejected if insufficient. SHORT_SELL bypasses. [code: service/OrderService.java]
- BUY: `availableCash = settledCash − open BUY values`; rejected if insufficient. [code: service/OrderService.java]
- `limit_price` precision NUMERIC(19,4). Routing defaults to "NYSE". [decided: V3__fix_limitprice_precision.sql]
- DAY orders expire 16:00 ET Mon–Fri; GTD when `expiryDate == today`; 09:30 ET recovery for stale GTD. [code: service/MarketCloseScheduler.java]
- `POST /api/v1/orders` TRADER-only (BRD 12.2); ADMIN excluded. [code: filter/RoleAuthorizationFilter.java]
- Redis cache 15 s TTL for status counts; failures silently swallowed. Page size capped at 200. [code: service/OrderService.java]
- Kafka DLT: 1→2→4 s, 3 attempts. Resilience4j: sliding window 10, failure 50 %, wait 10 s. [code: config/KafkaConfig.java, application.yml]

## Must never
- Skip two-level pre-trade validation (L1 bean + L2 semantic/external) before persisting an order.
- Let a TRADER read another client's orders (TRADER data isolation in `OrderService.getOrderById`).
- Let any role except TRADER submit orders; bypass the market-hours check.
- Accept `limitPrice` on MARKET or omit it on LIMIT.
- Run DB schema changes outside Flyway.
- Delete the market-open recovery scheduler; change `time_in_force` to accept GTC.

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
