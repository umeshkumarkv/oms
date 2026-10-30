<!-- plumbline:template claude-reference v1 -->
# marketdata-service — reference

<!-- plumbline:begin reference -->
Last updated: 4586741 (2026-10-30, /pl-onboard, claude-reference v1)

## API

### MarketDataController — `/api/v1/market` — 10 endpoints

| Method | Path | Handler method | Purpose |
|--------|------|----------------|---------|
| GET | `/api/v1/market/search` | `searchInstruments` | Full-text instrument typeahead search (`q`, `limit` up to 20); results from Redis cache (10 s TTL) |
| GET | `/api/v1/market/hours` | `getMarketHours` | Current market open/closed status plus next open date |
| GET | `/api/v1/market/holidays` | `getHolidays` | List of all market holidays ordered by date |
| GET | `/api/v1/market/instruments/search` | `searchInstrumentsByType` | Search instruments by `query` and `type` (SYMBOL, CUSIP, SEC_NUMBER, ALL); results from Redis cache (60 s TTL) |
| GET | `/api/v1/market/instruments` | `getInstruments` | All instruments with live enriched prices and market status |
| GET | `/api/v1/market/config` | `getMarketConfig` | Market open flag, hours, timezone, polling interval, data source (WEBSOCKET vs POLLING) |
| GET | `/api/v1/market/movers` | `getMarketMovers` | Top 5 gainers and losers by open-price percent change; Redis cache (30 s TTL) |
| GET | `/api/v1/market/indices` | `getMarketIndices` | Market index list (S&P 500, Nasdaq, Dow Jones) with computed percent change; Redis cache (30 s TTL) |
| GET | `/api/v1/market/ticks/history` | `getTickHistory` | Intraday tick history for a symbol (`symbol` query param); today midnight ET to current minute |
| GET | `/api/v1/market/instrument/{symbol}` | `getInstrument` | Full instrument detail for a single symbol; Redis cache (30 s TTL) |

### WatchlistController — `/api/v1/watchlists` (3 endpoints)

| Method | Path | Handler method | Purpose |
|--------|------|----------------|---------|
| GET | `/api/v1/watchlists` | `getWatchlist` | Get the authenticated user's watchlist entries, ordered by `added_at` asc; requires `X-User-Id` UUID header |
| POST | `/api/v1/watchlists/{symbol}` | `addSymbol` | Add a symbol to the user's watchlist (201); validates symbol exists and enforces 50-symbol limit; requires `X-User-Id` |
| DELETE | `/api/v1/watchlists/{symbol}` | `removeSymbol` | Remove a symbol from the user's watchlist (204); requires `X-User-Id` |

### AdminController — `/api/v1/admin` (2 endpoints)

| Method | Path | Handler method | Purpose |
|--------|------|----------------|---------|
| POST | `/api/v1/admin/scenario` | `startScenario` | Start a named price scenario for a symbol; requires `X-User-Role: ADMIN` header |
| POST | `/api/v1/admin/reset` | `reset` | Reset all instrument prices to seed values and clear active scenarios; requires `X-User-Role: ADMIN` in `Authorization` header and `confirmReset: true` body |

### WebSocket

- Endpoint: `ws://host:8084/ws/v1` — `MarketDataWebSocketHandler`
- Client sends `{"type":"AUTH"}` → server replies `{"type":"AUTH_OK"}` to start live feed
- Client sends `{"action":"subscribe","symbol":"AAPL"}` to subscribe to a symbol
- Client sends `{"action":"unsubscribe","symbol":"AAPL"}` to unsubscribe
- Server pushes `{"type":"MARKET_DATA_TICK", ...}` to subscribed sessions on every tick (1 s)
- Server sends `{"type":"PING"}` broadcast to all sessions every 30 s

All REST responses wrap in `{"data": ..., "meta": {"timestamp": ..., "requestId": ...}}`.
All error responses use `{"error": {"code": ..., "message": ..., "details": []}, "meta": {...}}`.

## Data

### Tables (schema: `market_schema`)

