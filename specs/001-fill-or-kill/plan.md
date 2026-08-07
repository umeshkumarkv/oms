# Implementation Plan: [FEATURE]

**Branch**: `[###-feature-name]` | **Date**: [DATE] | **Spec**: [link]

**Input**: Feature specification from `/specs/[###-feature-name]/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

[Extract from feature spec: primary requirement + technical approach from research]

## Technical Context

<!--
  ACTION REQUIRED: Replace the content in this section with the technical details
  for the project. The structure here is presented in advisory capacity to guide
  the iteration process.
-->

**Language/Version**: Multi-language microservices:
- Backend services: Java (OpenJDK 17+), Maven-built Spring-style services (service modules under `services/`).
- Frontend: Node.js + TypeScript (Vite + React) for the web UI in `frontend/`.

**Primary Dependencies**:
- Backend: Spring Boot ecosystem conventions (HTTP JSON endpoints), Maven-managed dependencies.
- Frontend: React, Vite, Playwright for end-to-end tests.
- Storage: PostgreSQL (docker/postgres/init.sql present).

**Storage**: Orders and trade/execution records persisted in PostgreSQL; services follow service-owned schema rules (no direct cross-service DB sharing).

**Testing**: Unit tests via JUnit for Java services, Playwright E2E for frontend (`frontend/e2e`), integration tests for cross-service flows (to be added).

**Target Platform**: Linux containers (Kubernetes manifests under `k8s/` and `services/`), local Docker Compose for development.

**Project Type**: Multi-service web application (broker-dealer OMS) with separate backend services (order, execution, marketdata, etc.) and a single-page frontend.

**Performance Goals**: Domain expects low-latency order handling; concrete numeric SLAs (e.g., p99 < X ms) NEEDS CLARIFICATION — default aim: sub-100ms order handling within a single service for synchronous flows.

**Constraints**: Must follow project constitution: explicit service contracts, risk/compliance controls, and test-first validation. No direct cross-service DB access allowed; new dependencies must be justified.

**Scale/Scope**: Multi-service system handling production trading workloads; specific scale targets NEEDS CLARIFICATION.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Gates derived from Broker Dealer OMS Constitution (see `.specify/memory/constitution.md`):

- **I. Service Contract Clarity**: Plan must define an API contract change for order submission to include the `fillOrKill` flag. (Compliant — contract artifact created in `/specs/001-fill-or-kill/contracts/`.)
- **II. Risk and Compliance by Design**: Order behavior changes must include audit logs and deterministic failure modes; design documents specify validation and outcome reporting. (Compliant — design includes explicit outcome states and validation rules.)
- **III. Test-First Validation**: Tasks must include unit, integration, and e2e tests. (Note: tests are outlined in `quickstart.md` and `contracts` references.)
- **IV. Observability and Operational Transparency**: Design includes logging and outcome metrics for FOK orders. (Compliant — to be implemented in implementation tasks.)
- **V. Incremental Simplicity**: Implementation recommended as a small increment: add toggle and payload, persist flag, enforce in execution-service, and add tests; avoid large architectural changes.

No constitution gates are violated by the proposed approach. If any gate requires stronger justification after Phase 1, the plan will record violations in Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/[###-feature]/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)
<!--
  ACTION REQUIRED: Replace the placeholder tree below with the concrete layout
  for this feature. Delete unused options and expand the chosen structure with
  real paths (e.g., apps/admin, packages/something). The delivered plan must
  not include Option labels.
-->

```text
# [REMOVE IF UNUSED] Option 1: Single project (DEFAULT)
src/
├── models/
├── services/
├── cli/
└── lib/

tests/
├── contract/
├── integration/
└── unit/

# [REMOVE IF UNUSED] Option 2: Web application (when "frontend" + "backend" detected)
backend/
├── src/
│   ├── models/
│   ├── services/
│   └── api/
└── tests/

frontend/
├── src/
│   ├── components/
│   ├── pages/
│   └── services/
└── tests/

# [REMOVE IF UNUSED] Option 3: Mobile + API (when "iOS/Android" detected)
api/
└── [same as backend above]

ios/ or android/
└── [platform-specific structure: feature modules, UI flows, platform tests]
```

**Structure Decision**: [Document the selected structure and reference the real
directories captured above]

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| [e.g., 4th project] | [current need] | [why 3 projects insufficient] |
| [e.g., Repository pattern] | [specific problem] | [why direct DB access insufficient] |
