# Broker-Dealer OMS — Architecture Overview

Generated from a full repo walkthrough on 2026-08-11. Reflects the state of the codebase at that time; re-derive rather than trust this blindly once services evolve.

## 1. What this is

A proof-of-concept Order Management System for a broker-dealer: order intake, pre-trade risk checks, simulated execution, client/portfolio state, market data, audit trail, and notifications. Nine Spring Boot microservices behind a gateway, plus a React frontend, sharing one Postgres instance (one schema per service) and Kafka for cross-service events.

```
                                   ┌─────────────┐
                                   │  Frontend   │  React 18 + Vite, served via nginx
                                   │ (port 3000) │
                                   └──────┬──────┘
                                          │ REST (/api/v1/**) + WS (/ws/v1) + SSE
                                          ▼
                                   ┌─────────────┐
                                   │ api-gateway │  Spring Cloud Gateway MVC, JWT validation,
                                   │ (9090/8080) │  header injection (X-User-Id/Role), SSE proxy
                                   └──────┬──────┘
              ┌───────────┬───────────────┼───────────────┬────────────┬─────────────┐
              ▼           ▼               ▼               ▼            ▼             ▼
        ┌──────────┐┌───────────┐  ┌─────────────┐ ┌────────────┐┌──────────┐┌──────────────┐
        │  auth    ││  client   │  │    order     │ │ marketdata ││   risk   ││  execution   │
        │ :8081    ││  :8083    │  │    :8082     │ │   :8084    ││  :8085   ││    :8086     │
        └──────────┘└─────┬─────┘  └──────┬───────┘ └─────┬──────┘└────┬─────┘└──────┬───────┘
                           │               │  Kafka        │            │  Kafka      │
                           │               │◀──────────────┼────────────┴─────────────┘
                           │ REST          │  events        REST (price lookups from
                           └───────────────┘                order/execution/client)
                                          │
                    ┌─────────────────────┼─────────────────────┐
                    ▼                                           ▼
             ┌──────────────┐                            ┌────────────────┐
             │ audit-service │◀──── order/execution ─────│ notification-   │
             │    :8087      │      events (Kafka)        │ service :8088   │
             └──────────────┘                            └────────────────┘
                                                            (SSE push, no DB)

        Shared infra: Postgres 15 (one DB, per-service schema) · Kafka+ZK · Redis 7
```

## 2. Service catalog

| Service | Port | Datastore | Role |
|---|---|---|---|
| api-gateway | 9090 (compose) / 8080 (k8s — **mismatch**) | Redis (JWT blacklist) | Single entry point; JWT validation, header injection, routing, SSE proxying |
| auth-service | 8081 | Postgres `auth_schema` | User accounts, login, HS256 JWT issuance |
| order-service | 8082 | Postgres `order_schema`, Redis (status-count cache) | Order lifecycle state machine, orchestrates risk + execution via Kafka |
| client-service | 8083 | Postgres `client_schema` | Client profile, cash balance, holdings/portfolio |
| marketdata-service | 8084 | Postgres `market_schema`, Redis (price cache) | Simulated price feed, WebSocket push, instrument search |
| risk-service | 8085 | Postgres `risk_schema`, Redis (dedup) | Rule-based pre-trade risk evaluation, maker-checker rule config |
| execution-service | 8086 | Postgres `execution_schema` | Simulated fills against a mock ECN |
| audit-service | 8087 | Postgres `audit_schema` | Immutable audit log of order/execution events |
| notification-service | 8088 | none (in-memory) | SSE push of order/execution/risk events to connected clients |
| frontend | 3000 (nginx) | — | React SPA: dashboard, order entry/blotter, portfolio, market data, admin, audit (stub) |

All nine backend services share one Maven parent (`broker-dealer-parent`, Spring Boot 3.2.4, Java 21, virtual threads enabled) and the same cross-cutting stack: Flyway (schema-owned DDL, Hibernate `ddl-auto: validate`), MapStruct, Lombok, springdoc-openapi, Resilience4j circuit breakers, JaCoCo at an 80% line-coverage gate.

## 3. Order lifecycle — the core event flow

```
Frontend                order-service            risk-service          execution-service
   │  POST /orders            │                        │                        │
   ├─────────────────────────▶│                        │                        │
   │                          │ risk.evaluation.requested (Kafka)                │
   │                          ├───────────────────────▶│                        │
   │                          │                        │ evaluates rules        │
   │                          │  risk.evaluation.result │                        │
   │                          │◀───────────────────────┤                        │
   │                          │  (also → notification-service, audit-service)   │
   │                          │                        │                        │
   │                          │ order.execution.requested (Kafka)                │
   │                          ├──────────────────────────────────────────────▶  │
   │                          │                        │      simulates fill    │
   │                          │        execution.completed (Kafka)              │
   │                          │◀──────────────────────────────────────────────  │
   │                          │  (also → notification-service, audit-service,   │
   │                          │   client-service for cash/holdings update)      │
   │  GET /orders/{id}        │                        │                        │
   ├─────────────────────────▶│  order.state.changed published at every         │
   │                          │  transition → audit-service, notification-svc  │
```

