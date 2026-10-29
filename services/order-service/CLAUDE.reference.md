<!-- plumbline:template claude-reference v1 -->
# Order Service — reference

<!-- plumbline:begin reference -->
Last updated: 4369a80 (2026-10-28, /pl-onboard, claude-reference v1)

## API

### OrderController — `/api/v1/orders`

| Method | Path | Handler | Purpose |
|--------|------|---------|---------|
| POST | `/api/v1/orders` | `OrderController.submitOrder` | Submit a new order; runs L1+L2 validation, persists as NEW, publishes Kafka events; returns 202 Accepted |
| GET | `/api/v1/orders` | `OrderController.getOrders` | Paginated order list with optional filters: `status`, `clientId`, `side`, `timeInForce`, `createdAfter` (ISO-8601 UTC), `page`, `pageSize` (max 200) |
| GET | `/api/v1/orders/status-counts` | `OrderController.getStatusCounts` | Count of today's orders (ET) grouped by status; Redis cache-aside with 15s TTL |
| GET | `/api/v1/orders/{id}` | `OrderController.getOrderById` | Single order with fills fetched from execution-service; Redis cache-aside; TRADER role enforces caller-owns-order check |

### DashboardController — `/api/v1/dashboard`

| Method | Path | Handler | Purpose |
|--------|------|---------|---------|
| GET | `/api/v1/dashboard/risk-alerts` | `DashboardController.getRiskAlerts` | Today's risk alert counts: clients near notional limit, cash-reject count, large orders awaiting review, pending venue acks; Redis cache-aside 15s TTL |
| GET | `/api/v1/dashboard/summary` | `DashboardController.getTradingSummary` | Today's trading summary: turnover, order count, fill rate, buy/sell notional; Redis cache-aside 15s TTL |
| GET | `/api/v1/dashboard/health` | `DashboardController.getSystemHealth` | Fan-out health probe to all 8 OMS services via `/actuator/health`; returns UP / DEGRADED / DOWN per service |

**Request headers used:**
- `X-User-Role` — required by `RoleAuthorizationFilter` for all non-actuator endpoints
- `X-User-Id` — optional UUID forwarded by gateway; used as `createdBy` on submission and for TRADER ownership check on GET

**Error shape:** `{ "error": { "code", "message", "details": [] }, "meta": { "timestamp", "requestId" } }`

**Status filter alias:** `status=OPEN` expands to all non-terminal statuses (excludes RISK_REJECTED, FILLED, CANCELLED, REJECTED, EXPIRED).

## Data

### Table: `order_schema.orders`

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| id | UUID | PK, NOT NULL | Generated via `gen_random_uuid()` |
| client_order_id | VARCHAR(100) | nullable | Caller-supplied idempotency key |
| client_id | UUID | NOT NULL | FK to client-service (not enforced in DB) |
| symbol | VARCHAR(20) | NOT NULL | Equity ticker |
| side | VARCHAR(10) | NOT NULL, CHECK IN ('BUY','SELL','SHORT_SELL') | V6 added SHORT_SELL |
| order_type | VARCHAR(10) | NOT NULL, CHECK IN ('MARKET','LIMIT') | |
| time_in_force | VARCHAR(10) | NOT NULL, CHECK IN ('DAY','GTD') | V4 renamed GTC→GTD |
| quantity | INTEGER | NOT NULL, CHECK > 0 | |
| limit_price | NUMERIC(19,4) | nullable | Required for LIMIT; forbidden for MARKET; precision raised in V3 |
| last_price | NUMERIC(19,4) | nullable | Snapshot of instrument lastPrice at submission; added V5 |
| order_value | NUMERIC(19,4) | nullable | Pre-computed: limitPrice×qty or lastPrice×qty; added V5 |
| status | VARCHAR(30) | NOT NULL, CHECK enum | One of 11 states (see OrderStatus enum) |
| rejection_reason | VARCHAR(500) | nullable | Set on RISK_REJECTED |
| expiry_date | DATE | nullable | GTD expiry date |
| expiry_applied | VARCHAR(10) | nullable, CHECK IN ('DEFAULT','TRADER') | Records who set expiry |
| created_at | TIMESTAMPTZ | NOT NULL | Set by `@PrePersist` |
| updated_at | TIMESTAMPTZ | NOT NULL | Updated by `@PreUpdate` |
| created_by | UUID | NOT NULL | User UUID from `X-User-Id` header |
| routing_destination | VARCHAR(50) | nullable | Added V7; defaults to 'NYSE' if not provided |

