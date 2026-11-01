<!-- plumbline:template claude-reference v1 -->
# order-service — reference

<!-- plumbline:begin reference -->
Last updated: 39916bd (2026-10-30, /pl-onboard, claude-reference v1)

## API

### OrderController — base path `/api/v1/orders`

| Method | Path | Handler | Purpose |
|--------|------|---------|---------|
| POST | `/api/v1/orders` | `OrderController.submitOrder` | Submit a new order; runs L1+L2 validation, persists in NEW state, publishes risk evaluation event. Returns 202 Accepted. Requires `X-User-Role: TRADER`. |
| GET | `/api/v1/orders` | `OrderController.getOrders` | List orders with optional filters: `status`, `clientId`, `side`, `timeInForce`, `createdAfter`; paginated (`page`, `pageSize`; default 0/50, max 200). |
| GET | `/api/v1/orders/status-counts` | `OrderController.getStatusCounts` | Return count of orders per status for today (ET date boundary); served from Redis cache (15 s TTL). |
| GET | `/api/v1/orders/{id}` | `OrderController.getOrderById` | Get a single order by UUID; enriched with execution fills from execution-service. Redis cache-aside on `order:state:{id}`. TRADER callers restricted to their own clientId. |

### DashboardController — base path `/api/v1/dashboard`

| Method | Path | Handler | Purpose |
|--------|------|---------|---------|
| GET | `/api/v1/dashboard/risk-alerts` | `DashboardController.getRiskAlerts` | Return risk alert counts: clientsNearLimit, cashRejectCount, largeOrdersAwaitingReview, pendingVenueAck. Cached 15 s. |
| GET | `/api/v1/dashboard/summary` | `DashboardController.getTradingSummary` | Return today's trading summary: turnoverToday, ordersToday, fillRate, buySideNotional, sellSideNotional. Cached 15 s. |
| GET | `/api/v1/dashboard/health` | `DashboardController.getSystemHealth` | Aggregates `/actuator/health` from all 8 registered services in parallel (virtual threads, 500 ms timeout). |

**Total: 7 endpoints, 2 controllers.**

**Role permission matrix (enforced by `RoleAuthorizationFilter`):**

