## Purpose

Guarantees that an order flagged Fill-or-Kill is evaluated exactly once at execution time and results in either a full fill or a full rejection, with no partial fill ever produced.

## ADDED Requirements

### Requirement: Immediate, single evaluation for Fill-or-Kill orders
The system SHALL evaluate a Fill-or-Kill order for execution exactly once, immediately upon receiving the execution request, and SHALL NOT place it on a pending/resting path that re-evaluates it later.

#### Scenario: Fill-or-Kill order does not rest awaiting the limit price
- **WHEN** an execution request arrives for a LIMIT order with `fillOrKill: true` and the limit condition is not currently satisfied
- **THEN** the system rejects the order immediately
- **AND** the order is not added to the pending/monitored orders awaiting a later price match

### Requirement: Fill-or-Kill orders are never partially filled
The system SHALL NOT record a partial fill for an order flagged Fill-or-Kill. If the order cannot be fully executed at the moment of evaluation, the system SHALL reject/cancel the entire order instead of executing any tranche of it.

#### Scenario: Order would otherwise be partially filled
- **WHEN** an execution request arrives for an order with `fillOrKill: true` and the simulated outcome for that quantity/symbol would ordinarily be a partial fill
- **THEN** the system rejects the entire order
- **AND** no execution/tranche is recorded against the order

#### Scenario: Order is fully fillable
- **WHEN** an execution request arrives for an order with `fillOrKill: true` and the simulated outcome for that quantity/symbol is a full fill
- **THEN** the system fills the order in full immediately

### Requirement: Fill-or-Kill outcome reuses standard order states
The system SHALL represent a killed Fill-or-Kill order using the existing rejected order state, without introducing a new order status value.

#### Scenario: Killed order status
- **WHEN** a Fill-or-Kill order is killed because it could not be fully executed immediately
- **THEN** the order's final status is the same rejected status used for other execution rejections
