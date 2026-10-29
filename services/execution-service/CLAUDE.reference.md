<!-- plumbline:template claude-reference v1 -->
# Execution Service — Reference

<!-- plumbline:begin reference -->
Last updated: 4369a80 (2026-10-28, /pl-onboard, claude-reference v1)

## API

### ExecutionController (`controller/ExecutionController.java`) — base path `/api/v1/executions`

| Method | Path | Handler | Purpose |
|--------|------|---------|---------|
| GET | `/api/v1/executions/{orderId}` | `getExecutionsByOrderId` | Returns all fill records for the given order UUID, ordered by `executedAt` ascending. Returns 404 if no executions exist. |

**Access control** (enforced by `RoleAuthorizationFilter`, not Spring Security):
- GET `/api/v1/executions/**` — roles: VIEWER, TRADER, OPERATIONS, ADMIN
- All write methods / unmatched paths — 403 Forbidden
- Missing `X-User-Role` header — 401 Unauthorized
- `/actuator/**` and OPTIONS — no role check

## Data

### Table: `execution_schema.executions`

| Column | Type | Notes |
|--------|------|-------|
| id | UUID PK | generated, `gen_random_uuid()` |
| order_id | UUID NOT NULL | FK-like reference to order-service; indexed |
| client_id | UUID NOT NULL | added V3; default `gen_random_uuid()` (migration default only) |
| symbol | VARCHAR(20) NOT NULL | ticker symbol |
| side | VARCHAR(10) NOT NULL | `BUY` or `SELL` |
| executed_qty | INTEGER NOT NULL | >= 0 (V4 relaxed from > 0 to allow REJECTED fills with qty 0) |
| executed_price | NUMERIC(18,4) NOT NULL | price at which fill was executed |
| fill_type | VARCHAR(10) NOT NULL | `FULL`, `PARTIAL`, or `REJECTED`; added V3, default `FULL` |
| fill_value | NUMERIC(19,4) NOT NULL | qty * executed_price; added V3 |
| remaining_quantity | INTEGER NOT NULL | shares not yet filled; added V3 |
| market_price_at_fill | NUMERIC(19,4) NOT NULL | last market price at time of fill; added V3 |
| execution_venue | VARCHAR(50) NOT NULL | always `MOCK_ECN` at present; added V3 |
| executed_at | TIMESTAMPTZ NOT NULL | indexed |

Indexes: `idx_executions_order_id`, `idx_executions_executed_at`

### Table: `execution_schema.pending_order_executions`

| Column | Type | Notes |
|--------|------|-------|
| id | UUID PK | generated |
| order_id | UUID NOT NULL UNIQUE | unique constraint `uq_pending_order_id`; indexed |
| client_id | UUID NOT NULL | |
| symbol | VARCHAR(20) NOT NULL | |
| side | VARCHAR(10) NOT NULL | `BUY` or `SELL` |
| order_type | VARCHAR(10) NOT NULL | `LIMIT` (only LIMIT orders are queued) |
| quantity | INTEGER NOT NULL | > 0 |
| limit_price | NUMERIC(19,4) NOT NULL | target trigger price |
| last_price | NUMERIC(19,4) NOT NULL | market price at time of queuing |
| order_value | NUMERIC(19,4) NOT NULL | notional value |
| status | VARCHAR(10) NOT NULL | `PENDING` or `PROCESSED`; indexed |
| created_at | TIMESTAMPTZ NOT NULL | |
| updated_at | TIMESTAMPTZ NOT NULL | |

Indexes: `idx_pending_executions_status`, `idx_pending_executions_order_id`

### Flyway migrations

| Version | File | Summary |
|---------|------|---------|
| V1 | `V1__schema.sql` | Creates `executions` table with basic columns and indexes |
| V2 | `V2__seed.sql` | Seed data (content not read; present in migration folder) |
| V3 | `V3__add_fill_fields_and_pending_table.sql` | Adds fill columns to `executions`; creates `pending_order_executions` (SCRUM-48) |
| V4 | `V4__relax_executed_qty_check_for_rejected_fills.sql` | Relaxes `executed_qty > 0` to `>= 0` to allow REJECTED fills (SCRUM-99) |

Schema location: `classpath:db/migration`, schema: `execution_schema`, `baseline-on-migrate: true`.

## Integrations

### Kafka (consumed)

| Topic | Group ID | Deserialized as | Purpose |
|-------|----------|-----------------|---------|
| `order.execution.requested` | `execution-service-order.execution.requested-group` | `OrderExecutionRequest` | Triggers fill processing for an approved order |

