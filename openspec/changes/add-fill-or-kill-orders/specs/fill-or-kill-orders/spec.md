## Purpose

Lets a trader require that a Limit order execute for its full quantity
immediately or not execute at all, with no partial fills and no resting on
the book, so orders that must be all-or-nothing don't linger as
partially-filled or pending positions.

## ADDED Requirements

### Requirement: Fill-or-Kill applies only to Limit orders
The system SHALL accept an optional Fill-or-Kill indicator on order
submission. The system SHALL reject order submission with a validation
error when the Fill-or-Kill indicator is set to Yes and the order type is
not Limit.

#### Scenario: Fill-or-Kill accepted on a Limit order
- **WHEN** a trader submits a Limit order with Fill-or-Kill set to Yes
- **THEN** the system accepts the order for processing

#### Scenario: Fill-or-Kill rejected on a Market order
- **WHEN** a trader submits a Market order with Fill-or-Kill set to Yes
- **THEN** the system rejects the submission with a validation error and
  does not create the order

### Requirement: Fill-or-Kill orders execute in full immediately or are killed
The system SHALL execute a Fill-or-Kill order for its entire submitted
quantity immediately, or cancel the entire order with zero quantity filled.
The system SHALL NOT partially fill a Fill-or-Kill order and SHALL NOT hold
a Fill-or-Kill order in a pending/resting state awaiting a future price
match.

#### Scenario: Full immediate fill
- **WHEN** a Fill-or-Kill Limit order's full quantity can be executed
  immediately
- **THEN** the system fills the entire order quantity and the order's final
  status is Filled

#### Scenario: Cannot fill in full is killed, not partially filled
- **WHEN** a Fill-or-Kill Limit order's full quantity cannot be executed
  immediately
- **THEN** the system cancels the entire order with zero quantity filled
  and the order's final status is Rejected

#### Scenario: Fill-or-Kill orders never rest
- **WHEN** a Fill-or-Kill Limit order's limit price is not immediately
  marketable
- **THEN** the system does not place the order into a pending/resting queue
  for later execution

### Requirement: Fill-or-Kill selection is visible only for Limit orders
The order entry interface SHALL present a Fill-or-Kill Yes/No selection
when the trader has selected Limit as the order type, and SHALL NOT present
or submit a Fill-or-Kill value when the order type is Market.

#### Scenario: Selection shown for Limit orders
- **WHEN** a trader selects Order Type = Limit in the order entry form
- **THEN** the Fill-or-Kill Yes/No selection is visible and selectable

#### Scenario: Selection hidden for Market orders
- **WHEN** a trader selects Order Type = Market in the order entry form
- **THEN** the Fill-or-Kill selection is not shown and no Fill-or-Kill value
  is included in the order submission

### Requirement: A killed Fill-or-Kill order is distinguishable downstream
When a Fill-or-Kill order is killed because it could not be filled in full,
the system SHALL record and surface that outcome through the same
order-status, audit, and notification channels used for other order
rejections, with a reason that identifies it as a Fill-or-Kill cancellation.

#### Scenario: Rejection reason identifies the Fill-or-Kill cancellation
- **WHEN** a Fill-or-Kill order is killed because it could not be filled in
  full
- **THEN** the order's final status is Rejected and its rejection reason
  identifies that the order was killed due to the Fill-or-Kill condition
