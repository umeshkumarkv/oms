<!-- plumbline:template claude-unit v1 -->
# execution-service

<!-- plumbline:begin unit -->
Last updated: 4369a80 (2026-10-30, /pl-onboard, claude-unit v1)

## Purpose

Receives trade-execution requests from Kafka, simulates market and limit order fills via a deterministic mock fill engine, persists fill records to PostgreSQL, and publishes `execution.completed` events back to Kafka. Exposes one REST endpoint for querying fill history by order. LIMIT orders whose price condition is unmet are queued in `pending_order_executions` and re-evaluated every 2 seconds by a scheduler that polls the market-data service.

## Build & run

```
mvn clean package -DskipTests   # from services/execution-service/
mvn spring-boot:run              # requires Postgres, Kafka, Redis
```

Port: **8086** (`application.yml`). No Spring profiles; config via environment variables.

Test recipe: `mvn test` · Java 21 required (`<java.version>21</java.version>` in root pom.xml) · Java 24 available (openjdk version "24") · **FAILED** — `Fatal error compiling: java.lang.ExceptionInInitializerError: com.sun.tools.javac.code.TypeTag :: UNKNOWN` — Lombok/MapStruct annotation processors are incompatible with Java 24; tests cannot run until Java 21 is used.

## Architecture & structure

- **Stack:** Java 21, Spring Boot 3.2.4, Spring Data JPA (Hibernate), Spring Kafka, Spring Data Redis, Flyway, PostgreSQL, MapStruct 1.5.5.Final, Lombok 1.18.30, Resilience4j 2.2.0, jjwt 0.12.3, Springdoc OpenAPI 2.3.0.
- **Layers:** `controller` → `service` → `repository`; Kafka consumer feeds `service`; `service` calls `kafka/producer`; `MarketDataClient` makes outbound HTTP calls.
- **Pattern:** `ExecutionService` orchestrates; fill logic in `FillSimulationService`; `ExecutionMapper` (MapStruct) converts entities to DTOs; `@Scheduled` method polls pending LIMIT orders every 2 s.
- **Main packages:**
  - `controller` — REST endpoint
  - `service` — `ExecutionService`, `FillSimulationService`, `MarketDataClient`
  - `kafka/consumer` — `OrderExecutionConsumer` (topic `order.execution.requested`)
  - `kafka/producer` — `ExecutionEventProducer` (topic `execution.completed`)
  - `domain` — JPA entities and enums
  - `dto` — request, response, event DTOs
  - `repository` — `ExecutionRepository`, `PendingOrderExecutionRepository`
  - `filter` — `RoleAuthorizationFilter` (RBAC)
  - `config` — Kafka, Redis, RestTemplate, Scheduling
  - `exception` — `GlobalExceptionHandler`, `ResourceNotFoundException`, `ValidationException`
  - `mapper` — `ExecutionMapper`
- **Database schema:** `execution_schema` (Flyway-managed, 4 migrations).
- **Async:** tranche-2 partial fills run on a virtual-thread executor (`@Async`; virtual threads enabled in `application.yml`).

## Security

- No Spring Security. `RoleAuthorizationFilter` reads the `X-User-Role` header forwarded by the API gateway. [code: `filter/RoleAuthorizationFilter.java`]
- JWT tokens are not parsed; trust is delegated to the gateway. [code: `filter/RoleAuthorizationFilter.java`]
- Actuator (`/actuator/**`) and `OPTIONS` requests bypass the filter. [code: `filter/RoleAuthorizationFilter.java`]
- `GET /api/v1/executions/**` — allowed for VIEWER, TRADER, OPERATIONS, ADMIN. [code: `filter/RoleAuthorizationFilter.java`]
- Any other method/path returns 403 FORBIDDEN. [code: `filter/RoleAuthorizationFilter.java`]
- Missing `X-User-Role` returns 401 UNAUTHORIZED with `{"error":{"code":"UNAUTHORIZED",...}}`. [code: `filter/RoleAuthorizationFilter.java`]

## Rules

