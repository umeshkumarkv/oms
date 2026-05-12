# CLAUDE.md — auth-service
# Part of Broker Dealer OMS POC
# Read root CLAUDE.md first. This file adds service-specific detail.

---

## 1. Service Purpose

The auth-service is the sole issuer of JWT tokens for the Broker Dealer OMS. It owns all user
account records, handles login/logout, and manages token blacklisting via Redis. No other service
issues or validates tokens at source — validation is delegated to the API Gateway. This service
must never access trading data, order records, or client portfolios.

---

## 2. Port

8081

---

## 3. Database Schema

auth_schema

---

## 4. Kafka Topics Produced

None — auth-service does not publish to any Kafka topic.

---

## 5. Kafka Topics Consumed

None — auth-service does not consume any Kafka topic.

---

## 6. Redis Cache Keys Owned

| Key Pattern              | TTL          | Purpose                              |
|--------------------------|--------------|--------------------------------------|
| auth:blacklist:{token}   | Token expiry | Blacklisted tokens on logout         |

Cache-aside pattern applies. On logout, add token to blacklist with TTL = remaining token expiry.

---

## 7. REST Endpoints Exposed

See openapi.yaml for full schema.

| Method | Path               | Auth Required | Role      | Description            |
|--------|--------------------|---------------|-----------|------------------------|
| POST   | /api/v1/auth/login | No            | Any       | Issue JWT              |
| POST   | /api/v1/auth/logout| Yes           | Any       | Blacklist token        |
| GET    | /api/v1/auth/me    | Yes           | Any       | Current user info      |

---

## 8. REST Endpoints Called (Downstream Dependencies)

None — auth-service has no downstream service dependencies.

---

## 9. Entities Owned

User:
- id: UUID (PK, auto-generated)
- username: String (unique, non-null)
- passwordHash: String (BCrypt, non-null)
- role: UserRole enum (TRADER / OPERATIONS / ADMIN)
- createdAt: Instant (non-null, immutable)

---

## 10. What This Service Must NEVER Do

- Never access order_schema, client_schema, market_schema, risk_schema, execution_schema, audit_schema
- Never call order-service, client-service, marketdata-service, risk-service, execution-service, or audit-service
- Never publish to any Kafka topic
- Never store plain text passwords — always BCrypt
- Never log passwords or tokens
- Never issue tokens with role not in [TRADER, OPERATIONS, ADMIN]
- Never set token expiry longer than 8 hours

---

## 11. Key Business Rules

- JWT algorithm: HS256
- JWT expiry: 8 hours
- JWT secret: read from JWT_SECRET environment variable — never hardcoded
- JWT payload: userId (UUID), role (string), expiry (epoch seconds)
- On logout: insert token into Redis blacklist with key auth:blacklist:{token}, TTL = remaining expiry
- API Gateway checks blacklist before forwarding — auth-service does not re-check on each call
- Password hashing: BCryptPasswordEncoder with strength 10
- Seed users: trader.john, trader.sarah (TRADER), ops.manager (OPERATIONS), risk.manager (ADMIN), viewer.user (VIEWER)
- All seed user passwords: Demo@1234