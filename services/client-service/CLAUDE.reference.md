<!-- plumbline:template claude-reference v1 -->
# client-service — reference

<!-- plumbline:begin reference -->
Last updated: 4586741 (2026-10-30, /pl-onboard, claude-reference v1)

## API

All endpoints are under `ClientController` (`controller/ClientController.java`), base path `/api/v1/clients`.
All responses use the envelope `{ "data": ..., "meta": { "timestamp": "...", "requestId": "..." } }`.
All error responses use `{ "error": { "code": "...", "message": "...", "details": [] }, "meta": { ... } }`.

### ClientController

| Method | Path | Handler | Purpose |
|--------|------|---------|---------|
| GET | `/api/v1/clients` | `getAllClients()` | Return all client profiles (no pagination) |
| GET | `/api/v1/clients/active` | `getActiveClients(?limit=10)` | Return top N clients ranked by today's turnover; limit 1–50, default 10; Redis-cached 30 s |
| GET | `/api/v1/clients/{id}` | `getClient(UUID)` | Return single client profile by UUID |
| GET | `/api/v1/clients/{id}/portfolio` | `getPortfolio(UUID)` | Return client portfolio: cash, holdings with live market prices and unrealised PnL |
| PATCH | `/api/v1/clients/{id}/default-route` | `updateDefaultRoute(UUID, body)` | Update client default exchange route; ADMIN only; body `{"defaultRoute":"NASDAQ\|NYSE\|ARCA\|BATS"}` |

**Note:** `PATCH /api/v1/clients/{id}/reserved-cash` was removed in V7 migration; the path returns 404 with `ENDPOINT_NOT_FOUND`. The role filter exempts it from the role check (legacy system-to-system path), but the controller no longer handles it.

### Role access matrix

| Endpoint | VIEWER | TRADER | OPERATIONS | ADMIN | SYSTEM |
|----------|--------|--------|------------|-------|--------|
| GET /clients | yes | yes | yes | yes | — |
| GET /clients/active | **no** | yes | yes | yes | — |
| GET /clients/{id} | yes | yes | yes | yes | — |
| GET /clients/{id}/portfolio | yes | yes | yes | **no** | yes |
| PATCH /clients/{id}/default-route | no | no | no | yes | — |

### HTTP error codes

| Code | HTTP | Trigger |
|------|------|---------|
| `CLIENT_NOT_FOUND` | 404 | Unknown client UUID |
| `VALIDATION_ERROR` | 400 | `limit` < 1 or > 50 |
| `INVALID_ROUTE` | 400 | Route value not in NASDAQ/NYSE/ARCA/BATS |
| `UNAUTHORIZED` | 401 | Missing or blank `X-User-Role` header |
| `FORBIDDEN` | 403 | Role not permitted for the operation |
| `ENDPOINT_NOT_FOUND` | 404 | Unmapped path |
| `INTERNAL_ERROR` | 500 | Unhandled exception |

## Data

### Table: `client_schema.clients`

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PK, not null | Generated UUID |
| `name` | VARCHAR(200) | not null | Client display name |
| `account_type` | VARCHAR(20) | not null, CHECK IN ('CASH','MARGIN','SHORT_SELLING') | Added V3; default 'CASH' |
| `status` | VARCHAR(20) | not null, CHECK IN ('ACTIVE','BLOCKED') | Client trading status |
| `trading_enabled` | BOOLEAN | not null, default true | Per-client trading switch |
| `kyc_status` | VARCHAR(20) | not null, CHECK IN ('VERIFIED','PENDING','EXPIRED') | KYC state |
| `kyc_expiry_date` | DATE | nullable | KYC expiry |
| `margin_call_active` | BOOLEAN | not null, default false | Margin call flag |
| `cash_balance` | NUMERIC(18,2) | not null, default 0 | Settled cash |
| `trading_limit` | NUMERIC(18,2) | nullable | Optional total trading limit |
| `daily_order_count` | INTEGER | not null, default 0 | Running count for the day |
| `daily_order_value` | NUMERIC(18,2) | not null, default 0 | Running value for the day |
| `daily_order_limit` | NUMERIC(18,2) | nullable | Daily order limit; used for `cashLimitWarning` threshold |
| `max_open_orders` | INTEGER | nullable | Max concurrent open orders |
| `avg_order_qty` | INTEGER | nullable | Historical average order quantity |
| `default_route` | VARCHAR(20) | not null, default 'NASDAQ' | Added V5 |
| `turnover_today` | NUMERIC(18,2) | not null, default 0 | Added V6; cumulative fill value today |
| `version` | BIGINT | not null, default 0 | Added V4; optimistic lock (`@Version`) |

