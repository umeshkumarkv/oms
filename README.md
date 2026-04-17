# Broker Dealer OMS POC

Sell-Side Broker Order Management System — Agentic AI SDLC POC

**Principal Architect:** Umesh Kumar KV  
**Methodology:** Agentic AI SDLC — Claude Code executes JIRA stories, humans review and govern  
**Timeline:** 2 Sprints — 4 Weeks  
**Status:** 🟡 Sprint 1 — In Progress

---

## What We Are Building

> A trader places an equity order — it passes compliance checks — it executes — it appears in the audit trail.

This single vertical slice, working end to end, is the POC.

---

## Tech Stack

| Layer | Technology | Version |
|---|---|---|
| Language | Java | 21 LTS |
| Framework | Spring Boot Web MVC | 3.2.x |
| Frontend | React + TypeScript + Vite | 18.x |
| Database | PostgreSQL | 15 |
| Async | Apache Kafka | Latest |
| Cache | Redis | Latest |
| Gateway | Spring Cloud Gateway | Via Boot |
| Containers | Docker + Minikube | Latest |
| CI/CD | GitHub Actions | Latest |

---

## Services

| Service | Port | Responsibility |
|---|---|---|
| api-gateway | 8080 | JWT validation + routing |
| auth-service | 8081 | Login, JWT, roles |
| order-service | 8082 | Order entry, state machine |
| client-service | 8083 | Client profiles, portfolio |
| marketdata-service | 8084 | Mock prices, market hours |
| risk-service | 8085 | Configurable risk rules |
| execution-service | 8086 | Mock fill engine |
| audit-service | 8087 | Immutable audit log |

---

## Quick Start

### Prerequisites

```bash
java --version        # Must show 21
mvn --version         # Must show 3.9+
node --version        # Must show 20+
docker --version
docker compose version
minikube version
claude --version
```

### Run locally

```bash
git clone git@github.com:umeshkumarkv/broker-dealer.git
cd broker-dealer
git checkout develop
docker compose up
```

### Verify all services healthy

```bash
curl http://localhost:8080/actuator/health   # API Gateway
curl http://localhost:8081/actuator/health   # auth-service
curl http://localhost:8082/actuator/health   # order-service
curl http://localhost:8083/actuator/health   # client-service
curl http://localhost:8084/actuator/health   # marketdata-service
curl http://localhost:8085/actuator/health   # risk-service
curl http://localhost:8086/actuator/health   # execution-service
curl http://localhost:8087/actuator/health   # audit-service
```

All must return `{ "status": "UP" }`.

---

## Swagger UI

Each service exposes Swagger UI at `/swagger-ui.html`:

- http://localhost:8081/swagger-ui.html — auth-service
- http://localhost:8082/swagger-ui.html — order-service
- http://localhost:8083/swagger-ui.html — client-service
- http://localhost:8084/swagger-ui.html — marketdata-service
- http://localhost:8085/swagger-ui.html — risk-service
- http://localhost:8086/swagger-ui.html — execution-service
- http://localhost:8087/swagger-ui.html — audit-service

---

## How Claude Code Works on This Project

### Every Claude Code session opener

```
Read CLAUDE.md and services/{service-name}/CLAUDE.md
fully before writing any code.

You are working on JIRA story BD-{number}.
Story: {paste full story including AI Context,
        Acceptance Criteria, Out of Scope,
        Tech Constraints}

The skeleton is already in place.
Implement within the existing structure only.
Follow openapi.yaml exactly for all endpoints.
Create branch ai/BD-{number}-{description} from develop.
```

### Branch naming

| Branch | Created By | Purpose |
|---|---|---|
| ai/BD-{n}-{desc} | Claude Code | Feature stories |
| infra/{desc} | Dev 1 | Infrastructure |
| bootstrap/{desc} | Architect | Skeleton setup |
| hotfix/{desc} | Humans only | Emergency fixes |

---

## Mock Test Users

| Username | Password | Role |
|---|---|---|
| trader.john | Demo@1234 | TRADER |
| trader.sarah | Demo@1234 | TRADER |
| ops.manager | Demo@1234 | OPERATIONS |
| admin.user | Demo@1234 | ADMIN |

---

## Key Documents

| Document | Location |
|---|---|
| BRD | Confluence — BD-POC > Project Foundation |
| Design Debrief | Confluence — BD-POC > Design Debrief |
| CLAUDE.md root | /CLAUDE.md |
| CLAUDE.md per service | /services/{name}/CLAUDE.md |
| Bootstrap instructions | /BOOTSTRAP.md |
| JIRA Board | BD-POC Scrum Board |

---

## Out of Scope for POC

- Real exchange connectivity
- Order cancellation by user
- Order modification
- Short selling
- Options and fixed income
- Cloud deployment
- Regulatory reporting
- Mobile application
