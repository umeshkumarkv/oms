<!-- plumbline:template claude-root v2 -->
# Broker-Dealer OMS

<!-- plumbline:begin solution -->
Last updated: 4369a80 (2026-10-30, /pl-onboard, claude-root v2)

## Solution

The Broker-Dealer OMS is a proof-of-concept multi-service order management system for a broker-dealer. It covers the full pre-trade-to-post-trade lifecycle: client onboarding and portfolio management, order submission and state-machine-driven lifecycle, pre-trade risk evaluation against configurable rules, simulated execution with partial/full/rejected fill outcomes, market-data simulation with real-time WebSocket ticks, audit trail persistence, and real-time SSE notifications to browser clients. All external traffic enters through a single Spring Cloud Gateway MVC on port 9090 that validates JWTs, checks a Redis blacklist, and injects identity headers (`X-User-Id`, `X-Username`, `X-User-Role`) before routing to downstream services. A React frontend (Docker image `umeshkumarkv/frontend`, served on port 3000) provides the trader-facing UI.

## Units

| Unit | Folder | Kind | Port | Owns | CLAUDE.md |
|---|---|---|---|---|---|
| api-gateway | `services/api-gateway` | Spring Cloud Gateway MVC | 9090 | JWT validation, Redis blacklist, routing | `services/api-gateway/CLAUDE.md` |
| auth-service | `services/auth-service` | Spring Boot service | 8081 | User accounts, JWT issuance/revocation (`auth_schema`) | `services/auth-service/CLAUDE.md` |
| order-service | `services/order-service` | Spring Boot service | 8082 | Order lifecycle, state machine, `order_schema` | `services/order-service/CLAUDE.md` |
| client-service | `services/client-service` | Spring Boot service | 8083 | Client profiles, holdings, cash balances (`client_schema`) | `services/client-service/CLAUDE.md` |
| marketdata-service | `services/marketdata-service` | Spring Boot service | 8084 | Price simulation, tick history, watchlists (`market_schema`) | `services/marketdata-service/CLAUDE.md` |
| risk-service | `services/risk-service` | Spring Boot service | 8085 | Pre-trade risk rules, maker/checker workflow (`risk_schema`) | `services/risk-service/CLAUDE.md` |
| execution-service | `services/execution-service` | Spring Boot service | 8086 | Fill simulation, fill records, pending LIMIT orders (`execution_schema`) | `services/execution-service/CLAUDE.md` |
| audit-service | `services/audit-service` | Spring Boot service | 8087 | Immutable audit trail (`audit_schema`) | `services/audit-service/CLAUDE.md` |
| notification-service | `services/notification-service` | Spring Boot service | 8088 | SSE push to browser; no database | `services/notification-service/CLAUDE.md` |

## How they connect

**Shared infrastructure (all services):** Single PostgreSQL 15 instance (`brokerdealer` database), each service in its own schema. Single Redis 7 instance. Single Kafka broker (Confluent Platform 7.5.0 / Zookeeper). All run on Docker network `broker-dealer-net`.

**HTTP calls (service → service, bypassing the gateway):**

| Caller | Callee | Purpose |
|---|---|---|
| order-service | client-service (`/api/v1/clients/{id}/portfolio`) | Client validation at order submission |
| order-service | marketdata-service (`/api/v1/market/instrument/{symbol}`, `/api/v1/market/hours`, `/api/v1/market/holidays`) | Symbol/price/market-hours validation at order submission |
| order-service | execution-service (`/api/v1/executions/{orderId}`) | Fill status query for dashboard |
| client-service | marketdata-service (`/api/v1/market/instrument/{symbol}`) | Current price for portfolio valuation (Resilience4j circuit breaker, falls back to `null`) |
| execution-service | marketdata-service (`/api/v1/market/{symbol}`) | Current price for LIMIT order re-evaluation (scheduler, every 2 s) |

**Kafka topic flows:**

| Topic | Producer | Consumers | Purpose |
|---|---|---|---|
| `order.state.changed` | order-service | audit-service, notification-service, client-service | Order state transitions throughout the lifecycle |
| `risk.evaluation.requested` | order-service | risk-service | Pre-trade risk check request |
| `risk.evaluation.result` | risk-service | order-service, notification-service | Risk PASSED/REJECTED/WARNED result |
| `order.execution.requested` | order-service | execution-service | Route order to fill engine after risk PASSED |
| `execution.completed` | execution-service | audit-service, notification-service, client-service | Fill result for audit, push, and portfolio update |

Dead-letter topics (DLT) created by docker-compose: `order.state.changed.DLT`, `risk.evaluation.requested.DLT`, `execution.completed.DLT`; each consumer uses exponential back-off (1 s → 2 s → 4 s, 3 attempts) before routing to the DLT.

**Gateway → downstream HTTP routing:** api-gateway routes all `/api/v1/**` paths to the appropriate service. SSE path `GET /api/v1/notifications/stream` is proxied by `SseProxyFilter` directly to notification-service:8088 to avoid response buffering.

**Redis usage per service:**

