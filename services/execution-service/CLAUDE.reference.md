<!-- plumbline:template claude-reference v1 -->
# execution-service — reference

<!-- plumbline:begin reference -->
Last updated: 4586741 (2026-10-30, /pl-onboard, claude-reference v1)

## API

### ExecutionController (`/api/v1/executions`)

| Method | Path | Handler | Purpose |
|--------|------|---------|---------|
| GET | `/api/v1/executions/{orderId}` | `ExecutionController.getExecutionsByOrderId` | Return all fill records for the given orderId, ordered by executedAt ASC. Returns 404 with code `ORDER_NOT_FOUND` when no records exist. |

**Response shape (200):**
```json
{
  "data": [
    {
      "executionId": "<uuid>",
      "orderId": "<uuid>",
      "symbol": "AAPL",
      "side": "BUY",
      "executedQty": 100,
      "executedPrice": "174.50",
      "fillType": "FULL",
      "fillValue": "17450.00",
      "remainingQuantity": 0,
      "marketPriceAtFill": "174.50",
      "executionVenue": "MOCK_ECN",
      "executedAt": "2024-01-01T10:00:00Z"
    }
  ],
  "meta": { "timestamp": "...", "requestId": "<uuid>" }
}
```

**Error shape:**
```json
{
  "error": { "code": "ORDER_NOT_FOUND", "message": "...", "details": [] },
  "meta": { "timestamp": "...", "requestId": "<uuid>" }
}
```

**Auth:** `X-User-Role` header required; allowed roles: VIEWER, TRADER, OPERATIONS, ADMIN. Missing header → 401. Unknown role → 403.

## Data

### Table: `execution_schema.executions`

| Column | Type | Notes |
|--------|------|-------|
| `id` | UUID PK | `gen_random_uuid()` |
| `order_id` | UUID NOT NULL | FK to order-service (logical, no DB constraint); indexed |
| `client_id` | UUID NOT NULL | added in V3 |
| `symbol` | VARCHAR(20) NOT NULL | ticker symbol |
| `side` | VARCHAR(10) NOT NULL | `CHECK (side IN ('BUY','SELL'))` |
| `executed_qty` | INTEGER NOT NULL | `CHECK (executed_qty >= 0)` — relaxed in V4 to allow 0 for REJECTED |
| `executed_price` | NUMERIC(18,4) NOT NULL | price at which fill executed |
| `fill_type` | VARCHAR(10) NOT NULL | `FULL`, `PARTIAL`, or `REJECTED`; added V3, default `'FULL'` |
| `fill_value` | NUMERIC(19,4) NOT NULL | executed_qty × executed_price; added V3 |
| `remaining_quantity` | INTEGER NOT NULL | shares not yet filled; added V3 |
| `market_price_at_fill` | NUMERIC(19,4) NOT NULL | market price captured at fill time; added V3 |
| `execution_venue` | VARCHAR(50) NOT NULL | always `MOCK_ECN`; added V3 |
| `executed_at` | TIMESTAMPTZ NOT NULL | fill timestamp |

Indexes: `idx_executions_order_id`, `idx_executions_executed_at`.

### Table: `execution_schema.pending_order_executions`

| Column | Type | Notes |
|--------|------|-------|
| `id` | UUID PK | `gen_random_uuid()` |
| `order_id` | UUID NOT NULL UNIQUE | constraint `uq_pending_order_id` |
| `client_id` | UUID NOT NULL | |
| `symbol` | VARCHAR(20) NOT NULL | |
| `side` | VARCHAR(10) NOT NULL | `BUY` or `SELL` |
| `order_type` | VARCHAR(10) NOT NULL | always `LIMIT` for pending entries |
| `quantity` | INTEGER NOT NULL | `CHECK (quantity > 0)` |
| `limit_price` | NUMERIC(19,4) NOT NULL | target execution price |
| `last_price` | NUMERIC(19,4) NOT NULL | market price at time of queuing |
| `order_value` | NUMERIC(19,4) NOT NULL | quantity × limit_price |
| `status` | VARCHAR(10) NOT NULL | `PENDING` or `PROCESSED` |
| `created_at` | TIMESTAMPTZ NOT NULL | immutable |
| `updated_at` | TIMESTAMPTZ NOT NULL | set to NOW() when status changes to PROCESSED |

Indexes: `idx_pending_executions_status`, `idx_pending_executions_order_id`.

### Flyway migrations

| Version | File | Change |
|---------|------|--------|
| V1 | `V1__schema.sql` | Creates `executions` table with initial columns (id, order_id, symbol, side, executed_qty, executed_price, executed_at) and two indexes |
| V2 | `V2__seed.sql` | No-op seed — no data required; executions created by fill engine |
| V3 | `V3__add_fill_fields_and_pending_table.sql` | Adds fill_type, fill_value, remaining_quantity, market_price_at_fill, execution_venue, client_id to executions; creates pending_order_executions table (SCRUM-48) |
| V4 | `V4__relax_executed_qty_check_for_rejected_fills.sql` | Drops `executed_qty > 0` constraint, adds `executed_qty >= 0` to allow REJECTED fills with 0 quantity (SCRUM-99) |

## Integrations

### Kafka — consumed topics

| Topic | Group ID | Message type | Handler |
|-------|----------|-------------|---------|
| `order.execution.requested` | `execution-service-order.execution.requested-group` | `OrderExecutionRequest` (JSON) | `kafka/consumer/OrderExecutionConsumer.consumeOrderExecutionRequested` → `ExecutionService.processFill` |

