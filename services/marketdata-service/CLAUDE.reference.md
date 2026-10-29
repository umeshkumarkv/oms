<!-- plumbline:template claude-reference v1 -->
# Market Data Service — reference

<!-- plumbline:begin reference -->
Last updated: 4369a80 (2026-10-28, /pl-onboard, claude-reference v1)

## API

### MarketDataController (`GET /api/v1/market/**`)

| Verb | Path | Handler method | Purpose |
|------|------|---------------|---------|
| GET | /api/v1/market/search | `searchInstruments` | Typeahead search by symbol/name/CUSIP/SEC; `?q=`+`&limit=` (max 20) |
| GET | /api/v1/market/hours | `getMarketHours` | Current market open/closed status and next open date |
| GET | /api/v1/market/holidays | `getHolidays` | All configured US equity market holidays ordered by date |
| GET | /api/v1/market/instruments/search | `searchInstrumentsByType` | Search by `?query=`+`&type=` (SYMBOL, CUSIP, SEC_NUMBER, ALL) |
| GET | /api/v1/market/instruments | `getInstruments` | All instruments with summary price data (sorted by symbol) |
| GET | /api/v1/market/config | `getMarketConfig` | Market hours + polling interval + data source (WEBSOCKET or POLLING) |
| GET | /api/v1/market/movers | `getMarketMovers` | Top 5 gainers and top 5 losers by % change from open |
| GET | /api/v1/market/indices | `getMarketIndices` | All market indices with current value and % change from open |
| GET | /api/v1/market/ticks/history | `getTickHistory` | Intraday tick history for `?symbol=`; time range: today midnight ET to current minute |
| GET | /api/v1/market/instrument/{symbol} | `getInstrument` | Full instrument detail including bid/ask/change/changePct; Redis-cached 30 s |

### WatchlistController (`/api/v1/watchlists`)

| Verb | Path | Handler method | Purpose |
|------|------|---------------|---------|
| GET | /api/v1/watchlists | `getWatchlist` | List watchlist entries for the user identified by `X-User-Id` header |
| POST | /api/v1/watchlists/{symbol} | `addSymbol` | Add symbol to user's watchlist (201 Created); validates symbol exists; max 50 per user |
| DELETE | /api/v1/watchlists/{symbol} | `removeSymbol` | Remove symbol from user's watchlist (204 No Content) |

### AdminController (`/api/v1/admin/**`) — requires `X-User-Role: ADMIN`

| Verb | Path | Handler method | Purpose |
|------|------|---------------|---------|
| POST | /api/v1/admin/scenario | `startScenario` | Start a scripted price-move scenario by scenarioId and symbol |
| POST | /api/v1/admin/reset | `reset` | Reset all instrument prices to seed values and clear active scenarios; requires `confirmReset: true` |

### WebSocket endpoint

| Path | Handler | Purpose |
|------|---------|---------|
| /ws/v1 | `MarketDataWebSocketHandler` | Live price tick feed; clients send `AUTH`, `subscribe`/`unsubscribe` with symbol; server pushes `MARKET_DATA_TICK` JSON and `PING` every 30 s |

### Actuator (unauthenticated)

| Verb | Path | Purpose |
|------|------|---------|
| GET | /actuator/health | Liveness/readiness probe; shows details |
| GET | /actuator/info | App info |
| GET | /actuator/metrics | Metrics |

## Data

### market_schema.instruments (PK: symbol VARCHAR(20))
Core price and reference data for each tradable instrument. Updated on every tick.

Key fields: `symbol` (PK), `bid_price`, `ask_price`, `last_price`, `tradable` (NOT NULL), `trading_halted` (NOT NULL), `min_quantity`, `max_quantity`, `day_high`, `day_low`, `previous_close`, `volume`, `open_price`, `seed_last_price`, `seed_bid_price`, `seed_ask_price` (used by admin reset), `company_name`, `cusip` (9), `sec_number` (20), `exchange` (20), `data_timestamp` (TIMESTAMPTZ).

