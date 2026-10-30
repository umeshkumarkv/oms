<!-- plumbline:template claude-unit v1 -->
# marketdata-service

<!-- plumbline:begin unit -->
Last updated: 4369a80 (2026-10-30, /pl-onboard, claude-unit v1)

## Purpose
Provides real-time and historical market data for the Broker-Dealer OMS. Simulates intraday price ticks every second via a random-walk model, persists tick history in PostgreSQL, broadcasts live prices over WebSocket, and caches hot data in Redis. Exposes REST endpoints for instruments, quotes, market hours, holidays, movers, indices, tick history, watchlists, and admin controls (scenario injection, data reset). Runs on port 8084.

## Build & run
Build and test: `mvn test` (from `services/marketdata-service/`)
Run: `mvn spring-boot:run` or `java -jar target/marketdata-service-*.jar`
Port: 8084
Profile: `dev` sets `market.simulation.bypass-market-hours=true`
Env vars: `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` (default: `brokerdealer`), `SPRING_DATASOURCE_PASSWORD` (default: `brokerdealer`), `SPRING_KAFKA_BOOTSTRAP_SERVERS` (default: `localhost:9092`), `SPRING_REDIS_HOST` (default: `localhost`), `SPRING_REDIS_PORT` (default: `6379`)

**Test recipe:** `mvn test` · Java 21 required (root pom.xml `<java.version>21</java.version>`) · Runtime available: Java 24 (openjdk version "24") · **FAILED** — `Fatal error compiling: java.lang.ExceptionInInitializerError: com.sun.tools.javac.code.TypeTag :: UNKNOWN` — Lombok/MapStruct annotation processors are incompatible with Java 24; tests cannot run until Java 21 is used · 2026-10-30

## Architecture & structure
**Stack:** Java 21, Spring Boot 3.x, Spring Data JPA, Spring Kafka, Spring Data Redis, Spring WebSocket, Flyway, PostgreSQL (schema `market_schema`), Redis, MapStruct, Lombok, Resilience4j, jjwt

**Layers:** Controller → Service → Repository (JPA); MapStruct DTOs; `RoleAuthorizationFilter` + `RoleGuard` handle authorization.

**Key packages:**
- `controller` — `MarketDataController` (`/api/v1/market`), `WatchlistController` (`/api/v1/watchlists`), `AdminController` (`/api/v1/admin`)
- `service` — `MarketDataService` (query + cache), `PriceTickService` (@Scheduled 1s tick loop), `TickPersistenceService` (@Async DB writes), `TickService` (history queries), `WatchlistService`, `ScenarioService`, `AdminResetService`
- `websocket` — `MarketDataWebSocketHandler` (`/ws/v1`; symbol-scoped subscriptions; 30s PING)
- `domain` — `Instrument`, `Tick`, `UserWatchlist`, `MarketHoliday`, `MarketIndex`, `SearchType`
- `repository` — JPA repositories; `TickRepository` has a native upsert query
- `config` — `KafkaConfig`, `RedisConfig`, `WebSocketConfig`, `ScenarioConfig`, `SchedulingConfig`, `RoleGuard`
- `filter` — `RoleAuthorizationFilter`
- `mapper` — `InstrumentMapper`, `InstrumentSummaryMapper`, `InstrumentSearchMapper`, `MarketHolidayMapper`

**Scheduling:** `PriceTickService.tick()` every 1 000 ms; `MarketDataWebSocketHandler.ping()` every 30 000 ms. `SchedulingConfig` enables `@Async` on virtual threads (`spring.threads.virtual.enabled=true`).

## Security
- **No Spring Security.** Authorization via custom servlet filter and guard.
- `RoleAuthorizationFilter` reads `X-User-Role` header from gateway. `GET /api/v1/market/**` allows `VIEWER`, `TRADER`, `OPERATIONS`; denies `ADMIN` (BRD 12.2). Missing header = internal call, passed through. [code: `filter/RoleAuthorizationFilter.java`]
- `AdminController` (`/api/v1/admin/**`) excluded from filter; calls `RoleGuard.requireRole(header, "ADMIN")` directly. [code: `controller/AdminController.java`, `config/RoleGuard.java`]
- Watchlist endpoints require valid UUID in `X-User-Id` header; invalid throws `AccessForbiddenException` (401). [code: `service/WatchlistService.java`]
- Gateway is trusted to validate JWT; this service reads forwarded headers only. No tokens stored here. [code: `config/RoleGuard.java` comment]

