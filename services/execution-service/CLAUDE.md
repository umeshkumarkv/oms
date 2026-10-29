<!-- plumbline:template claude-unit v1 -->
# Execution Service

<!-- plumbline:begin unit -->
Last updated: 4369a80 (2026-10-28, /pl-onboard, claude-unit v1)

## Purpose

Consumes `order.execution.requested` Kafka events and simulates order fills for the Broker-Dealer OMS. For MARKET orders it executes immediately; for LIMIT orders it queues a `PendingOrderExecution` row and polls market-data every 2 seconds until the limit condition is met. On completion it persists an `Execution` record and publishes `execution.completed`. Exposes one REST endpoint so other services and the UI can read fill history for an order.

## Build & run

```
# from repo root or service folder
./mvnw test            # Linux / macOS
mvnw.cmd test          # Windows

./mvnw spring-boot:run -pl services/execution-service
```

Port: **8086**. No Spring profiles are defined; all config is driven by environment variables.

docker-compose `execution-service` depends_on:
- `kafka` — `condition: service_healthy`
- `redis` — `condition: service_started`
- `postgres` — `condition: service_started`

**Test recipe:** `mvn test` · requires Java 21 (`<java.version>21</java.version>` in parent pom.xml; maven-compiler-plugin 3.11.0) · **not run** — Java 24 installed locally is incompatible with maven-compiler-plugin 3.11.0; first error: `Fatal error compiling: java.lang.ExceptionInInitializerError: com.sun.tools.javac.code.TypeTag :: UNKNOWN` · 2026-10-28

## Architecture & structure

- **Stack:** Java 21, Spring Boot 3.2.4, Spring Kafka, Spring Data JPA, Spring Data Redis, Flyway, PostgreSQL 15, Redis 7, Resilience4j 2.2.0, MapStruct 1.5.5, Lombok 1.18.30, springdoc-openapi 2.3.0, jjwt 0.12.3
- **Pattern:** Kafka consumer → service → repository; scheduled poller for LIMIT orders; REST controller for read-back
- **Layers / packages:**
  - `controller` — `ExecutionController` (REST, read-only)
  - `kafka/consumer` — `OrderExecutionConsumer` (consumes `order.execution.requested`)
  - `kafka/producer` — `ExecutionEventProducer` (publishes `execution.completed`)
  - `service` — `ExecutionService` (idempotency guard, routing), `FillSimulationService` (fill logic, scheduled poller), `MarketDataClient` (HTTP call to marketdata-service)
  - `domain` — `Execution`, `PendingOrderExecution`, enums `FillType` (FULL/PARTIAL/REJECTED), `OrderSide`, `OrderType`, `PendingStatus`
  - `repository` — `ExecutionRepository`, `PendingOrderExecutionRepository`
  - `filter` — `RoleAuthorizationFilter` (header-based RBAC, no Spring Security)
  - `config` — `KafkaConfig` (DLT + exponential backoff), `RedisConfig`, `RestTemplateConfig`, `SchedulingConfig`
  - `dto` — `OrderExecutionRequest`, `ExecutionCompletedEvent`, `ExecutionResponse`, `ExecutionListResponse`, `MarketDataResponse`
  - `mapper` — `ExecutionMapper` (MapStruct)
- **Schema:** `execution_schema` in shared PostgreSQL DB `brokerdealer`; managed by Flyway (4 migrations)
- **Virtual threads** enabled (`spring.threads.virtual.enabled: true`)

## Security

- No Spring Security dependency. Authorization is enforced by `RoleAuthorizationFilter` (`filter/RoleAuthorizationFilter.java`), a servlet filter that reads the `X-User-Role` header forwarded by the API gateway.
- JWT validation is performed at the gateway; this service trusts the forwarded role header.
- `JWT_SECRET` env var is present in docker-compose for consistency but is not parsed by this service.
- `/actuator/**` and `OPTIONS` requests are excluded from role checks. [code: filter/RoleAuthorizationFilter.java]
- Role permission matrix:
  - `GET /api/v1/executions/**` — VIEWER, TRADER, OPERATIONS, ADMIN [code: filter/RoleAuthorizationFilter.java]
  - All other paths — denied (returns 403)

## Rules

