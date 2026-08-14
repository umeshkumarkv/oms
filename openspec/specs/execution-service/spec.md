# execution-service -- Service Specification

## Purpose

execution-service is the fill-simulation engine for the broker-dealer OMS. It consumes order-execution requests from Kafka, determines a fill outcome (FULL, PARTIAL, or REJECTED) based on symbol overrides and quantity ranges, persists execution records, monitors LIMIT orders against live market prices, and publishes `execution.completed` events to notify downstream services of fill results.

(application.yml:6 — `spring.application.name: execution-service`)

## Port

`8086` (application.yml:44 — `server.port: 8086`)

## Database Schema

**Schema:** `execution_schema`
(application.yml:18 — `hibernate.default_schema: execution_schema`; application.yml:21 — `spring.flyway.schemas: execution_schema`)

| Table | Introduced |
|---|---|
| `execution_schema.executions` | V1__schema.sql |
| `execution_schema.pending_order_executions` | V3__add_fill_fields_and_pending_table.sql |

**Indexes on `executions`:** `idx_executions_order_id` (order_id), `idx_executions_executed_at` (executed_at) — V1__schema.sql:14-15

**Indexes on `pending_order_executions`:** `idx_pending_executions_status` (status), `idx_pending_executions_order_id` (order_id) — V3__add_fill_fields_and_pending_table.sql:29-30

**V4 migration note (SCRUM-99):** relaxed `executed_qty > 0` check to `executed_qty >= 0` to allow zero quantity for REJECTED fills (V4__relax_executed_qty_check_for_rejected_fills.sql:4-8).

## Kafka Produced

| Topic (exact literal) | Trigger | Payload fields | file:line |
|---|---|---|---|
| `"execution.completed"` | After every fill persisted (FULL, PARTIAL tranche 1, PARTIAL tranche 2, REJECTED) | orderId, executionId, clientId, symbol, side, executedQty, executedPrice, fillType, fillValue, remainingQuantity, marketPriceAtFill, executionVenue, executedAt | ExecutionEventProducer.java:14,19 |

Key: `event.getOrderId().toString()` (ExecutionEventProducer.java:19)

Payload type: `ExecutionCompletedEvent` (ExecutionEventProducer.java:3; ExecutionCompletedEvent.java)

## Kafka Consumed

| Topic (exact literal) | Consumer Group | Action | file:line |
|---|---|---|---|
| `"order.execution.requested"` | `"execution-service-order.execution.requested-group"` | Calls `ExecutionService.processFill(request)`; routes MARKET orders to `executeMarketFill`, LIMIT orders to `initiateLimitFill` | OrderExecutionConsumer.java:18-19,29 |

**Error handling:** Exponential backoff 1s/2s/4s (max 3 attempts), then Dead Letter Topic (DLT). Deserialization failures are non-retryable and go straight to DLT. (KafkaConfig.java:64-69)

## Redis Keys Owned

Redis is configured with a default cache TTL of 30 seconds (RedisConfig.java:42 — `Duration.ofSeconds(30)`). A `CacheManager` and `RedisTemplate<String, Object>` are registered as beans. However, no `@Cacheable`, `@CachePut`, `@CacheEvict`, or direct `redisTemplate` calls were found in any main-source Java file. The Redis infrastructure is wired but not yet actively used by this service.

**None found in scan** — Redis caching beans are defined but no cache key patterns or active cache annotations were found in src/main.

## REST Endpoints Exposed

| Method | Path | Auth/Role | Handler | file:line |
|---|---|---|---|---|
| GET | `/api/v1/executions/{orderId}` | X-User-Role header required; allowed roles: VIEWER, TRADER, OPERATIONS, ADMIN | `ExecutionController.getExecutionsByOrderId(UUID orderId)` | ExecutionController.java:13,20 |

**Authorization mechanism:** `RoleAuthorizationFilter` (OncePerRequestFilter) reads `X-User-Role` header forwarded by the API gateway. Returns 401 if header is missing, 403 if role is not in the allowed set. `/actuator/**` and OPTIONS requests are excluded. (RoleAuthorizationFilter.java:27-33,66-74)

**Response:** `ExecutionListResponse` containing a list of `ExecutionResponse` objects and a `ResponseMeta`. Throws `ResourceNotFoundException` (404) if no executions exist for the given orderId. (ExecutionService.java:37-39)

## REST Endpoints Called