**Order states:** `NEW → VALIDATED → RISK_CHECK_PASSED|RISK_REJECTED → ACCEPTED → SENT_TO_EXECUTION → PARTIALLY_FILLED|FILLED`, with `CANCELLED`/`REJECTED`/`EXPIRED` as other terminal states. Enforced by `OrderStateMachineService` in order-service, the only service that writes to the `orders` table.

**Synchronous REST calls** exist alongside the Kafka backbone, mainly for order-service and execution-service to pull live data before/while acting:
- order-service → client-service (`GET /clients/{id}/portfolio`, for cash/holdings checks)
- order-service → marketdata-service (`GET /market/{instrument,hours,holidays}`)
- order-service → execution-service (`GET /executions/{orderId}`, for order-detail views)
- execution-service → marketdata-service (`GET /market/{symbol}`, for fill pricing and a 2s-interval pending-limit-order monitor)
- client-service → marketdata-service (to price holdings for the portfolio view)

Risk-service and auth-service have **no outbound REST calls to other services** — risk-service is purely event-driven (all context it needs arrives pre-enriched on the Kafka payload); auth-service is only called by the gateway.

### Kafka topics

| Topic | Producer | Consumers |
|---|---|---|
| `order.state.changed` | order-service | audit-service, notification-service, client-service (currently no-op) |
| `risk.evaluation.requested` | order-service | risk-service |
| `risk.evaluation.result` | risk-service | order-service, notification-service |
| `order.execution.requested` | order-service | execution-service |
| `execution.completed` | execution-service | order-service, audit-service, notification-service, client-service |

Every topic above has a paired `.DLT` dead-letter topic. order-service, risk-service, and execution-service wire a `DefaultErrorHandler` with exponential backoff (1s/2s/4s, 3 attempts) before publishing to the DLT; **audit-service and notification-service do not** — a bad message on their consumers retries indefinitely rather than dead-lettering.

marketdata-service and auth-service fully configure Kafka producer/consumer factories but **use none of it** — dead infrastructure in both.

## 4. Auth model

Self-issued JWT (HS256), not OAuth2/OIDC. `auth-service` issues 30-minute tokens (no refresh endpoint — expiry forces re-login) and maintains a Redis logout-blacklist (`auth:blacklist:<token>`). `api-gateway` is the only service that actually **verifies the JWT signature**; on success it injects `X-User-Id` / `X-Username` / `X-User-Role` headers, and every downstream service (order, client, market, risk, execution, audit) trusts those headers via its own `RoleAuthorizationFilter` rather than re-validating the token. Roles: `VIEWER, TRADER, OPERATIONS, ADMIN` (+ synthetic `SYSTEM` for service-to-service calls).

Implication: any service reachable directly (bypassing the gateway — true today inside the Docker/k8s network, since only the gateway and frontend are meant to be externally exposed) would accept a forged `X-User-Role` header with no cryptographic check.

## 5. Frontend

React 18 + TypeScript + Vite 5, React Router v6, Context+`useReducer` for state (no Redux), Axios for REST, native WebSocket for live prices, SSE (`@microsoft/fetch-event-source`) for notifications. No component/UI library — styling is inline plus a couple of plain CSS files.

Pages: Login, Dashboard, Order Blotter, Order Entry (TRADER-only), Portfolio, Market Data, Audit Trail, Admin Panel — role-gated via a `ProtectedRoute` wrapper. All REST traffic goes through the api-gateway (`baseURL: /api/v1`); the WebSocket market-data feed bypasses the gateway and hits marketdata-service directly. E2E coverage (Playwright) exists for every page including a spec that explicitly pins down that **Audit Trail is still a "coming soon" stub** despite having a full API client behind it.

## 6. Infrastructure

- **Local dev**: `docker-compose.yml` — one Postgres (7 schemas via `docker/postgres/init.sql`), one Kafka+ZooKeeper, one Redis, all 9 services, frontend via nginx. Shared dev `JWT_SECRET` hardcoded across services (expected for local).
- **Kubernetes**: namespace `broker-dealer-poc`; Kafka/ZK as StatefulSets with no PVCs (data loss on restart); Postgres as a StatefulSet with a 1Gi PVC; Redis as a Deployment. One Deployment+Service per microservice, all single-replica, ClusterIP, with liveness/readiness probes on `/actuator/health/*`. Only the frontend is externally reachable (NodePort) — **no Ingress**, so the gateway isn't reachable from outside the cluster as currently configured. Secrets are properly externalized (`secret.yaml` vs. a checked-in `secret.example.yaml` template).
- **CI** (`.github/workflows/ci.yml`): on push/PR to `develop`/`main` — build+test (`mvn clean verify`, JaCoCo gate) → `docker compose build` → on `develop` push, push images and `kubectl apply -f k8s/` to Minikube, with no manual approval gate and no frontend test/build step.
- **API tooling**: `.postman/` + `postman/` wire a workspace to each service's OpenAPI spec, but the actual Postman collection has only one sample request (auth login); most contract coverage is meant to come from the OpenAPI specs themselves.
- **OpenSpec**: `openspec/config.yaml` is populated with project context and rules; `openspec/changes/` and `openspec/specs/` are both empty — no changes proposed or specs synced yet.

