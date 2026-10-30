<!-- plumbline:template claude-unit v1 -->
# client-service

<!-- plumbline:begin unit -->
Last updated: 4369a80 (2026-10-30, /pl-onboard, claude-unit v1)

## Purpose
Manages broker-dealer client (customer) accounts for the OMS. Owns client profiles, KYC state,
cash balances, equity holdings, and per-client routing preferences. Provides REST endpoints for
profile and portfolio reads and default-route updates, and updates holdings and cash balances
in real time by consuming execution fill events from Kafka.

## Build & run
```
mvn test          # run tests
mvn spring-boot:run  # start service
```
Port: **8083**. No active Spring profiles defined; configuration is supplied via environment
variables (see Configuration in CLAUDE.reference.md).

**Test recipe:** `mvn test` · Java 21 required (`<java.version>21</java.version>` in root pom.xml)
· Java 24 available (openjdk version "24") · **FAILED** —
`Fatal error compiling: java.lang.ExceptionInInitializerError: com.sun.tools.javac.code.TypeTag :: UNKNOWN`
— Lombok/MapStruct annotation processors are incompatible with Java 24; tests cannot run until
Java 21 is used · 2026-10-30

## Architecture & structure
- **Stack:** Java 21, Spring Boot 3.2.4, Spring Data JPA (Hibernate), Spring Data Redis,
  Spring Kafka, Flyway, PostgreSQL, MapStruct 1.5.5.Final, Lombok 1.18.30, Resilience4j 2.2.0,
  springdoc-openapi 2.3.0
- **Layer pattern:** `controller` → `service` → `repository` (JPA); Kafka consumer calls
  `service` directly; `client` package holds an outbound HTTP client for market-data
- **Main packages:**
  - `controller` — `ClientController` (REST, base path `/api/v1/clients`)
  - `service` — `ClientService` (business logic, cash/holding mutations, Redis cache)
  - `repository` — `ClientRepository`, `HoldingRepository` (Spring Data JPA)
  - `domain` — JPA entities (`Client`, `Holding`) and enums
  - `dto` — request/response DTOs (no domain objects in API surface)
  - `mapper` — `ClientMapper` (MapStruct, `Client` → `ClientProfileResponse`)
  - `kafka/consumer` — `OrderEventConsumer` (consumes `order.state.changed`, `execution.completed`)
  - `client` — `MarketDataClient` (outbound HTTP to marketdata-service, Resilience4j circuit breaker)
  - `filter` — `RoleAuthorizationFilter` (servlet filter, RBAC via `X-User-Role` header)
  - `config` — `KafkaConfig`, `RedisConfig`, `RestTemplateConfig`
  - `exception` — `GlobalExceptionHandler`, `ClientNotFoundException`, `ValidationException`
- **Database schema:** `client_schema` (Flyway-managed, 7 migrations, V1–V7)
- **Virtual threads:** enabled (`spring.threads.virtual.enabled: true`)

## Security
- No Spring Security. RBAC is enforced by `RoleAuthorizationFilter`
  (`filter/RoleAuthorizationFilter.java`), a `OncePerRequestFilter` that reads the
  `X-User-Role` header forwarded by the API gateway.
- Missing or blank `X-User-Role` → HTTP 401.
- Unrecognised role or insufficient permission → HTTP 403.
- Role permission matrix (from `RoleAuthorizationFilter.java`):
  - `GET /api/v1/clients/**` — VIEWER, TRADER, OPERATIONS, ADMIN
  - `GET /api/v1/clients/active` — TRADER, OPERATIONS, ADMIN only (VIEWER denied)
  - `GET /api/v1/clients/{id}/portfolio` — VIEWER, TRADER, OPERATIONS, SYSTEM (ADMIN denied, BRD 12.2)
  - `PATCH /api/v1/clients/{id}/default-route` — ADMIN only
  - `PATCH /api/v1/clients/{id}/reserved-cash` — exempt from role check (system-to-system)
  - `/actuator/**` and `OPTIONS` — excluded from filter