| Target Service | Method | URL/Path | Circuit Breaker? | file:line |
|---|---|---|---|---|
| market-data-service | GET | `${app.marketdata.base-url}/api/v1/market/{symbol}` (default: `http://localhost:8084/api/v1/market/{symbol}`) | No `@CircuitBreaker` annotation found on call site; Resilience4j config exists in application.yml (default instance, sliding-window 10, 50% threshold, 10s open wait) but is not wired via annotation to this client | MarketDataClient.java:25-26; application.yml:57-67 |

**Timeout:** ConnectTimeout 3000ms, ReadTimeout 3000ms (RestTemplateConfig.java:13-14). Exception is caught and `Optional.empty()` returned on failure (MarketDataClient.java:48-50).

## Entities

### Execution

Table: `execution_schema.executions` (@Table annotation at Execution.java:15)

| Field | Java Type | Column | Constraints | file:line |
|---|---|---|---|---|
| `id` | `UUID` | `id` | @Id, @GeneratedValue(UUID), updatable=false, nullable=false | Execution.java:19-21 |
| `orderId` | `UUID` | `order_id` | nullable=false | Execution.java:23-24 |
| `clientId` | `UUID` | `client_id` | nullable=false | Execution.java:26-27 |
| `symbol` | `String` | `symbol` | nullable=false, length=20 | Execution.java:29-30 |
| `side` | `OrderSide` (enum) | `side` | @Enumerated(EnumType.STRING), nullable=false, length=10 | Execution.java:32-34 |
| `executedQty` | `Integer` | `executed_qty` | nullable=false; DB check: executedQty >= 0 (relaxed from > 0 in V4) | Execution.java:36-37 |
| `executedPrice` | `BigDecimal` | `executed_price` | nullable=false, precision=19, scale=4 | Execution.java:39-40 |
| `fillType` | `FillType` (enum) | `fill_type` | @Enumerated(EnumType.STRING), nullable=false, length=10 | Execution.java:42-44 |
| `fillValue` | `BigDecimal` | `fill_value` | nullable=false, precision=19, scale=4 | Execution.java:46-47 |
| `remainingQuantity` | `Integer` | `remaining_quantity` | nullable=false | Execution.java:49-50 |
| `marketPriceAtFill` | `BigDecimal` | `market_price_at_fill` | nullable=false, precision=19, scale=4 | Execution.java:52-53 |
| `executionVenue` | `String` | `execution_venue` | nullable=false, length=50 | Execution.java:55-56 |
| `executedAt` | `Instant` | `executed_at` | nullable=false | Execution.java:58-59 |

### PendingOrderExecution

Table: `execution_schema.pending_order_executions` (@Table annotation at PendingOrderExecution.java:15)

| Field | Java Type | Column | Constraints | file:line |
|---|---|---|---|---|
| `id` | `UUID` | `id` | @Id, @GeneratedValue(UUID), updatable=false, nullable=false | PendingOrderExecution.java:19-21 |
| `orderId` | `UUID` | `order_id` | nullable=false, unique=true | PendingOrderExecution.java:23-24 |
| `clientId` | `UUID` | `client_id` | nullable=false | PendingOrderExecution.java:26-27 |
| `symbol` | `String` | `symbol` | nullable=false, length=20 | PendingOrderExecution.java:29-30 |
| `side` | `OrderSide` (enum) | `side` | @Enumerated(EnumType.STRING), nullable=false, length=10 | PendingOrderExecution.java:32-34 |
| `orderType` | `OrderType` (enum) | `order_type` | @Enumerated(EnumType.STRING), nullable=false, length=10 | PendingOrderExecution.java:36-38 |
| `quantity` | `Integer` | `quantity` | nullable=false; DB check: quantity > 0 | PendingOrderExecution.java:40-41 |
| `limitPrice` | `BigDecimal` | `limit_price` | nullable=false, precision=18, scale=4 | PendingOrderExecution.java:43-44 |
| `lastPrice` | `BigDecimal` | `last_price` | nullable=false, precision=18, scale=4 | PendingOrderExecution.java:46-47 |
| `orderValue` | `BigDecimal` | `order_value` | nullable=false, precision=18, scale=4 | PendingOrderExecution.java:49-50 |
| `status` | `PendingStatus` (enum) | `status` | @Enumerated(EnumType.STRING), nullable=false, length=10, DB default 'PENDING' | PendingOrderExecution.java:52-54 |
| `createdAt` | `Instant` | `created_at` | nullable=false, updatable=false | PendingOrderExecution.java:56-57 |
| `updatedAt` | `Instant` | `updated_at` | nullable=false | PendingOrderExecution.java:59-60 |