| Service | Key pattern | Purpose |
|---|---|---|
| api-gateway | `auth:blacklist:<token>` | JWT revocation check |
| auth-service | `auth:blacklist:<token>` (write) | Writes blacklist entry on logout |
| order-service | Various dashboard/status-count keys | Cache with 15 s TTL |
| client-service | `client:active-ranked:today:{limit}`, `client:portfolio:{id}`, `client:balance:{id}` | Query caching (30 s TTL) |
| marketdata-service | Price, search, movers, indices keys | Cache with 10–60 s TTL |
| execution-service | Execution result cache | 30 s TTL |
| risk-service | `dup:{clientId}:{symbol}:{side}:{qty}:{limitPrice|MARKET}` | Duplicate-order detection (30 s TTL) |
| audit-service | Cache manager (wired, no `@Cacheable` annotations active) | Unused / future |

## Architecture

**Overall shape:** Microservices behind a single gateway. Nine independently deployable Spring Boot services plus a React frontend. No service mesh or inter-service discovery; downstream URLs are configured via environment variables. Docker Compose orchestrates local development and CI; Kubernetes (`k8s/`) is targeted for deployment (applied in CI on pushes to `develop`).

**Shared stack and versions:**
- Language: Java 21 (all services); Maven multi-module build (`broker-dealer-parent 1.0.0-SNAPSHOT`, root `pom.xml`).
- Framework: Spring Boot 3.2.4 (all services); Spring Cloud 2023.0.1 (gateway only).
- Build tool: Maven 3; `mvn clean verify` is the CI command; `mvn test` per service.
- Database: PostgreSQL 15; one database, per-service schemas; Flyway for migrations; Hibernate `ddl-auto: validate` everywhere.
- Messaging: Confluent Kafka 7.5.0; JSON serialisation; `Map<String, Object>` consumer pattern to avoid class-name header coupling.
- Cache: Redis 7 (Alpine); Spring Data Redis with JSON serialisation.
- Security library: JJWT 0.12.3 (HS256); same `JWT_SECRET` env var used by api-gateway, auth-service, and notification-service.
- Other libraries shared: Lombok 1.18.30, MapStruct 1.5.5.Final, Resilience4j 2.2.0, springdoc-openapi 2.3.0, JaCoCo 0.8.11.
- Container base: `amazoncorretto:21-alpine` for all service images.
- Virtual threads: `spring.threads.virtual.enabled: true` enabled in all services.

**Cross-cutting patterns:**

- **Authentication:** JWT Bearer (HS256) validated exclusively at the api-gateway. Downstream services trust the `X-User-Id`, `X-Username`, `X-User-Role` headers injected by the gateway. notification-service is the sole exception: it validates JWT directly (SSE connection lives outside a gateway HTTP proxy for streaming).
- **Authorization:** Every downstream service implements a `RoleAuthorizationFilter` (`OncePerRequestFilter`) that enforces `X-User-Role`; no Spring Security in downstream services. Role values: `VIEWER`, `TRADER`, `OPERATIONS`, `ADMIN`, `SYSTEM`. BRD Section 12.2 prohibits ADMIN from order-write and market-data-read paths.
- **Error envelope:** `{"error":{"code","message","details":[]},"meta":{"timestamp","requestId"}}` for errors; `{"data":{...},"meta":{...}}` for success. Applied uniformly via `@RestControllerAdvice` `GlobalExceptionHandler` in each service.
- **Monetary precision:** All money values use `BigDecimal`; serialised as plain strings in API responses and Kafka events. Never `double` or `float`.
- **Kafka error handling:** Exponential back-off retry (1 s, 2 s, 4 s; 3 attempts) then `DeadLetterPublishingRecoverer` — consistent across all consumer services.
- **Resilience4j circuit breakers:** Applied to all outbound HTTP calls in order-service and client-service (sliding window 10, failure threshold 50 %, open wait 10 s). execution-service uses a circuit breaker for market-data calls.
- **JaCoCo coverage gate:** 80 % line coverage minimum enforced in audit-service and notification-service; entry points, config beans, and generated mappers excluded.

## Decisions & trade-offs

