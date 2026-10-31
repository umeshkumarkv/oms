<!-- plumbline:template claude-unit v2 -->
# execution-service

<!-- plumbline:begin unit -->
Last updated: 39916bd (2026-10-30, /pl-onboard, claude-unit v2)

## Purpose

Receives trade-execution requests from Kafka, simulates market and limit order fills via a deterministic mock fill engine, persists fill records to PostgreSQL (`execution_schema`), and publishes `execution.completed` events to Kafka. Exposes one REST endpoint for querying fill history by order. LIMIT orders whose price condition is unmet are queued in `pending_order_executions` and re-evaluated every 2 s by a scheduler that polls market-data service.

## Build & run

```
mvn clean package -DskipTests   # from services/execution-service/
mvn spring-boot:run              # requires Postgres, Kafka, Redis
```

Port: **8086** (`application.yml`). No Spring profiles; config via environment variables.

Test recipe: `mvn test` · Java 21 required · **FAILED** — `ExceptionInInitializerError: TypeTag::UNKNOWN` (Lombok/MapStruct incompatible with Java 24) · 2026-10-30

## Architecture & structure

- **Stack:** Java 21, Spring Boot 3.2.4, Spring Data JPA, Spring Kafka, Spring Data Redis, Flyway, PostgreSQL, MapStruct 1.5.5.Final, Lombok 1.18.30, Resilience4j 2.2.0, Springdoc OpenAPI 2.3.0.
- **Layers:** `controller` → `service` → `repository`; Kafka consumer feeds `service`; `service` calls `kafka/producer`; `MarketDataClient` makes outbound HTTP calls.
- **Key classes:** `ExecutionService` (orchestration), `FillSimulationService` (fill logic), `ExecutionMapper` (MapStruct DTOs), `@Scheduled` LIMIT-order poller (2 s).
- **Packages:** `controller`, `service` (ExecutionService, FillSimulationService, MarketDataClient), `kafka/consumer` (OrderExecutionConsumer), `kafka/producer` (ExecutionEventProducer), `domain`, `dto`, `repository`, `filter`, `config`, `exception`, `mapper`.
- **Async:** tranche-2 partial fills via `@Async` on virtual-thread executor (`spring.threads.virtual.enabled: true`).

## Security
- Identity: `X-User-Role` header from api-gateway; no JWT parsing here.
- Verified at: filter/RoleAuthorizationFilter.java
- Reachable without that check: docker-compose: yes, port 8086 published (docker-compose.yml); k8s: no, ClusterIP (k8s/services/execution-service-service.yaml).
- `GET /api/v1/executions/**` — VIEWER/TRADER/OPERATIONS/ADMIN; missing role → 401; actuator/OPTIONS bypass filter. [code: filter/RoleAuthorizationFilter.java]

## Rules

- Idempotency: check `ExecutionRepository.existsByOrderId` and `PendingOrderExecutionRepository.existsByOrderId` before processing. [code: `service/ExecutionService.java`]
- MARKET orders fill immediately at `lastPrice`; LIMIT orders fill at `limitPrice` when met, else queued. [code: `service/FillSimulationService.java`]
- Fill outcome: symbol overrides first (FAIL→REJECTED, SLOW→PARTIAL), then qty ranges (10–99→PARTIAL; 1000–1003→REJECTED; others→FULL). [code: `service/FillSimulationService.java`]
- PARTIAL fills: tranche-1 60 % (sync), tranche-2 40 % (async, 2–5 s). [code: `service/FillSimulationService.java`]
- REJECTED fills persist `executedQty=0`, `fillValue=0`, `remainingQuantity=originalQty`. [code: `service/FillSimulationService.java`; migration V4]
- Every fill outcome (FULL, PARTIAL tranche, REJECTED) publishes `execution.completed`. [code: `kafka/producer/ExecutionEventProducer.java`]
- Kafka retry: exponential backoff 1 s/2 s/4 s, 3 attempts, then DLT; deserialization failures skip retries. [code: `config/KafkaConfig.java`]
- Redis cache TTL 30 s; null values not cached. [code: `config/RedisConfig.java`]
- All money as `BigDecimal`, serialized as plain strings. [code: `mapper/ExecutionMapper.java`, `service/FillSimulationService.java`]
- `executed_qty >= 0` (relaxed from `> 0` in V4 for REJECTED fills). [code: migration V4]

## Must never

- Do not add write/delete endpoints to `ExecutionController`; fills are created only by the fill engine via Kafka. [code: `controller/ExecutionController.java`]
- Do not skip publishing `execution.completed` for any fill outcome. [code: `service/FillSimulationService.java`]
- Do not remove the idempotency checks (`existsByOrderId`). [code: `service/ExecutionService.java`]
- Do not remove `X-User-Role` gate; no other auth mechanism exists. [code: `filter/RoleAuthorizationFilter.java`]
- Do not change consumer group ID (`execution-service-order.execution.requested-group`) without an offset reset. [code: `kafka/consumer/OrderExecutionConsumer.java`]
- Do not use `double`/`float` for money; use `BigDecimal`. [code: `domain/Execution.java`]

## Decisions & trade-offs

- Mock fill engine — deterministic simulation avoids external venue dependency. [code: service/FillSimulationService.java]
- Role filter without Spring Security — gateway authenticates; downstream uses forwarded `X-User-Role`. [code: filter/RoleAuthorizationFilter.java]
- Tranche-2 async delay — simulates partial-fill latency via `@Async` + virtual threads. [code: service/FillSimulationService.java]
- V4 constraint relaxation (SCRUM-99) — `executed_qty > 0` → `>= 0` for REJECTED fills. [code: V4__relax_executed_qty_check_for_rejected_fills.sql]
- V3 schema expansion (SCRUM-48) — added fill fields and `pending_order_executions` table. [code: V3__add_fill_fields_and_pending_table.sql]

## Reference

Open CLAUDE.reference.md only for the section your task needs.
- API — 1 endpoint, 1 controller.
- Data — 2 tables (executions, pending_order_executions), 4 Flyway migrations.
- Integrations — 1 Kafka topic consumed, 1 produced, 1 outbound HTTP (market-data), Redis cache.
- Configuration — 7 env vars + 6 app-level keys.
- Tech debt — TODO/FIXME count and locations.
- Open questions — unresolved design gaps.
<!-- plumbline:end unit -->