## 7. Notable findings

Grouped by theme; useful as a starting punch-list, not a verdict — confirm each before acting on it.

**Schema/code drift**
- order-service: `V8__add_fill_or_kill_to_orders.sql` adds a `fill_or_kill` column with no corresponding field anywhere in the entity, DTOs, `OrderType`, or business logic — an orphaned migration (matches the untracked file seen in git status for this session).
- Five of nine services (order, risk, marketdata, client, auth) have an `openapi.yaml` that has drifted from the actual controllers — undocumented endpoints, wrong status codes/response shapes, or stale paths. Treat the specs as unreliable without cross-checking the controller.
- api-gateway: `openapi.yaml` server URL says port 8080; actual `server.port` is 9090. Also matches the k8s-vs-compose port mismatch noted below.

**Dead or vestigial code**
- Kafka is fully wired (producer/consumer factories, DLT config) in auth-service and marketdata-service but never used by either — either remove it or it's a half-built feature.
- `jjwt` is a declared dependency in order-service, risk-service, client-service, and execution-service, none of which actually parse/verify JWTs (they trust gateway-injected headers instead).
- client-service's `RoleAuthorizationFilter` still special-cases a `reserved-cash` route that references a column dropped from the schema in V7 — unreachable dead code.

**Consistency gaps**
- api-gateway container port is 8080 in Kubernetes manifests vs. 9090 in docker-compose — the two environments aren't equivalent as configured.
- marketdata-service's `AdminController` reads `X-User-Role` on `/scenario` but `Authorization` on `/reset` while still expecting a role string — likely a bug, not an intentional difference.
- audit-service's `GET /audit/trail` accepts `from`/`to`/`entityType` query params in its spec and controller signature, but the service layer never applies them — filtering is silently a no-op.
- Two parallel instrument-search methods exist in the frontend's `marketApi.ts` (`searchInstruments` / `searchInstrumentsV2`), suggesting an unfinished API migration.

**Resilience/HA gaps**
- Every k8s Deployment is single-replica with no HPA/PodDisruptionBudget; Kafka/ZooKeeper StatefulSets have no persistent volumes.
- notification-service holds SSE subscriptions in memory only — horizontally scaling it would silently drop pushes to clients connected to a different pod (no shared pub/sub backing it).
- audit-service and notification-service consumers have no DLT/backoff, unlike order/risk/execution — a poison message can block their consumer group indefinitely.

**Security-relevant (flagging for awareness, not a full security review)**
- Trust boundary: only api-gateway verifies the JWT signature; every other service trusts an `X-User-Role` header. If a service is ever reachable without going through the gateway, that header can be forged.
- Frontend stores the JWT in `localStorage` with no refresh flow — standard XSS exposure tradeoff, and re-login is required every 30 minutes.
- auth-service seed migrations hardcode bcrypt hashes of shared demo passwords directly in SQL (one migration, V11, exists specifically to fix a wrong hash from an earlier one) — fine for a POC/demo, but shouldn't be the pattern carried into anything closer to production.

**Incomplete features**
- Frontend Audit Trail page is a stub ("coming soon") despite a full API client and dedicated e2e spec already existing for it.
- execution-service's fills are entirely simulated against a hardcoded `"MOCK_ECN"` venue, with magic-value rules (specific symbols/quantity ranges trigger REJECTED/PARTIAL outcomes) — appropriate for a POC, but should be called out explicitly wherever this repo's scope or maturity gets described externally.

## 8. Where to look next

- If extending the order lifecycle: `order-service`'s `OrderStateMachineService` and `OrderEventProducer` are the center of gravity; almost every other service reacts to what it publishes.
- If adding a new cross-service data need: the existing pattern is Kafka events carrying pre-enriched payloads (risk-service never calls out for data), with synchronous REST reserved for read-oriented lookups (portfolio, pricing, holidays). `openspec/config.yaml`'s `operations.apply.guidance` codifies this preference explicitly.
- If touching auth/roles: changes need to stay consistent across auth-service (issues), api-gateway (verifies + injects headers), and every downstream service's `RoleAuthorizationFilter` (trusts headers) — there's no single source of truth today.
