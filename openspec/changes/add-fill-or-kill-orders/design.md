## Context

See proposal.md - Why. Relevant current state:

- `order-service`'s `Order` entity has independent `orderType`
  (MARKET/LIMIT) and `timeInForce` (DAY/GTD) fields with no cross-validation
  between them today; `OrderService.validateLevel1()` is where the existing
  LIMIT-requires-limitPrice rule lives.
- `execution-service`'s `FillSimulationService.determineFillOutcome()` is a
  deterministic mock: magic symbols (`FAIL`→REJECTED, `SLOW`→PARTIAL) and a
  quantity-bucket heuristic (1000-1003→REJECTED, 10-99→PARTIAL, else FULL).
  There is no real liquidity/depth signal anywhere in the system.
- `initiateLimitFill()` checks `checkLimitCondition()` against the current
  market price; if not immediately marketable, it inserts a row into
  `PendingOrderExecution`, polled every 2s by `monitorPendingLimitOrders()`.
  This resting path is what a Fill-or-Kill order must never enter.
- `OrderExecutionRequest`/`OrderExecutionRequestedEvent` already carry
  `timeInForce` as an unused raw string end-to-end from order-service to
  execution-service — the same passthrough shape is the template for
  carrying `fillOrKill`.

## Goals / Non-Goals

**Goals:**
- Decide where the Limit-only validation lives and how it's enforced at
  both the application and database layers.
- Decide how execution-service turns the existing mock fill-outcome signal
  into an immediate fill-or-kill decision without touching the resting
  queue.
- Keep the change additive to existing DTOs/events/topics (no breaking
  contract changes, no new Kafka topics or REST endpoints).

**Non-Goals:**
- Modeling real market depth/liquidity — this still rides on the existing
  mock `determineFillOutcome()` heuristic.
- Implementing Immediate-or-Cancel (IOC), which permits partial fills; that
  is a related but separate order attribute not covered here.
- Any change to risk-service's evaluation logic — a Fill-or-Kill Limit
  order goes through the same risk check as any other order before it ever
  reaches execution-service.

## Decisions

**1. Independent boolean flag, not a TimeInForce value.**
Confirmed with the requester: `fillOrKill` is a separate boolean on the
order, orthogonal to `timeInForce` (DAY/GTD), rather than a third
`TimeInForce` enum value. Alternative considered — adding `FOK` to
`TimeInForce` — was rejected because it would remove the DAY/GTD choice
whenever FOK is selected and doesn't match the requested "Yes/No toggle"
interaction.

**2. Enforce the Limit-only rule at both the app and DB layer.**
`OrderService.validateLevel1()` gets a new check alongside the existing
limitPrice rule: reject if `fillOrKill == true && orderType != LIMIT`. The
Flyway migration additionally adds a CHECK constraint
(`order_type = 'LIMIT' OR fill_or_kill = false`) so the invariant holds even
against writes that bypass the application layer — consistent with how
`V4`/`V6` already enforce enum-shaped columns via named CHECK constraints.

**3. Reuse the existing mock fill-outcome function; don't build a new
liquidity model.**
For a Fill-or-Kill order, execution-service evaluates the current
`checkLimitCondition()` (is the limit price immediately marketable?) and,
if so, `determineFillOutcome()` (would this fill fully, partially, or be
rejected?) exactly once, synchronously, when it consumes
`order.execution.requested`. It never calls `initiateLimitFill()`'s
queue-to-`PendingOrderExecution` branch for an FOK order:
- Not marketable, or outcome ≠ FULL → kill: build a `REJECTED` `Execution`
  with `executedQty = 0`, same as `executeRejectedFill()` today, tagged
  with a reason identifying the Fill-or-Kill cancellation.
- Marketable and outcome = FULL → fill immediately via the existing
  `executeMarketFill`-equivalent full-fill path.

This keeps the fill-decision surface identical to today's (still a mock),
just changes what happens with a non-FULL outcome when `fillOrKill = true`.

**4. Propagate `fillOrKill` the same way `timeInForce` already is.**
Add `fillOrKill: boolean` to `SubmitOrderRequest`,
`OrderExecutionRequestedEvent`, and `OrderExecutionRequest` — mirroring the
existing (currently unused) `timeInForce` passthrough field rather than
introducing a new propagation mechanism.

## Risks / Trade-offs

- **[Risk]** `determineFillOutcome()` is a deterministic mock (magic
  symbols/quantity buckets), so FOK kill decisions will look arbitrary
  rather than realistic → **Mitigation**: acceptable for this POC; document
  the heuristic in test cases so the behavior is at least predictable and
  testable.
- **[Risk]** A bug could let an FOK order slip into `PendingOrderExecution`,
  violating "never rests" → **Mitigation**: guard at insertion time (the
  FOK branch never calls the insert path at all, rather than filtering it
  out later at read time in `monitorPendingLimitOrders()`); cover with a
  unit test asserting zero `PendingOrderExecution` rows are ever created
  for an FOK order regardless of outcome.
- **[Risk]** Adding fields to `OrderExecutionRequest`/
  `OrderExecutionRequestedEvent` touches a live Kafka contract →
  **Mitigation**: additive-only (new optional field, default `false`),
  so existing/older consumers deserializing the same payload are unaffected.

## Migration Plan

1. Ship the order-service Flyway migration (new nullable-with-default
   `fill_or_kill boolean not null default false` column + CHECK constraint)
   — purely additive, safe against existing rows and any in-flight orders.
2. Deploy order-service and execution-service in either order: the new
   field defaults to `false` on both sides, so a version skew window where
   one service knows about `fillOrKill` and the other doesn't is
   behaviorally equivalent to today (no FOK orders exist yet in that
   window).
3. Ship the frontend toggle last (or alongside) — it only ever sends
   `fillOrKill: true` once both backend services support it.
4. Rollback: no down-migration is used elsewhere in this repo's Flyway
   history; rolling back means redeploying the previous service versions,
   which simply ignore the now-present `fill_or_kill` column/field.

## Open Questions

- Exact rejection-reason string/code surfaced for a killed Fill-or-Kill
  order (e.g. `"FOK_UNABLE_TO_FILL_IN_FULL"`) — cosmetic, can be finalized
  during implementation without affecting the spec or approach above.
