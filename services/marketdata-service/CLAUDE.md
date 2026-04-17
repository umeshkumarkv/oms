# CLAUDE.md — marketdata-service
# Part of Broker Dealer OMS POC
# Read root CLAUDE.md first. This file adds service-specific detail.

---

## 1. Service Purpose

The marketdata-service owns all mock instrument data and market hours state. It serves as the
read-only data source for order-service during Level 2 validation. All prices are mock/static
values — no real exchange connectivity. It must never store orders, client data, or execution
records. It is the sole authority for market open/close state and instrument tradability.

---

## 2. Port

8084

---

## 3. Database Schema

market_schema

---

## 4. Kafka Topics Produced

None — marketdata-service does not publish to any Kafka topic.

---

## 5. Kafka Topics Consumed

None — marketdata-service does not consume any Kafka topic.

---

## 6. Redis Cache Keys Owned

| Key Pattern              | TTL        | Purpose                              |
|--------------------------|------------|--------------------------------------|
| market:price:{symbol}    | 30 seconds | Mock price data for instruments      |

Cache-aside pattern. No explicit invalidation — auto-expiry only.

---

## 7. REST Endpoints Exposed

See openapi.yaml for full schema.

| Method | Path                        | Auth Required | Description                    |
|--------|-----------------------------|---------------|--------------------------------|
| GET    | /api/v1/market/{symbol}     | No            | Get instrument by symbol       |
| GET    | /api/v1/market/hours        | No            | Market open/close status       |
| GET    | /api/v1/market/instruments  | No            | List all instruments           |

---

## 8. REST Endpoints Called (Downstream Dependencies)

None — marketdata-service has no downstream service dependencies.

---

## 9. Entities Owned

Instrument:
- symbol: String (PK, non-null, max 20)
- bidPrice: BigDecimal (precision 18 scale 4)
- askPrice: BigDecimal (precision 18 scale 4)
- lastPrice: BigDecimal (precision 18 scale 4)
- tradable: Boolean (non-null)
- tradingHalted: Boolean (non-null, default false)
- minQuantity: Integer
- maxQuantity: Integer
- dataTimestamp: Instant

---

## 10. What This Service Must NEVER Do

- Never access order_schema, client_schema, risk_schema, execution_schema, auth_schema, audit_schema
- Never call order-service, client-service, risk-service, execution-service, auth-service, or audit-service
- Never store orders or client data
- Never publish to Kafka
- Never use real exchange prices — mock data only

---

## 11. Key Business Rules

- Market hours: 09:30-16:00 ET Monday-Friday (America/New_York timezone)
- A symbol is tradable if tradable=true AND tradingHalted=false
- Seed instruments: AAPL, MSFT, GOOGL, TSLA, AMZN, NVDA, META, JPM, BAC (all tradable, not halted)
- Seed instrument HALT: tradable=true, tradingHalted=true — used for testing halt scenarios
- Prices are static in database — no real-time updates in POC
- Cache TTL for prices: 30 seconds — auto-expiry only
- GET /api/v1/market/{symbol} returns 404 SYMBOL_NOT_FOUND if symbol not in database
- GET /api/v1/market/hours determines open status from current America/New_York time