- Idempotency: if an execution or pending entry already exists for an `orderId`, `processFill` returns without re-processing. [code: `service/ExecutionService.java`]
- MARKET orders fill immediately at `lastPrice`; LIMIT orders fill at `limitPrice` when condition is met, otherwise queued. [code: `service/FillSimulationService.java`]
- Fill outcome: symbol overrides first (FAIL→REJECTED, SLOW→PARTIAL), then quantity ranges (qty 10–99 → PARTIAL; qty 1000–1003 → REJECTED; all others → FULL). [code: `service/FillSimulationService.java`]
- PARTIAL fills split into tranche-1 (60 % of qty, synchronous) and tranche-2 (40 %, async 2–5 s delay). [code: `service/FillSimulationService.java`]
- REJECTED fills persist `executedQty=0`, `fillValue=0`, `remainingQuantity=originalQty`. [code: `service/FillSimulationService.java`; migration V4]
- Every completed fill (FULL, PARTIAL tranche, REJECTED) publishes an `execution.completed` Kafka event. [code: `kafka/producer/ExecutionEventProducer.java`]
- Kafka consumer errors retry with exponential backoff (1 s, 2 s, 4 s; max 3 attempts) then route to Dead Letter Topic; deserialization failures skip retries. [code: `config/KafkaConfig.java`]
- Redis cache TTL is 30 s; null values are not cached. [code: `config/RedisConfig.java`]
- Monetary values serialized as plain-string `BigDecimal` in all API responses and Kafka events. [code: `mapper/ExecutionMapper.java`, `service/FillSimulationService.java`]
- `executed_qty` must be >= 0 (relaxed from > 0 in V4 to allow REJECTED fills). [code: migration V4]

## Must never

- Do not add write or delete endpoints to `ExecutionController`; executions are created only by the fill engine via Kafka. [code: `controller/ExecutionController.java`]
- Do not skip publishing `execution.completed` on any fill outcome (FULL, PARTIAL, REJECTED). [code: `service/FillSimulationService.java`]
- Do not break idempotency: always check `ExecutionRepository.existsByOrderId` and `PendingOrderExecutionRepository.existsByOrderId` before processing. [code: `service/ExecutionService.java`]
- Do not remove the `X-User-Role` gate; this service has no other authentication mechanism. [code: `filter/RoleAuthorizationFilter.java`]
- Do not change the Kafka consumer group ID (`execution-service-order.execution.requested-group`) without coordinating offset reset. [code: `kafka/consumer/OrderExecutionConsumer.java`]
- Do not store monetary amounts as `double` or `float`; always use `BigDecimal`. [code: `domain/Execution.java`]

## Decisions & trade-offs

- **Mock fill engine:** Proof-of-concept OMS; deterministic simulation avoids external venue dependencies while exercising the full fill lifecycle. [code: `service/FillSimulationService.java` comment; `openapi.yaml` description]
- **Role filter without Spring Security:** Comment in `RoleAuthorizationFilter.java` states "No Spring Security dependency required"; gateway authenticates and forwards `X-User-Role`. [code: `filter/RoleAuthorizationFilter.java`]
- **Tranche-2 async delay (2–5 s):** Simulates realistic latency of a partial fill completing asynchronously via `@Async` with virtual threads. [code: `service/FillSimulationService.java`]
- **V4 constraint relaxation:** `executed_qty > 0` changed to `>= 0` (SCRUM-99) to allow REJECTED fills to store `executedQty=0`. [code: migration `V4__relax_executed_qty_check_for_rejected_fills.sql`]
- **V3 schema expansion (SCRUM-48):** Added `fill_type`, `fill_value`, `remaining_quantity`, `market_price_at_fill`, `execution_venue`, `client_id` columns and `pending_order_executions` table. [code: migration `V3__add_fill_fields_and_pending_table.sql`]

## Reference

- API — 1 endpoint, 1 controller. Open CLAUDE.reference.md only for the section your task needs.
- Data — 2 tables (executions, pending_order_executions), 4 Flyway migrations. Open CLAUDE.reference.md only for the section your task needs.
- Integrations — 1 Kafka topic consumed, 1 topic produced, 1 outbound HTTP call (market-data service), Redis cache. Open CLAUDE.reference.md only for the section your task needs.
- Configuration — 7 environment variables (SPRING_DATASOURCE_URL/USERNAME/PASSWORD, SPRING_KAFKA_BOOTSTRAP_SERVERS, SPRING_REDIS_HOST/PORT, MARKETDATA_SERVICE_URL) plus 6 app-level keys. Open CLAUDE.reference.md only for the section your task needs.
- Tech debt — TODO/FIXME count and locations. Open CLAUDE.reference.md only for the section your task needs.
- Open questions — unresolved design gaps. Open CLAUDE.reference.md only for the section your task needs.
<!-- plumbline:end unit -->