## What This Service Must NEVER Do

1. [CONVENTION -- INFERRED, CONFIRM WITH TEAM] Write to or read from database schemas belonging to other services (e.g., order_schema, risk_schema, portfolio_schema). No code in this service references other schemas; this is a boundary convention not enforced by DDL grants or code checks visible in the scan.

2. [CONVENTION -- INFERRED, CONFIRM WITH TEAM] Use `@FeignClient` or declarative HTTP clients for inter-service calls. Only `RestTemplate` is present (MarketDataClient.java). The absence of `@FeignClient` is not enforced by a compile-time constraint.

3. [CONVENTION -- INFERRED, CONFIRM WITH TEAM] Publish to topics owned by other services (e.g., `order.execution.requested`). The service only publishes to `"execution.completed"` (ExecutionEventProducer.java:14,19). No code enforcement prevents publishing to arbitrary topics.

4. [ENFORCED] Allow unauthenticated access to REST endpoints: `RoleAuthorizationFilter.doFilterInternal` returns HTTP 401 if the `X-User-Role` header is absent or blank, and HTTP 403 if the role is not in the allowed set. Actuator endpoints and OPTIONS are excluded. (RoleAuthorizationFilter.java:48-61,66-74)

5. [ENFORCED] Store an execution with `executed_qty < 0`: V4 migration adds `CHECK (executed_qty >= 0)` enforced at the database level (V4__relax_executed_qty_check_for_rejected_fills.sql:8).

## Key Business Rules

1. **Fill outcome determination** (FillSimulationService.java:37-46): Symbol overrides take precedence over quantity ranges.
   - Symbol `"FAIL"` → `FillType.REJECTED` regardless of quantity (FillSimulationService.java:39)
   - Symbol `"SLOW"` → `FillType.PARTIAL` regardless of quantity (FillSimulationService.java:40)
   - Quantity in [1000, 1003] inclusive → `FillType.REJECTED` (FillSimulationService.java:43)
   - Quantity in [10, 99] inclusive → `FillType.PARTIAL` (FillSimulationService.java:44)
   - All other quantities → `FillType.FULL` (FillSimulationService.java:45)

2. **Partial fill split** (FillSimulationService.java:131-132): Tranche 1 = floor(quantity * 0.60); Tranche 2 = quantity − tranche1Qty. Two `execution.completed` events are emitted. Tranche 2 is executed asynchronously with a random delay of 2000–5000ms (FillSimulationService.java:159).

3. **Rejected fill** (FillSimulationService.java:146-148): `executedQty = 0`, `fillValue = BigDecimal.ZERO`, `remainingQuantity = request.getQuantity()`, `executedPrice = marketPrice`.

4. **Execution venue constant** (FillSimulationService.java:29): All fills are stamped with `executionVenue = "MOCK_ECN"`.

5. **Fill value computation** (FillSimulationService.java:186): `qty * executedPrice` rounded to 2 decimal places using `RoundingMode.HALF_UP`.

6. **LIMIT order condition** (FillSimulationService.java:177-182):
   - BUY: fills immediately if `marketPrice <= limitPrice`
   - SELL: fills immediately if `marketPrice >= limitPrice`
   - If condition not met, order is stored as `PendingStatus.PENDING` in `pending_order_executions`.

7. **LIMIT order price monitoring** (FillSimulationService.java:80-81): Scheduled `@Scheduled(fixedDelay = 2000)` — polls market-data service every 2000ms for all PENDING limit orders, fills those whose condition is now met, and marks them `PendingStatus.PROCESSED`.

8. **Idempotency** (ExecutionService.java:52-58): If an execution or a pending execution already exists for the given `orderId`, the fill request is silently ignored (logged at WARN).

9. **Enum values in use:**
   - `OrderSide`: BUY, SELL (OrderSide.java:3-6)
   - `OrderType`: MARKET, LIMIT (OrderType.java:3-6)
   - `FillType`: FULL, PARTIAL, REJECTED (FillType.java:3-7)
   - `PendingStatus`: PENDING, PROCESSED (PendingStatus.java:3-6)

10. **Default consumer value type** (application.yml:33; KafkaConfig.java:53): Incoming `order.execution.requested` messages are deserialized as `OrderExecutionRequest`. Null `orderId` messages are discarded with a WARN log (OrderExecutionConsumer.java:23-26).
