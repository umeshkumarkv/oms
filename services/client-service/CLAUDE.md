<!-- plumbline:template claude-unit v1 -->
# Client Service

<!-- plumbline:begin unit -->
Last updated: 4369a80 (2026-10-28, /pl-onboard, claude-unit v1)

## Purpose
Manages broker-dealer client profiles, cash balances, equity holdings, and order-routing preferences.
Exposes a REST API for reading client data and updating the default exchange route.
Consumes Kafka execution events to keep cash and holdings up to date after every fill.
Caches the active-client ranking in Redis (30 s TTL) and calls market-data-service to compute live portfolio valuations.

## Build & run
```
# from repo root or this folder
./mvnw clean package -pl services/client-service -am
./mvnw spring-boot:run -pl services/client-service
# or inside this folder
mvn clean package
mvn spring-boot:run
```
Port: **8083** (hardcoded in `src/main/resources/application.yml`)

Docker (requires kafka=healthy, redis=started, postgres=started):
```
docker compose up client-service
```

Test recipe: `mvn test` · requires Java 21 (`<java.version>21</java.version>` in root `pom.xml`) · not run — Java 24 installed locally is incompatible with maven-compiler-plugin 3.11.0; first error: `Fatal error compiling: java.lang.ExceptionInInitializerError: com.sun.tools.javac.code.TypeTag :: UNKNOWN` · 2026-10-28

No hard-coded absolute file-system paths in startup or migration scripts.

## Architecture & structure
- **Language / runtime:** Java 21, Spring Boot 3.2.4
- **Build:** Maven (parent `broker-dealer-parent` 1.0.0-SNAPSHOT), maven-compiler-plugin Java 21
- **Database:** PostgreSQL 15, schema `client_schema`, Flyway V1–V7, JPA/Hibernate (ddl-auto: validate)
- **Cache:** Redis 7 (Spring Data Redis, `RedisTemplate<String,Object>`, 30 s TTL via `RedisCacheManager`)
- **Messaging:** Apache Kafka (Spring Kafka), consumer group `client-service-group`; DLT with exponential back-off (1 s/2 s/4 s, 3 attempts) in `KafkaConfig`
- **HTTP outbound:** `RestTemplate` guarded by Resilience4j circuit breaker (`marketdata` instance)
- **API docs:** springdoc-openapi 2.3.0 (Swagger UI at `/swagger-ui.html`)
- **Layers:** `controller` → `service` → `repository`; MapStruct 1.5.5 (`ClientMapper`) for DTOs; domain enums in `domain/`
- **Key packages:** `controller` (ClientController, 5 endpoints), `service` (ClientService — business logic, cache, Kafka), `repository` (ClientRepository, HoldingRepository), `kafka/consumer` (OrderEventConsumer), `client` (MarketDataClient), `filter` (RoleAuthorizationFilter), `config` (KafkaConfig, RedisConfig, RestTemplateConfig), `domain` (Client, Holding entities; AccountType/ClientStatus/DefaultRoute/KycStatus enums), `exception` (GlobalExceptionHandler, ClientNotFoundException, ValidationException), `mapper` (ClientMapper), `dto/request`, `dto/response`

## Security
- No Spring Security. `RoleAuthorizationFilter` (`filter/RoleAuthorizationFilter.java`, `OncePerRequestFilter`) enforces access control.
- Role from `X-User-Role` header injected by the API gateway; no JWT parsing here.
- **Permission matrix** (`filter/RoleAuthorizationFilter.java`):
  - `GET /api/v1/clients/**` — VIEWER, TRADER, OPERATIONS, ADMIN
  - `GET /api/v1/clients/active` — TRADER, OPERATIONS, ADMIN (VIEWER excluded)
  - `GET /api/v1/clients/{id}/portfolio` — VIEWER, TRADER, OPERATIONS; ADMIN blocked (BRD 12.2)
  - `PATCH /api/v1/clients/{id}/default-route` — ADMIN only
  - `PATCH /api/v1/clients/{id}/reserved-cash` — exempt (system-to-system, no role header required)
  - `/actuator/**` and `OPTIONS` — always exempt
- SYSTEM role in `ALL_ROLES` is not blocked on portfolio GET.
- JWTs not stored here. Credentials (`SPRING_DATASOURCE_PASSWORD`, `SPRING_REDIS_*`, `JWT_SECRET`) from docker-compose or deployment platform.

