<!-- plumbline:template claude-unit v2 -->
# marketdata-service

<!-- plumbline:begin unit -->
Last updated: 39916bd (2026-10-30, /pl-onboard, claude-unit v2)

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

**Packages:** `controller` (`MarketDataController`, `WatchlistController`, `AdminController`) · `service` (`PriceTickService` @Scheduled 1 s, `TickPersistenceService` @Async, `MarketDataService`, `WatchlistService`, `ScenarioService`, `AdminResetService`) · `websocket` (`MarketDataWebSocketHandler`, `/ws/v1`, 30 s PING) · `domain` (5 entities) · `repository` (`TickRepository` native upsert) · `filter` + `mapper` (4 MapStruct mappers).

**Scheduling:** tick every 1 000 ms; WebSocket ping every 30 000 ms; `@Async` on virtual threads.

## Security
- Identity: `X-User-Role` from api-gateway; missing = internal call, passed through.
- Verified at: filter/RoleAuthorizationFilter.java (market/watchlist paths); controller/AdminController.java reads raw `Authorization` header — inconsistent, see risk below.
- Reachable without that check: docker-compose: yes, port 8084 published (docker-compose.yml); k8s: no, ClusterIP (k8s/services/marketdata-service-service.yaml).
- No Spring Security; `GET /api/v1/market/**` VIEWER/TRADER/OPERATIONS only — ADMIN denied (BRD 12.2). [code: filter/RoleAuthorizationFilter.java]
- Risk: `AdminController.reset()` reads from raw `Authorization` header, not `X-User-Role`; direct caller can bypass gateway. [code: controller/AdminController.java]

## Rules

- Symbols to uppercase; watchlist cap 50, search 20. [code: service/MarketDataService.java, service/WatchlistService.java]
- `tradingHalted = true` instruments skipped in tick loop and blocked from scenario start. [code: service/PriceTickService.java]
- Price bounded [0.01, 99999.9999]; tick upsert idempotent on `(symbol, tick_time)`. [code: service/PriceTickService.java, repository/TickRepository.java]
- Scenario start atomic (`putIfAbsent`); one per symbol; `confirmReset` must be `true`. [code: service/ScenarioService.java, service/AdminResetService.java]
- Market-hours check skipped when `bypass-market-hours=true` (default `true`). [code: application.yml]
- Redis TTLs: prices/movers/indices 30 s, search 10 s, type-search 60 s. [code: service/MarketDataService.java]

## Must never

- Remove the ADMIN exclusion from `GET /api/v1/market/**` (BRD 12.2). [code: filter/RoleAuthorizationFilter.java]
- Change `UpsertTick` to plain INSERT — upsert is required for `@Async` retry idempotency. [code: repository/TickRepository.java]
- Add Spring Security alongside the custom filter. [code: filter/RoleAuthorizationFilter.java]
- Log raw `X-User-Id` or `Authorization` header values. [code: filter/RoleAuthorizationFilter.java]
- Introduce blocking I/O on the `@Scheduled` tick thread. [code: service/TickPersistenceService.java]
- Bypass `MAX_WATCHLIST_SIZE = 50`. [code: service/WatchlistService.java]

## Decisions & trade-offs

- No Spring Security — custom filter reads gateway-forwarded headers. [code: filter/RoleAuthorizationFilter.java]
- Virtual threads — `@Async` tick persistence uses Spring Boot 3.2 virtual-thread executor. [code: config/SchedulingConfig.java]
- bypass-market-hours defaults `true` — continuous simulation without NYSE clock alignment. [code: application.yml]
- Tick upsert not insert — idempotent; prevents duplicate-key on async retry. [code: repository/TickRepository.java]
- Tick time anchored to midnight ET — keeps history valid in bypass mode. [code: service/TickService.java]
- Scenario state in-memory — `ConcurrentHashMap`; ephemeral, cleared by admin reset. [code: service/ScenarioService.java]
- NUMERIC(12,4)→(19,4) (V12) — prior precision overflowed in extended simulation. [code: db/migration/V12__fix_ticks_numeric_precision.sql]
- percentChange computed on read — avoids stale stored values. [code: db/migration/V16__create_market_indices.sql]

## Reference

- API — 15 REST endpoints across 3 controllers, plus WebSocket at `/ws/v1`
- Data — 5 tables (instruments, ticks, user_watchlist, market_holidays, market_indices), 17 Flyway migrations
- Integrations — Kafka (consumer configured, no listeners found), Redis cache, WebSocket push
- Configuration — 13 config keys / env vars that change runtime behaviour
- Tech debt — 0 TODO/FIXME; 3 structural observations documented
- Open questions — 2 items

Open CLAUDE.reference.md only for the section your task needs.
<!-- plumbline:end unit -->
