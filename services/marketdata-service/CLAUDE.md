<!-- plumbline:template claude-unit v1 -->
# marketdata-service

<!-- plumbline:begin unit -->
Last updated: 4586741 (2026-10-30, /pl-onboard, claude-unit v1)

## Purpose

Provides real-time and historical market data for the Broker-Dealer OMS. Simulates intraday price ticks every second via a random-walk model, persists tick history in PostgreSQL, and broadcasts live prices over WebSocket. Exposes REST endpoints for instruments, quotes, market hours, holidays, movers, indices, tick history, watchlists, and admin controls (scenario injection, data reset). Runs on port 8084.

## Build & run

Build and test: `mvn test` (from `services/marketdata-service/`)
Run: `mvn spring-boot:run` or `java -jar target/marketdata-service-*.jar`
Port: 8084
Profile: `dev` sets `market.simulation.bypass-market-hours=true`
Env vars: `SPRING_DATASOURCE_URL` (required), `SPRING_DATASOURCE_USERNAME` (default: `brokerdealer`), `SPRING_DATASOURCE_PASSWORD` (default: `brokerdealer`), `SPRING_KAFKA_BOOTSTRAP_SERVERS` (default: `localhost:9092`), `SPRING_REDIS_HOST` (default: `localhost`), `SPRING_REDIS_PORT` (default: `6379`)

**Test recipe:** `mvn test` · Java 21 required · **FAILED** — `ExceptionInInitializerError: TypeTag::UNKNOWN` (Lombok/MapStruct incompatible with Java 24; tests cannot run until Java 21 is used) · 2026-10-30

## Architecture & structure

**Stack:** Java 21, Spring Boot 3.x, Spring Data JPA, Spring Kafka, Spring Data Redis, Spring WebSocket, Flyway, PostgreSQL (`market_schema`), Redis, MapStruct, Lombok, Resilience4j, jjwt

**Layers:** Controller → Service → Repository (JPA); MapStruct DTOs; `RoleAuthorizationFilter` + `RoleGuard` for authorization.

**Packages:** `controller` (3 controllers: `MarketDataController`, `WatchlistController`, `AdminController`) · `service` (`MarketDataService`, `PriceTickService` @Scheduled 1 s, `TickPersistenceService` @Async, `TickService`, `WatchlistService`, `ScenarioService`, `AdminResetService`) · `websocket` (`MarketDataWebSocketHandler`, `/ws/v1`, 30 s PING) · `domain` (5 entities: `Instrument`, `Tick`, `UserWatchlist`, `MarketHoliday`, `MarketIndex`) · `repository` (JPA repos; `TickRepository` native upsert) · `config` + `filter` + `mapper` (4 MapStruct mappers).

**Scheduling:** tick every 1 000 ms; WebSocket ping every 30 000 ms; `@Async` on virtual threads.

## Security

- **No Spring Security.** Authorization via custom servlet filter and guard.
- `RoleAuthorizationFilter` reads `X-User-Role` from gateway. `GET /api/v1/market/**` allows VIEWER, TRADER, OPERATIONS; denies ADMIN (BRD 12.2). Missing header = internal call, passed through. [code: `filter/RoleAuthorizationFilter.java`]
- `AdminController` (`/api/v1/admin/**`) excluded from filter; calls `RoleGuard.requireRole(header, "ADMIN")` directly. [code: `controller/AdminController.java`]
- Watchlist endpoints require valid UUID in `X-User-Id`; invalid → 401. [code: `service/WatchlistService.java`]
- **Risk:** `AdminController.reset()` reads role from raw `Authorization` header (not `X-User-Role`) — inconsistent; a caller that can set `Authorization: ADMIN` can trigger a data reset without gateway. [code: `controller/AdminController.java`]

## Rules

- Symbols normalised to uppercase. Watchlist capped at 50; search results at 20. [code: `service/MarketDataService.java`, `service/WatchlistService.java`]
- `tradingHalted = true` instruments skipped in tick loop and blocked from scenario start. [code: `service/PriceTickService.java`]
- Price bounded [0.01, 99999.9999]; tick upsert idempotent on `(symbol, tick_time)`. [code: `service/PriceTickService.java`, `repository/TickRepository.java`]
- Scenario start atomic (`putIfAbsent`); one per symbol; `confirmReset` must be `true`. [code: `service/ScenarioService.java`, `service/AdminResetService.java`]
- Market-hours check skipped when `bypass-market-hours=true` (default `true`). [code: `application.yml`]
- Redis TTLs: prices/movers/indices 30 s, search 10 s, type-search 60 s. [code: `service/MarketDataService.java`]

## Must never

- Remove the ADMIN exclusion from `GET /api/v1/market/**` (BRD 12.2). [code: `filter/RoleAuthorizationFilter.java`]
- Change `UpsertTick` to a plain INSERT — upsert is load-bearing for the `@Async` retry path.
- Add Spring Security alongside the custom filter — conflicting access control.
- Log raw `X-User-Id` or `Authorization` header values.
- Introduce blocking I/O on the `@Scheduled` tick thread — persistence is `@Async`.
- Bypass `MAX_WATCHLIST_SIZE = 50`.

## Decisions & trade-offs

- **No Spring Security** — custom filter reads gateway-forwarded headers. [code: `filter/RoleAuthorizationFilter.java`]
- **Virtual threads** — `@Async` tick persistence uses Spring Boot 3.2 virtual-thread executor. [code: `config/SchedulingConfig.java`]
- **bypass-market-hours defaults `true`** — continuous simulation without NYSE clock alignment. [code: `application.yml`]
- **Tick upsert not insert** — idempotent; prevents duplicate-key on async retry. [code: `repository/TickRepository.java`]
- **Tick time anchored to midnight ET** — keeps history valid in bypass mode. [code: `service/TickService.java`]
- **Scenario state in-memory** — `ConcurrentHashMap`; ephemeral, cleared by admin reset. [code: `service/ScenarioService.java`]
- **NUMERIC(12,4)→(19,4)** (V12) — prior precision overflowed in extended simulation. [code: `db/migration/V12__fix_ticks_numeric_precision.sql`]
- **percentChange computed on read** — avoids stale stored values. [code: `db/migration/V16__create_market_indices.sql`]

## Reference

- API — 15 REST endpoints across 3 controllers, plus WebSocket at `/ws/v1`
- Data — 5 tables (instruments, ticks, user_watchlist, market_holidays, market_indices), 17 Flyway migrations
- Integrations — Kafka (consumer configured, no listeners found), Redis cache, WebSocket push
- Configuration — 13 config keys / env vars that change runtime behaviour
- Tech debt — 0 TODO/FIXME; 3 structural observations documented
- Open questions — 2 items

Open CLAUDE.reference.md only for the section your task needs.
<!-- plumbline:end unit -->
