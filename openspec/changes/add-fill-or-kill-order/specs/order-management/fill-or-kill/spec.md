## Purpose

Lets a trader mark an order as Fill-or-Kill so it is only ever fully executed or fully cancelled, never partially filled, and lets the order entry form expose and validate that choice.

## ADDED Requirements

### Requirement: Fill-or-Kill flag on order submission
The system SHALL accept an optional `fillOrKill` boolean on order submission. When omitted, the system SHALL treat the order as `fillOrKill: false`.

#### Scenario: Submission omits the flag
- **WHEN** a client submits an order without a `fillOrKill` field
- **THEN** the system records the order with `fillOrKill` equal to `false`

#### Scenario: Submission explicitly sets the flag
- **WHEN** a client submits a LIMIT order with `fillOrKill: true`
- **THEN** the system records the order with `fillOrKill` equal to `true`

### Requirement: Fill-or-Kill restricted to LIMIT orders
The system SHALL reject any order submission where `fillOrKill` is `true` and `orderType` is not `LIMIT`, returning a validation error and not persisting the order.

#### Scenario: Fill-or-Kill requested on a MARKET order
- **WHEN** a client submits an order with `orderType: MARKET` and `fillOrKill: true`
- **THEN** the system rejects the submission with a validation error identifying that Fill-or-Kill requires a LIMIT order
- **AND** no order is persisted

#### Scenario: Fill-or-Kill requested on a LIMIT order
- **WHEN** a client submits an order with `orderType: LIMIT`, a valid `limitPrice`, and `fillOrKill: true`
- **THEN** the system accepts the submission for further processing

### Requirement: Order entry form exposes a Fill-or-Kill toggle
The order entry form SHALL present a Fill-or-Kill option as a Yes/No toggle, enabled only when the selected order type is LIMIT.

#### Scenario: Order type is LIMIT
- **WHEN** the trader selects order type LIMIT in the order entry form
- **THEN** the Fill-or-Kill toggle is enabled and defaults to "No"

#### Scenario: Order type is MARKET
- **WHEN** the trader selects order type MARKET in the order entry form
- **THEN** the Fill-or-Kill toggle is disabled and its value is treated as "No" regardless of any prior selection

#### Scenario: Switching away from LIMIT clears the flag
- **WHEN** the trader has set Fill-or-Kill to "Yes" while order type is LIMIT, then changes order type to MARKET
- **THEN** the form resets the Fill-or-Kill toggle to "No" and disables it