- Idempotency: if an `Execution` or `PendingOrderExecution` already exists for an `orderId`, the incoming `order.execution.requested` event is silently dropped. [code: service/ExecutionService.java]
- MARKET orders are filled immediately using `lastPrice` from the event. [code: service/FillSimulationService.java]
- LIMIT BUY triggers when `marketPrice <= limitPrice`; LIMIT SELL triggers when `marketPrice >= limitPrice`. [code: service/FillSimulationService.java]
- Fill outcome is determined by symbol and quantity: symbol `FAIL` → REJECTED; symbol `SLOW` → PARTIAL; qty 1000–1003 → REJECTED; qty 10–99 → PARTIAL; otherwise FULL. [code: service/FillSimulationService.java]
- PARTIAL fills split into two tranches: 60% immediate, 40% async after a 2–5 s random delay. [code: service/FillSimulationService.java]
- REJECTED fills are persisted with `executedQty = 0` (V4 migration relaxed the `> 0` check to `>= 0`). [code: db/migration/V4__relax_executed_qty_check_for_rejected_fills.sql]
- Pending LIMIT orders are polled every 2 000 ms via `@Scheduled(fixedDelay = 2000)`. [code: service/FillSimulationService.java]
- Kafka consumer uses exponential backoff (1 s, 2 s, 4 s, max 3 attempts) then routes to DLT (`order.execution.requested.DLT` is not explicitly referenced but is the Spring Kafka default suffix). [code: config/KafkaConfig.java]
- Deserialization failures go directly to DLT without retries. [code: config/KafkaConfig.java]
- Execution venue is hard-coded to `MOCK_ECN`. [code: service/FillSimulationService.java]
- `X-User-Role` header is mandatory on all non-actuator, non-OPTIONS requests; absence returns HTTP 401. [code: filter/RoleAuthorizationFilter.java]
- Database schema validated at startup (`ddl-auto: validate`); Flyway manages all DDL. [code: application.yml]

## Must never

- Do not bypass the `RoleAuthorizationFilter`; all REST paths must be covered in `isAllowed()`.
- Do not add write endpoints without updating the permission matrix in `RoleAuthorizationFilter`.
- Do not remove the idempotency guard in `ExecutionService.processFill`; duplicate fills corrupt position data.
- Do not commit credentials or secret values; `SPRING_DATASOURCE_PASSWORD` and `JWT_SECRET` come from environment variables only.
- Do not use `ddl-auto: create` or `update`; Flyway owns all schema changes.
- Do not add a hard dependency on a specific `orderId` format outside UUID; the domain uses `UUID` throughout.

## Decisions & trade-offs

- No Spring Security: authorization relies solely on the `X-User-Role` header from the gateway. This avoids a second JWT parse per service but means the service fully trusts the gateway's header. [code: filter/RoleAuthorizationFilter.java — Javadoc comment]
- Fill logic is simulated (`MOCK_ECN` venue, deterministic outcomes by symbol/quantity). [code: service/FillSimulationService.java — EXECUTION_VENUE constant and comment]
- Kafka DLT + exponential backoff instead of infinite retry, to prevent consumer-lag buildup on transient failures. [code: config/KafkaConfig.java — inline comment]
- LIMIT order polling uses a scheduled method rather than a separate message-driven flow, keeping the service self-contained without an additional topic. [code: service/FillSimulationService.java]
- Resilience4j circuit-breaker wraps the MarketData HTTP call (sliding window 10, 50% failure threshold, 10 s open wait). [code: application.yml resilience4j block]
- Virtual threads enabled to maximise throughput for I/O-bound scheduled polling and HTTP calls under Java 21. [code: application.yml spring.threads.virtual.enabled]

## Reference

- API — 1 endpoint, 1 controller
- Data — 2 tables (executions, pending_order_executions), 4 Flyway migrations
- Integrations — 1 Kafka consumer topic, 1 Kafka producer topic, 1 outbound HTTP call (marketdata-service), Redis configured (session/cache infrastructure)
- Configuration — 9 environment variables

Open CLAUDE.reference.md only for the section your task needs.

— Plumbline 2.8.7 · /pl-onboard · claude-unit v1 · commit 4369a80 · judge pending · AI-drafted, posted after SME review
<!-- plumbline:end unit -->