**Indexes:** `idx_orders_client_id`, `idx_orders_status`, `idx_orders_symbol`, `idx_orders_created_at`

**OrderStatus enum values:** NEW, VALIDATED, RISK_CHECK_PASSED, RISK_REJECTED, ACCEPTED, SENT_TO_EXECUTION, PARTIALLY_FILLED, FILLED, CANCELLED, REJECTED, EXPIRED

### Flyway migrations

| Version | File | Change |
|---------|------|--------|
| V1 | `V1__schema.sql` | Creates `order_schema.orders` table and 4 indexes |
| V2 | `V2__seed.sql` | Seed data |
| V3 | `V3__fix_limitprice_precision.sql` | Raises `limit_price` from NUMERIC(18,4) to NUMERIC(19,4) (SCRUM-41) |
| V4 | `V4__rename_gtc_to_gtd.sql` | Renames GTC → GTD in constraint and existing rows (BRD Section 14) |
| V5 | `V5__add_last_price_and_order_value.sql` | Adds `last_price` and `order_value` columns |
| V6 | `V6__add_short_sell_side.sql` | Extends side CHECK to include SHORT_SELL |
| V7 | `V7__add_routing_destination_to_orders.sql` | Adds `routing_destination VARCHAR(50)` |

## Integrations

### Outbound HTTP (synchronous, circuit-breaker protected)

| Client | Endpoint called | Circuit breaker | Fallback |
|--------|----------------|-----------------|---------|
| `ClientServiceClient` | `GET {client-service}/api/v1/clients/{id}/portfolio` | `client-service` (window=10, threshold=50%, wait=10s, half-open=3; ignores InsufficientBuyingPowerException) | throws `ServiceUnavailableException` |
| `MarketDataServiceClient` | `GET {marketdata-service}/api/v1/market/instrument/{symbol}` | `marketdata-service` (same params) | throws `ServiceUnavailableException` |
| `MarketDataServiceClient` | `GET {marketdata-service}/api/v1/market/hours` | `marketdata-service` | throws `ServiceUnavailableException` |
| `MarketDataServiceClient` | `GET {marketdata-service}/api/v1/market/holidays` | `marketdata-service` | throws `ServiceUnavailableException` |
| `ExecutionServiceClient` | `GET {execution-service}/api/v1/executions/{orderId}` | `execution-service` | returns empty list (fills not available) |
| `HealthAggregatorService` | `GET {serviceUrl}/actuator/health` (for each of 8 services) | none — parallel virtual-thread fan-out with 500ms timeout | returns DEGRADED or DOWN entry |

HTTP connect timeout: 2000ms; read timeout: 2000ms [code: HttpClientConfig.java]
Resilience4j time-limiter: 2s per call for all three circuit-breaker instances [application.yml]

### Kafka topics

**Produced:**

| Topic | Event class | Key | Trigger |
|-------|-------------|-----|---------|
| `order.state.changed` | `OrderStateChangedEvent` | orderId | Every state transition (NEW, VALIDATED, RISK_CHECK_PASSED, ACCEPTED, SENT_TO_EXECUTION, RISK_REJECTED, FILLED, PARTIALLY_FILLED, REJECTED, EXPIRED) |
| `risk.evaluation.requested` | `RiskEvaluationRequestedEvent` | orderId | On order submission (after persist); carries portfolio snapshot for risk engine (SCRUM-119) |
| `order.execution.requested` | `OrderExecutionRequestedEvent` | orderId | After risk passes and order reaches SENT_TO_EXECUTION |

**Consumed:**

| Topic | Group ID | Handler |
|-------|----------|---------|
| `risk.evaluation.result` | `order-service-risk.evaluation.result-group` | `RiskResultConsumer.consumeRiskResult` — routes to `transitionOnRiskPassed` or `transitionOnRiskRejected` |
| `execution.completed` | `order-service-execution.completed-group` | `RiskResultConsumer.consumeExecutionCompleted` — routes on fillType: FULL→FILLED, PARTIAL→PARTIALLY_FILLED, REJECTED→REJECTED |

**Dead-letter topics (auto-created):** `order.state.changed.DLT`, `risk.evaluation.requested.DLT`, `execution.completed.DLT`
**Error handling:** exponential backoff 1s/2s/4s (3 attempts) then publish to DLT [code: KafkaConfig.java]

### Redis caches

