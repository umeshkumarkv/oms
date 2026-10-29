<!-- plumbline:template claude-reference v1 -->
# Client Service — reference

<!-- plumbline:begin reference -->
Last updated: 4369a80 (2026-10-28, /pl-onboard, claude-reference v1)

## API

All endpoints are under base path `/api/v1/clients`. Controller: `ClientController`.

| Method | Path | Handler method | Purpose |
|--------|------|---------------|---------|
| GET | `/api/v1/clients` | `getAllClients` | Returns all client profiles (id, name, accountType, status, tradingEnabled, kycStatus, kycExpiryDate, marginCallActive) |
| GET | `/api/v1/clients/active` | `getActiveClients` | Returns top-N clients by today's turnover (default N=10, max 50); includes cash-limit warning flag; Redis-cached 30 s |
| GET | `/api/v1/clients/{id}` | `getClient` | Returns single client profile by UUID; 404 if not found |
| GET | `/api/v1/clients/{id}/portfolio` | `getPortfolio` | Returns full portfolio: client fields + holdings with live prices from marketdata-service |
| PATCH | `/api/v1/clients/{id}/default-route` | `updateDefaultRoute` | Sets the client's default exchange route (NASDAQ, NYSE, ARCA, BATS); ADMIN only |

**Response envelope (all endpoints):**
```json
{
  "data": { ... },
  "meta": { "timestamp": "<ISO-8601>", "requestId": "<UUID>" }
}
```

**Error envelope:**
```json
{
  "error": { "code": "<CODE>", "message": "<text>", "details": [] },
  "meta": { "timestamp": "<ISO-8601>", "requestId": "<UUID>" }
}
```

**Notable behaviour:**
- `/api/v1/clients/active` — query param `limit` (int, 1–50, default 10); `ValidationException` (HTTP 400) if out of range.
- `PATCH /default-route` body: `{ "defaultRoute": "NASDAQ" }` (validated `@NotBlank`); invalid enum value → HTTP 400.
- Portfolio `currentPrice`, `marketValue`, and `unrealisedPnL` are `null` when marketdata-service circuit breaker is open.

**Actuator endpoints** (not role-gated): `GET /actuator/health`, `GET /actuator/info`, `GET /actuator/metrics`

## Data

### Entity: `client_schema.clients`
| Column | Type | Constraints / Notes |
|--------|------|---------------------|
| `id` | UUID | PK, generated |
| `name` | VARCHAR(200) | NOT NULL |
| `account_type` | VARCHAR(20) | NOT NULL, CHECK IN ('CASH','MARGIN','SHORT_SELLING'), default 'CASH' (added V3) |
| `status` | VARCHAR(20) | NOT NULL, CHECK IN ('ACTIVE','BLOCKED') |
| `trading_enabled` | BOOLEAN | NOT NULL, default true |
| `kyc_status` | VARCHAR(20) | NOT NULL, CHECK IN ('VERIFIED','PENDING','EXPIRED') |
| `kyc_expiry_date` | DATE | nullable |
| `margin_call_active` | BOOLEAN | NOT NULL, default false |
| `cash_balance` | NUMERIC(18,2) | NOT NULL, default 0 |
| `trading_limit` | NUMERIC(18,2) | nullable |
| `daily_order_count` | INTEGER | NOT NULL, default 0 |
| `daily_order_value` | NUMERIC(18,2) | NOT NULL, default 0 |
| `daily_order_limit` | NUMERIC(18,2) | nullable; used for cash-limit-warning threshold (10% rule) |
| `max_open_orders` | INTEGER | nullable |
| `avg_order_qty` | INTEGER | nullable |
| `default_route` | VARCHAR(20) | NOT NULL, default 'NASDAQ' (added V5) |
| `turnover_today` | NUMERIC(18,2) | NOT NULL, default 0 (added V6) |
| `version` | BIGINT | NOT NULL, default 0; optimistic-locking `@Version` (added V4) |

Removed columns: `reserved_cash` dropped in V7.

### Entity: `client_schema.holdings`
| Column | Type | Constraints / Notes |
|--------|------|---------------------|
| `id` | UUID | PK, generated |
| `client_id` | UUID | NOT NULL, FK → `clients(id)` |
| `symbol` | VARCHAR(20) | NOT NULL |
| `quantity` | INTEGER | NOT NULL, CHECK >= 0 |
| `avg_price` | NUMERIC(18,4) | NOT NULL; weighted average recalculated on each BUY fill |

Indexes: `idx_holdings_client_id` (client_id), `uidx_holdings_client_symbol` (client_id, symbol) — unique per client per symbol.

### Flyway migrations
| File | Change |
|------|--------|
| `V1__schema.sql` | Creates `clients` and `holdings` tables, indexes |
| `V2__seed.sql` | Inserts 5 seed clients with holdings (dev data) |
| `V3__add_account_type.sql` | Adds `account_type` column (NOT NULL, default 'CASH') |
| `V4__add_client_version.sql` | Adds `version` BIGINT for optimistic locking |
| `V5__add_default_route.sql` | Adds `default_route` VARCHAR(20), default 'NASDAQ' |
| `V6__add_turnover_today.sql` | Adds `turnover_today` NUMERIC(18,2), default 0 |
| `V7__remove_reserved_cash.sql` | Drops `reserved_cash` column |

