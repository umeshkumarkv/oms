# client-service -- Service Specification

## Purpose

Manages broker-dealer client profiles, holdings, cash balances, and trading limits. Serves as the authoritative source of record for client state, including KYC status, account type, and default order route. Reacts to execution fills from Kafka to update cash balances and holding positions in real time. Exposes a REST API for profile retrieval and preference updates.

## Port

`8083` (application.yml:43)

> Note: port table is also maintained in service-directory.md.

## Database Schema

Schema name: `client_schema` (application.yml:21, application.yml:18)

Tables (from Flyway migrations):

| Table | Created In | Notes |
|---|---|---|
| `client_schema.clients` | V1__schema.sql:4 | Altered by V3, V4, V5, V6, V7 |
| `client_schema.holdings` | V1__schema.sql:22 | Unique index on (client_id, symbol) |

Column history for `clients`:
- V1: id, name, status, trading_enabled, kyc_status, kyc_expiry_date, margin_call_active, cash_balance, reserved_cash, trading_limit, daily_order_count, daily_order_value, daily_order_limit, max_open_orders, avg_order_qty
- V3: adds `account_type VARCHAR(20) NOT NULL DEFAULT 'CASH'` CHECK (CASH, MARGIN, SHORT_SELLING)
- V4: adds `version BIGINT NOT NULL DEFAULT 0`
- V5: adds `default_route VARCHAR(20) NOT NULL DEFAULT 'NASDAQ'`
- V6: adds `turnover_today NUMERIC(18,2) NOT NULL DEFAULT 0`
- V7: drops `reserved_cash`

> Schema list is also maintained in service-directory.md.

## Kafka Produced

None found in scan. The `KafkaTemplate` bean is defined (KafkaConfig.java:37-39) and wired into the `DeadLetterPublishingRecoverer` for consumer error handling (KafkaConfig.java:63), but `kafkaTemplate.send(...)` is not called anywhere in the application source tree.

## Kafka Consumed

| Topic (exact literal) | Consumer Group | Action | File:Line |
|---|---|---|---|
| `"order.state.changed"` | `"client-service-order.state.changed-group"` | Logs orderId and toState; no state mutation in current implementation | OrderEventConsumer.java:18-23 |
| `"execution.completed"` | `"client-service-execution.completed-group"` | Calls `clientService.processExecutionFill(payload)` -- mutates cash balance, turnoverToday, and holdings based on fill | OrderEventConsumer.java:25-32 |

Consumer error handling: exponential backoff (1 s, 2 s, 4 s; max 3 attempts) then Dead Letter Topic via `DeadLetterPublishingRecoverer` (KafkaConfig.java:60-63).

## Redis Keys Owned

| Key Pattern | TTL | Purpose | File:Line |
|---|---|---|---|
| `client:active-ranked:today:{limit}` | 30 seconds (`TimeUnit.SECONDS`) | Caches the top-N active clients ranked by turnover_today | ClientService.java:57, ClientService.java:109 |
| `client:portfolio:{clientId}` | Evict on write (no TTL set) | Invalidated after execution fill or default-route update | ClientService.java:271 |
| `client:balance:{clientId}` | Evict on write (no TTL set) | Invalidated after execution fill or default-route update | ClientService.java:272 |

Note: `client:portfolio:*` and `client:balance:*` are eviction-only keys in this service (no explicit set with TTL found). They may be populated by another service or a future implementation.

## REST Endpoints Exposed

Base path: `/api/v1/clients` (ClientController.java:21)

| Method | Path | Auth/Role | Handler Method | File:Line |
|---|---|---|---|---|
| GET | `/api/v1/clients` | None found (no @PreAuthorize) | `getAllClients()` | ClientController.java:28 |
| GET | `/api/v1/clients/active?limit={n}` | None found | `getActiveClients(limit)` | ClientController.java:35 |
| GET | `/api/v1/clients/{id}` | None found | `getClient(id)` | ClientController.java:43 |
| GET | `/api/v1/clients/{id}/portfolio` | None found | `getPortfolio(id)` | ClientController.java:50 |
| PATCH | `/api/v1/clients/{id}/default-route` | None found | `updateDefaultRoute(id, request)` | ClientController.java:57 |

