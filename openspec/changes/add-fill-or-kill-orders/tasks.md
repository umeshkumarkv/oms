## 1. order-service: data model & validation

- [ ] 1.1 Add Flyway migration `V8__add_fill_or_kill_to_orders.sql`: add
      `fill_or_kill boolean not null default false` to `order_schema.orders`
      plus a named CHECK constraint
      (`order_type = 'LIMIT' OR fill_or_kill = false`), following the
      explicit-named-constraint pattern used in `V4__rename_gtc_to_gtd.sql`
- [ ] 1.2 Add `fillOrKill` field to the `Order` entity
- [ ] 1.3 Add `fillOrKill` field to `SubmitOrderRequest` (default `false`
      when absent from the request)
- [ ] 1.4 Extend `OrderService.validateLevel1()` to reject
      `fillOrKill = true` combined with `orderType != LIMIT` as a validation
      error, alongside the existing limit-price rule
- [ ] 1.5 Update the MapStruct mapper(s) to carry `fillOrKill` between DTO
      and entity
- [ ] 1.6 Propagate `fillOrKill` onto `OrderExecutionRequestedEvent`
      published to `order.execution.requested`
- [ ] 1.7 Update springdoc/OpenAPI annotations on `SubmitOrderRequest` and
      the order endpoints to document the new field
- [ ] 1.8 Ensure `OrderStateMachineService.transitionOnExecutionRejected`
      surfaces a Fill-or-Kill-specific rejection reason on `Order` when the
      incoming `execution.completed` event indicates an FOK kill
- [ ] 1.9 Unit tests: FOK+MARKET is rejected, FOK+LIMIT is accepted, and
      `fillOrKill` is present on the published execution-requested event
- [ ] 1.10 Confirm JaCoCo line coverage stays at/above 80% for
      order-service after these changes

## 2. execution-service: fill-or-kill execution logic

- [ ] 2.1 Add `fillOrKill` field to `OrderExecutionRequest`, mirroring the
      existing (currently unused) `timeInForce` passthrough field
- [ ] 2.2 In the fill-simulation flow, branch on `fillOrKill = true`:
      evaluate `checkLimitCondition()` and `determineFillOutcome()`
      synchronously exactly once, and never invoke the
      `PendingOrderExecution` insert path for this order
- [ ] 2.3 When marketable and the outcome is FULL: execute the full fill
      immediately via the existing full-fill path
- [ ] 2.4 When not marketable, or the outcome is not FULL: build a
      `REJECTED` `Execution` with `executedQty = 0` and a reason identifying
      the Fill-or-Kill cancellation, then publish `execution.completed`
      with that reason
- [ ] 2.5 Add a regression test asserting that no `PendingOrderExecution`
      row is ever created for a `fillOrKill = true` order, regardless of
      outcome
- [ ] 2.6 Unit tests covering: FOK immediate full fill, FOK killed due to
      non-full outcome, FOK killed due to non-marketable limit price
- [ ] 2.7 Update springdoc/OpenAPI annotations for any execution-service
      response DTO that now exposes `fillOrKill` or the FOK rejection reason
- [ ] 2.8 Confirm JaCoCo line coverage stays at/above 80% for
      execution-service after these changes

## 3. frontend: Fill-or-Kill toggle

- [ ] 3.1 Add `fillOrKill: boolean` to the `SubmitOrderRequest` type in
      `types/order.types.ts`
- [ ] 3.2 Pass `fillOrKill` through `submitOrder()` in `api/orderApi.ts`
- [ ] 3.3 Add a Fill-or-Kill Yes/No toggle to `OrderEntryPage.tsx`,
      conditionally rendered only when `orderType === 'LIMIT'` (same pattern
      already used for the Limit Price field and the GTD date picker)
- [ ] 3.4 Reset `fillOrKill` to `false`/unset whenever `orderType` changes
      away from `LIMIT`, so a stale `true` value can never be submitted for
      a Market order
- [ ] 3.5 Include the Fill-or-Kill selection in the order summary preview
      text when set
- [ ] 3.6 Extend `OrderEntryPage.test.tsx`: toggle visibility tied to order
      type, submitted payload shape, summary text
- [ ] 3.7 Add a Playwright e2e test covering the Fill-or-Kill flow: submit a
      Fill-or-Kill Limit order, and confirm the toggle is absent when Order
      Type = Market

## 4. Cross-service verification

- [ ] 4.1 Run the full local stack (`docker-compose up`) and manually
      verify an FOK Limit order either fills in full or is rejected
      end-to-end (order-service → risk-service → execution-service →
      order status, audit trail, and notification)
- [ ] 4.2 Confirm audit-service and notification-service display the
      Fill-or-Kill rejection reason without requiring code changes on
      their side, as assumed in proposal.md's Impact section
- [ ] 4.3 Add a Fill-or-Kill example request to the Postman collection
      under `postman/collections/`, alongside the existing order-related
      requests