**instruments** (PK: `symbol VARCHAR(20)`)
- `symbol` VARCHAR(20) NOT NULL PK
- `bid_price`, `ask_price`, `last_price` NUMERIC(18,4) — live prices
- `open_price`, `previous_close`, `day_high`, `day_low` NUMERIC(19,4) — daily price tracking
- `seed_last_price`, `seed_bid_price`, `seed_ask_price` NUMERIC(19,4) — reset anchor prices
- `tradable` BOOLEAN NOT NULL DEFAULT true
- `trading_halted` BOOLEAN NOT NULL DEFAULT false
- `min_quantity`, `max_quantity` INTEGER
- `volume` BIGINT DEFAULT 0
- `data_timestamp` TIMESTAMPTZ
- `company_name` VARCHAR(200), `cusip` VARCHAR(9), `sec_number` VARCHAR(20), `exchange` VARCHAR(20) — added V13

**ticks** (PK: `id BIGSERIAL`)
- `symbol` VARCHAR(10) NOT NULL
- `price` NUMERIC(19,4) NOT NULL, `bid` NUMERIC(19,4), `ask` NUMERIC(19,4)
- `volume` BIGINT
- `tick_time` TIMESTAMPTZ NOT NULL
- UNIQUE constraint: `(symbol, tick_time)` — upsert-safe
- Index: `idx_ticks_symbol_time (symbol, tick_time)`

**user_watchlist** (PK: `id UUID`)
- `user_id` UUID NOT NULL
- `symbol` VARCHAR(20) NOT NULL
- `added_at` TIMESTAMPTZ NOT NULL DEFAULT NOW()
- UNIQUE constraint: `(user_id, symbol)`
- Index: `idx_user_watchlist_user_id (user_id)`

**market_holidays** (PK: `holiday_date DATE`)
- `holiday_name` VARCHAR(100) NOT NULL
- Seeded with US equity market holidays for 2026 (V3)

**market_indices** (PK: `id UUID`)
- `name` VARCHAR(100) NOT NULL UNIQUE
- `current_value`, `open_value` NUMERIC(19,4) NOT NULL
- `created_at`, `updated_at` TIMESTAMPTZ NOT NULL
- Index: `idx_market_indices_name (name)`
- Seeded with S&P 500, Nasdaq, Dow Jones (V16)
- percentChange computed on read, not stored (V16 comment)

### Flyway migrations (17 total, in order)

| Migration | Change |
|-----------|--------|
| V1 | Create `instruments` table (base schema) |
| V2 | Seed initial instrument rows |
| V3 | Create `market_holidays` table; seed 2026 US holidays |
| V4 | Add `day_high`, `day_low`, `previous_close`, `volume` to instruments |
| V5 | Increase `day_high`, `day_low`, `previous_close` precision to NUMERIC(19,4) |
| V6 | Add `seed_last_price`, `seed_bid_price`, `seed_ask_price`; populate from current prices |
| V7 | Seed `previous_close` values for all instruments |
| V8 | Create `ticks` table with unique `(symbol, tick_time)` constraint |
| V9 | Alter `tick_time` from TIMESTAMP (ET local) to TIMESTAMPTZ (UTC) |
| V10 | Create `user_watchlist` table (SCRUM-117) |
| V11 | Reset all instrument prices to realistic May 2026 values; fix volatility from 0.5%/s to 0.01–0.10%/tick |
| V12 | Increase tick price/bid/ask precision from NUMERIC(12,4) to NUMERIC(19,4); truncate ticks |
| V13 | Add `company_name`, `cusip`, `sec_number`, `exchange` to instruments (SCRUM-139) |
| V14 | Seed search columns (company_name, cusip, exchange) for all instruments |
| V15 | Add `open_price` to instruments; seed offset values for movers display |
| V16 | Create `market_indices` table; seed S&P 500, Nasdaq, Dow Jones |
| V17 | Correct `sec_number` to SEC CIK format for all 10 instruments (SCRUM-170) |

## Integrations

**Kafka**
- Bootstrap servers: `SPRING_KAFKA_BOOTSTRAP_SERVERS` (default: `localhost:9092`)
- Consumer group: `marketdata-service-group`, auto-offset-reset: `earliest`
- Error handling: exponential backoff (1 s, 2 s, 4 s, max 3 attempts) then Dead Letter Topic via `DeadLetterPublishingRecoverer`
- No `@KafkaListener` found in source — Kafka infrastructure is configured but no consumer listeners are implemented in the current codebase
- `KafkaTemplate<String, Object>` bean available for producing; no active producers found in source

