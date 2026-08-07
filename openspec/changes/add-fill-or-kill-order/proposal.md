## Why

Traders currently cannot require all-or-nothing execution on an order: every LIMIT order is eligible for the existing 60/40 partial-fill simulation, with no way to demand immediate full execution or rejection. A Fill-or-Kill (FOK) option closes this gap, giving traders control over partial-fill exposure without waiting for a resting order to work.

## What Changes

- Add a `fillOrKill` boolean flag to the Order domain model and order submission API, independent of `orderType`/`timeInForce`.
- Order Entry frontend: add a Fill-or-Kill "Yes/No" toggle, enabled only when Order Type is LIMIT (disabled/hidden for MARKET), following the existing toggle-group convention (Order Type, Time in Force).
- Backend validation (order-service): reject `fillOrKill=true` submissions where `orderType` is not `LIMIT`.
- Thread `fillOrKill` through the order lifecycle: `Order` entity/DB column, `SubmitOrderRequest` DTO, `OrderExecutionRequestedEvent`, `OrderExecutionRequest` DTO — so execution-service knows the flag at fill time.
- Execution-service fill semantics for FOK orders:
  - Evaluate the order once, immediately, without entering the pending/polling path (`monitorPendingLimitOrders`) used for ordinary resting LIMIT orders.
  - If the simulated outcome is FULL, fill it immediately as today.
  - If the simulated outcome would be PARTIAL, or the limit condition is not currently met, reject/cancel the entire order immediately instead — no tranches are ever executed.
- Reuse the existing `REJECTED` order-state transition (`OrderStateMachineService.transitionOnExecutionRejected`) for the "kill" outcome; no new `OrderStatus` value is introduced.

## Capabilities

### New Capabilities
- `order-management/fill-or-kill`: order submission, validation, and toggle behavior for the Fill-or-Kill flag (API + frontend contract).
- `order-execution/fill-or-kill`: all-or-nothing fill/kill semantics in the execution simulator for orders flagged Fill-or-Kill.

### Modified Capabilities
(none — `order-management` and `order-execution` have no pre-existing specs in this repo; both capabilities above are net-new spec files, not deltas to prior specs.)

## Impact

- **order-service**: `Order` entity + Flyway migration (new `fill_or_kill` column), `OrderType`/`TimeInForce` unaffected, `SubmitOrderRequest` DTO, `OrderService.validateLevel1`, `OrderExecutionRequestedEvent`, `OrderStateMachineService`.
- **execution-service**: `OrderExecutionRequest` DTO, `ExecutionService.processFill`, `FillSimulationService` (new FOK branch bypassing `initiateLimitFill`/`monitorPendingLimitOrders` and the partial-tranche path), `OrderExecutionConsumer`.
- **frontend**: `types/order.types.ts` (add `fillOrKill: boolean`), `pages/OrderEntryPage.tsx` (new toggle group, gated on Order Type = LIMIT), `api/orderApi.ts` (submit payload).
- **Tests**: `OrderServiceTest`, `OrderControllerTest`, `OrderStateMachineServiceTest`, execution-service fill-simulation tests, `OrderEntryPage.test.tsx`, `order-entry.spec.ts` (Playwright) all need new FOK cases.
- **No breaking changes**: `fillOrKill` defaults to `false`, existing clients/requests are unaffected.
