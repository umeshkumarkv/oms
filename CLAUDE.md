<!-- plumbline:template claude-root v1 -->
# Broker-Dealer OMS

<!-- plumbline:begin solution -->
Last updated: 4369a80 (2026-10-28, /pl-onboard, claude-root v1)

## Solution
The Broker-Dealer OMS is a regulated equity order-management system composed of nine Spring Boot 3.2.4 / Java 21 microservices and a React/TypeScript frontend. It accepts trade orders from authenticated users, enforces risk rules and market-hours checks, routes accepted orders to an execution engine, and provides real-time status updates via Server-Sent Events. All nine backend services share one PostgreSQL 15 database (one schema per service), one Redis 7 instance, and one Kafka cluster (Confluent Platform 7.5.0 / Zookeeper 7.5.0). The API Gateway on port 9090 is the sole network ingress: it validates JWTs, checks the Redis token blacklist, injects identity headers, and reverse-proxies requests to downstream services. Docker Compose orchestrates the full stack locally; CI runs on GitHub Actions with Java 21 Temurin and deploys built images to Minikube.

## Units

| Unit | Folder | Kind | Port | Owns | CLAUDE.md |
|---|---|---|---|---|---|
| API Gateway | services/api-gateway | Spring Boot 3.2.4 | 9090 | JWT validation, request routing, SSE proxy | services/api-gateway/CLAUDE.md |
| Auth Service | services/auth-service | Spring Boot 3.2.4 | 8081 | auth_schema (users, sessions) | services/auth-service/CLAUDE.md |
| Order Service | services/order-service | Spring Boot 3.2.4 | 8082 | order_schema (orders, state machine, dashboard) | services/order-service/CLAUDE.md |
| Client Service | services/client-service | Spring Boot 3.2.4 | 8083 | client_schema (clients, portfolios, cash) | services/client-service/CLAUDE.md |
| Market Data Service | services/marketdata-service | Spring Boot 3.2.4 | 8084 | market_schema (instruments, prices, market hours) | services/marketdata-service/CLAUDE.md |
| Risk Service | services/risk-service | Spring Boot 3.2.4 | 8085 | risk_schema (risk rules, evaluations) | services/risk-service/CLAUDE.md |
| Execution Service | services/execution-service | Spring Boot 3.2.4 | 8086 | execution_schema (executions) | services/execution-service/CLAUDE.md |
| Audit Service | services/audit-service | Spring Boot 3.2.4 | 8087 | audit_schema (audit events) | services/audit-service/CLAUDE.md |
| Notification Service | services/notification-service | Spring Boot 3.2.4 | 8088 | No database schema; SSE fan-out only | services/notification-service/CLAUDE.md |
| Frontend | frontend | React/TypeScript (Vite) | 3000 | UI; calls API Gateway exclusively | frontend/CLAUDE.md |

## How they connect

**HTTP (synchronous)**
- Frontend → API Gateway (port 9090) — all UI REST/SSE traffic; gateway proxies each path prefix to the owning service. [code: services/api-gateway/src/main/resources/application.yml route table]
- Frontend → Market Data Service (8084) — WebSocket `/ws/v1` for live price ticks; Vite dev-proxy routes directly to port 8084. [code: frontend/CLAUDE.md integrations; frontend/vite.config.ts]
- API Gateway → Auth Service (8081) — `/api/v1/auth/**`, `/api/v1/users/**`
- API Gateway → Order Service (8082) — `/api/v1/orders/**`, `/api/v1/dashboard/**`
- API Gateway → Client Service (8083) — `/api/v1/clients/**`
- API Gateway → Market Data Service (8084) — `/api/v1/market/**`, `/api/v1/watchlists/**`
- API Gateway → Risk Service (8085) — `/api/v1/risk/**`
- API Gateway → Execution Service (8086) — `/api/v1/executions/**`
- API Gateway → Audit Service (8087) — `/api/v1/audit/**`
- API Gateway → Notification Service (8088) — `/api/v1/notifications/**` (SSE stream via SseProxyFilter)
- Order Service → Client Service (8083) — client validation, buying-power check; Resilience4j circuit-breaker. [code: services/order-service/src/main/resources/application.yml]
- Order Service → Market Data Service (8084) — market-hours and symbol tradability checks; Resilience4j circuit-breaker. [code: services/order-service/src/main/resources/application.yml]
- Order Service → Execution Service (8086) — execution status queries; Resilience4j circuit-breaker. [code: services/order-service/src/main/resources/application.yml]
- Execution Service → Market Data Service (8084) — instrument price lookup; configured via `MARKETDATA_SERVICE_URL`. [code: docker-compose.yml execution-service env]

**Kafka (asynchronous)**
- Order Service → Risk Service: `risk.evaluation.requested` (produced by Order, consumed by Risk)
- Risk Service → Order Service: `risk.evaluation.result` (produced by Risk, consumed by Order)
- Order Service → Execution Service: `order.execution.requested` (produced by Order, consumed by Execution)
- Execution Service → Order Service: `execution.completed` (produced by Execution, consumed by Order)
- Order Service → Audit Service: `order.state.changed` (Audit consumes)
- Execution Service → Audit Service: `execution.completed` (Audit consumes)
- Order/Execution/Risk → Notification Service: `order.state.changed`, `execution.completed`, `risk.evaluation.result` (Notification consumes for SSE fan-out)

