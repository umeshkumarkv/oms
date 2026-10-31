<!-- plumbline:template claude-root v3 -->
# Broker-Dealer OMS

<!-- plumbline:begin solution -->
Last updated: 39916bd (2026-10-31, /pl-onboard, claude-root v3)

## Solution

POC order management system: client onboarding, order submission + state machine, pre-trade risk, simulated fills, market-data WebSocket ticks, audit trail, SSE notifications. External traffic enters via Spring Cloud Gateway MVC (port 9090, JWT validation, Redis blacklist, `X-User-Id`/`X-Username`/`X-User-Role` injection). React frontend on port 3000.

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
| frontend | `frontend` | React SPA (Vite + nginx) | 3000 | Trader UI, E2E tests (`e2e/`) | `frontend/CLAUDE.md` |

## How they connect

**Shared infrastructure:** PostgreSQL 15 (`brokerdealer` DB, per-service schemas), Redis 7, Kafka 7.5.0 — Docker network `broker-dealer-net`.

**Service-to-service HTTP (bypass gateway):**

| Caller | Callee | Purpose |
|---|---|---|
| order-service | client-service | Client validation |
| order-service | marketdata-service | Symbol/price/market-hours check |
| order-service | execution-service | Fill status query for dashboard |
| client-service | marketdata-service | Portfolio price (CB, null fallback) |
| execution-service | marketdata-service | LIMIT price re-evaluation (2 s) |

**Kafka topics:**

| Topic | Producer | Consumers | Purpose |
|---|---|---|---|
| `order.state.changed` | order-service | audit, notification, client | Order state transitions |
| `risk.evaluation.requested` | order-service | risk-service | Risk check request |
| `risk.evaluation.result` | risk-service | order, notification | Risk result (PASSED/REJECTED/WARNED) |
| `order.execution.requested` | order-service | execution-service | Route to fill engine |
| `execution.completed` | execution-service | audit, notification, client | Fill result |

DLTs: `order.state.changed.DLT`, `risk.evaluation.requested.DLT`, `execution.completed.DLT`; 1 s→2 s→4 s, 3 attempts.

**Gateway routing:** `/api/v1/**` → downstream services; `SseProxyFilter` streams `/api/v1/notifications/stream` direct to notification-service:8088.

**Redis:** `auth:blacklist:<token>` (api-gateway/auth-service); dashboard keys 15 s (order); portfolio/balance/active-ranked 30 s (client); price/search/indices 10–60 s (market); dup-detect + execution cache 30 s (execution/risk); cache wired, unused (audit).

## Architecture

**Stack:** Java 21 · Spring Boot 3.2.4 · Spring Cloud 2023.0.1 (gateway) · Maven multi-module · PostgreSQL 15 (Flyway) · Kafka 7.5.0 · Redis 7 Alpine · JJWT 0.12.3 (HS256) · Lombok 1.18.30 · MapStruct 1.5.5.Final · Resilience4j 2.2.0 · JaCoCo 0.8.11 · amazoncorretto:21-alpine · virtual threads.

**Cross-cutting:**
- **Auth:** JWT HS256 at api-gateway only; downstream trusts forwarded headers. notification-service validates JWT directly (SSE bypasses gateway).
- **Authz:** `RoleAuthorizationFilter` in every service; no Spring Security. Roles: VIEWER/TRADER/OPERATIONS/ADMIN/SYSTEM. BRD 12.2 blocks ADMIN on order-write and market-data-read.
- **Error envelope:** `{"error":{...},"meta":{...}}` / `{"data":{...},"meta":{...}}` via `GlobalExceptionHandler`.
- **Money:** `BigDecimal`; serialised as plain strings; never `double`/`float`.
- **Kafka errors:** 1 s→2 s→4 s, 3 attempts, then `DeadLetterPublishingRecoverer`.
- **Resilience4j:** CB on outbound HTTP in order-service, client-service, execution-service (window 10, 50 %, 10 s wait).
- **JaCoCo:** 80 % line coverage in audit-service and notification-service.

## Decisions & trade-offs