| Endpoint | TRADER | VIEWER | OPERATIONS | ADMIN |
|----------|--------|--------|------------|-------|
| POST /api/v1/orders | YES | NO | NO | NO |
| GET /api/v1/orders/** | YES | YES | YES | YES |
| GET /api/v1/dashboard/** | YES | NO | YES | YES |
| /actuator/** | (no filter) | (no filter) | (no filter) | (no filter) |

**Common headers:**
- `X-User-Role` — required on all non-actuator requests; enforced by filter.
- `X-User-Id` — optional UUID; used by POST (sets `createdBy`) and GET /{id} (TRADER isolation check).

**Error response envelope:**
```json
{
  "error": { "code": "...", "message": "...", "details": [] },
  "meta":  { "timestamp": "...", "requestId": "..." }
}
```

**HTTP status codes:**
- 202 — order accepted (POST)
- 400 — L1 bean or syntactic validation failure
- 403 — role not permitted or TRADER accessing another client's order
- 404 — order not found
- 422 — L2 semantic/external validation failure (SemanticValidationException)
- 503 — downstream service unavailable (circuit breaker open)
- 500 — unexpected error

## Data

### Table: `order_schema.orders`

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| id | UUID | PK, NOT NULL, DEFAULT gen_random_uuid() | |
| client_order_id | VARCHAR(100) | nullable | Client-assigned idempotency key |
| client_id | UUID | NOT NULL | FK concept to client-service |
| symbol | VARCHAR(20) | NOT NULL | |
| side | VARCHAR(10) | NOT NULL, CHECK IN ('BUY','SELL','SHORT_SELL') | V6 extended from BUY/SELL |
| order_type | VARCHAR(10) | NOT NULL, CHECK IN ('MARKET','LIMIT') | |
| time_in_force | VARCHAR(10) | NOT NULL, CHECK IN ('DAY','GTD') | V4 renamed GTC→GTD |
| quantity | INTEGER | NOT NULL, CHECK > 0 | |
| limit_price | NUMERIC(19,4) | nullable | V3 precision fix; required for LIMIT, null for MARKET |
| last_price | NUMERIC(19,4) | nullable | V5: market last price at submission time |
| order_value | NUMERIC(19,4) | nullable | V5: pre-computed (limitPrice or lastPrice) × quantity |
| status | VARCHAR(30) | NOT NULL, CHECK IN (11 values) | See OrderStatus enum |
| rejection_reason | VARCHAR(500) | nullable | Populated on RISK_REJECTED or REJECTED |
| expiry_date | DATE | nullable | Set for GTD orders |
| expiry_applied | VARCHAR(10) | nullable, CHECK IN ('DEFAULT','TRADER') | DEFAULT=system-assigned, TRADER=explicit |
| created_at | TIMESTAMPTZ | NOT NULL, DEFAULT now() | Set by @PrePersist |
| updated_at | TIMESTAMPTZ | NOT NULL, DEFAULT now() | Updated by @PreUpdate |
| created_by | UUID | NOT NULL | Auth-service userId from X-User-Id header |
| routing_destination | VARCHAR(50) | nullable | V7: execution venue; defaults to "NYSE" |

**Indexes:** `idx_orders_client_id`, `idx_orders_status`, `idx_orders_symbol`, `idx_orders_created_at`

**OrderStatus enum (11 values):** NEW → VALIDATED → RISK_CHECK_PASSED → ACCEPTED → SENT_TO_EXECUTION → PARTIALLY_FILLED / FILLED; RISK_REJECTED, CANCELLED, REJECTED, EXPIRED (terminal).

### Flyway migrations

| Version | File | Change |
|---------|------|--------|
| V1 | V1__schema.sql | Create `order_schema.orders` table with indexes; initial side CHECK allows BUY/SELL only; TIF allows DAY/GTC |
| V2 | V2__seed.sql | No-op; comment only — orders are created via API |
| V3 | V3__fix_limitprice_precision.sql | `limit_price` precision NUMERIC(18,4) → NUMERIC(19,4) [SCRUM-41] |
| V4 | V4__rename_gtc_to_gtd.sql | Drop old TIF constraint, migrate existing GTC rows to GTD, add CHECK IN ('DAY','GTD') [BRD Section 14] |
| V5 | V5__add_last_price_and_order_value.sql | Add `last_price` and `order_value` columns (NUMERIC(19,4)) |
| V6 | V6__add_short_sell_side.sql | Dynamically drops pg-generated side constraint, adds CHECK IN ('BUY','SELL','SHORT_SELL') |
| V7 | V7__add_routing_destination_to_orders.sql | Add `routing_destination` VARCHAR(50) |

## Integrations

### Outbound HTTP (RestTemplate + Resilience4j circuit breaker)

All three clients use `JdkClientHttpRequestFactory` with 2 s connect/read timeout and circuit-breaker (sliding window 10, failure threshold 50 %, open wait 10 s, half-open 3 calls).

| Client | URL config key | Endpoint called | Purpose |
|--------|---------------|-----------------|---------|
| `ClientServiceClient` | `app.services.client-service.url` (default `http://client-service:8083`) | `GET /api/v1/clients/{clientId}/portfolio` | Fetch client status, KYC, cashBalance, holdings, maxOpenOrders |
| `MarketDataServiceClient` | `app.services.marketdata-service.url` (default `http://marketdata-service:8084`) | `GET /api/v1/market/hours` | Check market open/closed |
| `MarketDataServiceClient` | same | `GET /api/v1/market/instrument/{symbol}` | Fetch instrument tradable/halted/lastPrice |
| `MarketDataServiceClient` | same | `GET /api/v1/market/holidays` | Fetch US market holiday list for GTD expiry validation |
| `ExecutionServiceClient` | `app.services.execution-service.url` (default `http://execution-service:8086`) | `GET /api/v1/executions/{orderId}` | Fetch fills for order detail response |

**Fallback behaviour:**
- `ClientServiceClient` / `MarketDataServiceClient`: throws `ServiceUnavailableException` (503) when circuit breaker is open.
- `ExecutionServiceClient`: returns empty fills list when circuit breaker is open (non-blocking degradation).
- `InsufficientBuyingPowerException` is excluded from client-service circuit-breaker failure counting.

**Health aggregation:** `HealthAggregatorService` calls `GET {serviceUrl}/actuator/health` for 8 services (auth, order, client, marketdata, risk, execution, audit, notification) in parallel using virtual thread executor, with 500 ms timeout.

### Kafka topics produced (by `OrderEventProducer`)

| Topic | Key | Event class | When published |
|-------|-----|-------------|----------------|
| `order.state.changed` | `orderId` (String) | `OrderStateChangedEvent` | On every state transition (NEW, VALIDATED, RISK_CHECK_PASSED, ACCEPTED, SENT_TO_EXECUTION, RISK_REJECTED, FILLED, PARTIALLY_FILLED, REJECTED, EXPIRED) |
| `risk.evaluation.requested` | `orderId` (String) | `RiskEvaluationRequestedEvent` | Immediately after order persisted in NEW state; carries full portfolio snapshot |
| `order.execution.requested` | `orderId` (String) | `OrderExecutionRequestedEvent` | When order transitions to SENT_TO_EXECUTION (risk passed path) |

### Kafka topics consumed (by `RiskResultConsumer`)

| Topic | Group ID | Payload | Action |
|-------|----------|---------|--------|
| `risk.evaluation.result` | `order-service-risk.evaluation.result-group` | `RiskEvaluationResultEvent` (result: PASSED\|REJECTED) | PASSED → drive NEW→VALIDATED→RISK_CHECK_PASSED→ACCEPTED→SENT_TO_EXECUTION + publish execution requested; REJECTED → NEW→RISK_REJECTED |
| `execution.completed` | `order-service-execution.completed-group` | Map with `orderId`, `fillType` (FULL\|PARTIAL\|REJECTED) | FULL→FILLED, PARTIAL→PARTIALLY_FILLED, REJECTED→REJECTED |

**Error handling:** 3 retries with exponential backoff (1 s, 2 s, 4 s) then `DeadLetterPublishingRecoverer` routes message to DLT.

### Redis cache

| Cache key pattern | Content | TTL | Used by |
|------------------|---------|-----|---------|
| `order:state:{orderId}` | Serialized `Order` entity | No TTL (evicted on state change) | `OrderService.getOrderById`, `OrderStateMachineService` |
| `dashboard:status-counts:today` | `Map<String,Integer>` of status counts | 15 s | `OrderService.getStatusCountsForToday` |
| `dashboard:risk-alerts:today` | `Map<String,Integer>` of alert counts | 15 s | `DashboardService.getRiskAlerts` |
| `dashboard:summary:today` | `Map<String,Object>` of trading summary | 15 s | `DashboardService.getTradingSummary` |

Redis failures are silently swallowed; service continues without cache (cache-aside pattern, non-critical path).

## Configuration

| Key / Env var | Default | Purpose |
|---------------|---------|---------|
| `SPRING_DATASOURCE_URL` | (required) | PostgreSQL JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | `brokerdealer` | DB username |
| `SPRING_DATASOURCE_PASSWORD` | `brokerdealer` | DB password |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker address |
| `SPRING_REDIS_HOST` | `localhost` | Redis host |
| `SPRING_REDIS_PORT` | `6379` | Redis port |
| `CLIENT_SERVICE_URL` | `http://client-service:8083` | Base URL for client-service HTTP client |
| `MARKETDATA_SERVICE_URL` | `http://marketdata-service:8084` | Base URL for market-data-service HTTP client |
| `EXECUTION_SERVICE_URL` | `http://execution-service:8086` | Base URL for execution-service HTTP client |
| `dashboard.risk-alerts.clients-near-limit-threshold` | `800000` | Notional threshold (BigDecimal) for clients-near-limit alert |
| `dashboard.risk-alerts.large-order-notional-threshold` | `500000` | Notional threshold (BigDecimal) for large-orders-awaiting-review alert |
| `app.health.timeout-ms` | `500` | Connect + read timeout (ms) for health aggregation RestTemplate |

**Service health URLs (used only by HealthAggregatorService — no routing logic):**

| Key | Default |
|-----|---------|
| `AUTH_SERVICE_URL` | `http://auth-service:8081` |
| `ORDER_SERVICE_URL` | `http://localhost:8082` |
| `RISK_SERVICE_URL` | `http://risk-service:8085` |
| `AUDIT_SERVICE_URL` | `http://audit-service:8087` |
| `NOTIFICATION_SERVICE_URL` | `http://notification-service:8088` |

**Resilience4j circuit-breaker defaults** (all instances): sliding window 10, failure threshold 50 %, wait in open state 10 s, half-open 3 calls, timeout 2 s.

**Scheduling (MarketCloseScheduler):** `cron = "0 0 16 * * MON-FRI"` (market close, ET); `cron = "0 30 9 * * MON-FRI"` (market open recovery, ET). Clock bean is `Clock.system(America/New_York)`.

## Tech debt

No TODO or FIXME comments found in any source file under `src/main/` or `src/test/`.

**Notable observations (not TODO-tagged):**

- JaCoCo excludes `com/brokerdealer/orderservice/kafka/**` with comment "Kafka stubs — not implemented in this story (SCRUM-42)"; however the Kafka producer and consumer classes are fully implemented in the current codebase — the exclusion comment is stale (`pom.xml`).
- `RiskEvaluationRequestedEvent.timeInForce` Javadoc says `"DAY" | "GTC"` but GTC was renamed GTD in V4 — the comment is stale (`dto/kafka/RiskEvaluationRequestedEvent.java`).
- The holiday list in `MarketCloseScheduler` is hard-coded for calendar year 2026 only; it will be incomplete for any other year (`service/MarketCloseScheduler.java`).
- `jjwt` (api, impl, jackson) is declared as a dependency but no JWT parsing/verification code exists in this service — the dependency appears unused.

## Open questions

1. Who provides the DLT consumer for Kafka messages that exhaust retries? No DLT consumer exists in this service.
2. The hard-coded 2026 US market holiday list in `MarketCloseScheduler` will be wrong in 2027+. Is the intention for market-data-service to own holidays for the scheduler too, or will the list be updated annually?
3. `jjwt` is declared but not used — is this an intentional placeholder for future in-service token verification, or a leftover dependency?
<!-- plumbline:end reference -->
