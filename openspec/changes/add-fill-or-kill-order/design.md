## Context

See proposal.md - Why. Order data currently flows: frontend (`OrderEntryPage.tsx`) → `order-service` (`OrderController` → `OrderService.submitOrder` → `Order` entity, Postgres `order_schema.orders`) → Kafka `order.state.changed` / `risk.evaluation.requested` → `OrderStateMachineService` → Kafka `order.execution.requested` (carrying `OrderExecutionRequestedEvent`, which already includes `timeInForce`) → `execution-service` (`OrderExecutionConsumer` → `ExecutionService.processFill` → `FillSimulationService`). LIMIT orders whose price isn't currently met are queued as `PendingOrderExecution` and re-checked every 2s by `monitorPendingLimitOrders`. Partial fills are simulated deterministically by `FillSimulationService.determineFillOutcome()` (quantity/symbol heuristics) and executed as two tranches (60% immediate, 40% after a 2-5s async delay).

This change adds a `fillOrKill` boolean that must survive the full path above and change execution-service's fill decision for orders carrying it.

## Goals / Non-Goals

**Goals:**
- Add `fillOrKill` as a boolean field, independent of `orderType`/`timeInForce`, threaded through DTOs, the Kafka event, and the DB schema.
- Make execution-service treat `fillOrKill: true` orders as single-shot, all-or-nothing: no pending/polling path, no partial tranches.
- Reuse existing `OrderStatus.REJECTED` and the existing `OrderStateMachineService.transitionOnExecutionRejected` path for the "kill" outcome - no new order status.
- Frontend: a Yes/No toggle in the existing toggle-group style, gated on Order Type = LIMIT.

**Non-Goals:**
- No changes to `OrderType` or `TimeInForce` enums or their existing validation rules.
- No real matching engine / order book - `FillSimulationService`'s heuristic-based simulation model is preserved, only its FOK branch is new.
- No support for Fill-or-Kill on MARKET orders (rejected at validation, per proposal).
- No change to how non-FOK partial fills behave (60/40 tranches unchanged).

## Decisions

### 1. `fillOrKill` as a standalone boolean, not a `TimeInForce` value
Alternative considered: add `FOK` as a third `TimeInForce` enum value (industry-standard modeling, alongside DAY/GTD). Rejected because the user's requirement is explicitly a yes/no toggle orthogonal to order duration, and folding it into `TimeInForce` would force reworking existing DAY/GTD validation, the DB check constraint, and the toggle-group UI for no behavioral gain here. A standalone `fill_or_kill BOOLEAN NOT NULL DEFAULT false` column is additive and keeps `TimeInForce` semantics untouched.

### 2. Validation at order-service `Level 1` (syntactic), not `Level 2`
`OrderService.validateLevel1` already checks `orderType`/`limitPrice` consistency synchronously before persistence. The `fillOrKill` + `orderType` compatibility check (LIMIT-only) is a pure structural rule with no external dependency, so it belongs in the same synchronous `validateLevel1` pass and fails fast with a `202`-blocking validation error, consistent with existing limit-price validation.

### 3. FOK bypasses the pending/polling path entirely in execution-service
`FillSimulationService`/`ExecutionService.processFill` branches on `orderType` today (MARKET vs LIMIT). Add a check ahead of that branch: if `fillOrKill == true`, evaluate fillability once synchronously (reusing `determineFillOutcome()` and the existing limit-price-met check used by `initiateLimitFill`) and never call into `monitorPendingLimitOrders`/`PendingOrderExecution`. This directly implements "immediate or kill" and avoids adding FOK-awareness inside the scheduled polling loop, which would otherwise need to distinguish and immediately kill FOK orders it happens to pick up.

### 4. PARTIAL outcome becomes REJECTED for FOK, no tranche executed
In `determineFillOutcome()`'s caller, when `fillOrKill == true` and the outcome is `PARTIAL`, treat it as `REJECTED` before any tranche logic runs (`executePartialFill`/`executeTrancheTwo` are skipped entirely for that order). `FULL` outcomes proceed unchanged. This is a decision-point branch, not a change to `determineFillOutcome()`'s heuristic itself - the simulation logic that decides "would this be partial" stays the same; only what happens next differs for FOK orders.

### 5. Threading the flag through Kafka events
`OrderExecutionRequestedEvent` and `OrderExecutionRequest` DTO gain a `fillOrKill` boolean field, mirroring how `timeInForce` is already carried end-to-end. No new Kafka topic; existing consumers ignore unknown-to-them fields safely (additive DTO change).

### 6. DB migration
New Flyway migration in order-service (`V{next}__add_fill_or_kill.sql`) adds `fill_or_kill BOOLEAN NOT NULL DEFAULT false` to `order_schema.orders`. Default ensures existing rows and any in-flight client not yet sending the field remain valid - no backfill needed.

### 7. Frontend toggle placement and gating
Add a Fill-or-Kill toggle group next to the existing Time in Force toggle in the "Order Details" card of `OrderEntryPage.tsx`, following the same `role="group"`/`aria-pressed`/roving-focus (`rove()`) pattern as the Order Type and Time in Force toggles. Disabled and forced to "No" whenever `orderType !== 'LIMIT'` (mirrors the GTD date-picker's conditional-rendering pattern, but here as disable-and-reset rather than hide, since the option is conceptually always present, just inapplicable to MARKET).

## Risks / Trade-offs

- **[Risk]** Existing `OrderExecutionRequestedEvent`/DTO consumers deserializing with strict schemas could break on an added field. → **Mitigation**: confirm event/DTO (de)serialization is additive-tolerant (Jackson default `FAIL_ON_UNKNOWN_PROPERTIES=false` is standard in this codebase's other additive TIF-field precedent); add the field as optional/defaulted.
- **[Risk]** Skipping the polling path for FOK LIMIT orders means a FOK order that narrowly misses the price at the exact evaluation instant is killed rather than filled, even though a normal LIMIT order would have filled moments later. → **Mitigation**: this is intentional/correct FOK semantics (no resting), and is called out in the spec so it's not mistaken for a bug.
- **[Risk]** Reusing `OrderStatus.REJECTED` for kills means a FOK kill is not distinguishable from other rejection reasons by status alone. → **Mitigation**: rely on the existing `rejectionReason` field to carry a distinct reason string (e.g. "Fill-or-Kill: unable to fill in full"), no schema change needed.

## Migration Plan

1. Ship order-service DB migration (additive column, default `false`) - safe to deploy ahead of application code.
2. Deploy order-service with `fillOrKill` in DTO/entity/event and Level 1 validation.
3. Deploy execution-service with FOK-aware fill branch.
4. Deploy frontend toggle.
5. Rollback: each step is additive/backward-compatible independently; rolling back frontend or execution-service alone leaves `fillOrKill` persisted-but-unused (safe no-op), rolling back order-service requires no data cleanup since the column default is `false`.
