<!-- plumbline:template claude-unit v1 -->
# Market Data Service

<!-- plumbline:begin unit -->
Last updated: 4369a80 (2026-10-28, /pl-onboard, claude-unit v1)

## Purpose
Provides real-time and historical equity market data for the Broker-Dealer OMS. Simulates intraday price ticks via a 1-second scheduled loop that writes to PostgreSQL and pushes updates over WebSocket. Exposes REST endpoints for instruments, indices, movers, market hours, holidays, and per-user watchlists. ADMIN-only endpoints allow triggering scripted price scenarios and resetting simulation state.

## Build & run
Build: `./mvnw package -pl services/marketdata-service` (or `mvnw.cmd` on Windows).
Run: `./mvnw spring-boot:run -pl services/marketdata-service`.
Port: **8084** (`server.port=8084` in application.yml).
Profile: `SPRING_PROFILES_ACTIVE=dev` set in docker-compose.yml; no profile-specific yml files present.
Startup order (docker-compose depends_on): kafka must be `service_healthy`; redis and postgres require `service_started`. [code: `docker-compose.yml`]

Test recipe: `mvn test` · requires Java 21 (`<java.version>21</java.version>` in parent pom) · not run — Java 24 installed locally is incompatible with maven-compiler-plugin 3.11.0; first error: `Fatal error compiling: java.lang.ExceptionInInitializerError: com.sun.tools.javac.code.TypeTag :: UNKNOWN` · 2026-10-28

## Architecture & structure
Stack: Java 21, Spring Boot 3.x, Spring Data JPA (Hibernate), Spring Data Redis, Spring WebSocket, Spring Kafka, Flyway, PostgreSQL, Lombok, MapStruct, Resilience4j, jjwt, springdoc-openapi.

Layer pattern: `controller` → `service` → `repository`; MapStruct mappers handle entity-to-DTO conversion. `RoleAuthorizationFilter` enforces role access before controllers run. `@Scheduled` and `@Async` run on virtual threads (Spring Boot 3.2, `spring.threads.virtual.enabled=true`).

Packages:
- `controller` — `MarketDataController`, `WatchlistController`, `AdminController`.
- `service` — `MarketDataService` (queries + Redis caching), `PriceTickService` (1-second scheduler), `TickPersistenceService` (async DB write), `TickService` (intraday history), `WatchlistService`, `ScenarioService`, `AdminResetService`.
- `domain` — JPA entities: `Instrument`, `Tick`, `MarketHoliday`, `MarketIndex`, `UserWatchlist`.
- `repository` — Spring Data JPA repos; `TickRepository` has native upsert query.
- `config` — `KafkaConfig`, `RedisConfig`, `WebSocketConfig`, `ScenarioConfig`, `RoleGuard`, `SchedulingConfig`.
- `filter` — `RoleAuthorizationFilter`. `websocket` — `MarketDataWebSocketHandler`. `exception` — domain exceptions + `GlobalExceptionHandler`. `mapper` — MapStruct mappers. `dto` — request/response DTOs.

## Security
No Spring Security dependency. Role enforcement is split:

- **Filter-level** (`RoleAuthorizationFilter`): blocks `ADMIN` role from `GET /api/v1/market/**`; requests with no `X-User-Role` header pass through as internal. [code: `filter/RoleAuthorizationFilter.java`]
- **Handler-level** (`RoleGuard`): `POST /api/v1/admin/scenario` and `POST /api/v1/admin/reset` require `X-User-Role: ADMIN` (scenario) or `Authorization: ADMIN` (reset). [code: `controller/AdminController.java`, `config/RoleGuard.java`]
- **Identity**: `WatchlistService` reads `X-User-Id` header (UUID forwarded by gateway); missing or non-UUID value throws 401. [code: `service/WatchlistService.java`]
- JWT dependency (`jjwt-*`) is in pom.xml; no JWT validation code in this service — gateway validates tokens before forwarding headers.
- `JWT_SECRET` sourced from environment variable; value must never appear in code or config files. [code: `docker-compose.yml`]

