# marketdata-service -- Service Specification

## Purpose

marketdata-service is the read-heavy market-data hub of the OMS broker-dealer platform. It owns the `market_schema` PostgreSQL schema and serves real-time and historical instrument prices, market hours, market holidays, market indices, market movers, per-user watchlists, and intraday tick history to frontend clients and peer services. The service runs a 1-second scheduled price-simulation engine (`PriceTickService`) that random-walks all non-halted instrument prices, stores ticks, broadcasts real-time updates via WebSocket, and invalidates Redis caches after each tick. Admin users can trigger named price-movement scenarios or perform a full market-data reset.

## Port

`8084` (application.yml:75)

## Database Schema

Schema: `market_schema`
Configured via: `spring.flyway.schemas: market_schema` (application.yml:21) and `hibernate.default_schema: market_schema` (application.yml:18).

### Tables (in migration order)

| Table | Source migration |
|---|---|
| `market_schema.instruments` | V1__schema.sql + V4, V5, V6, V13, V15 |
| `market_schema.market_holidays` | V3__market_holidays.sql |
| `market_schema.ticks` | V8__create_ticks_table.sql + V9, V12 |
| `market_schema.user_watchlist` | V10__create_user_watchlist.sql |
| `market_schema.market_indices` | V16__create_market_indices.sql |

Key DDL facts:
- `ticks.tick_time` was migrated from `TIMESTAMP` (ET local) to `TIMESTAMPTZ` (UTC) in V9 (V9__alter_ticks_tick_time_to_timestamptz.sql).
- `instruments.sec_number` corrected from ticker-exchange format to SEC CIK format in V17 (V17__fix_sec_number_to_cik.sql).
- `market_indices.percentChange` is computed on read; it is never stored (V16__create_market_indices.sql:2).

## Kafka Produced

None found in scan.

The service has a `KafkaTemplate<String, Object>` bean wired (KafkaConfig.java:37-39) and a `ConcurrentKafkaListenerContainerFactory` with exponential-backoff dead-letter error handler (KafkaConfig.java:52-64), but no `kafkaTemplate.send()` calls and no `@KafkaListener` annotations exist in any main-source Java file. Kafka infrastructure is present but unused at this scan.

## Kafka Consumed

None found in scan.

No `@KafkaListener` annotations found anywhere in `src/main` (confirmed by grep step 4).

## Redis Keys Owned

All keys use `redisTemplate.opsForValue()` with explicit TTL. No Spring `@Cacheable` annotations; all caching is manual via `RedisTemplate<String, Object>`.

| Key Pattern | TTL | Purpose | File:line |
|---|---|---|---|
| `market:price:{SYMBOL}` | 30 seconds | Cached `InstrumentResponse` per symbol (upper-cased) | MarketDataService.java:57,62,107 |
| `market:search:{q_lowercase}` | 10 seconds | Cached `List<InstrumentSearchResponse>` for typeahead search by raw query string | MarketDataService.java:58,63,207 |
| `market:instrument:search:{q_lowercase}:{TYPE}` | 60 seconds | Cached `List<InstrumentSearchResponse>` for typed instrument search (SYMBOL/CUSIP/SEC_NUMBER/ALL) | MarketDataService.java:59,64,250 |
| `market:movers` | 30 seconds | Cached `MarketMoversResponse` (top-5 gainers and losers by open-price pct) | MarketDataService.java:60,62,339 |
| `market:indices` | 30 seconds | Cached `List<IndexEntryResponse>` (all market indices ordered by name) | MarketDataService.java:61,62,367 |

**Cache invalidation:** `PriceTickService` deletes `market:price:{SYMBOL}` after each 1-second price tick (PriceTickService.java:119). `AdminResetService` deletes `market:price:{SYMBOL}` for all instruments on admin reset (AdminResetService.java:46).

## REST Endpoints Exposed

Base path `/api/v1/market` -- `MarketDataController` (MarketDataController.java:27-28)
Base path `/api/v1/admin` -- `AdminController` (AdminController.java:22-23)
Base path `/api/v1/watchlists` -- `WatchlistController` (WatchlistController.java:24)
WebSocket: `/ws/v1` -- `MarketDataWebSocketHandler` (WebSocketConfig.java:19)

### MarketDataController (`/api/v1/market`)