### market_schema.ticks (PK: id BIGSERIAL)
One row per second per symbol. Written asynchronously by `TickPersistenceService` via native upsert.

Key fields: `id` (PK), `symbol` (NOT NULL), `price` (NOT NULL, NUMERIC 19,4), `bid`, `ask`, `volume`, `tick_time` (TIMESTAMPTZ, NOT NULL). Unique constraint: `(symbol, tick_time)`. Index: `idx_ticks_symbol_time`.

### market_schema.market_holidays (PK: holiday_date DATE)
US equity market holiday calendar. Seeded with 2026 holidays in V3.

Key fields: `holiday_date` (PK), `holiday_name` (NOT NULL, 100).

### market_schema.market_indices (PK: id UUID)
Market index snapshot rows (S&P 500, Nasdaq, Dow Jones seeded). `percentChange` is computed at read time, never stored.

Key fields: `id` (PK UUID), `name` (UNIQUE, 100), `current_value` (NOT NULL), `open_value` (NOT NULL), `created_at`, `updated_at`. Index: `idx_market_indices_name`.

### market_schema.user_watchlist (PK: id UUID)
Per-user watchlist; max 50 symbols per user enforced in code.

Key fields: `id` (PK UUID), `user_id` (UUID, NOT NULL), `symbol` (VARCHAR 20, NOT NULL), `added_at` (TIMESTAMPTZ). Unique constraint: `(user_id, symbol)`. Index: `idx_user_watchlist_user_id`.

### Flyway migrations (17 total, all under `db/migration/`)

| Version | File | Change |
|---------|------|--------|
| V1 | V1__schema.sql | Create instruments table |
| V2 | V2__seed.sql | Seed instrument rows |
| V3 | V3__market_holidays.sql | Create market_holidays; seed 2026 US holidays |
| V4 | V4__instrument_price_fields.sql | Add day_high, day_low, previous_close, volume to instruments |
| V5 | V5__fix_instrument_price_field_precision.sql | Fix numeric precision on price simulation columns |
| V6 | V6__add_seed_prices.sql | Add seed_last_price, seed_bid_price, seed_ask_price columns |
| V7 | V7__seed_previous_close.sql | Back-fill previous_close from last_price |
| V8 | V8__create_ticks_table.sql | Create ticks table with TIMESTAMP tick_time |
| V9 | V9__alter_ticks_tick_time_to_timestamptz.sql | Migrate tick_time from ET-local TIMESTAMP to TIMESTAMPTZ (UTC) |
| V10 | V10__create_user_watchlist.sql | Create user_watchlist table |
| V11 | V11__reset_realistic_prices.sql | Reset instrument prices to realistic seed values |
| V12 | V12__fix_ticks_numeric_precision.sql | Increase ticks price/bid/ask precision to NUMERIC(19,4); TRUNCATE ticks |
| V13 | V13__add_search_columns.sql | Add company_name, cusip, sec_number, exchange to instruments |
| V14 | V14__seed_search_columns.sql | Back-fill search columns on existing instrument rows |
| V15 | V15__add_open_price.sql | Add open_price column to instruments |
| V16 | V16__create_market_indices.sql | Create market_indices table; seed S&P 500, Nasdaq, Dow Jones |
| V17 | V17__fix_sec_number_to_cik.sql | Rename/fix sec_number column data |

## Integrations

### Redis
- Used for price caching (`market:price:<SYMBOL>`, TTL 30 s), search caching (`market:search:<q>`, TTL 10 s), instrument-search caching (`market:instrument:search:<q>:<type>`, TTL 60 s), movers (`market:movers`, TTL 30 s), and indices (`market:indices`, TTL 30 s).
- `RedisTemplate<String, Object>` with `GenericJackson2JsonRedisSerializer`; Jackson configured with `JavaTimeModule`.
- Connection: `SPRING_REDIS_HOST` / `SPRING_REDIS_PORT`; timeout 2000 ms.