`reserved_cash` column was dropped in V7.

### Table: `client_schema.holdings`

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PK, not null | Generated UUID |
| `client_id` | UUID | not null, FK → clients(id) | Owning client |
| `symbol` | VARCHAR(20) | not null | Ticker symbol |
| `quantity` | INTEGER | not null, CHECK >= 0 | Current position size |
| `avg_price` | NUMERIC(18,4) | not null | Weighted-average cost basis |

Indexes: `idx_holdings_client_id` (non-unique), `uidx_holdings_client_symbol` (unique on `client_id, symbol`).

### Flyway migrations

| Version | Script | Change |
|---------|--------|--------|
| V1 | `V1__schema.sql` | Create `clients` and `holdings` tables with indexes |
| V2 | `V2__seed.sql` | Insert 5 seed clients and their holdings |
| V3 | `V3__add_account_type.sql` | Add `account_type` column (CASH/MARGIN/SHORT_SELLING, default CASH) |
| V4 | `V4__add_client_version.sql` | Add `version BIGINT` for optimistic locking |
| V5 | `V5__add_default_route.sql` | Add `default_route VARCHAR(20)`, default 'NASDAQ' |
| V6 | `V6__add_turnover_today.sql` | Add `turnover_today NUMERIC(18,2)`, default 0 |
| V7 | `V7__remove_reserved_cash.sql` | Drop `reserved_cash` column |

### Enums (Java domain)

- `AccountType`: CASH, MARGIN, SHORT_SELLING
- `ClientStatus`: ACTIVE, BLOCKED
- `KycStatus`: VERIFIED, PENDING, EXPIRED
- `DefaultRoute`: NASDAQ, NYSE, ARCA, BATS

## Integrations

### Outbound HTTP

| Target | URL pattern | Class | Purpose | Circuit breaker |
|--------|------------|-------|---------|-----------------|
| marketdata-service | `${services.marketdata.url}/api/v1/market/instrument/{symbol}` | `client/MarketDataClient.java` | Fetch last traded price per symbol for portfolio valuation | `marketdata` (Resilience4j); fallback returns `null`, portfolio still returned without price fields |

Config key: `services.marketdata.url`, default `http://marketdata-service:8084`.

### Kafka — consumed topics

| Topic | Group ID | Handler method | Action |
|-------|----------|---------------|--------|
| `order.state.changed` | `client-service-order.state.changed-group` | `OrderEventConsumer.consumeOrderStateChanged` | Logs order state transition; no state mutation in current code |
| `execution.completed` | `client-service-execution.completed-group` | `OrderEventConsumer.consumeExecutionCompleted` | Calls `ClientService.processExecutionFill()` — mutates cash balance, turnover, and holdings |

Kafka error handling: `DefaultErrorHandler` with `ExponentialBackOff` (initial 1 s, multiplier 2.0, max 3 attempts) then `DeadLetterPublishingRecoverer` publishes failed messages to the DLT topic.

**Payload fields expected by `processExecutionFill`:** `clientId` (String UUID), `side` ("BUY"/"SELL"), `fillType` (e.g. "FULL_FILL", "PARTIAL_FILL", "REJECTED"), `executedQty` (Number), `executedPrice` (String BigDecimal), `fillValue` (String BigDecimal), `symbol` (String).

### Redis cache