## Rules
- Symbols normalised to uppercase before lookup or storage. [code: `service/MarketDataService.java`, `service/WatchlistService.java`]
- Watchlist capped at 50 symbols (`MAX_WATCHLIST_SIZE = 50`). [code: `service/WatchlistService.java`]
- Price search results capped at 20 (`SEARCH_MAX_LIMIT = 20`). [code: `service/MarketDataService.java`]
- `tradingHalted = true` instruments skipped in tick loop and blocked from scenario start. [code: `service/PriceTickService.java`, `service/ScenarioService.java`]
- Price bounded to [0.01, 99999.9999] in simulation. [code: `service/PriceTickService.java`]
- Tick upsert idempotent: duplicate `(symbol, tick_time)` updates price/bid/ask/volume. [code: `repository/TickRepository.java`]
- Scenario start atomic via `ConcurrentHashMap.putIfAbsent`; one scenario per symbol at a time. [code: `service/ScenarioService.java`]
- Market-hours check skipped when `market.simulation.bypass-market-hours=true` (default `true`). [code: `service/MarketDataService.java`, `service/PriceTickService.java`]
- Redis TTLs: prices 30 s, search 10 s, instrument-type-search 60 s, movers 30 s, indices 30 s. [code: `service/MarketDataService.java`]
- `confirmReset` must be `true` for admin reset. [code: `service/AdminResetService.java`]

## Must never
- Do not remove the `ADMIN` exclusion from `GET /api/v1/market/**` in `RoleAuthorizationFilter`; the comment cites BRD 12.2.
- Do not change `UpsertTick` to a plain `INSERT`; the upsert contract is load-bearing for the `@Async` retry path.
- Do not add Spring Security without removing the custom filter; both active simultaneously would create conflicting access control.
- Do not store or log the raw `X-User-Id` or `Authorization` header values in audit logs; they carry identity/token data.
- Do not introduce blocking I/O on the `@Scheduled` tick thread; tick persistence is `@Async` for this reason.
- Do not bypass the `MAX_WATCHLIST_SIZE` limit in code; it is a business rule.

## Decisions & trade-offs
- **No Spring Security** — custom filter reads gateway-forwarded headers, avoiding a full security context chain. [code: `filter/RoleAuthorizationFilter.java` class comment]
- **Virtual threads** — `@Async` tick persistence uses the Spring Boot 3.2 virtual-thread executor; no custom bean needed. [code: `config/SchedulingConfig.java` comment]
- **bypass-market-hours defaults to `true`** — enables continuous simulation without clock alignment to NYSE hours. [code: `application.yml`]
- **Tick upsert not insert** — prevents duplicate-key errors on async retry; write path is fully idempotent. [code: `repository/TickRepository.java` comment]
- **Tick time anchored to midnight ET** — keeps history window valid when bypass mode causes pre-open ticks. [code: `service/TickService.java` class comment]
- **Scenario state in-memory (`ConcurrentHashMap`)** — ephemeral; lost on restart; cleared by admin reset. [code: `service/ScenarioService.java`]
- **NUMERIC(12,4) → NUMERIC(19,4)** (V12) — previous precision overflowed during extended unconstrained simulation. [code: `db/migration/V12__fix_ticks_numeric_precision.sql` comment]
- **percentChange computed on read** — avoids stale stored values; V16 migration comment states this explicitly. [code: `db/migration/V16__create_market_indices.sql` comment]

## Reference
- API — 15 REST endpoints across 3 controllers, plus WebSocket at `/ws/v1`
- Data — 5 tables (instruments, ticks, user_watchlist, market_holidays, market_indices), 17 Flyway migrations
- Integrations — Kafka (producer + consumer configured, no listeners found), Redis cache, WebSocket push
- Configuration — 13 config keys / env vars that change runtime behaviour
- Tech debt — 0 TODO/FIXME in source; open questions documented below
- Open questions — 2 items

Open CLAUDE.reference.md only for the section your task needs.
<!-- plumbline:end unit -->