Role enforcement: `RoleAuthorizationFilter` enforces that requests with an `X-User-Role` header containing `ADMIN` are denied access to all `GET /api/v1/market/**` endpoints (RoleAuthorizationFilter.java:74-76, per BRD 12.2 comment on line 25). Requests without the header (internal system calls) pass through.

| Method | Path | Auth/Role | Handler | File:line |
|---|---|---|---|---|
| GET | `/api/v1/market/search` | VIEWER, TRADER, OPERATIONS (not ADMIN) | `searchInstruments(q, limit)` | MarketDataController.java:37-43 |
| GET | `/api/v1/market/hours` | VIEWER, TRADER, OPERATIONS (not ADMIN) | `getMarketHours()` | MarketDataController.java:47-50 |
| GET | `/api/v1/market/holidays` | VIEWER, TRADER, OPERATIONS (not ADMIN) | `getHolidays()` | MarketDataController.java:54-57 |
| GET | `/api/v1/market/instruments/search` | VIEWER, TRADER, OPERATIONS (not ADMIN) | `searchInstrumentsByType(query, type)` | MarketDataController.java:61-66 |
| GET | `/api/v1/market/instruments` | VIEWER, TRADER, OPERATIONS (not ADMIN) | `getInstruments()` | MarketDataController.java:69-73 |
| GET | `/api/v1/market/config` | VIEWER, TRADER, OPERATIONS (not ADMIN) | `getMarketConfig()` | MarketDataController.java:76-80 |
| GET | `/api/v1/market/movers` | VIEWER, TRADER, OPERATIONS (not ADMIN) | `getMarketMovers()` | MarketDataController.java:83-87 |
| GET | `/api/v1/market/indices` | VIEWER, TRADER, OPERATIONS (not ADMIN) | `getMarketIndices()` | MarketDataController.java:90-94 |
| GET | `/api/v1/market/ticks/history` | VIEWER, TRADER, OPERATIONS (not ADMIN) | `getTickHistory(symbol)` | MarketDataController.java:97-101 |
| GET | `/api/v1/market/instrument/{symbol}` | VIEWER, TRADER, OPERATIONS (not ADMIN) | `getInstrument(symbol)` | MarketDataController.java:106-110 |

### AdminController (`/api/v1/admin`)

Admin endpoints are excluded from `RoleAuthorizationFilter` (RoleAuthorizationFilter.java:68); role enforcement is done inline via `RoleGuard.requireRole()` which checks the `X-User-Role` header (AdminController.java:33-34, RoleGuard.java:17-26).

| Method | Path | Auth/Role | Handler | File:line |
|---|---|---|---|---|
| POST | `/api/v1/admin/scenario` | ADMIN (`X-User-Role: ADMIN` required) | `startScenario(request)` | AdminController.java:31-38 |
| POST | `/api/v1/admin/reset` | ADMIN (`Authorization` header checked via RoleGuard) | `reset(request)` | AdminController.java:40-47 |

### WatchlistController (`/api/v1/watchlists`)

Role enforcement: none at controller level; user identity from `X-User-Id` header forwarded by API gateway (WatchlistController.java:32,40,49).

| Method | Path | Auth/Role | Handler | File:line |
|---|---|---|---|---|
| GET | `/api/v1/watchlists` | None (X-User-Id header) | `getWatchlist(userId)` | WatchlistController.java:31-35 |
| POST | `/api/v1/watchlists/{symbol}` | None (X-User-Id header) | `addSymbol(userId, symbol)` | WatchlistController.java:38-43 |
| DELETE | `/api/v1/watchlists/{symbol}` | None (X-User-Id header) | `removeSymbol(userId, symbol)` | WatchlistController.java:46-51 |

### WebSocket

| Path | Notes |
|---|---|
| `/ws/v1` | Real-time market tick broadcast; `setAllowedOriginPatterns("*")` (WebSocketConfig.java:19-20) |

## REST Endpoints Called

None found in scan.

No `RestTemplate`, `WebClient`, or `@FeignClient` usages found in `src/main` (grep step 3 returned no results). This service does not call any downstream services over HTTP.

## Entities

### Instrument

File: `src/main/java/com/brokerdealer/marketdataservice/domain/Instrument.java`
Table: `market_schema.instruments`