Response envelope: all endpoints return `{ "data": ..., "meta": { "timestamp": ..., "requestId": ... } }` (ClientController.java:66-73).

## REST Endpoints Called

| Target Service | Method | URL / Path | Circuit Breaker | File:Line |
|---|---|---|---|---|
| marketdata-service | GET | `${services.marketdata.url}/api/v1/market/instrument/{symbol}` resolves to `http://marketdata-service:8084/api/v1/market/instrument/{symbol}` | Yes -- `@CircuitBreaker(name = "marketdata", fallbackMethod = "getLastPriceFallback")` | MarketDataClient.java:23-36 |

Circuit breaker config for `marketdata` instance: sliding-window-size=5, failure-rate-threshold=50%, wait-duration=10s, permitted-half-open-calls=3 (application.yml:69-72). Fallback returns `null` (MarketDataClient.java:38-41).

## Entities

### Client

Table: `client_schema.clients` (Client.java:16)

| Field | Java Type | Column Name | Constraints | File:Line |
|---|---|---|---|---|
| `id` | `UUID` | `id` | @Id, @GeneratedValue(UUID), updatable=false, nullable=false | Client.java:19-22 |
| `name` | `String` | `name` | nullable=false, length=200 | Client.java:24-25 |
| `accountType` | `AccountType` (enum) | `account_type` | @Enumerated(STRING), nullable=false, length=20 | Client.java:27-29 |
| `status` | `ClientStatus` (enum) | `status` | @Enumerated(STRING), nullable=false, length=20 | Client.java:31-33 |
| `tradingEnabled` | `Boolean` | `trading_enabled` | nullable=false | Client.java:35-36 |
| `kycStatus` | `KycStatus` (enum) | `kyc_status` | @Enumerated(STRING), nullable=false, length=20 | Client.java:38-40 |
| `kycExpiryDate` | `LocalDate` | `kyc_expiry_date` | nullable (no constraint) | Client.java:42-43 |
| `marginCallActive` | `Boolean` | `margin_call_active` | nullable=false | Client.java:45-46 |
| `cashBalance` | `BigDecimal` | `cash_balance` | nullable=false, precision=18, scale=2 | Client.java:48-49 |
| `tradingLimit` | `BigDecimal` | `trading_limit` | nullable (no constraint), precision=18, scale=2 | Client.java:51-52 |
| `dailyOrderCount` | `Integer` | `daily_order_count` | nullable=false | Client.java:54-55 |
| `dailyOrderValue` | `BigDecimal` | `daily_order_value` | nullable=false, precision=18, scale=2 | Client.java:57-58 |
| `dailyOrderLimit` | `BigDecimal` | `daily_order_limit` | nullable (no constraint), precision=18, scale=2 | Client.java:60-61 |
| `maxOpenOrders` | `Integer` | `max_open_orders` | nullable (no constraint) | Client.java:63-64 |
| `avgOrderQty` | `Integer` | `avg_order_qty` | nullable (no constraint) | Client.java:66-67 |
| `defaultRoute` | `DefaultRoute` (enum) | `default_route` | @Enumerated(STRING), nullable=false, length=20 | Client.java:69-71 |
| `turnoverToday` | `BigDecimal` | `turnover_today` | nullable=false, precision=18, scale=2 | Client.java:73-74 |
| `version` | `Long` | `version` | @Version, nullable=false | Client.java:76-78 |

### Holding

Table: `client_schema.holdings` (Holding.java:14)

| Field | Java Type | Column Name | Constraints | File:Line |
|---|---|---|---|---|
| `id` | `UUID` | `id` | @Id, @GeneratedValue(UUID), updatable=false, nullable=false | Holding.java:18-20 |
| `clientId` | `UUID` | `client_id` | nullable=false | Holding.java:22-23 |
| `symbol` | `String` | `symbol` | nullable=false, length=20 | Holding.java:25-26 |
| `quantity` | `Integer` | `quantity` | nullable=false | Holding.java:28-29 |
| `avgPrice` | `BigDecimal` | `avg_price` | nullable=false, precision=18, scale=4 | Holding.java:31-32 |

