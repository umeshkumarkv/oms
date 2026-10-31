<!-- plumbline:template claude-unit v2 -->
# client-service

<!-- plumbline:begin unit -->
Last updated: 39916bd (2026-10-30, /pl-onboard, claude-unit v2)

## Purpose
Manages broker-dealer client (customer) accounts. Owns client profiles, KYC state, cash balances,
equity holdings, and per-client routing preferences. Exposes REST endpoints for profile/portfolio
reads and default-route updates. Consumes Kafka fill events to update holdings and cash balances
in real time.

## Build & run
```
mvn test             # run tests
mvn spring-boot:run  # start service
```
Port: **8083**. No active Spring profiles; configuration via environment variables (see Configuration in CLAUDE.reference.md).

**Test recipe:** `mvn test` · Java 21 required · **FAILED** — `ExceptionInInitializerError: TypeTag::UNKNOWN` — Lombok/MapStruct incompatible with Java 24; use Java 21 · 2026-10-30

## Architecture & structure
- **Stack:** Java 21, Spring Boot 3.2.4, Spring Data JPA (Hibernate), Spring Data Redis, Spring Kafka, Flyway, PostgreSQL, MapStruct 1.5.5.Final, Lombok 1.18.30, Resilience4j 2.2.0, springdoc-openapi 2.3.0
- **Layer pattern:** `controller` → `service` → `repository` (JPA); Kafka consumer calls `service` directly; `client` package holds outbound HTTP to market-data
- **Packages:** `controller` (`ClientController`), `service` (`ClientService`), `repository` (`ClientRepository`, `HoldingRepository`), `domain` (entities `Client`, `Holding`; enums), `dto` (DTOs), `mapper` (`ClientMapper`, MapStruct), `kafka/consumer` (`OrderEventConsumer`), `client` (`MarketDataClient`, Resilience4j), `filter` (`RoleAuthorizationFilter`), `config` (`KafkaConfig`, `RedisConfig`), `exception` (`GlobalExceptionHandler`)
- **Database schema:** `client_schema` (Flyway-managed, 7 migrations V1–V7)
- **Virtual threads:** enabled (`spring.threads.virtual.enabled: true`)

## Security
- Identity: `X-User-Role` header forwarded by api-gateway; no JWT parsing.
- Verified at: filter/RoleAuthorizationFilter.java
- Reachable without that check: docker-compose: yes, port 8083 published (docker-compose.yml); k8s: no, ClusterIP (k8s/services/client-service-service.yaml).
- No Spring Security; missing/blank role → 401; insufficient role → 403. [code: filter/RoleAuthorizationFilter.java]
- Role matrix: `GET /clients/**` VIEWER+; `GET /clients/active` TRADER+; `GET /clients/{id}/portfolio` VIEWER/TRADER/OPERATIONS/SYSTEM (ADMIN denied — BRD 12.2); `PATCH /clients/{id}/default-route` ADMIN only. [code: filter/RoleAuthorizationFilter.java]

## Rules
- `limit` for `/active` must be 1–50 inclusive; outside values throw `VALIDATION_ERROR` [code: service/ClientService.java]
- Active-client results cached in Redis under `client:active-ranked:today:{limit}` for 30 s; Redis failures degrade gracefully [code: service/ClientService.java]
- Portfolio/balance caches (`client:portfolio:{id}`, `client:balance:{id}`) evicted on `updateDefaultRoute` and `processExecutionFill` [code: service/ClientService.java]
- Default route must be `NASDAQ`, `NYSE`, `ARCA`, or `BATS`; other values throw `INVALID_ROUTE` [code: service/ClientService.java]
- BUY REJECTED fills do not mutate cash balance or turnover [code: service/ClientService.java]
- BUY fills subtract `fillValue` from `cashBalance` and add to `turnoverToday`; SELL fills add `fillValue` to both [code: service/ClientService.java]
- Holdings upserted (weighted-average price) on BUY; quantity reduced (floor 0) on SELL [code: service/ClientService.java]
- `Client` entity uses optimistic locking (`@Version`) to prevent concurrent cash mutation races [code: domain/Client.java]
- Kafka consumer retries with exponential backoff (1 s, 2 s, 4 s — 3 attempts) then publishes to DLT [code: config/KafkaConfig.java]
- ADMIN role cannot access portfolio endpoints (BRD 12.2) [decided RoleAuthorizationFilter.java comment]
- All money values serialised as plain-string `BigDecimal` (scale 2 in responses, scale 4 for `avgPrice`) [code: service/ClientService.java, dto/response/]

## Must never
- Do not remove or bypass `RoleAuthorizationFilter` — it is the only RBAC layer.
- Do not expose `cashBalance`, `dailyOrderLimit`, or `tradingLimit` in `ClientProfileResponse`; those belong in `PortfolioResponse` only.
- Do not re-add the `reserved_cash` column — intentionally dropped in V7; any restoration requires a new migration.
- Do not remove the optimistic-lock `@Version` field on `Client`; removal causes lost-update races under concurrent Kafka fills.
- Do not let the marketdata circuit-breaker fallback throw; it must return `null` so portfolio calculations degrade gracefully.

## Decisions & trade-offs
- **No Spring Security:** plain servlet filter because the gateway handles authentication; Spring Security would add complexity with no benefit [code: filter/RoleAuthorizationFilter.java comment].
- **Redis cache for `/active`:** 30 s TTL avoids repeated full-table sorts while remaining actionable [code: service/ClientService.java].
- **BigDecimal as String in DTOs:** avoids JSON floating-point precision loss for money values [code: dto/response/].
- **MapStruct and Lombok/domain excluded from JaCoCo:** generated/data-holder code [pom.xml JaCoCo excludes].

## Reference
- API — 5 endpoints, 1 controller; open CLAUDE.reference.md § API
- Data — 2 tables, 7 migrations; open CLAUDE.reference.md § Data
- Integrations — 1 outbound HTTP (marketdata-service), 2 Kafka topics consumed; open CLAUDE.reference.md § Integrations
- Configuration — 9 environment variables / config keys; open CLAUDE.reference.md § Configuration
- Tech debt — open CLAUDE.reference.md § Tech debt
- Open questions — open CLAUDE.reference.md § Open questions

Open CLAUDE.reference.md only for the section your task needs.
<!-- plumbline:end unit -->