## Integrations

### Outbound HTTP
- **marketdata-service** — `GET http://<MARKETDATA_SERVICE_URL>/api/v1/market/instrument/{symbol}`
  - Called by `MarketDataClient.getLastPrice()` for each holding in `getPortfolio`.
  - Wrapped with Resilience4j circuit breaker (`marketdata` instance): sliding window 5, failure threshold 50%, open-state wait 10 s, half-open 3 calls.
  - Fallback: returns `null` (portfolio continues to render without price data).
  - File: `client/MarketDataClient.java`

### Kafka (consumed topics)
| Topic | Group ID | Handler | Action |
|-------|----------|---------|--------|
| `order.state.changed` | `client-service-order.state.changed-group` | `OrderEventConsumer.consumeOrderStateChanged` | Logs event only — no state mutation (stub) |
| `execution.completed` | `client-service-execution.completed-group` | `OrderEventConsumer.consumeExecutionCompleted` | Calls `ClientService.processExecutionFill`; updates cash, turnover, holdings |

Kafka produces no topics from this service (producer factory is configured but unused in application code).

DLT: failed messages after 3 retries (exponential back-off 1 s/2 s/4 s) are published to `<topic>.DLT`. Config: `KafkaConfig.java`.

### Redis cache
- **Provider:** Spring Data Redis, `RedisTemplate<String, Object>` with `GenericJackson2JsonRedisSerializer`.
- **Keys used at runtime:**
  - `client:active-ranked:today:<limit>` — active-client ranking list, TTL 30 s
  - `client:portfolio:<clientId>` — evicted on fill / default-route update
  - `client:balance:<clientId>` — evicted on fill / default-route update
- `RedisCacheManager` default TTL: 30 s.
- Redis errors are swallowed (warn log); service continues without cache.

## Configuration

| Key / Env Var | Default | Behaviour |
|---------------|---------|-----------|
| `SPRING_DATASOURCE_URL` | none (required) | PostgreSQL JDBC URL; set to `jdbc:postgresql://postgres:5432/brokerdealer?currentSchema=client_schema` in docker-compose |
| `SPRING_DATASOURCE_USERNAME` | `brokerdealer` | DB username |
| `SPRING_DATASOURCE_PASSWORD` | `brokerdealer` | DB password — supply from secret store in production |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker(s) |
| `SPRING_REDIS_HOST` | `localhost` | Redis host |
| `SPRING_REDIS_PORT` | `6379` | Redis port |
| `MARKETDATA_SERVICE_URL` | `http://marketdata-service:8084` | Base URL for market-data HTTP calls |
| `JWT_SECRET` | _(set in docker-compose)_ | Declared in docker-compose env; not parsed by this service — forwarded to gateway only |
| `spring.jpa.hibernate.ddl-auto` | `validate` | Hibernate never modifies schema |
| `spring.flyway.schemas` | `client_schema` | Flyway manages only this schema |
| `resilience4j.circuitbreaker.instances.marketdata.*` | see `application.yml` | Controls market-data circuit breaker thresholds |
| `resilience4j.timelimiter.instances.default.timeout-duration` | `2s` | Time limiter for default circuit breaker instance |
| `management.endpoints.web.exposure.include` | `health,info,metrics` | Actuator exposure |

Secrets (DB password, JWT secret) come from docker-compose `environment:` in dev; production delivery mechanism is not defined in this repository.

## Tech debt

**TODO / FIXME count: 0** — no TODO or FIXME comments found in `src/main/`.

**Other debt:**
- `OrderEventConsumer.consumeOrderStateChanged` is a stub: it receives `order.state.changed` events, logs them, and takes no action. The handler is a registered listener consuming the topic with no effect (`kafka/consumer/OrderEventConsumer.java` line 20–23).
- `UpdateDefaultRouteResponse` returns only `clientId` and `defaultRoute`; no timestamp or audit trail in the response.
- `PATCH /api/v1/clients/{id}/reserved-cash` is referenced in the `RoleAuthorizationFilter` exemption list but no such endpoint exists in `ClientController`. The path was presumably removed when V7 dropped the `reserved_cash` column.
- `JWT_SECRET` is present in docker-compose env for client-service but the service performs no JWT parsing; the value is unused.
- Active-client cache key includes `today` in its name but has no daily reset mechanism; `turnoverToday` is only ever incremented by fills and is never zeroed by a scheduled job in this service.

## Open questions

1. **`PATCH /reserved-cash` endpoint removal:** The `RoleAuthorizationFilter` exempts `PATCH` requests whose path contains `/reserved-cash` from role checks (intended as a system-to-system bypass for order-service). This endpoint does not exist in `ClientController`. It is unclear whether the endpoint was removed but the filter exemption was left in place, or whether the endpoint is expected in a future migration.

2. **`turnoverToday` daily reset:** The `turnover_today` column is incremented on every execution fill but no scheduler or cron job in this service resets it to 0 at end of day. Whether the reset is owned by another service, a database job, or is simply missing is not documented.

— Plumbline 2.8.7 · /pl-onboard · claude-reference v1 · commit 4369a80 · judge pending · AI-drafted, posted after SME review
<!-- plumbline:end reference -->