| Decision | Source |
|---|---|
| Single Postgres, per-schema — one `brokerdealer` DB with isolated schemas (POC simplicity) | `docker-compose.yml` env vars |
| Gateway MVC not WebFlux — servlet stack throughout | `services/api-gateway/pom.xml` |
| SSE via `SseProxyFilter` — MVC 4.1.x buffers responses; filter at `LOWEST_PRECEDENCE-1` streams bytes | `SseProxyFilter.java` Javadoc |
| No Spring Security downstream — `OncePerRequestFilter` reads `X-User-Role` | `RoleAuthorizationFilter.java` |
| Kafka `Map<String, Object>` consumers — avoids `__TypeId__` coupling | `config/KafkaConfig.java` |
| Mock fill engine — deterministic simulation, no external venue | `FillSimulationService.java` |
| In-memory SSE registry — POC only; not restart-safe | notification-service CLAUDE.md |
| Risk maker/checker — second ADMIN required; self-approval blocked | `PendingChangeService.java` |
| GTC → GTD — BRD §14 rename via `V4__rename_gtc_to_gtd.sql` | order-service CLAUDE.md |
| Virtual threads platform-wide — `spring.threads.virtual.enabled: true`; no `ThreadLocal` middleware | `application.yml` |

## Conventions

- Logging: `com.brokerdealer` INFO; `org.springframework.kafka` WARN (downstream services; api-gateway logs `org.springframework.cloud.gateway` at WARN instead). [code: application.yml]
- Kafka consumers: `Map<String, Object>`, `USE_TYPE_INFO_HEADERS=false`. [code: config/KafkaConfig.java]
- Flyway schema: one schema per service, `ddl-auto: validate`, `baseline-on-migrate: true`. [code: application.yml]
- Virtual threads: `spring.threads.virtual.enabled: true` platform-wide; no `ThreadLocal` middleware. [code: application.yml]

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
- JWT_SECRET: local-dev placeholder; inject via Vault, Kubernetes Secret, or CI env var in non-local environments (decided 2026-10-30)

## Decisions needed

- Secrets found: `k8s/secret.yaml`: JWT_SECRET, POSTGRES_PASSWORD (base64 dev values); `docker-compose.yml`: JWT_SECRET, POSTGRES_PASSWORD, SPRING_DATASOURCE_PASSWORD — plaintext dev values; rotate before non-local deployment.
- Reachable without check: backend services publish docker-compose ports (8081–8088, 9090) — direct calls bypass api-gateway; service-to-service HTTP also bypasses it. k8s: backend ClusterIP; frontend NodePort. Apply network controls in production.
- Docs vs code: order-service `filter/RoleAuthorizationFilter.java` — ADMIN in ORDER_READ_ROLES despite "excluded per BRD 12.2" Javadoc; verify intent.
- Docs vs code: audit-service `filter/RoleAuthorizationFilter.java` — ADMIN in AUDIT_READ_ROLES despite "excluded per BRD 12.2" Javadoc; verify intent.

## Rules for every change

- **Security — JWT_SECRET:** Never hardcode; supply via env var; rotate immediately if exposed.
- **Security — identity headers:** `X-User-Id`/`X-Username`/`X-User-Role` must originate from api-gateway only; network controls enforce this in production.
- **Security — no Spring Security bypass:** Do not add `permitAll()` on authenticated paths; `RoleAuthorizationFilter` is the sole RBAC layer per service.
- **Security — BRD 12.2:** ADMIN must be denied order-write (`POST /api/v1/orders`) and market-data-read (`GET /api/v1/market/**`); check both `RoleAuthorizationFilter` instances when changing role rules.
- **Data — Flyway only:** Never set Hibernate `ddl-auto` to anything other than `validate`; all schema changes via versioned Flyway migrations.
- **Data — monetary values:** `BigDecimal` only; serialise as plain strings. Never `double`/`float` for money.
- **Data — cross-service ownership:** Each service owns one schema; cross-service data flows via Kafka or REST only.
- **Testing — Java 21:** `mvn test` requires Java 21; Lombok/MapStruct are incompatible with Java 24. CI enforces via `actions/setup-java@v4`.
- **Testing — JaCoCo gate:** 80 % line coverage in audit-service and notification-service; do not shrink exclusion lists without justification.
- **Kafka — DLT pattern:** Every consumer must use exponential back-off (1 s, 2 s, 4 s, 3 attempts) + `DeadLetterPublishingRecoverer`; do not remove DLT handling.
- **Kafka — consumer group IDs:** Do not change `groupId` values without coordinating an offset reset.
- **Virtual threads — ThreadLocal:** Do not introduce `ThreadLocal`-dependent middleware incompatible with virtual-thread pinning.
- **Client-facing deliverables:** All output must be reviewed by a qualified Wissen reviewer before client delivery.
<!-- plumbline:end solution -->