## Rules
- Active-client limit must be 1–50; outside range raises `ValidationException` [code: `service/ClientService.java`]
- `defaultRoute` must be NASDAQ, NYSE, ARCA, or BATS; other values raise `ValidationException` [code: `service/ClientService.java`]
- ADMIN denied `GET /api/v1/clients/{id}/portfolio` per BRD Section 12.2 [code: `filter/RoleAuthorizationFilter.java`]
- Missing `X-User-Role` header (non-exempt paths) returns HTTP 401 [code: `filter/RoleAuthorizationFilter.java`]
- Rejected BUY fills do not mutate cash; only non-rejected BUY fills deduct cash [code: `service/ClientService.java`]
- Rejected SELL fills are silently ignored (no cash credit, no holding change) [code: `service/ClientService.java`]
- Optimistic locking (`@Version`) on `Client` prevents lost updates [code: `domain/Client.java`]
- BUY fill: cash decremented by `fillValue`, `turnoverToday` incremented, holding upserted at weighted-average price [code: `service/ClientService.java`]
- SELL fill: cash incremented by `fillValue`, `turnoverToday` incremented, holding quantity reduced (floor 0) [code: `service/ClientService.java`]
- Cache evicted on `updateDefaultRoute` or execution-fill mutation [code: `service/ClientService.java`]
- Active-client results cached at `client:active-ranked:today:<limit>` for 30 s; Redis errors swallowed (warn log) [code: `service/ClientService.java`]
- Market-data fetch uses Resilience4j circuit breaker; fallback returns `null` prices/PnL [code: `client/MarketDataClient.java`]
- Flyway manages schema; `ddl-auto: validate` — Hibernate never alters tables [code: `src/main/resources/application.yml`]
- JaCoCo 80% line-coverage gate; domain, DTOs, config, mappers excluded [code: `pom.xml`]

## Must never
- Do not add JWT parsing or Spring Security; the gateway owns auth and forwards `X-User-Role`.
- Do not change `ddl-auto` from `validate`; schema changes require a new Flyway migration file.
- Do not grant ADMIN access to portfolio endpoints; BRD compliance rule in `RoleAuthorizationFilter`.
- Do not rethrow Redis exceptions as hard failures; warn-log and fall through (cache-aside).
- Do not add schema-altering DDL outside Flyway migration scripts.

## Decisions & trade-offs
- **No Spring Security:** Plain servlet filter reads `X-User-Role` forwarded by the gateway; avoids JWT dependency in every downstream service; trade-off is gateway-bypass spoofing risk. Source: `filter/RoleAuthorizationFilter.java` Javadoc.
- **Virtual threads:** `spring.threads.virtual.enabled: true` uses Project Loom for I/O-bound handling. Source: `src/main/resources/application.yml`.
- **Cache-aside with soft failure:** Redis failures are caught, logged WARN, and fall through to DB. Source: `service/ClientService.java`.
- **Kafka DLT with exponential back-off:** 3 retries (1 s/2 s/4 s) then `.DLT` topics to avoid partition blocking. Source: `config/KafkaConfig.java` comment.
- **`order.state.changed` consumer is a stub:** `OrderEventConsumer.consumeOrderStateChanged` only logs; no action taken. Source: `kafka/consumer/OrderEventConsumer.java`.
- **MapStruct for DTO mapping:** Generated code avoids reflection; `ClientMapper` maps `Client.id` → `ClientProfileResponse.clientId`. Source: `mapper/ClientMapper.java`.

## Reference
- API — 5 endpoints, 1 controller (`ClientController`)
- Data — 2 entities (`clients`, `holdings`), 7 Flyway migrations
- Integrations — 1 outbound HTTP call (marketdata-service), 2 Kafka topics consumed, 1 Redis cache
- Configuration — 8 environment variables / config keys
- Tech debt — 0 TODO/FIXME found in `src/main/`
- Open questions — 2 items

Open CLAUDE.reference.md only for the section your task needs.

— Plumbline 2.8.7 · /pl-onboard · claude-unit v1 · commit 4369a80 · judge pending · AI-drafted, posted after SME review
<!-- plumbline:end unit -->
