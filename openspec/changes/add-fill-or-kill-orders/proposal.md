## Why

Traders currently have no way to require immediate, all-or-nothing execution
on a Limit order: every Limit order either fills partially or rests on the
book (via `PendingOrderExecution`) until the limit price is crossed. A
Fill-or-Kill (FOK) order — fill the entire quantity immediately or cancel
the whole order — is a standard order-entry option that this OMS is missing
today for Limit orders.

## What Changes

- Add a new independent boolean flag, `fillOrKill` ("Yes"/"No"), to order
  submission — separate from the existing `tif` (DAY/GTD) selector. It only
  applies to `orderType = LIMIT`; submitting it as `true` for a `MARKET`
  order is rejected as a validation error (mirrors the existing
  limit-price-required-for-LIMIT rule).
- **order-service**: persist `fillOrKill` on `Order`, add a Flyway migration
  and CHECK constraint, validate it in `OrderService.validateLevel1()`, and
  propagate it through to `OrderExecutionRequestedEvent` so execution-service
  knows the order is FOK.
- **execution-service**: when `fillOrKill = true`, the order must never enter
  the `PendingOrderExecution` resting queue and must never partially fill —
  the existing fill-outcome decision (`FillSimulationService`) is evaluated
  once, immediately; any outcome other than a full fill is converted to a
  `REJECTED` execution (the "kill").
- **frontend**: a "Fill or Kill" Yes/No toggle on the order entry form,
  visible only when Order Type = LIMIT (same conditional-render pattern
  already used for the Limit Price field and the GTD date picker), wired
  into the `SubmitOrderRequest` payload and the order summary text.
- No new Kafka topics or REST endpoints — this extends the existing
  `order.execution.requested` payload and `POST /orders` request/response
  contracts with one new field each.

## Capabilities

### New Capabilities
- `fill-or-kill-orders`: end-to-end Fill-or-Kill behavior for Limit
  orders — accepting the flag at order entry (API + UI), restricting it to
  Limit orders, and enforcing immediate all-or-nothing execution
  (no partial fills, no resting) in execution-service.

### Modified Capabilities
(none — no pre-existing specs cover order submission or execution today)

## Impact

- **order-service**: `Order` entity, `OrderType`/`TimeInForce`-adjacent
  validation in `OrderService.validateLevel1()`, `SubmitOrderRequest` DTO,
  `OrderExecutionRequestedEvent`, new Flyway migration under
  `src/main/resources/db/migration/` (new `fill_or_kill` column + CHECK
  constraint on `order_schema.orders`).
- **execution-service**: `OrderExecutionRequest` DTO (new `fillOrKill`
  field, alongside the already-present but unused `timeInForce` string),
  `FillSimulationService` (skip `initiateLimitFill`'s resting path and
  `executePartialFill` path for FOK orders; reject instead), no schema
  change needed (no FOK-specific persistence beyond the existing
  `Execution`/`FillType` fields).
- **frontend**: `OrderEntryPage.tsx` (new toggle, conditional render tied to
  `orderType === 'LIMIT'`, validation, order summary text),
  `types/order.types.ts` (new `fillOrKill: boolean` field on
  `SubmitOrderRequest`), `api/orderApi.ts` (payload passthrough), and the
  corresponding Vitest coverage in `OrderEntryPage.test.tsx`.
- No changes to api-gateway routing, Kafka topic names/DLTs, auth, risk,
  client, marketdata, audit, or notification services — `risk-service`
  continues to evaluate orders unchanged, and `order.state.changed` /
  `execution.completed` events already carry enough information for
  audit-service and notification-service to reflect an FOK rejection like
  any other rejection.