Dead-letter topics provisioned: `order.state.changed.DLT`, `risk.evaluation.requested.DLT`, `execution.completed.DLT`. [code: docker-compose.yml KAFKA_CREATE_TOPICS]

**Shared infrastructure**
- All nine backend services connect to a single PostgreSQL 15 database (`brokerdealer`) — each via its own schema (auth_schema, order_schema, client_schema, market_schema, risk_schema, execution_schema, audit_schema). Schemas are created by `docker/postgres/init.sql`; no schema is created by a service other than its own Flyway migrations. [code: docker/postgres/init.sql, docker-compose.yml datasource URLs]
- All nine backend services connect to the same Redis 7 instance (port 6379). Auth Service and API Gateway use Redis for the JWT token blacklist; other services use it for caching. [code: docker-compose.yml]
- All nine backend services connect to the same Kafka cluster (port 9092). [code: docker-compose.yml]

## Architecture

**Overall shape:** Event-driven microservices. Nine independently deployable Spring Boot services sit behind a single API Gateway. Async workflows (risk evaluation, order execution) run over Kafka; synchronous lookups (client data, market data) use HTTP with Resilience4j circuit-breakers and time-limiters.

**Shared stack:** Java 21, Spring Boot 3.2.4, Maven (multi-module root pom), PostgreSQL 15, Redis 7-alpine, Kafka (Confluent Platform 7.5.0), Docker Compose 3.9, GitHub Actions CI (ubuntu-latest, Java 21 Temurin), Minikube (k8s/ manifests applied on push to develop).

**Cross-cutting patterns:**
- **JWT auth (stateless):** Auth Service mints HS256 JWTs (30-minute TTL) signed with `JWT_SECRET`. API Gateway validates every token on ingress, checks the Redis blacklist, and injects `X-User-Id`, `X-Username`, `X-User-Role` headers into forwarded requests. Downstream services read identity from those headers; they do not re-validate the JWT. [code: services/auth-service/src/.../JwtService.java, services/api-gateway/src/.../JwtValidationFilter.java]
- **Role enforcement:** API Gateway does not enforce RBAC; it passes the role header through. Each service enforces its own role requirements via a filter or `@PreAuthorize`. [code: services/api-gateway/src/.../SecurityConfig.java, services/order-service/.../RoleAuthorizationFilter.java, services/auth-service/.../UserController.java]
- **Error envelope:** `{ error: { code, message, details }, meta: { timestamp, requestId } }` for errors; `{ data: ..., meta: { timestamp, requestId } }` for success. Used across all services with a `GlobalExceptionHandler`. [code: services/auth-service/.../GlobalExceptionHandler.java, services/api-gateway/.../JwtValidationFilter.java]
- **Virtual threads:** `spring.threads.virtual.enabled: true` in every service. Outbound HTTP uses `JdkClientHttpRequestFactory` with no connection pool in order-service. [code: application.yml files, services/order-service/.../HttpClientConfig.java]
- **Flyway:** Each service owns its own migrations under `classpath:db/migration` against its schema; `baseline-on-migrate: true`; Hibernate `ddl-auto: validate` in all services. [code: application.yml files]
- **Resilience4j:** Circuit-breakers and time-limiters on all inter-service HTTP calls from Order Service (sliding-window 10, 50% failure threshold, 10s open-state wait, 2s timeout). [code: services/order-service/src/main/resources/application.yml]
- **Kafka consumer back-off:** Exponential back-off (1s → 2s → 4s, 3 attempts) then dead-letter topic. [code: services/order-service/.../KafkaConfig.java]
- **Monetary values:** BigDecimal throughout; enforced by PR checklist. [code: .github/PULL_REQUEST_TEMPLATE.md]
- **CI pipeline:** `mvn clean verify` → `docker compose build` → push images (on develop) → `kubectl apply -f k8s/` (on develop). [code: .github/workflows/ci.yml]

## Decisions & trade-offs