**Redis**
- Host/port: `SPRING_REDIS_HOST:SPRING_REDIS_PORT` (defaults: `localhost:6379`), timeout 2 000 ms
- Used directly via `RedisTemplate<String, Object>` with manual key management; `CacheManager` bean also registered (TTL 30 s)
- Cache key prefixes: `market:price:` (30 s), `market:search:` (10 s), `market:instrument:search:` (60 s), `market:movers` (30 s), `market:indices` (30 s)
- Serialization: `StringRedisSerializer` for keys, `GenericJackson2JsonRedisSerializer` (Jackson with JavaTimeModule) for values

**WebSocket**
- Endpoint: `/ws/v1`, allowed origins: `*`
- In-memory session registry (`ConcurrentHashMap`); no external broker
- Tick broadcasts are symbol-scoped; PING broadcast is to all sessions every 30 s

**Actuator**
- Exposed: `health`, `info`, `metrics`
- Health probes enabled with `show-details: always`

**Resilience4j**
- Circuit breaker: sliding window 10, failure threshold 50%, open wait 10 s, half-open calls 3
- Time limiter: timeout 2 s

## Configuration

| Key | Env var override | Default | Effect |
|-----|-----------------|---------|--------|
| `spring.datasource.url` | `SPRING_DATASOURCE_URL` | (none — required) | PostgreSQL connection URL |
| `spring.datasource.username` | `SPRING_DATASOURCE_USERNAME` | `brokerdealer` | DB username |
| `spring.datasource.password` | `SPRING_DATASOURCE_PASSWORD` | `brokerdealer` | DB password |
| `spring.kafka.bootstrap-servers` | `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker address |
| `spring.data.redis.host` | `SPRING_REDIS_HOST` | `localhost` | Redis host |
| `spring.data.redis.port` | `SPRING_REDIS_PORT` | `6379` | Redis port |
| `market.hours.open-time` | — | `09:00:00` | Market open time (ET) |
| `market.hours.close-time` | — | `16:00:00` | Market close time (ET) |
| `market.hours.timezone` | — | `America/New_York` | Timezone for market hours and tick history |
| `market.polling.interval-ms` | — | `5000` | Polling interval returned in `/api/v1/market/config` response |
| `market.simulation.bypass-market-hours` | — | `true` | If true, tick loop and queries ignore NYSE hours — simulation always on |
| `market.scenarios.items` | — | 5 built-in scenarios | Named scenario definitions (scenarioId → symbol, directionPct, ticks) |
| `server.port` | — | `8084` | HTTP port |

**Built-in scenarios (application.yml):**
- `SCN-AAPL-DROP-5PCT` — AAPL −5% over 10 ticks
- `SCN-AAPL-RISE-5PCT` — AAPL +5% over 10 ticks
- `SCN-TSLA-DROP-5PCT` — TSLA −5% over 10 ticks
- `SCN-TSLA-RISE-5PCT` — TSLA +5% over 10 ticks
- `SCN-MSFT-RISE-3PCT` — MSFT +3% over 6 ticks

## Tech debt

No TODO, FIXME, HACK, or XXX comments found in source (0 occurrences across all 95 files).

Notable structural observations (not TODO-marked):
- Kafka consumer infrastructure (group, error handler, DLT) is fully configured but no `@KafkaListener` implementations exist — the consumer side is unused dead code.
- `AdminController.reset()` reads the role from the raw `Authorization` header value directly (not a parsed bearer token); the header content must match the string `"ADMIN"` exactly, which is inconsistent with the `X-User-Role` header used by `startScenario` in the same controller.
- Market index `current_value` and `open_value` are never updated by the running service — the 3 seeded rows are static and the percent-change figure never reflects real tick movement.

## Open questions

1. **Kafka consumers** — `KafkaConfig` registers a full consumer factory with DLT recovery, but no `@KafkaListener` exists in the service. It is unclear whether consuming from an upstream topic (e.g. order events) is planned, partially implemented, or was removed. The Kafka consumer configuration may be safe to remove if no consumer is planned.

2. **Market index updates** — `market_indices` rows are seeded once in V16 and never written to by the running service. Whether `current_value` is intended to be updated by the tick loop (or by a separate job) is not documented in code or migration comments.
<!-- plumbline:end reference -->
