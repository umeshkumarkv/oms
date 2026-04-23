# CLAUDE.md — risk-service
# Part of Broker Dealer OMS POC
# Read root CLAUDE.md first. This file adds service-specific detail.

---

## 1. Service Purpose

The risk-service is a pluggable, configuration-driven rule engine. It evaluates incoming orders
against enabled risk rules and publishes a PASSED or REJECTED result. Rules are stored in the
database with ON/OFF toggles and threshold values, enabling ADMIN users to configure risk limits
without code changes. Each rule implements the RiskRule interface. It must never modify orders
or portfolios directly — it only evaluates and responds.

---

## 2. Port

8085

---

## 3. Database Schema

risk_schema

---

## 4. Kafka Topics Produced

| Topic                    | Trigger                          | Payload                              |
|--------------------------|----------------------------------|--------------------------------------|
| risk.evaluation.result   | After evaluating all enabled rules | orderId, result (PASSED/REJECTED), failedRule, reason |

---

## 5. Kafka Topics Consumed

| Topic                      | Consumer Group ID                               | Action on Receive                  |
|----------------------------|-------------------------------------------------|------------------------------------|
| risk.evaluation.requested  | risk-service-risk.evaluation.requested-group    | Evaluate order against enabled rules and publish result |

Idempotent consumers — check if result already published before re-evaluating.

---

## 6. Redis Cache Keys Owned

None — risk-service does not use Redis caching.

---

## 7. REST Endpoints Exposed

See openapi.yaml for full schema.

| Method | Path                          | Auth Required | Role  | Description               |
|--------|-------------------------------|---------------|-------|---------------------------|
| GET    | /api/v1/risk/rules            | Yes           | ADMIN | List all risk rules       |
| PATCH  | /api/v1/risk/rules/{ruleCode} | Yes           | ADMIN | Enable/disable or update threshold |

---

## 8. REST Endpoints Called (Downstream Dependencies)

None — risk-service has no synchronous downstream dependencies. All communication is via Kafka.

---

## 9. Entities Owned

RiskRuleConfig:
- ruleCode: String (PK, non-null, max 100)
- enabled: Boolean (non-null)
- thresholdValue: BigDecimal (nullable, precision 18 scale 4)
- description: String (max 500)
- updatedAt: Instant (non-null, updated on every change)

---

## 10. What This Service Must NEVER Do

- Never access order_schema, client_schema, market_schema, execution_schema, auth_schema, audit_schema
- Never call order-service, client-service, marketdata-service, execution-service, auth-service, or audit-service
- Never modify orders directly
- Never modify portfolio or holdings directly
- Never hardcode rule logic — all rules must implement RiskRule interface and be loaded from DB
- Never ignore disabled rules (skip them, never evaluate)

---

## 11. Key Business Rules

- RiskRule interface contract: getRuleCode(), evaluate(Order, RiskContext), isEnabled()
- Rules loaded from risk_rule_config at startup and on config change
- Evaluation: run all ENABLED rules in sequence. First REJECTED result stops evaluation.
- SINGLE_ORDER_QTY_LIMIT: quantity > thresholdValue → REJECTED (default ON, threshold=10000)
- ORDER_VALUE_LIMIT: notional value > thresholdValue → REJECTED (default ON, threshold=500000)
- All other 11 rules: default OFF (redundant with Level 1 or Level 2 validation)
- Response to Kafka: always publish result (PASSED or REJECTED) regardless of rule outcome
- Seed: 13 rules as defined in V2__seed.sql