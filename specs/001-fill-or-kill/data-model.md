# Data Model: Fill or Kill (FOK)

## Entities

### Order
- `id` (UUID) — primary key
- `user_id` (UUID) — reference to trader
- `symbol` (string)
- `quantity` (integer)
- `price` (decimal)
- `side` (enum: buy|sell)
- `fill_or_kill` (boolean) — NEW: indicates FOK instruction; default `false`
- `status` (enum: pending|filled|canceled|rejected)
- `created_at` (timestamp)
- `updated_at` (timestamp)

Validation rules:
- `quantity` > 0
- `price` > 0 for limit orders (or `market` semantics handled separately)
- `fill_or_kill` must be boolean (reject malformed values)

State transitions for FOK orders:
- Initial: `pending` → on processing by `execution-service`:
  - if full match possible immediately → `filled`
  - else → `canceled` (no partial fills allowed)

Audit requirements:
- Every FOK order processing must emit `order.fok.outcome` event with `orderId`, `outcome` (`filled`|`canceled`), `reason`, and `timestamp`.

## Schema change (Postgres example)

ALTER TABLE orders
ADD COLUMN fill_or_kill boolean NOT NULL DEFAULT false;

(Apply migration in `order-service` migrations; ensure service-owned schema and migration tooling is used.)

## Ownership
- `order-service`: owns persistence of `orders` table and validation at ingest; records `fill_or_kill` value.
- `execution-service`: owns enforcement of match-or-cancel semantics and emits outcome events/metrics.
