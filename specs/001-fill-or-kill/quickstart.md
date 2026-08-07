# Quickstart: Validate Fill-or-Kill Feature

This guide provides minimal validation scenarios to confirm end-to-end behavior for FOK orders.

Prerequisites
- Docker & Docker Compose or a running local dev environment for services
- Java JDK 17, Maven for backend services
- Node.js (16+) and npm/yarn for the frontend

1) Apply DB migration (order-service)

- Add `fill_or_kill` boolean column to `orders` table. Example migration (Postgres):

```sql
ALTER TABLE orders
ADD COLUMN fill_or_kill boolean NOT NULL DEFAULT false;
```

2) Start services (development)

- Using Docker Compose (from repo root):

```bash
docker-compose up --build
```

- Alternatively, run `order-service` and `execution-service` locally via Maven and the frontend via `npm run dev` in `frontend/`.

3) Validate API contract (manual)

- Submit an order with FOK=true:

```bash
curl -X POST \
  -H "Content-Type: application/json" \
  -d '{"symbol":"ABC","quantity":100,"price":10.0,"side":"buy","fillOrKill":true}' \
  http://localhost:8080/orders
```

- Expected outcomes:
  - If liquidity exists immediately: response status `201` with `status: filled` and an `orderId`.
  - If liquidity does not exist for full quantity: response status `201` with `status: canceled` and no partial fills in system.

4) Run automated checks (recommended)

- Order-service unit tests (validate payload and persistence):

```bash
mvn -pl services/order-service test
```

- Execution-service integration test (simulate matching engine):

```bash
mvn -pl services/execution-service test
```

- Frontend Playwright e2e (verify toggle + messaging):

```bash
cd frontend
npm install
npm run test:e2e
```

5) Observability check

- Confirm `order.fok.outcome` events are emitted (logs or event bus) and metric `orders.fok.outcome` increments with `filled` or `canceled` labels.

Notes
- The quickstart focuses on validation; full implementation should include migration files, unit/integration tests, and e2e coverage as indicated in `data-model.md` and `contracts/order-api.yaml`.
