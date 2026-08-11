# Broker Dealer OMS — Architecture Overview

> High-level map of the system as it exists in this repo today. Generated from a
> full repo walkthrough (backend services, frontend, infra/CI) on 2026-08-11.
> This is a snapshot, not a spec — re-derive from code when it drifts.

## 1. What this is

A proof-of-concept **Order Management System (OMS)** for a broker-dealer: a
trader submits an order, it's checked against risk rules, routed for
execution, and every state change is pushed live to the UI and recorded for
audit — all coordinated across 9 independently-deployable Spring Boot
services plus a React frontend.

## 2. System topology

```
                                   ┌─────────────────────┐
                                   │      Browser          │
                                   │  (React SPA, :3000)   │
                                   └──────────┬─────────────┘
                                              │ HTTPS (REST + SSE + WS)
                                              ▼
                                   ┌─────────────────────┐
                                   │     api-gateway        │  :9090
                                   │ Spring Cloud Gateway   │
                                   │ MVC + JWT validation   │
                                   │ + SSE proxy filter     │
                                   └──────────┬─────────────┘
                     ┌────────────┬───────────┼───────────┬────────────┬─────────────┐
                     ▼            ▼           ▼           ▼            ▼             ▼
              ┌───────────┐ ┌───────────┐ ┌─────────┐ ┌─────────┐ ┌──────────┐ ┌─────────────┐
              │   auth    │ │  client   │ │  order  │ │  risk   │ │execution │ │ marketdata  │
              │  :8081    │ │  :8083    │ │  :8082  │ │ :8085   │ │  :8086   │ │   :8084     │
              └───────────┘ └───────────┘ └────┬────┘ └────┬────┘ └────┬─────┘ └──────┬──────┘
                     ┌───────────┐              │            │            │              ▲
                     │  audit    │◀─────────────┴────────────┴────────────┘   REST calls │
                     │  :8087    │                     Kafka events               (order/client/
                     └───────────┘                                                 execution →
                     ┌───────────┐                                                 marketdata)
                     │notification│◀── SSE fan-out to api-gateway → browser
                     │  :8088    │
                     └───────────┘

   Shared infra:  Kafka (+Zookeeper)  ·  Redis  ·  PostgreSQL (one schema per service)
```

Every service builds on the same Spring Boot 3.2.4 / Java 21 parent POM
(`pom.xml`), so cross-cutting libraries (Lombok, MapStruct, Resilience4j,
springdoc-openapi, jjwt) are consistent across the fleet.

## 3. Tech stack

| Layer | Choices |
|---|---|
| Backend runtime | Java 21, Spring Boot 3.2.4, Spring Cloud 2023.0.1 |
| API gateway | Spring Cloud Gateway MVC (declarative routing) + custom JWT/SSE filters |
| Persistence | PostgreSQL 15, one schema per service, Flyway migrations (`V{n}__desc.sql`) |
| Messaging | Apache Kafka (Confluent images), `spring-kafka`, per-topic `.DLT` dead-letter topics |
| Cache/session | Redis 7 |
| Auth | JWT (`jjwt`), validated at the gateway; each service also validates locally |
| Resilience | Resilience4j circuit breakers around all outbound REST calls |
| Mapping/boilerplate | MapStruct, Lombok |
| API docs | springdoc-openapi (Swagger UI) per service |
| Testing (backend) | JUnit + `spring-boot-starter-test`, JaCoCo (80% line-coverage rule defined in parent POM, but **not enforced as a CI gate** — see §7) |
| Frontend | React 18, TypeScript, Vite 5, react-router-dom 6 |
| Frontend data | Axios (REST), native `WebSocket` (live market ticks), `@microsoft/fetch-event-source` (SSE notifications) |
| Frontend testing | Vitest + Testing Library + jsdom (unit), Playwright (e2e) |
| Containers/orchestration | Docker (multi-stage `maven:3.9-amazoncorretto-21` → `amazoncorretto:21-alpine`), Docker Compose (local), Kubernetes manifests under `k8s/` |
| CI/CD | GitHub Actions (`.github/workflows/ci.yml`) |

## 4. Service catalog

| Service | Port | Purpose | Own DB? | Kafka in | Kafka out | Calls out to |
|---|---|---|---|---|---|---|
| **api-gateway** | 9090 | Routes all client traffic, validates JWT, proxies SSE | – (Redis only) | – | – | all services (routing) |
| **auth-service** | 8081 | Login/logout, user + role management | ✅ (12 migrations) | – | – | – |
| **order-service** | 8082 | Order entry & lifecycle orchestration, dashboard aggregation | ✅ (7 migrations) | `risk.evaluation.result`, `execution.completed` | `order.state.changed`, `risk.evaluation.requested`, `order.execution.requested` | client-service, execution-service, marketdata-service |
| **client-service** | 8083 | Client/account profiles, holdings, portfolio valuation | ✅ (7 migrations) | `order.state.changed`, `execution.completed` | – | marketdata-service |
| **marketdata-service** | 8084 | Instruments, prices, indices, market hours/holidays, watchlists; live ticks over WebSocket | ✅ (17 migrations — largest schema) | *(configured, unused)* | – | – |
| **risk-service** | 8085 | Pre-trade risk rule evaluation + rule-change approval workflow | ✅ (14 migrations) | `risk.evaluation.requested` | `risk.evaluation.result` | – |
| **execution-service** | 8086 | Simulates fills against orders | ✅ (4 migrations) | `order.execution.requested` | `execution.completed` | marketdata-service |
| **audit-service** | 8087 | Immutable audit trail, compliance queries | ✅ (3 migrations) | `order.state.changed`, `execution.completed` | – | – |
| **notification-service** | 8088 | Fans Kafka events out to browsers via SSE | – | `order.state.changed`, `risk.evaluation.result`, `execution.completed` | – | – |