Error handling: `ErrorHandlingDeserializer` wrapping `JsonDeserializer`; exponential backoff (1 s → 2 s → 4 s, max 3 attempts) then dead-letter topic. `DeserializationException` goes straight to DLT with no retries. Dead-letter topic: `order.execution.requested.DLT` (Spring Kafka appends `.DLT` suffix by convention; topic created in docker-compose).

Trusted packages: `com.brokerdealer.*`

### Kafka (produced)

| Topic | Key | Payload | Purpose |
|-------|-----|---------|---------|
| `execution.completed` | `orderId` (String) | `ExecutionCompletedEvent` (JSON) | Notifies downstream services (order-service, audit-service, notification-service) of each fill or rejection |

### Outbound HTTP

| Call | URL | Class | Circuit-breaker |
|------|-----|-------|-----------------|
| GET market data | `${app.marketdata.base-url}/api/v1/market/{symbol}` | `MarketDataClient` | Resilience4j `default` instance: window=10, failure threshold=50%, wait=10 s, half-open calls=3; timeout=2 s |

`app.marketdata.base-url` defaults to `http://localhost:8084`; overridden by `MARKETDATA_SERVICE_URL` env var in docker-compose (`http://marketdata-service:8084`).

### Redis

Configured via `spring.data.redis` (host, port, timeout 2000 ms). `RedisConfig` is present; no explicit caching annotations were observed in the service layer — Redis infrastructure is available for future use.

### Actuator endpoints exposed

`health`, `info`, `metrics` — at `/actuator/...`; health probes enabled; `show-details: always`.

## Configuration

| Key / Env Var | Default | Purpose |
|---------------|---------|---------|
| `SPRING_DATASOURCE_URL` | (required) | JDBC URL; docker-compose sets `jdbc:postgresql://postgres:5432/brokerdealer?currentSchema=execution_schema` |
| `SPRING_DATASOURCE_USERNAME` | `brokerdealer` | DB username |
| `SPRING_DATASOURCE_PASSWORD` | `brokerdealer` | DB password — **never commit a real value; rotate if exposed** |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker address |
| `SPRING_REDIS_HOST` | `localhost` | Redis host |
| `SPRING_REDIS_PORT` | `6379` | Redis port |
| `MARKETDATA_SERVICE_URL` | `http://localhost:8084` | Base URL for market-data HTTP calls |
| `JWT_SECRET` | (none in app config) | Passed by docker-compose for gateway consistency; not parsed by this service |
| `server.port` | `8086` | HTTP listen port (hard-coded in application.yml) |

Secrets (`SPRING_DATASOURCE_PASSWORD`, `JWT_SECRET`) come from environment variables or a secrets manager in production; no values are stored in application.yml.

## Tech debt

- 0 TODO/FIXME/HACK comments found in `src/main/java` (ripgrep scan, commit 4369a80).
- `RedisConfig` is present but no explicit cache annotations were found in service classes; Redis usage is unclear beyond infrastructure setup. [config/RedisConfig.java]
- Fill simulation is entirely deterministic/mocked (`MOCK_ECN`); real venue integration is not implemented.
- V3 migration set `client_id DEFAULT gen_random_uuid()`, meaning any row inserted without an explicit `client_id` will silently get a random UUID rather than failing — a potential data-integrity gap. [db/migration/V3__add_fill_fields_and_pending_table.sql]
- `MarketDataClient` uses a raw `RestTemplate` and casts response to `Map<String, Object>` without strong typing, making it brittle to upstream response schema changes. [service/MarketDataClient.java]

## Open questions

- Is Redis used for anything in this service today, or is it wired in for a future feature (e.g. rate limiting, deduplication cache)?
- The `execution.completed.DLT` topic is created in docker-compose; is this service expected to produce to a DLT, or is this pre-created for a downstream consumer?
- Should the `LIMIT` polling scheduler interval (2 000 ms) be configurable via a property rather than hard-coded?
- The `JWT_SECRET` env var is present in docker-compose for this service but not consumed by any code; confirm it can be removed without breaking anything.
- V2 seed data: content not verified — confirm it does not insert rows that would conflict with idempotency checks in tests.

— Plumbline 2.8.7 · /pl-onboard · claude-reference v1 · commit 4369a80 · judge pending · AI-drafted, posted after SME review
<!-- plumbline:end reference -->
