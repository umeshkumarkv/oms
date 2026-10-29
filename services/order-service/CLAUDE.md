<!-- plumbline:template claude-unit v1 -->
# Order Service

<!-- plumbline:begin unit -->
Last updated: 4369a80 (2026-10-28, /pl-onboard, claude-unit v1)

## Purpose
Accepts, validates and lifecycle-manages equity orders for the Broker-Dealer OMS. On submission it performs two-level validation (syntactic then external), persists the order in status NEW, and publishes Kafka events that drive async risk evaluation and execution. It owns the order state machine (NEW → VALIDATED → RISK_CHECK_PASSED → ACCEPTED → SENT_TO_EXECUTION → FILLED/REJECTED/EXPIRED) and runs scheduled jobs to expire DAY and GTD orders at market close. It also exposes a dashboard API for trading-summary, risk-alert and cross-service health aggregation.

## Build & run
Build: `./mvnw package -pl services/order-service -am`
Run: `./mvnw spring-boot:run -pl services/order-service` — port **8082**
Docker: `docker-compose up order-service` — depends_on kafka (`service_healthy`), redis (`service_started`), postgres (`service_started`)
No hard-coded absolute paths for startup or tests.

Test recipe: `mvn test` · requires Java 21 (`<java.version>21</java.version>` in root pom.xml) · not run — Java 24 installed locally is incompatible with maven-compiler-plugin 3.11.0; first error: `Fatal error compiling: java.lang.ExceptionInInitializerError: com.sun.tools.javac.code.TypeTag :: UNKNOWN` · 2026-10-28

## Architecture & structure
- **Stack:** Java 21, Spring Boot 3.2.4, Spring Data JPA (Hibernate), Spring Kafka, Spring Data Redis, Flyway, PostgreSQL 15, Redis 7, Resilience4j 2.2.0, MapStruct 1.5.5, Lombok 1.18.30, JJWT 0.12.3, springdoc-openapi 2.3.0
- **Pattern:** controller → service → repository; outbound HTTP via typed client classes with Resilience4j circuit-breakers; Kafka producer/consumer pair; single `RoleAuthorizationFilter` (no Spring Security)
- **Virtual threads:** `spring.threads.virtual.enabled=true`; `HttpClientConfig` uses `JdkClientHttpRequestFactory` — one connection per virtual thread, no pool [code: HttpClientConfig.java]
- **Packages:**
  - `controller` — `OrderController`, `DashboardController`
  - `service` — `OrderService`, `OrderStateMachineService`, `DashboardService`, `HealthAggregatorService`, `MarketCloseScheduler`
  - `repository` — `OrderRepository` (JPA + custom JPQL/native queries)
  - `kafka/producer` — `OrderEventProducer` (3 topics); `kafka/consumer` — `RiskResultConsumer` (2 topics)
  - `client` — `ClientServiceClient`, `MarketDataServiceClient`, `ExecutionServiceClient`
  - `domain` — `Order` entity, enums (`OrderStatus`, `OrderSide`, `OrderType`, `TimeInForce`, `ExpiryApplied`)
  - `filter` — `RoleAuthorizationFilter`; `exception` — `GlobalExceptionHandler` + typed exceptions
  - `config` — `KafkaConfig`, `RedisConfig`, `HttpClientConfig`, `SchedulingConfig`, `HealthCheckConfig/Properties`
- **DB schema:** `order_schema` (PostgreSQL), managed by Flyway (V1–V7 migrations)

## Security
- No Spring Security. `RoleAuthorizationFilter` reads `X-User-Role` header from the API gateway and enforces the permission matrix. [code: RoleAuthorizationFilter.java]
- `POST /api/v1/orders` — TRADER only [code: RoleAuthorizationFilter.java]
- `GET /api/v1/orders/**` — VIEWER, TRADER, OPERATIONS, ADMIN [code: RoleAuthorizationFilter.java]
- `GET /api/v1/dashboard/**` — TRADER, OPERATIONS, ADMIN [code: RoleAuthorizationFilter.java]
- Actuator (`/actuator/**`) and OPTIONS requests excluded from role checking [code: RoleAuthorizationFilter.java]
- TRADER on `GET /api/v1/orders/{id}`: `X-User-Id` must match `order.clientId`; mismatch throws 403 [code: OrderService.java]
- Outbound calls to client-service carry `X-User-Role: SYSTEM` [code: ClientServiceClient.java]
- `JWT_SECRET` env var injected via docker-compose; identity context arrives via headers only, no tokens stored [docker-compose.yml]