- **Microservices with a shared PostgreSQL database (separate schemas):** Services are independently deployable but share one Postgres instance to reduce infrastructure cost at POC scale. Schema isolation (one schema per service, no cross-schema queries) preserves logical data ownership. [code: docker/postgres/init.sql, docker-compose.yml datasource URLs]
- **Kafka for the risk and execution workflows:** Risk evaluation and order execution are potentially slow and must not block the HTTP response path. Async Kafka events decouple the workflows and allow each service to retry independently via DLTs. [code: docker-compose.yml KAFKA_CREATE_TOPICS, services/order-service/.../KafkaConfig.java]
- **Servlet-stack Spring Cloud Gateway MVC (not WebFlux reactive):** Gateway MVC keeps filters synchronous. SSE streams require a raw-servlet bypass (`SseProxyFilter`) to avoid MVC response buffering. [code: services/api-gateway/src/.../SseProxyFilter.java class-level Javadoc]
- **JWT token blacklist in Redis:** TTL on each blacklist entry mirrors the token expiry, eliminating a cleanup job. [code: services/auth-service/src/.../JwtService.java]
- **JWT_SECRET via environment variable only:** Secret is never baked into application.yml or Docker images; docker-compose supplies a dev placeholder with a comment to replace in production. [code: docker-compose.yml]
- **Virtual threads on all services:** Blocking I/O (JDBC, Redis, Kafka, HTTP proxy) does not consume OS threads; no thread-pool tuning required. Order Service uses `JdkClientHttpRequestFactory` with no pool for the same reason. [code: application.yml files, services/order-service/.../HttpClientConfig.java]
- **GTC renamed to GTD:** Time-in-force `GTC` was renamed `GTD` per BRD Section 14. Migration `V4__rename_gtc_to_gtd.sql` in order-service handles the rename. [decided services/order-service/src/main/resources/db/migration/V4__rename_gtc_to_gtd.sql]
- **No Kubernetes manifests in this export:** `k8s/` folder is referenced in CI (`kubectl apply -f k8s/`) but the directory was stripped in commit `aeaa272`. Deployment to Minikube will fail until manifests are restored. [code: .github/workflows/ci.yml; git commit aeaa272]

## Plumbline settings

- **JIRA project:** OMS (user-specified; see Decisions needed below)
- **Confluence space:** OMS
- **Confluence parent page:** TBD
- **Base branch:** develop
- **PR target:** develop
- **Branch pattern:** `ai/<key>-<n>-<description>` (from git history: `ai/SCRUM-178-...`, `ai/SCRUM-179-...`)
- **API URL:** http://localhost:9090
- **Frontend URL:** http://localhost:3000

## Decisions needed

1. **JIRA project key — three values in use:** User specified `OMS`; the PR template (`.github/PULL_REQUEST_TEMPLATE.md` line 2) uses `BD-[story number]`; recent branch names in git history use `SCRUM` (e.g. `ai/SCRUM-178-...`, `ai/SCRUM-179-...`). All three appear in different artefacts. A team owner must declare the canonical key so PR links, branch names, and Plumbline settings align.
2. **PR target branch — CI triggers on both `develop` and `main`:** The CI workflow (`.github/workflows/ci.yml` line 7: `pull_request: branches: [ develop, main ]`) accepts PRs to either branch. The Base branch setting above and the PR template imply `develop` is primary. Confirm whether PRs to `main` are permitted and under what conditions.
3. **Confluence parent page:** Not specified anywhere in the repository. Required for Plumbline hand-offs. A team owner must supply the page title or ID.

## Rules for every change

- No service may read or write another service's database schema. Each service connects only to its own schema via its datasource URL. [code: docker-compose.yml datasource URLs, docker/postgres/init.sql]
- All monetary values must use `BigDecimal` — never `float` or `double`. [code: .github/PULL_REQUEST_TEMPLATE.md checklist]
- `JWT_SECRET` must come from the environment variable; never hardcode it in source files, application.yml, or migration scripts. [code: docker-compose.yml, services/auth-service/.../JwtService.java]
- All order state transitions must be published to the `order.state.changed` Kafka topic. [code: .github/PULL_REQUEST_TEMPLATE.md compliance checklist]
- Each service owns its own Flyway migrations; cross-service migrations are not permitted. Hibernate `ddl-auto` must remain `validate`. [code: application.yml files]
- Kafka consumer failures must go to the DLT after 3 attempts with exponential back-off (1s, 2s, 4s). [code: services/order-service/.../KafkaConfig.java]
- Test line coverage must be ≥ 80% (JaCoCo) per CI pipeline. [code: .github/workflows/ci.yml, pom.xml JaCoCo config]
- PR descriptions must follow `.github/PULL_REQUEST_TEMPLATE.md`. All checklist items must pass before merge. [code: .github/PULL_REQUEST_TEMPLATE.md]
- API response format must match the error/success envelope (`{ error/data, meta }`). [code: services/auth-service/.../GlobalExceptionHandler.java]
- Kafka topic names used in code must match the names declared in `docker-compose.yml` (`KAFKA_CREATE_TOPICS`). [code: .github/PULL_REQUEST_TEMPLATE.md, docker-compose.yml]
- Market-hours logic must use the `America/New_York` timezone. [code: .github/PULL_REQUEST_TEMPLATE.md compliance checklist]
- Fat-finger checks must be present for order submission. [code: .github/PULL_REQUEST_TEMPLATE.md compliance checklist]
- Audit events must be published on every order state transition. [code: .github/PULL_REQUEST_TEMPLATE.md compliance checklist]
- Risk rules must be implemented as pluggable components. [code: .github/PULL_REQUEST_TEMPLATE.md compliance checklist]

— Plumbline 2.8.7 · /pl-onboard · claude-root v1 · commit 4369a80 · judge pending · AI-drafted, posted after SME review
<!-- plumbline:end solution -->