Consumer config: `ErrorHandlingDeserializer` wrapping `JsonDeserializer`; trusted packages `com.brokerdealer.*`; default type `OrderExecutionRequest`; `USE_TYPE_INFO_HEADERS=false`. Retry: exponential backoff 1 s / 2× / max 3 attempts; on exhaustion publishes to Dead Letter Topic via `DeadLetterPublishingRecoverer`. Deserialization failures bypass retries.

### Kafka — produced topics

| Topic | Key | Message type | Publisher |
|-------|-----|-------------|-----------|
| `execution.completed` | `orderId` (String) | `ExecutionCompletedEvent` (JSON) | `kafka/producer/ExecutionEventProducer.publishExecutionCompleted` |

Published for every fill record persisted: FULL fills (once), PARTIAL fills (twice — tranche-1 sync, tranche-2 async), REJECTED fills (once).

### Outbound HTTP — market-data service

| Call | URL template | Method | Purpose |
|------|-------------|--------|---------|
| `MarketDataClient.getMarketData(symbol)` | `${app.marketdata.base-url}/api/v1/market/{symbol}` | GET | Fetch `lastPrice`, `tradable`, `halted` for a symbol during LIMIT order price monitoring |

- Base URL configured via `app.marketdata.base-url` (default `http://localhost:8084`).
- RestTemplate connect timeout: 3 000 ms; read timeout: 3 000 ms.
- Resilience4j circuit breaker `default` instance: sliding window 10, failure threshold 50 %, open-state wait 10 s, half-open calls 3; time limiter 2 s.
- Failures return `Optional.empty()`; the scheduler skips the pending order and retries on the next 2 s tick.

### Redis

- Spring Cache (`@EnableCaching`) with `RedisCacheManager`; default TTL 30 s; null values not cached.
- `RedisTemplate<String, Object>` with `GenericJackson2JsonRedisSerializer` (JavaTimeModule, ISO timestamps).
- No `@Cacheable` annotations are present in the current code; Redis infrastructure is provisioned but not actively used for caching in this version.

## Configuration

| Key / Environment Variable | Default | Purpose |
|---------------------------|---------|---------|
| `SPRING_DATASOURCE_URL` | _(required)_ | JDBC URL for PostgreSQL |
| `SPRING_DATASOURCE_USERNAME` | `brokerdealer` | DB username |
| `SPRING_DATASOURCE_PASSWORD` | `brokerdealer` | DB password |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker addresses |
| `SPRING_REDIS_HOST` | `localhost` | Redis hostname |
| `SPRING_REDIS_PORT` | `6379` | Redis port |
| `MARKETDATA_SERVICE_URL` | `http://localhost:8084` | Base URL for market-data service HTTP calls (`app.marketdata.base-url`) |
| `spring.jpa.hibernate.ddl-auto` | `validate` | Schema validation only; Flyway owns DDL |
| `spring.flyway.schemas` | `execution_schema` | Flyway-managed schema |
| `spring.threads.virtual.enabled` | `true` | Enables Java 21 virtual threads for all Spring MVC and async tasks |
| `resilience4j.circuitbreaker.instances.default.*` | see application.yml | Circuit breaker tuning for MarketDataClient |
| `resilience4j.timelimiter.instances.default.timeout-duration` | `2s` | Time limiter for circuit-breaker-wrapped calls |
| `management.endpoints.web.exposure.include` | `health,info,metrics` | Actuator endpoints exposed |

Secrets (`SPRING_DATASOURCE_PASSWORD`) must come from the deployment environment (Docker secrets, Kubernetes secrets, or vault). No secrets are stored in `application.yml`.

## Tech debt

**TODO/FIXME count:** 0 found in `src/` (grep across all Java and SQL files).

**Other debt:**

- Redis `@Cacheable` not applied: `RedisConfig` and the Redis dependency are fully configured but no service method uses `@Cacheable`, `@CacheEvict`, or `@CachePut`. The cache infrastructure is provisioned with no active users. [code: `config/RedisConfig.java`; `service/ExecutionService.java`, `service/FillSimulationService.java`]
- Resilience4j circuit breaker is declared in `application.yml` but `MarketDataClient` calls `RestTemplate` directly without a `@CircuitBreaker` annotation; the circuit breaker configuration has no active binding. [code: `service/MarketDataClient.java`; `application.yml`]
- No dead-letter topic name is configured; `DeadLetterPublishingRecoverer` uses the default Spring Kafka DLT naming convention (`<topic>.DLT`). If the DLT topic is not pre-created, poison messages are silently dropped by the broker. [code: `config/KafkaConfig.java`]
- `pending_order_executions.order_type` is always `LIMIT` (hard-coded in `FillSimulationService.initiateLimitFill`), but the column allows any VARCHAR(10) value with no DB-level check constraint. [code: `service/FillSimulationService.java`; migration V3]
- V1 schema created `executions` with `executed_qty CHECK (executed_qty > 0)`; V4 dropped and re-added it as `>= 0`. The interim period (V1–V3 deployed without V4) would have prevented REJECTED fill writes. No compensating migration for any rows affected in that window. [code: migrations V1, V4]

## Open questions

- Is the Resilience4j circuit breaker intended to wrap `MarketDataClient` calls? No `@CircuitBreaker` annotation is present; the circuit breaker configuration in `application.yml` has no active binding.
- Should Redis caching be applied to `getExecutionsByOrderId`? The infrastructure is in place but unused.
- What is the retention policy for `PROCESSED` rows in `pending_order_executions`? There is no purge job or TTL in the current code.
- The `timeInForce` field exists in `OrderExecutionRequest` but is never read or stored anywhere in the fill engine. Is it intentionally ignored?
- Should `execution_venue` be configurable (e.g., per-environment) rather than hard-coded to `"MOCK_ECN"` as a static constant?
<!-- plumbline:end reference -->