### Kafka
- Infrastructure wired (producer factory, consumer factory, `ConcurrentKafkaListenerContainerFactory` with DLT and exponential backoff).
- No `@KafkaListener` or `kafkaTemplate.send()` calls exist in the current source — Kafka is not actively used for producing or consuming any topic in the current codebase.
- Consumer group: `marketdata-service-group`; trusted packages: `com.brokerdealer.*`.

### WebSocket (`/ws/v1`)
- Spring WebSocket (not STOMP); `MarketDataWebSocketHandler` extends `TextWebSocketHandler`.
- Server pushes `MARKET_DATA_TICK` JSON per-symbol to subscribed sessions every tick (≤1 s).
- Server sends `PING` to all sessions every 30 s.
- All origins permitted (`setAllowedOriginPatterns("*")`).

### PostgreSQL
- Single DB `brokerdealer`, schema `market_schema`.
- URL: `SPRING_DATASOURCE_URL` (docker-compose default: `jdbc:postgresql://postgres:5432/brokerdealer?currentSchema=market_schema`).

## Configuration

| Key / Env var | Default | Purpose |
|---------------|---------|---------|
| `SPRING_DATASOURCE_URL` | (required) | PostgreSQL JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | `brokerdealer` | DB username |
| `SPRING_DATASOURCE_PASSWORD` | `brokerdealer` | DB password — use env var in all environments |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker |
| `SPRING_REDIS_HOST` | `localhost` | Redis host |
| `SPRING_REDIS_PORT` | `6379` | Redis port |
| `JWT_SECRET` | (required) | Secret forwarded by gateway; present in docker-compose.yml as env var — never hard-code |
| `SPRING_PROFILES_ACTIVE` | (none) | Set to `dev` in docker-compose.yml |
| `market.hours.open-time` | `09:00:00` | Market open time (ET) |
| `market.hours.close-time` | `16:00:00` | Market close time (ET) |
| `market.hours.timezone` | `America/New_York` | Timezone for market hours and tick history |
| `market.polling.interval-ms` | `5000` | Polling interval returned to clients via `/api/v1/market/config` |
| `market.simulation.bypass-market-hours` | `true` | When true, tick loop runs regardless of market hours |
| `market.scenarios.items` | 5 predefined scenarios | Map of scenario ID → `{symbol, directionPct, ticks}`; configured in application.yml |
| `server.port` | `8084` | HTTP port |
| `resilience4j.circuitbreaker.instances.default` | sliding-window 10, failure-rate 50%, wait 10 s, half-open 3 | Circuit-breaker defaults |
| `resilience4j.timelimiter.instances.default` | timeout 2 s | Time-limiter defaults |

Predefined scenario IDs (from application.yml): `SCN-AAPL-DROP-5PCT`, `SCN-AAPL-RISE-5PCT`, `SCN-TSLA-DROP-5PCT`, `SCN-TSLA-RISE-5PCT`, `SCN-MSFT-RISE-3PCT`.

## Tech debt
TODO/FIXME count: **0** — no TODO, FIXME, HACK, or XXX comments found in `src/main/java`.

Notable design gaps:
- Kafka infrastructure is fully wired but no topics are produced or consumed; the consumer group and DLT configuration are unused.
- `JWT_SECRET` is injected as an env var but no JWT validation occurs in this service — the secret has no code path that uses it.
- `RoleAuthorizationFilter` passes requests with no `X-User-Role` header unconditionally as "internal"; there is no additional check to verify the caller is actually an internal service.
- WebSocket allows `setAllowedOriginPatterns("*")` — no origin restriction in place.

## Open questions
- What topics, if any, are planned for the Kafka producer/consumer wired in `KafkaConfig`? No `@KafkaListener` or `send()` calls exist in current code.
- Is `JWT_SECRET` intended for future local JWT validation, or is it only needed by the gateway and mistakenly included in this service's env vars?
- V17 (`V17__fix_sec_number_to_cik.sql`) could not be read — its exact DDL change is unknown; owner of market data schema should confirm whether `sec_number` column was renamed or its data updated.

— Plumbline 2.8.7 · /pl-onboard · claude-reference v1 · commit 4369a80 · judge pending · AI-drafted, posted after SME review
<!-- plumbline:end reference -->
