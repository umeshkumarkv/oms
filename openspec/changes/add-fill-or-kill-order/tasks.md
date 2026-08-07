## 1. Database & Domain Model (order-service)

- [x] 1.1 Add Flyway migration `V{next}__add_fill_or_kill.sql` adding `fill_or_kill BOOLEAN NOT NULL DEFAULT false` to `order_schema.orders`
- [x] 1.2 Add `fillOrKill` field to `Order` entity
- [x] 1.3 Add `fillOrKill` field to `SubmitOrderRequest` DTO
- [x] 1.4 Add `fillOrKill` field to `OrderExecutionRequestedEvent`

## 2. Order Submission Validation (order-service)

- [x] 2.1 Extend `OrderService.validateLevel1` to reject `fillOrKill: true` when `orderType != LIMIT`, with a clear validation error message
- [x] 2.2 Persist `fillOrKill` on `Order` creation in `OrderService.submitOrder`
- [x] 2.3 Populate `fillOrKill` on the `OrderExecutionRequestedEvent` published by `OrderStateMachineService`
- [x] 2.4 Unit tests in `OrderServiceTest`: default-to-false when omitted, accepted on valid LIMIT+FOK, rejected on MARKET+FOK
- [x] 2.5 Controller tests in `OrderControllerTest`: request/response round-trip of `fillOrKill`, validation error response shape for MARKET+FOK

## 3. Execution Semantics (execution-service)

- [x] 3.1 Add `fillOrKill` field to `OrderExecutionRequest` DTO
- [x] 3.2 In `ExecutionService.processFill`, branch on `fillOrKill` ahead of the existing `orderType` branch: route FOK orders to a single synchronous evaluate-and-fill-or-reject path instead of `initiateLimitFill`/`monitorPendingLimitOrders`
- [x] 3.3 Evaluate fillability once via `determineFillOutcome()` (and existing limit-price-met check); on `FULL` outcome, fill immediately; on anything other than `FULL` (including `PARTIAL` and price-not-met), reject the entire order with no tranche executed
- [x] 3.4 Set a distinct `rejectionReason` (e.g. "Fill-or-Kill: unable to fill in full") on the killed order/execution result
- [x] 3.5 Confirm killed FOK orders flow through the existing `OrderStateMachineService.transitionOnExecutionRejected` path with no new `OrderStatus` value
- [x] 3.6 Unit tests for the fill-simulation branch: FOK + FULL-outcome quantity fills immediately; FOK + PARTIAL-outcome quantity rejects in full with zero tranches; FOK + LIMIT-not-met rejects immediately without entering `PendingOrderExecution`
- [x] 3.7 Confirm/adjust `OrderExecutionConsumer` passes `fillOrKill` through unchanged from the event to `ExecutionService`

## 4. Frontend Order Entry

- [x] 4.1 Add `fillOrKill: boolean` to `types/order.types.ts`
- [x] 4.2 Add Fill-or-Kill Yes/No toggle group in `OrderEntryPage.tsx`, following the existing `role="group"`/`aria-pressed`/roving-focus (`rove()`) pattern used by Order Type and Time in Force
- [x] 4.3 Wire toggle state: enabled and defaults to "No" when Order Type is LIMIT; disabled and forced to "No" when Order Type is MARKET; resets to "No" when switching away from LIMIT
- [x] 4.4 Include `fillOrKill` in the submit payload sent via `api/orderApi.ts`
- [x] 4.5 Component tests in `OrderEntryPage.test.tsx`: toggle enable/disable per order type, reset-on-switch behavior, submitted payload includes `fillOrKill`
- [x] 4.6 Playwright e2e case in `order-entry.spec.ts`: select LIMIT, toggle Fill-or-Kill to Yes, submit, and verify the toggle is disabled/reset after switching to MARKET

## 5. Cross-cutting Verification

- [x] 5.1 Run full order-service and execution-service test suites — ran via `maven:3.9-amazoncorretto-21` container: order-service 237/237 pass, execution-service 65/65 pass, 0 failures/errors
- [x] 5.2 Run frontend unit and e2e test suites — unit: 312/312 pass (`npx vitest run`); e2e: ran against the live stack, all 19 tests (18 pre-existing + 1 new) failed identically at the shared `beforeEach`'s `page.waitForLoadState('networkidle')` before any test-specific code ran — a pre-existing environment limitation (live bid/ask feed keeps the network non-idle), not a Fill-or-Kill regression
- [x] 5.3 Manually verify end-to-end — verified live against the rebuilt stack: LIMIT+FOK order that fully fills → status FILLED, single FULL execution at limit price; LIMIT+FOK order that would partially fill → status REJECTED with reason "Fill-or-Kill: unable to fill in full", single REJECTED execution record with the full original quantity as remainingQuantity (zero tranches); LIMIT+FOK with price not currently met → killed immediately (sub-second) without entering the pending/polling queue; MARKET+FOK → rejected at submission with FILL_OR_KILL_REQUIRES_LIMIT