- Tokens are not parsed by this service; auth is delegated to the gateway.

## Rules
- `limit` parameter for `/active` must be 1–50 inclusive; values outside throw `VALIDATION_ERROR` [code: service/ClientService.java]
- Active-client results are cached in Redis under `client:active-ranked:today:{limit}` for 30 seconds; Redis failures degrade gracefully (warn and continue) [code: service/ClientService.java]
- Portfolio and balance caches (`client:portfolio:{id}`, `client:balance:{id}`) are evicted on every `updateDefaultRoute` and `processExecutionFill` call [code: service/ClientService.java]
- Default route must be one of `NASDAQ`, `NYSE`, `ARCA`, `BATS`; other values throw `INVALID_ROUTE` [code: service/ClientService.java]
- BUY REJECTED fills do not mutate cash balance or turnover [code: service/ClientService.java]
- BUY fills subtract `fillValue` from `cashBalance` and add to `turnoverToday`; SELL fills add `fillValue` to both [code: service/ClientService.java]
- Holdings are upserted (weighted-average price) on BUY; quantity is reduced (floor 0) on SELL [code: service/ClientService.java]
- `Client` entity uses optimistic locking (`@Version`) to prevent concurrent cash mutation races [code: domain/Client.java]
- Kafka consumer retries with exponential backoff (1 s, 2 s, 4 s — 3 attempts) before publishing to DLT [code: config/KafkaConfig.java]
- ADMIN role cannot access portfolio endpoints (BRD Section 12.2) [decided RoleAuthorizationFilter.java comment]
- All money values are serialised as plain-string `BigDecimal` (scale 2 in responses, scale 4 for `avgPrice`) [code: service/ClientService.java, dto/response/]

## Must never
- Do not remove the `X-User-Role` filter or bypass it for HTTP endpoints — doing so removes the only RBAC layer.
- Do not expose `cashBalance`, `dailyOrderLimit`, or `tradingLimit` in `ClientProfileResponse`; those fields belong in `PortfolioResponse` only.
- Do not change the `reserved_cash` column — it was intentionally dropped in V7 and must not be re-added without a new migration.
- Do not bypass the optimistic-lock `@Version` field on `Client`; removing it will create lost-update races under concurrent Kafka fills.
- Do not allow the marketdata circuit breaker fallback to throw; it must return `null` so portfolio calculations degrade gracefully.

## Decisions & trade-offs
- **No Spring Security:** RBAC is implemented as a plain servlet filter because the gateway handles authentication; Spring Security would add complexity with no benefit here [code: filter/RoleAuthorizationFilter.java comment].
- **Redis cache for `/active`:** result is cached 30 s to avoid repeated full-table sorts; TTL is short enough to remain actionable for traders [code: service/ClientService.java].
- **BigDecimal as String in DTOs:** avoids JSON floating-point precision loss for money values [code: dto/response/].
- **MapStruct for entity→DTO mapping:** generated code, excluded from JaCoCo coverage [pom.xml JaCoCo excludes].
- **Lombok and domain excluded from JaCoCo:** entities and DTOs are data holders; no business logic to test [pom.xml JaCoCo excludes].

## Reference
- API — 5 endpoints, 1 controller; open CLAUDE.reference.md § API
- Data — 2 tables, 7 migrations; open CLAUDE.reference.md § Data
- Integrations — 1 outbound HTTP (marketdata-service), 2 Kafka topics consumed; open CLAUDE.reference.md § Integrations
- Configuration — 9 environment variables / config keys; open CLAUDE.reference.md § Configuration
- Tech debt — open CLAUDE.reference.md § Tech debt
- Open questions — open CLAUDE.reference.md § Open questions

Open CLAUDE.reference.md only for the section your task needs.
<!-- plumbline:end unit -->