Note: `Holding` stores `clientId` as a plain `UUID` column (not a JPA `@ManyToOne` association). The FK constraint is enforced at the database level only (V1__schema.sql:23: `REFERENCES client_schema.clients(id)`).

## What This Service Must NEVER Do

1. [CONVENTION -- INFERRED, CONFIRM WITH TEAM] Write to or read from any database schema other than `client_schema`. No cross-schema SQL found in the scanned source; separation is by convention, not enforced by a runtime guard.

2. [CONVENTION -- INFERRED, CONFIRM WITH TEAM] Call any downstream service other than `marketdata-service`. Only one `@Component` HTTP client exists (`MarketDataClient.java`). There is no code-level guard preventing future additions.

3. [CONVENTION -- INFERRED, CONFIRM WITH TEAM] Publish application-level Kafka events. No `kafkaTemplate.send(...)` calls exist in the main source tree. The `KafkaTemplate` bean is wired only for Dead Letter Topic delivery on consumer failure (KafkaConfig.java:63).

4. [CONVENTION -- INFERRED, CONFIRM WITH TEAM] Use JPA lazy-load associations across service boundaries. The `Holding` entity does not use `@ManyToOne` to `Client`; cross-entity lookups are done programmatically. This is a design choice, not an enforced constraint.

## Key Business Rules

1. **Active client limit parameter must be 1--50**: `getActiveClients` throws `ValidationException("VALIDATION_ERROR", "limit must be between 1 and 50")` if `limit < 1 || limit > 50` (ClientService.java:54-56).

2. **Cash limit warning threshold is 10% of dailyOrderLimit**: `cashLimitWarning` is set to `true` when `availableCash < dailyOrderLimit * 0.10` (ClientService.java:86-88, literal `new BigDecimal("0.10")`).

3. **Valid DefaultRoute values are NASDAQ, NYSE, ARCA, BATS**: Enforced by `DefaultRoute` enum (DefaultRoute.java:4) and validated in `updateDefaultRoute` with error message "Valid values: NASDAQ, NYSE, ARCA, BATS" (ClientService.java:188-192).

4. **Valid ClientStatus values are ACTIVE, BLOCKED**: Enforced by `ClientStatus` enum (ClientStatus.java:3-6) and DDL CHECK constraint (V1__schema.sql:8).

5. **Valid KycStatus values are VERIFIED, PENDING, EXPIRED**: Enforced by `KycStatus` enum (KycStatus.java:3-7) and DDL CHECK constraint (V1__schema.sql:9).

6. **Valid AccountType values are CASH, MARGIN, SHORT_SELLING**: Enforced by `AccountType` enum (AccountType.java:3-6) and DDL CHECK constraint (V3__add_account_type.sql:5-6). Default is `CASH`.

7. **BUY fill on non-REJECTED execution deducts fillValue from cashBalance and adds to turnoverToday**: `client.setCashBalance(cashBalance.subtract(fillValue))` and `client.setTurnoverToday(turnoverToday.add(fillValue))` (ClientService.java:224-226).

8. **SELL fill on non-REJECTED execution adds fillValue to cashBalance and adds to turnoverToday**: `client.setCashBalance(cashBalance.add(fillValue))` and `client.setTurnoverToday(turnoverToday.add(fillValue))` (ClientService.java:231-232).

9. **BUY REJECTED fill requires no cash mutation**: Logged but no balance change applied (ClientService.java:221-228).

10. **Holding average price recalculated on upsert using weighted average**: `newAvg = (existingAvgPrice * existingQty + executedPrice * executedQty) / newQty`, scale=4, HALF_UP (ClientService.java:246-248).

11. **Quantity floor on sell-reduce is 0**: `Math.max(0, h.getQuantity() - executedQty)` prevents negative holdings (ClientService.java:264).

12. **Optimistic locking on Client entity**: `@Version Long version` (Client.java:76-78) prevents concurrent write conflicts on the clients row.

13. **Portfolio and balance cache evicted on every execution fill or default-route update**: Keys `client:portfolio:{clientId}` and `client:balance:{clientId}` are deleted (ClientService.java:270-272).

14. **Active client cache TTL is 30 seconds**: Set with `TimeUnit.SECONDS` (ClientService.java:109).