| Key pattern | Content | TTL | Evicted by |
|-------------|---------|-----|------------|
| `order:state:{orderId}` | Serialized `Order` entity | No TTL (indefinite) | State transitions and expiry scheduler |
| `dashboard:status-counts:today` | `Map<String,Integer>` status counts | 15s | TTL expiry |
| `dashboard:risk-alerts:today` | `Map<String,Integer>` alert counts | 15s | TTL expiry |
| `dashboard:summary:today` | `Map<String,Object>` trading summary | 15s | TTL expiry |

Redis failures are swallowed (warn + DB fallback) for all dashboard/status-count paths.

## Configuration

| Key / Env var | Default | Purpose |
|---------------|---------|---------|
| `SPRING_DATASOURCE_URL` | — (required) | PostgreSQL JDBC URL; set to `jdbc:postgresql://postgres:5432/brokerdealer?currentSchema=order_schema` in Docker |
| `SPRING_DATASOURCE_USERNAME` | `brokerdealer` | DB username |
| `SPRING_DATASOURCE_PASSWORD` | `brokerdealer` | DB password |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker address |
| `SPRING_REDIS_HOST` | `localhost` | Redis host |
| `SPRING_REDIS_PORT` | `6379` | Redis port |
| `CLIENT_SERVICE_URL` | `http://client-service:8083` | Base URL for client-service HTTP calls |
| `MARKETDATA_SERVICE_URL` | `http://marketdata-service:8084` | Base URL for marketdata-service HTTP calls |
| `EXECUTION_SERVICE_URL` | `http://execution-service:8086` | Base URL for execution-service HTTP calls |
| `AUTH_SERVICE_URL` / `ORDER_SERVICE_URL` / `RISK_SERVICE_URL` / `AUDIT_SERVICE_URL` / `NOTIFICATION_SERVICE_URL` | per-service defaults | Used only by `HealthAggregatorService` health probes |
| `JWT_SECRET` | — | Injected by Docker; not used directly by this service (gateway validates JWT) |
| `dashboard.risk-alerts.clients-near-limit-threshold` | `800000` | Notional threshold for "clients near limit" risk alert |
| `dashboard.risk-alerts.large-order-notional-threshold` | `500000` | Notional threshold for "large orders awaiting review" risk alert |
| `app.health.timeout-ms` | `500` | Timeout for each health-probe HTTP call |

**Secrets source:** `SPRING_DATASOURCE_PASSWORD` and `JWT_SECRET` are supplied as Docker environment variables. No secrets are stored in application.yml or committed source files.

## Tech debt

**TODO/FIXME count:** 0 (grep of `src/main` returned no matches)

**Other debt:**
- Kafka stubs in `kafka/**` are excluded from JaCoCo explicitly; the comment in pom.xml references SCRUM-42 as the story that will implement them [code: pom.xml]
- `IDEMPOTENT_SKIP_STATES_FOR_RISK_RESULT` in `OrderStateMachineService` includes `SENT_TO_EXECUTION` solely as an idempotency guard for risk results; the comment warns it must NOT be reused in `consumeExecutionCompleted` (SCRUM-48) [code: OrderStateMachineService.java]
- US market holiday list in `MarketCloseScheduler` is hard-coded for 2026 only; it will need updating each year [code: MarketCloseScheduler.java]
- `V1__schema.sql` side CHECK still reads `IN ('BUY','SELL')` (two values); V6 migration adds SHORT_SELL by dropping and re-adding the constraint dynamically — the V1 DDL was not backfilled [code: V1__schema.sql, V6__add_short_sell_side.sql]

## Open questions

- No `DuplicateOrderException` handler is wired in `GlobalExceptionHandler`; the exception class exists but is never thrown — unclear whether duplicate detection on `clientOrderId` is planned or was removed.
- `SelfTradeException` class exists but is never thrown — self-trade prevention logic is not implemented yet.
- The `hasCrossingOpenOrder` flag is included in `risk.evaluation.requested` but there is no code in this service that acts on it beyond passing it to the risk engine.
- Health check timeout (`app.health.timeout-ms: 500`) is read by `HealthCheckProperties` but the actual RestTemplate read timeout is set separately in `HealthCheckConfig`; if they diverge, the RestTemplate timeout governs.
- `execution.completed.DLT` is declared in docker-compose but no consumer exists in this service for DLT replay.

— Plumbline 2.8.7 · /pl-onboard · claude-reference v1 · commit 4369a80 · judge pending · AI-drafted, posted after SME review
<!-- plumbline:end reference -->