| Field | Java Type | Column | Constraints | File:line |
|---|---|---|---|---|
| `symbol` | `String` | `symbol` | `@Id`, `@Column(nullable=false, length=20)` | Instrument.java:18-19 |
| `bidPrice` | `BigDecimal` | `bid_price` | `@Column(precision=18, scale=4)` | Instrument.java:21-22 |
| `askPrice` | `BigDecimal` | `ask_price` | `@Column(precision=18, scale=4)` | Instrument.java:24-25 |
| `lastPrice` | `BigDecimal` | `last_price` | `@Column(precision=18, scale=4)` | Instrument.java:27-28 |
| `tradable` | `Boolean` | `tradable` | `@Column(nullable=false)` | Instrument.java:30-31 |
| `tradingHalted` | `Boolean` | `trading_halted` | `@Column(nullable=false)` | Instrument.java:33-34 |
| `minQuantity` | `Integer` | `min_quantity` | `@Column` | Instrument.java:36-37 |
| `maxQuantity` | `Integer` | `max_quantity` | `@Column` | Instrument.java:39-40 |
| `dataTimestamp` | `Instant` | `data_timestamp` | `@Column` | Instrument.java:42-43 |
| `dayHigh` | `BigDecimal` | `day_high` | `@Column(precision=19, scale=4)` | Instrument.java:45-46 |
| `dayLow` | `BigDecimal` | `day_low` | `@Column(precision=19, scale=4)` | Instrument.java:48-49 |
| `previousClose` | `BigDecimal` | `previous_close` | `@Column(precision=19, scale=4)` | Instrument.java:51-52 |
| `volume` | `Long` | `volume` | `@Column` | Instrument.java:54-55 |
| `seedLastPrice` | `BigDecimal` | `seed_last_price` | `@Column(precision=19, scale=4)` | Instrument.java:57-58 |
| `seedBidPrice` | `BigDecimal` | `seed_bid_price` | `@Column(precision=19, scale=4)` | Instrument.java:60-61 |
| `seedAskPrice` | `BigDecimal` | `seed_ask_price` | `@Column(precision=19, scale=4)` | Instrument.java:63-64 |
| `openPrice` | `BigDecimal` | `open_price` | `@Column(precision=19, scale=4)` | Instrument.java:66-67 |
| `companyName` | `String` | `company_name` | `@Column(length=200)` | Instrument.java:69-70 |
| `cusip` | `String` | `cusip` | `@Column(length=9)` | Instrument.java:72-73 |
| `secNumber` | `String` | `sec_number` | `@Column(length=20)` | Instrument.java:75-76 |
| `exchange` | `String` | `exchange` | `@Column(length=20)` | Instrument.java:78-79 |

Note: `bid_price`, `ask_price`, `last_price` use `NUMERIC(18,4)` in V1 DDL but were not altered; simulation columns (`day_high`, `day_low`, `previous_close`) were corrected to `NUMERIC(19,4)` in V5. Entity reflects `precision=19` for simulation columns.

### Tick

File: `src/main/java/com/brokerdealer/marketdataservice/domain/Tick.java`
Table: `market_schema.ticks`

| Field | Java Type | Column | Constraints | File:line |
|---|---|---|---|---|
| `id` | `Long` | `id` | `@Id`, `@GeneratedValue(strategy=IDENTITY)` | Tick.java:17-19 |
| `symbol` | `String` | `symbol` | `@Column(nullable=false, length=10)` | Tick.java:21-22 |
| `price` | `BigDecimal` | `price` | `@Column(nullable=false, precision=19, scale=4)` | Tick.java:24-25 |
| `bid` | `BigDecimal` | `bid` | `@Column(precision=19, scale=4)` | Tick.java:27-28 |
| `ask` | `BigDecimal` | `ask` | `@Column(precision=19, scale=4)` | Tick.java:30-31 |
| `volume` | `Long` | `volume` | `@Column` | Tick.java:33-34 |
| `tickTime` | `Instant` | `tick_time` | `@Column(nullable=false)` — stored as UTC TIMESTAMPTZ | Tick.java:37-38 |

Unique constraint: `UNIQUE (symbol, tick_time)` defined in DDL (V8__create_ticks_table.sql:13); not mirrored by an entity annotation.

### MarketHoliday

File: `src/main/java/com/brokerdealer/marketdataservice/domain/MarketHoliday.java`
Table: `market_schema.market_holidays`

