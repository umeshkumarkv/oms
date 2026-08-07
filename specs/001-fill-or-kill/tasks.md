# Tasks: Fill or Kill Order

**Input**: Design documents from `/specs/001-fill-or-kill/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/order-api.yaml`

## Phase 1: Setup (Shared Infrastructure)

- [ ] T001 [P] Review existing order entry submission flow in `frontend/src/pages/OrderEntryPage.tsx`
- [ ] T002 [P] Review existing order API request type in `frontend/src/types/order.types.ts`
- [ ] T003 [P] Review existing order submission endpoint mapping in `frontend/src/api/orderApi.ts`
- [ ] T004 [P] Review existing order persistence model in `services/order-service/src/main/java/com/brokerdealer/orderservice/domain/Order.java`
- [ ] T005 [P] Review existing order execution request event in `services/order-service/src/main/java/com/brokerdealer/orderservice/dto/kafka/OrderExecutionRequestedEvent.java`
- [ ] T006 [P] Review existing execution service fill logic in `services/execution-service/src/main/java/com/brokerdealer/executionservice/service/FillSimulationService.java`

---

## Phase 2: Foundational (Blocking Prerequisites)

 - [X] T007 Create migration `services/order-service/src/main/resources/db/migration/V8__add_fill_or_kill_to_orders.sql` to add `fill_or_kill boolean NOT NULL DEFAULT false` to `order_schema.orders`
 - [X] T008 Update `services/order-service/src/main/java/com/brokerdealer/orderservice/dto/request/SubmitOrderRequest.java` to include optional `fillOrKill` and validate boolean semantics
 - [X] T009 Update `services/order-service/src/main/java/com/brokerdealer/orderservice/domain/Order.java` to persist `fillOrKill` with `@Column(name = "fill_or_kill", nullable = false)
 - [X] T010 Update `services/order-service/src/main/java/com/brokerdealer/orderservice/dto/kafka/OrderExecutionRequestedEvent.java` to carry `fillOrKill` from the order request
 - [X] T011 Update `services/execution-service/src/main/java/com/brokerdealer/executionservice/dto/request/OrderExecutionRequest.java` to include `fillOrKill`
 - [X] T012 Update execution request mapping and event handling code to propagate `fillOrKill` from `order-service` into `execution-service`

---

## Phase 3: User Story 1 - Set Fill or Kill on New Order (Priority: P1)

**Goal**: Add a visible yes/no FOK toggle to the frontend order entry form and submit the chosen value.

**Independent Test**: Verify the toggle appears, can be changed, and the backend request includes `fillOrKill`.

### Implementation

 - [X] T013 [P] [US1] Add a visible fill-or-kill yes/no toggle to `frontend/src/pages/OrderEntryPage.tsx`
 - [X] T014 [P] [US1] Add optional `fillOrKill?: boolean` to `frontend/src/types/order.types.ts`
 - [X] T015 [P] [US1] Update `frontend/src/api/orderApi.ts` to include `fillOrKill` in the order submission payload
 - [X] T016 [US1] Update `services/order-service/src/main/java/com/brokerdealer/orderservice/controller/OrderController.java` to bind `fillOrKill` from the request and pass it to order creation
 - [X] T017 [US1] Update `services/order-service/src/main/java/com/brokerdealer/orderservice/service/OrderService.java` to propagate `fillOrKill` into persisted `Order` entities and into execution request events

### Tests

 - [X] T018 [P] [US1] Add a frontend unit test in `frontend/src/test/OrderEntryPage.test.tsx` verifying the FOK toggle renders and the submitted request includes `fillOrKill` when changed
 - [X] T019 [US1] Add an order-service unit test in `services/order-service/src/test/java/com/brokerdealer/orderservice/service/OrderServiceTest.java` validating that `fillOrKill=true` and `fillOrKill=false` are accepted and stored on created orders

---

## Phase 4: User Story 2 - Backend Enforces Fill or Kill Semantics (Priority: P1)

**Goal**: Enforce that fill-or-kill orders are fully filled immediately or canceled with no partial fill.

**Independent Test**: Submit FOK orders and verify full fill or cancel outcomes, with no partial fill persisted.

### Implementation

 - [X] T020 [P] [US2] Add backend handling for FOK semantics in `services/execution-service/src/main/java/com/brokerdealer/executionservice/service/FillSimulationService.java`
 - [X] T021 [P] [US2] Ensure `FillSimulationService` uses `fillOrKill` to convert partial outcomes into cancel outcomes and emit no partial execution when FOK is true
 - [X] T022 [US2] Update any execution event or order lifecycle mapping in `services/execution-service/src/main/java/com/brokerdealer/executionservice/service/ExecutionService.java` or related service classes to preserve FOK outcome semantics

### Tests

 - [X] T023 [P] [US2] Create a unit test in `services/execution-service/src/test/java/com/brokerdealer/executionservice/service/FillSimulationServiceTest.java` covering FOK conversion of partial outcomes to rejected
 - [X] T024 [US2] Add an integration test in `services/order-service/src/test/java/com/brokerdealer/orderservice/service/OrderServiceIntegrationTest.java` validating an FOK order travels from `order-service` to `execution-service` and returns a terminal `filled` or `canceled` status (placeholder/disabled)

---

## Phase 5: User Story 3 - User Receives Clear Feedback for Fill or Kill Orders (Priority: P2)

**Goal**: Surface a clear filled or canceled outcome message for FOK orders on the frontend.

**Independent Test**: Verify the order result page or toast displays a definitive filled or canceled message for FOK submissions.

### Implementation

- [ ] T025 [P] [US3] Update frontend response handling in `frontend/src/pages/OrderEntryPage.tsx` to show a clear FOK outcome notification for `filled` and `canceled` statuses
- [ ] T026 [P] [US3] Update `frontend/src/api/orderApi.ts` or client-side response mapping to preserve backend `status` values for FOK orders
- [ ] T027 [US3] Add structured outcome emission in `services/execution-service/src/main/java/com/brokerdealer/executionservice/service/FillSimulationService.java` for `order.fok.outcome` events and metrics

### Tests

 - [X] T028 [P] [US3] Add an end-to-end Playwright test in `frontend/e2e/order-entry.spec.ts` verifying the FOK toggle, submission flow, and final filled/canceled user message (UI smoke + toggle test)
 - [ ] T029 [US3] Add a backend test in `services/execution-service/src/test/java/com/brokerdealer/executionservice/service/OrderOutcomeMetricsTest.java` validating that FOK `filled` and `canceled` outcomes emit structured events/metrics

---

## Phase 6: Polish & Cross-Cutting Concerns

 - [X] T030 [P] Update `specs/001-fill-or-kill/contracts/order-api.yaml` to document the `fillOrKill` boolean field and the `OrderResult` status expectations for FOK orders
 - [X] T031 [P] Update `specs/001-fill-or-kill/quickstart.md` with concrete validation commands for the new FOK payload and outcome scenarios
 - [X] T032 [P] Add any missing frontend accessibility labels or helper text for the FOK toggle in `frontend/src/pages/OrderEntryPage.tsx`
 - [X] T033 [P] Add regression tests in `services/order-service/src/test/java/com/brokerdealer/orderservice/controller/OrderControllerTest.java` for malformed `fillOrKill` values being rejected with validation errors
 - [ ] T034 [P] Run the end-to-end validation from `specs/001-fill-or-kill/quickstart.md` and confirm FOK orders behave correctly in the dev environment

---

## Dependencies & Execution Order

- **Phase 1**: Setup reviews can start immediately and in parallel
- **Phase 2**: Foundational changes must complete before user stories begin
- **Phase 3 (US1)**: Can start after Foundational phase finishes; implements core UI and payload persistence
- **Phase 4 (US2)**: Can start after Foundational phase finishes; enforces backend FOK semantics
- **Phase 5 (US3)**: Can start after Foundational phase finishes; adds outcome feedback
- **Phase 6**: Polish work depends on all user stories completing

### Story Dependencies

- `US1` and `US2` are both P1 and can proceed in parallel after Foundation
- `US3` is P2 and depends on FOK order handling being present, but remains independently testable once FOK responses are available

---

## Parallel Execution Examples

- **US1**: `T013`, `T014`, and `T015` can run in parallel because they touch separate frontend and controller files; `T016` then `T018` can follow.
- **US2**: `T020`, `T021`, `T022`, and `T023` can be developed in parallel across execution-service and mapping code; `T024` verifies the integrated path.
- **US3**: `T025`, `T026`, and `T027` can be implemented in parallel; `T028` and `T029` validate the full feedback behavior.

---

## Implementation Strategy

1. Complete Phases 1 and 2 to establish data flow and shared migration changes.
2. Deliver `US1` first as the MVP-visible feature: toggle + payload.
3. Add `US2` enforcement to make FOK orders deterministic.
4. Add `US3` messaging to complete the user experience.
5. Finish with cross-cutting polish, contract docs, and validation commands.