## Rules
- LIMIT orders must carry a positive `limitPrice`; MARKET orders must not [code: OrderService.java `validateLevel1`]
- `timeInForce` must be DAY or GTD; GTC was renamed GTD per BRD Section 14 [decided V4__rename_gtc_to_gtd.sql]
- GTD orders without a caller-supplied `expiryDate` get a 180-day default; `expiryApplied` records source (TRADER or DEFAULT) [code: OrderService.java]
- GTD `expiryDate` must be a future weekday and not a US market holiday [code: OrderService.java `validateExpiryDate`]
- SELL orders: `availableToSell = settledHoldings − openSellCommitments`; rejected if ≤ 0 or below quantity [code: OrderService.java L2 check #20-21]
- BUY orders: `availableCash = cashBalance − openBuyCommitments`; rejected if below `orderValue` [code: OrderService.java L2 check #13]
- Market must be open (marketdata-service `/api/v1/market/hours`) before accepting any order [code: OrderService.java]
- Client must be ACTIVE, `tradingEnabled=true`, `kycStatus=VERIFIED`, and have a future `kycExpiryDate` [code: OrderService.java]
- Symbol must be tradable and not halted (marketdata-service) [code: OrderService.java]
- State transitions are idempotent: already-terminal or already-target state is silently skipped [code: OrderStateMachineService.java]
- DAY orders expired at 16:00 ET Mon-Fri (skipping US 2026 holidays); GTD orders on `expiryDate` at same time [code: MarketCloseScheduler.java]
- Recovery job at 09:30 ET Mon-Fri expires past-due GTD orders still in non-terminal state [code: MarketCloseScheduler.java]
- Kafka consumer: exponential backoff (1s, 2s, 4s — 3 attempts) then dead-letter topic [code: KafkaConfig.java]
- Page size capped at 200; minimum 1 [code: OrderService.java]
- `routingDestination` defaults to `"NYSE"` if not provided [code: OrderService.java `resolveRoutingDestination`]
- JaCoCo line coverage minimum 80% (excludes domain, DTOs, config, kafka stubs, mappers, main class) [code: pom.xml]

## Must never
- Remove or weaken `RoleAuthorizationFilter` — it is the sole access-control mechanism for this service [code: RoleAuthorizationFilter.java]
- Add Spring Security without removing the filter; they would conflict
- Accept a MARKET order with `limitPrice` set — explicit validation rejection [code: OrderService.java]
- Transition an order out of a terminal state (FILLED, CANCELLED, REJECTED, EXPIRED, RISK_REJECTED) [code: OrderStateMachineService.java]
- Remove in-flight cash/holdings commitment checks; client-service holds only settled positions [code: OrderService.java]
- Change `order_schema` without a Flyway migration script

## Decisions & trade-offs
- No HTTP connection pool: virtual threads block independently without consuming OS threads, so pooling adds overhead [code: HttpClientConfig.java comment]
- Kafka consumers deserialize to `LinkedHashMap` then convert via `ObjectMapper` to decouple from producer schema type headers [code: KafkaConfig.java comment]
- `SENT_TO_EXECUTION` excluded from idempotency skip in `consumeExecutionCompleted` — not a true terminal state; guarded by explicit comment [code: OrderStateMachineService.java SCRUM-48 comment]
- Dashboard and status-counts cached in Redis (15-second TTL, cache-aside); Redis failures swallowed (warn + DB fallback) [code: DashboardService.java, OrderService.java]
- Kafka stubs (`kafka/**`) excluded from JaCoCo — not implemented in current story [code: pom.xml SCRUM-42 comment]
- `limit_price` precision raised from NUMERIC(18,4) to NUMERIC(19,4) per SCRUM-41 [decided V3__fix_limitprice_precision.sql]

## Reference
- API — 7 endpoints across 2 controllers; open CLAUDE.reference.md for full path and handler list
- Data — 1 table (`order_schema.orders`), 7 Flyway migrations; open CLAUDE.reference.md for field details
- Integrations — 3 outbound HTTP clients, 3 Kafka topics produced, 2 consumed; open CLAUDE.reference.md for details
- Configuration — 14 config rows (env vars and app keys); open CLAUDE.reference.md for full list
- Tech debt — 0 TODO/FIXME; open CLAUDE.reference.md for details

Open CLAUDE.reference.md only for the section your task needs.

— Plumbline 2.8.7 · /pl-onboard · claude-unit v1 · commit 4369a80 · judge pending · AI-drafted, posted after SME review
<!-- plumbline:end unit -->