- **Single PostgreSQL instance, per-service schemas:** All nine application services share one Postgres 15 instance (`brokerdealer` database) with isolated schemas (`auth_schema`, `order_schema`, etc.). This simplifies local development and Docker Compose setup for a POC while maintaining logical data ownership. Source: `docker-compose.yml` `SPRING_DATASOURCE_URL` env vars per service.
- **Spring Cloud Gateway MVC (servlet stack) rather than WebFlux Gateway:** The reactive gateway was not chosen; `spring-cloud-starter-gateway-mvc` keeps the whole platform on the servlet stack and avoids Reactor/WebFlux expertise. Source: `services/api-gateway/pom.xml` dependency selection; api-gateway CLAUDE.md Decisions section.
- **SSE proxied at servlet-filter level in api-gateway:** Gateway MVC 4.1.x buffers all proxied responses, breaking SSE streaming. `SseProxyFilter` at `LOWEST_PRECEDENCE - 1` intercepts `/api/v1/notifications/stream` and streams bytes directly. Source: `services/api-gateway/filter/SseProxyFilter.java` Javadoc (lines 27–40).
- **No Spring Security in downstream services:** Each service uses a plain `OncePerRequestFilter` reading the gateway-forwarded `X-User-Role` header. Comment pattern "No Spring Security dependency required" appears in every service's filter. Source: `filter/RoleAuthorizationFilter.java` across all downstream services.
- **Kafka deserialized to `Map<String, Object>`:** Avoids `__TypeId__` class-name coupling between producers and consumers across service boundaries. Source: `config/KafkaConfig.java` consumer configs in order-service, audit-service, notification-service.
- **Mock fill engine in execution-service:** Deterministic simulation (symbol overrides, quantity ranges) exercises the full fill lifecycle without an external venue dependency. Source: `services/execution-service/service/FillSimulationService.java` comment; `openapi.yaml` description.
- **In-memory SSE registry in notification-service:** Emitters held in JVM memory; connections do not survive a restart and events are lost across instances. Acceptable for a single-instance POC. Source: `pom.xml` comment ("POC"); notification-service CLAUDE.md Decisions section.
- **Risk maker/checker workflow:** Rule changes in risk-service require a second ADMIN to approve; self-approval is blocked in code. Source: `services/risk-service/service/PendingChangeService.java`.
- **`time_in_force` renamed GTC → GTD:** BRD Section 14 mandated the rename; enforced in DB migration `V4__rename_gtc_to_gtd.sql`. Source: order-service CLAUDE.md Rules section.
- **Virtual threads enabled platform-wide:** All services opt in to Java 21 virtual threads (`spring.threads.virtual.enabled: true`); `ThreadLocal`-dependent middleware must not be added. Source: `application.yml` across all services.

## Plumbline settings

- JIRA project: OMS
- Confluence space: OMS
- Confluence parent page: OMS Home
- Base branch: develop
- PR target: develop (features); main is a release branch that also accepts direct PRs (decided 2026-10-30)
- Branch pattern: `feature/OMS-<ticket>-<slug>` (decided 2026-10-30)
- Workflow: To Do → In Progress → In Review → Done
- API URL: http://localhost:9090
- Frontend URL: http://localhost:3000
- Frontend build: `./frontend` source context; `docker-compose.yml` supports both local build and pre-built image pull (decided 2026-10-30)
- JWT_SECRET: local-dev placeholder only; inject via Vault, Kubernetes Secret, or CI secret variable in non-local environments (decided 2026-10-30)

## Decisions needed

None — all items settled 2026-10-30.

## Rules for every change

- **Security — JWT_SECRET:** Never hardcode `JWT_SECRET` in source code or committed config files. It must be supplied via environment variable. Rotate immediately if exposed. [code: api-gateway `filter/JwtValidationFilter.java`, auth-service `service/JwtService.java`, notification-service `config/JwtAuthenticationFilter.java`]
- **Security — identity headers:** `X-User-Id`, `X-Username`, `X-User-Role` must originate exclusively from the api-gateway. Downstream services must not trust these headers from untrusted callers; network-level controls enforce this in production.
- **Security — no Spring Security bypass:** Do not add `permitAll()` on authenticated paths in any downstream service; `RoleAuthorizationFilter` is the sole RBAC layer for each service.
- **Security — BRD 12.2:** ADMIN role must be denied from order-write (`POST /api/v1/orders`) and market-data-read (`GET /api/v1/market/**`) paths. Check both `RoleAuthorizationFilter` instances when modifying role rules.
- **Data — schema migrations only via Flyway:** Never set Hibernate `ddl-auto` to anything other than `validate`. All schema changes must be new versioned Flyway migrations in the owning service.
- **Data — monetary values:** All money fields must use `BigDecimal`; serialise as plain strings in API responses and Kafka events. Never use `double` or `float` for money.
- **Data — cross-service data ownership:** Each service owns exactly one schema. No service may write to another service's schema directly; cross-service data flows through Kafka or REST.
- **Testing — Java 21 required:** All `mvn test` runs require Java 21. Lombok and MapStruct annotation processors are incompatible with Java 24. CI enforces Java 21 via `actions/setup-java@v4` with `java-version: '21'`.
- **Testing — JaCoCo gate:** audit-service and notification-service enforce 80 % line coverage; the build fails below this threshold. Do not reduce excluded class lists without justification.
- **Kafka — DLT pattern:** Every Kafka consumer must use exponential back-off (1 s, 2 s, 4 s, max 3 attempts) followed by `DeadLetterPublishingRecoverer`. Do not remove DLT error handling.
- **Kafka — consumer group IDs:** Do not change per-listener `groupId` values without coordinating an offset reset across the affected consumer group.
- **Virtual threads — ThreadLocal:** Virtual threads are enabled platform-wide. Do not introduce middleware or libraries that depend on `ThreadLocal` in ways incompatible with virtual-thread pinning.
- **Client-facing deliverables:** All code and configuration produced here must be reviewed by a qualified Wissen reviewer before delivery to the client. Treat all output as assistive, not authoritative.
<!-- plumbline:end solution -->