| Field | Java Type | Column | Constraints | File:line |
|---|---|---|---|---|
| `holidayDate` | `LocalDate` | `holiday_date` | `@Id`, `@Column(nullable=false)` | MarketHoliday.java:16-17 |
| `holidayName` | `String` | `holiday_name` | `@Column(nullable=false, length=100)` | MarketHoliday.java:19-21 |

### UserWatchlist

File: `src/main/java/com/brokerdealer/marketdataservice/domain/UserWatchlist.java`
Table: `market_schema.user_watchlist`

| Field | Java Type | Column | Constraints | File:line |
|---|---|---|---|---|
| `id` | `UUID` | `id` | `@Id`, `@GeneratedValue(strategy=AUTO)`, `@Column(nullable=false, updatable=false)` | UserWatchlist.java:25-27 |
| `userId` | `UUID` | `user_id` | `@Column(name="user_id", nullable=false, updatable=false)` | UserWatchlist.java:29-30 |
| `symbol` | `String` | `symbol` | `@Column(nullable=false, updatable=false, length=20)` | UserWatchlist.java:32-33 |
| `addedAt` | `Instant` | `added_at` | `@Column(name="added_at", nullable=false, updatable=false)` | UserWatchlist.java:35-37 |

Unique constraint: `UNIQUE (user_id, symbol)` defined in DDL (V10__create_user_watchlist.sql:12); not mirrored by entity annotation.

### MarketIndex

File: `src/main/java/com/brokerdealer/marketdataservice/domain/MarketIndex.java`
Table: `market_schema.market_indices`

| Field | Java Type | Column | Constraints | File:line |
|---|---|---|---|---|
| `id` | `UUID` | `id` | `@Id`, `@GeneratedValue(strategy=UUID)`, `@Column(nullable=false, updatable=false)` | MarketIndex.java:26-28 |
| `name` | `String` | `name` | `@Column(nullable=false, unique=true, length=100)` | MarketIndex.java:30-31 |
| `currentValue` | `BigDecimal` | `current_value` | `@Column(nullable=false, precision=19, scale=4)` | MarketIndex.java:33-34 |
| `openValue` | `BigDecimal` | `open_value` | `@Column(nullable=false, precision=19, scale=4)` | MarketIndex.java:36-37 |
| `createdAt` | `Instant` | `created_at` | `@Column(nullable=false, updatable=false)` | MarketIndex.java:39-40 |
| `updatedAt` | `Instant` | `updated_at` | `@Column(nullable=false)` | MarketIndex.java:42-43 |

## What This Service Must NEVER Do

1. [ENFORCED] Write to or read from any other service's PostgreSQL schema (`client_schema`, `order_schema`, `risk_schema`, `execution_schema`, `audit_schema`, `auth_schema`). Flyway is configured to manage only `market_schema` (application.yml:21), and JPA `default_schema` is `market_schema` (application.yml:18). Cross-schema access would require explicit schema-qualified queries which do not exist in any repository.

2. [CONVENTION -- INFERRED, CONFIRM WITH TEAM] Call other services over HTTP. No `RestTemplate`, `WebClient`, or `@FeignClient` is present in `src/main`. This is an absence of code, not an active enforcement block.

3. [CONVENTION -- INFERRED, CONFIRM WITH TEAM] Produce or consume Kafka messages. `KafkaTemplate` and `ConcurrentKafkaListenerContainerFactory` infrastructure is wired (KafkaConfig.java:37,52) but no topics are published or subscribed to in any current main-source file. Infrastructure may be reserved for future use.

4. [ENFORCED] Allow `ADMIN`-role users to call `GET /api/v1/market/**` endpoints. `RoleAuthorizationFilter` explicitly rejects any request where `X-User-Role = ADMIN` targeting `GET /api/v1/market` paths (RoleAuthorizationFilter.java:74-76), returning HTTP 403.

5. [ENFORCED] Execute admin operations (`/api/v1/admin/**`) without an `ADMIN` role. `RoleGuard.requireRole()` throws `AccessForbiddenException` if the role header is absent or not `ADMIN` (RoleGuard.java:17-26, AdminController.java:34,44).

6. [ENFORCED] Tick (price-simulate) a halted instrument. `PriceTickService.tick()` explicitly skips any instrument where `tradingHalted == true` (PriceTickService.java:65-67).