Database-per-service is consistent: one shared Postgres instance, but each
service owns its own schema (`order_schema`, `risk_schema`, etc.) and never
reaches into another service's tables — all cross-service reads go through
REST or Kafka.

## 5. Event flow (Kafka backbone)

```
 order-service ──risk.evaluation.requested──▶ risk-service
      ▲                                            │
      └──────────risk.evaluation.result────────────┘
                        │
                        ▼ (also fans to notification-service)

 order-service ──order.execution.requested──▶ execution-service
      │                                            │
      │◀─────────────execution.completed───────────┤
      │                                            │
      ▼                                            ▼
 client-service                          audit-service, notification-service

 order-service ──order.state.changed──▶ client-service, audit-service, notification-service
```

Every topic above has a matching `.DLT` dead-letter topic pre-created in
`docker-compose.yml`. `notification-service` and `audit-service` are pure
consumers — they never publish.

## 6. Frontend

Layer-based structure under `frontend/src` (`pages`, `components`, `api`,
`services`, `context`, `hooks`, `types`, `utils`) — not feature-based.

- **Routing** (`App.tsx`): Login → Dashboard, Order Blotter, Order Entry
  (TRADER-only), Portfolio, Audit Trail, Market Data, Admin Panel — gated by
  `ProtectedRoute` on role (TRADER / OPERATIONS / VIEWER / ADMIN).
- **State**: `AuthContext` (user/token, persisted to `localStorage`,
  triggers logout on `auth:session-expired`); `NotificationsContext` wraps
  live SSE notifications.
- **Backend access**: a shared Axios client (`baseURL: /api/v1`) through the
  api-gateway; a **raw WebSocket** singleton (`/ws/v1`) for live market
  ticks; **SSE** (`fetch-event-source`, `/api/v1/notifications/stream`) for
  push notifications. Two different live-data transports for two different
  purposes — worth knowing if you're touching either.
- **Testing**: Vitest/RTL for components, Playwright for e2e flows.

## 7. Infra, deployment & CI

- **`docker/postgres/init.sql`** provisions all 7 service schemas in one
  shared Postgres database.
- **`docker-compose.yml`** — full local stack: Zookeeper+Kafka, Redis,
  Postgres, all 9 services, frontend behind nginx, with health checks and
  Kafka topic (+DLT) pre-creation.
- **`k8s/`** — one Deployment + Service per microservice under
  `k8s/services/`, shared infra (`postgres-statefulset`, `kafka-statefulset`,
  `redis-deployment`) and config (`namespace`, `configmap`, `secret`) under
  `k8s/infra/`. **No Ingress manifest** — external access isn't yet modeled
  in the manifests.
- **CI** (`.github/workflows/ci.yml`): single job — `mvn clean verify`
  (builds+tests every Java module) → `docker compose build` → on `develop`,
  push images to Docker Hub and `kubectl apply -f k8s/` to Minikube.
  **No dedicated frontend build/test step**, and the JaCoCo 80%
  line-coverage rule declared in the parent POM is **not wired into CI as a
  hard gate** — it only fails a local `mvn verify` if triggered.
- **Postman** (`postman/`, `.postman/`) currently only covers the auth
  login request — thin compared to the service surface.
- **`scripts/`** contains a one-off slide-deck generator, not deployment
  tooling.

## 8. Cross-cutting patterns worth preserving

- **JWT everywhere**: validated once at the gateway, and again locally in
  each service — no service trusts the gateway blindly.
- **Resilience4j** wraps every outbound synchronous call (order-service →
  client/execution/marketdata; client-service → marketdata; execution-service
  → marketdata). Synchronous calls only ever flow "outward" from these three
  services — risk, audit, notification, and auth never call another service
  directly.
- **Event-driven over synchronous** for anything that isn't a direct data
  lookup — order state, risk results, and executions all propagate via
  Kafka, not REST callbacks.

## 9. Notable gaps (as observed, not necessarily problems)

- `marketdata-service` has Kafka config wired but no producers/consumers —
  live ticks go out over WebSocket instead; the Kafka setup looks vestigial.
- No CI-enforced coverage gate despite the JaCoCo rule existing in the POM.
- No frontend test/build step in CI.
- No k8s Ingress — the manifests assume some other means of external access.
- Postman coverage is minimal (auth only) relative to the number of
  endpoints across 9 services.