| Key pattern | TTL | Contents | Evicted by |
|------------|-----|----------|-----------|
| `client:active-ranked:today:{limit}` | 30 s | `List<Map<String,String>>` of active client summary | Not explicitly evicted; TTL expires |
| `client:portfolio:{clientId}` | evict-only | (key reserved for portfolio cache) | `updateDefaultRoute`, `processExecutionFill` |
| `client:balance:{clientId}` | evict-only | (key reserved for balance cache) | `updateDefaultRoute`, `processExecutionFill` |

Redis failures (read or write) are caught and logged as WARN; the service continues without caching.

## Configuration

Environment variables and config keys that change behaviour:

| Key / Env Var | Default | Purpose |
|---------------|---------|---------|
| `SPRING_DATASOURCE_URL` | (required) | PostgreSQL JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | `brokerdealer` | Database username |
| `SPRING_DATASOURCE_PASSWORD` | `brokerdealer` | Database password |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker(s) |
| `SPRING_REDIS_HOST` | `localhost` | Redis hostname |
| `SPRING_REDIS_PORT` | `6379` | Redis port |
| `MARKETDATA_SERVICE_URL` | `http://marketdata-service:8084` | Base URL for market-data HTTP client |
| `server.port` | `8083` | HTTP listen port (hard-coded in application.yml) |
| `spring.data.redis.timeout` | `2000ms` | Redis connection timeout |

Resilience4j `marketdata` circuit breaker: sliding window 5, failure threshold 50%, open-state wait 10 s, half-open 3 calls. Configured in `application.yml`, no env var overrides defined.

Actuator endpoints exposed: `health`, `info`, `metrics`. Health probes enabled with `show-details: always`.

Secrets come from environment variables only; no secret store integration is present in the code.

## Tech debt

### TODO/FIXME count
No `TODO` or `FIXME` comments found in source files.

### Other debt

- **`order.state.changed` consumer does nothing:** `OrderEventConsumer.consumeOrderStateChanged` only logs; no cash reservation or state update is implemented. The `reserved_cash` column that would have supported this was dropped in V7. [code: kafka/consumer/OrderEventConsumer.java]
- **No pagination on `GET /api/v1/clients`:** `getAllClients()` calls `findAll()` with no limit; will degrade under large datasets. [code: controller/ClientController.java, service/ClientService.java]
- **`RestTemplate` not configured with timeouts:** `RestTemplateConfig` uses `SimpleClientHttpRequestFactory` with default (infinite) connect/read timeouts; the Resilience4j time-limiter (2 s default) is the only protection. [code: config/RestTemplateConfig.java]
- **`@EnableAsync` declared but no `@Async` methods exist:** `ClientServiceApplication` enables async but no async usage found. [code: ClientServiceApplication.java]
- **Cash limit warning threshold hardcoded:** 10% of `dailyOrderLimit` is a magic number in `ClientService.getActiveClients`. [code: service/ClientService.java]
- **No `GET /api/v1/clients` (list-all) role restriction:** filter permits all five roles including VIEWER, but no guard against returning sensitive fields (though `ClientProfileResponse` deliberately omits financial fields). [code: filter/RoleAuthorizationFilter.java]

## Open questions

1. **`order.state.changed` consumer intent:** The consumer was written but performs no action. Was cash reservation intended to be implemented here and deferred, or is this topic consumed solely for audit/observability? No document in the repository answers this.
2. **DLT topic naming:** `DeadLetterPublishingRecoverer` uses Spring Kafka's default naming convention (`<topic>.DLT`). No downstream DLT consumer is visible in this service; ownership of `order.state.changed.DLT` and `execution.completed.DLT` is unknown.
3. **`client:portfolio:{id}` and `client:balance:{id}` cache keys are evicted but never populated:** `ClientService.getPortfolio` does not write to Redis; the eviction calls are defensive. Whether a future feature or an abandoned implementation is not documented.
4. **Spring Cloud / Spring Security dependency in pom.xml:** `jjwt-api` and friends are declared as dependencies but no JWT parsing code exists in this service. These appear to be a copy-paste artefact from auth-service; removing them is a candidate clean-up.
<!-- plumbline:end reference -->