## Rules
- Symbols are always normalised to uppercase before any lookup or persistence. [code: `service/MarketDataService.java`, `service/WatchlistService.java`]
- Watchlist capped at 50 symbols per user. [code: `service/WatchlistService.java`]
- `tradingHalted=true` instruments are skipped by the tick loop and cannot have scenarios triggered. [code: `service/PriceTickService.java`, `service/ScenarioService.java`]
- Only one active scenario per symbol at a time; enforced atomically via `ConcurrentHashMap.putIfAbsent`. [code: `service/ScenarioService.java`]
- Admin reset requires `confirmReset=true` in the request body. [code: `service/AdminResetService.java`]
- `ADMIN` role must not call `GET /api/v1/market/**` — returns 403; BRD 12.2. [code: `filter/RoleAuthorizationFilter.java`]
- Tick prices bounded to `[0.01, 99999.9999]` to prevent DB overflow. [code: `service/PriceTickService.java`]
- Tick persistence is async and idempotent: `ON CONFLICT (symbol, tick_time) DO UPDATE`. [code: `repository/TickRepository.java`]
- Flyway manages schema under `market_schema`; `ddl-auto=validate`. [code: `src/main/resources/application.yml`]
- Redis cache TTLs: price/movers/indices 30 s, search 10 s, instrument-search 60 s. [code: `service/MarketDataService.java`]
- Market hours gate: `market.simulation.bypass-market-hours=true` keeps tick loop always running. [code: `src/main/resources/application.yml`]
- Kafka consumer uses exponential backoff (1 s → 2 s → 4 s, max 3 attempts) then dead-letter topic. [code: `config/KafkaConfig.java`]

## Must never
- Never add Spring Security or JWT validation without removing/updating `RoleAuthorizationFilter` and `RoleGuard` — the current model trusts the gateway.
- Never remove the `confirmReset=true` guard from `AdminResetService.reset()`.
- Never allow a non-ADMIN role to call admin endpoints or an ADMIN role to call market-read endpoints.
- Never let `PriceTickService.tickInstrument()` write to DB synchronously; `TickPersistenceService.saveTick()` must remain `@Async`.
- Never store a price outside `[0.01, 99999.9999]`; causes Postgres numeric overflow.
- Never write credential values (JWT_SECRET, DB password) into application.yml or any committed file.

## Decisions & trade-offs
- Virtual threads (`spring.threads.virtual.enabled=true`) replace a custom thread pool; `SchedulingConfig.java` notes Spring Boot 3.2 provisions `SimpleAsyncTaskExecutor` automatically, so no `Executor` bean is needed. [code: `config/SchedulingConfig.java`]
- Native upsert (`ON CONFLICT DO UPDATE`) makes async tick retries idempotent rather than raising duplicate-key errors. [code: `repository/TickRepository.java`]
- `TickService` anchors history windows to today-midnight ET so the window is valid when `bypass-market-hours=true` causes ticks before configured open-time. [code: `service/TickService.java`]
- `ScenarioService` uses `ConcurrentHashMap.compute` for atomic tick application/removal, avoiding a TOCTOU race. [code: `service/ScenarioService.java`]
- No Spring Security: `RoleAuthorizationFilter` comment states the gateway validates the JWT; services read forwarded headers directly. [code: `filter/RoleAuthorizationFilter.java`]
- ticks.tick_time migrated from `TIMESTAMP` (ET local) to `TIMESTAMPTZ` (UTC) in V9 to fix timezone handling. [code: `db/migration/V9__alter_ticks_tick_time_to_timestamptz.sql`]

## Reference
- API — 15 REST + 1 WebSocket endpoints (3 controllers); 3 actuator endpoints separately; open CLAUDE.reference.md § API.
- Data — 5 tables (instruments, ticks, market_holidays, market_indices, user_watchlist), 17 Flyway migrations; open CLAUDE.reference.md § Data.
- Integrations — Redis cache, Kafka (infrastructure wired, no active topics), WebSocket `/ws/v1`; open CLAUDE.reference.md § Integrations.
- Configuration — 17 env vars / config keys; open CLAUDE.reference.md § Configuration.
- Tech debt — 0 TODO/FIXME; open CLAUDE.reference.md § Tech debt.

Open CLAUDE.reference.md only for the section your task needs.

— Plumbline 2.8.7 · /pl-onboard · claude-unit v1 · commit 4369a80 · judge pending · AI-drafted, posted after SME review
<!-- plumbline:end unit -->
