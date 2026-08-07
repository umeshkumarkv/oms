# Research: Fill or Kill (FOK)

## Context
Feature: add a Fill-Or-Kill (FOK) option to new order submissions so orders are either fully filled immediately or canceled with no partial fills.

## Extracted Unknowns (from Technical Context)

- Numeric performance SLAs for order handling (p99/p95 latencies).
- Exact service responsible for enforcing match/cancel semantics (order-service vs execution-service).
- Persistence location and schema for `fillOrKill` flag (which service owns orders table).
- Observability requirements (which metrics/events to emit for FOK outcomes).
- Integration test boundaries and environment expectations.

## Research Tasks

- Task: "Research numeric latency goals for order acceptance in OMS" → Outcome: left as "Operational SLA: NEEDS CLARIFICATION"; recommend aiming for sub-100ms for single-service synchronous flows unless ops specify otherwise.
- Task: "Determine owner service for FOK enforcement" → Outcome: assign enforcement to `execution-service` which performs matching; the `order-service` persists the request and forwards to `execution-service` for matching.
- Task: "Define persistence scheme for FOK flag" → Outcome: add boolean `fill_or_kill` column to the `orders` table owned by `order-service` (Postgres).
- Task: "Define observability" → Outcome: emit structured event `order.fok.outcome` with fields: `orderId`, `outcome` (filled|canceled), `reason`, `timestamp`, and a metric `orders.fok.outcome{outcome}`.
- Task: "Testing strategy" → Outcome: unit tests for validation logic; integration test between `order-service` and `execution-service` simulating available liquidity and insufficient liquidity; Playwright e2e to verify frontend toggle and user messaging.

## Decisions

- Decision: `fillOrKill` is a boolean flag carried on `OrderRequest` and persisted on `orders` table.
  - Rationale: boolean is simple, matches UI toggle, and easy to validate.
  - Alternatives considered: enum or separate order type; rejected due to complexity and no added value for this scope.

- Decision: Enforcement lives in `execution-service`.
  - Rationale: execution-service has responsibility for matching and immediate liquidity checks; this avoids coupling matching logic into the order API surface and keeps single responsibility per service.
  - Alternatives: enforce in order-service (would require order-service to implement matching logic or call execution-service anyway).

- Decision: Default behavior when omitted is `false` (normal order).
  - Rationale: backwards compatibility and safe default.

- Decision: Observability via structured event and counter metric.
  - Rationale: provides both event-level audit and aggregated telemetry for incident detection.

## Resolved Unknowns (for this plan)

- Owner service: `execution-service` enforces FOK semantics.
- Placement/persistence: `order-service` adds `fill_or_kill` boolean column and persists flag on order creation.
- Testing: Add unit tests in `order-service` for payload validation, integration tests between `order-service` and `execution-service`, and an e2e Playwright test to verify UI toggle and outcome messaging.
- Performance SLAs: left as "NEEDS CLARIFICATION"; recommend sub-100ms target for single-service synchronous handling as a default.
- Observability: implement `order.fok.outcome` events and `orders.fok.outcome` metrics.

## Output
This research resolves the technical unknowns necessary to proceed to data model and contract design.