7. [CONVENTION -- INFERRED, CONFIRM WITH TEAM] Store `percentChange` for `market_indices`. The DDL comment on V16 states "percentChange = ... computed on read, never stored" (V16__create_market_indices.sql:2), and `MarketDataService.getMarketIndices()` computes it at read time (MarketDataService.java:356-358). There is no DB column for it, which structurally prevents storage, but this is a design decision rather than an enforced application guard.

## Key Business Rules

1. **Price simulation tick interval:** 1 second via `@Scheduled(fixedDelay = 1000)` (PriceTickService.java:49). Controlled by the JVM scheduler, not a configurable property.

2. **Market hours (US equities):** Open `09:00:00`, Close `16:00:00`, Timezone `America/New_York` (application.yml:43-46). These are injected via `@Value` into `MarketDataService` (MarketDataService.java:48-55). When `market.simulation.bypass-market-hours=true` (application.yml:50, application-dev.yml:2), `isMarketOpen()` always returns `true` (MarketDataService.java:285-286).

3. **Price floor and ceiling (simulation):** `MIN_PRICE = 0.01`, `MAX_PRICE = 99999.9999` (PriceTickService.java:30-31). Any random-walk or scenario tick that would exceed these bounds is clamped.

4. **Random-walk volatility:** 0.01%–0.10% per tick (`changePct = BigDecimal.valueOf(1 + random.nextInt(9), 4)`, 50% up / 50% down) (PriceTickService.java:133-136). Comment states "realistic intraday random walk."

5. **Search result cap:** `SEARCH_MAX_LIMIT = 20` (MarketDataService.java:65). The `/api/v1/market/search` endpoint caps effective limit at `min(max(limit, 1), 20)` (MarketDataService.java:186).

6. **Market movers count:** Top 5 gainers and top 5 losers by open-price percentage change (`MOVERS_LIMIT = 5`, MarketDataService.java:66, lines 316-332). Formula: `(lastPrice - openPrice) / openPrice * 100` (MarketDataService.java:372-380).

7. **Percent change formula (all contexts):** `(lastPrice - previousClose) / previousClose * 100` for instrument change display (MarketDataService.java:410-419); `(lastPrice - openPrice) / openPrice * 100` for movers and indices (MarketDataService.java:372-380). Results are rounded to 2 decimal places, `HALF_UP`.

8. **Admin reset:** Requires `confirmReset = true` in the request body; throws `ValidationException` otherwise (AdminResetService.java:27-30). Resets prices to `seedLastPrice/seedBidPrice/seedAskPrice`, nullifies `dayHigh`, `dayLow`, `previousClose`, sets `volume = 0`, evicts all `market:price:*` Redis keys, and clears all active scenarios (AdminResetService.java:32-48).

9. **Scenario guard (TOCTOU prevention):** `ScenarioService.startScenario()` uses `ConcurrentHashMap.putIfAbsent()` to atomically prevent two concurrent scenario starts for the same symbol (ScenarioService.java:64-67).

10. **Scenario pre-conditions:** A scenario cannot be started on a halted instrument (`SymbolHaltedException` thrown, ScenarioService.java:48-51). Only scenario IDs present in `ScenarioConfig` (loaded from `market.scenarios.items` in application.yml:52-69) are accepted; unknown IDs throw `ScenarioNotFoundException` (ScenarioService.java:40-43).

11. **Volume increment per tick:** Random `500` to `5000` per tick (`500 + ThreadLocalRandom.current().nextLong(4501L)`, PriceTickService.java:113).

12. **SearchType enum values:** `SYMBOL`, `CUSIP`, `SEC_NUMBER`, `ALL` (SearchType.java:3-8). Used as the `type` parameter on `GET /api/v1/market/instruments/search`; defaults to `ALL` (MarketDataController.java:63).

13. **Instrument symbol normalisation:** All symbol inputs are upper-cased before lookup (MarketDataService.java:87, ScenarioService.java:32). The primary key on `instruments` is the raw `symbol` column (VARCHAR 20).

14. **Day high/low and previousClose reset:** On the first tick of a new calendar day (ET), `previousClose` is set to the price from the previous tick, `dayHigh` and `dayLow` are reset to the new price, and `volume` is reset to 0 (PriceTickService.java:98-110).
