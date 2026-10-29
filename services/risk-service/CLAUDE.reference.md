<!-- plumbline:template claude-reference v1 -->
# Risk Service — Reference

<!-- plumbline:begin reference -->
Last updated: 4369a80 (2026-10-28, /pl-onboard, claude-reference v1)

## API

**RiskRuleController** — base path `/api/v1/risk` — all endpoints require `X-User-Role: ADMIN`

| Method | Path | Handler | Purpose |
|--------|------|---------|---------|
| GET | `/api/v1/risk/rules` | `getRiskRules` | Return all risk rule configurations |
| PATCH | `/api/v1/risk/rules/{ruleCode}` | `submitPendingChange` | Submit a maker-checker change for a rule; returns HTTP 202 |
| GET | `/api/v1/risk/rules/audit` | `getAuditLog` | Return the full rule audit log |
| GET | `/api/v1/risk/rules/pending-changes` | `listPendingChanges` | List pending changes; optional `?status=` filter |
| GET | `/api/v1/risk/rules/pending-changes/{changeId}` | `getPendingChange` | Get a single pending change by UUID |
| PATCH | `/api/v1/risk/rules/pending-changes/{changeId}/approve` | `approveChange` | Approve a pending change (checker must differ from maker) |
| PATCH | `/api/v1/risk/rules/pending-changes/{changeId}/reject` | `rejectChange` | Reject a pending change with a mandatory rejection reason |

All responses are wrapped: `{ "data": <payload>, "meta": { "timestamp": ..., "requestId": ... } }`.

Kafka interface (not REST):
- Consumes `risk.evaluation.requested` → `RiskEvaluationConsumer.consumeRiskEvaluationRequested`
- Produces `risk.evaluation.result` → `RiskResultProducer.publishRiskResult` (keyed by `orderId`)

## Data

**risk_rule_config** — primary key: `rule_code` (VARCHAR 100)
- `rule_code` PK, `rule_name` NOT NULL, `rule_category` NOT NULL, `enabled` BOOLEAN NOT NULL, `threshold_value` NUMERIC(18,4), `threshold_unit` VARCHAR(50), `description` VARCHAR(500), `action` VARCHAR(20) NOT NULL (`REJECT` | `WARN`), `priority` INT NOT NULL, `updated_at` TIMESTAMPTZ NOT NULL
- Indexes: none beyond PK in V1; updated by V3 (priority), V4 (metadata), V5–V10, V12 (rule seeding)

**risk_rule_audit** — append-only, never UPDATE/DELETE [migration V4 comment]
- `id` UUID PK, `rule_code` VARCHAR(100) NOT NULL, `field_changed` VARCHAR(100) NOT NULL, `old_value` TEXT, `new_value` TEXT, `changed_by` VARCHAR(255) NOT NULL, `change_reason` VARCHAR(1000) NOT NULL, `changed_at` TIMESTAMPTZ NOT NULL
- Indexes: `idx_risk_rule_audit_rule_code`, `idx_risk_rule_audit_changed_at DESC`

**risk_rule_pending_changes** — maker-checker staging table [migration V13]
- `id` UUID PK, `rule_code` VARCHAR(100) FK → `risk_rule_config.rule_code`, `change_type` VARCHAR(30) NOT NULL, `proposed_enabled` BOOLEAN, `proposed_threshold` NUMERIC(19,4), `maker_user_id` VARCHAR(200) NOT NULL, `maker_username` VARCHAR(200) NOT NULL, `status` VARCHAR(20) NOT NULL DEFAULT `'PENDING'` (enum: `PENDING`, `APPROVED`, `REJECTED`), `maker_note` VARCHAR(500), `rejection_reason` VARCHAR(500), `checker_user_id` VARCHAR(200), `submitted_at` TIMESTAMPTZ NOT NULL, `reviewed_at` TIMESTAMPTZ
- V14 adds `current_enabled` BOOLEAN and `current_threshold` VARCHAR(255) to snapshot the rule state at submission time
- Indexes: `idx_risk_rule_pending_changes_status`, `idx_risk_rule_pending_changes_rule_code`

**risk_warnings** — one row per WARN verdict per order [migration V11]
- `id` UUID PK DEFAULT gen_random_uuid(), `order_id` UUID NOT NULL, `rule_code` VARCHAR(100) NOT NULL, `reason` TEXT NOT NULL, `created_at` TIMESTAMPTZ NOT NULL DEFAULT NOW()
- Index: `idx_risk_warnings_order_id`

**Flyway migrations summary (V1–V14):**
- V1 — initial `risk_rule_config` schema
- V2 — seed initial rules (SINGLE_ORDER_QTY_LIMIT, ORDER_VALUE_LIMIT, PRICE_DEVIATION_CHECK, NOTIONAL_VALUE_CHECK, ORDER_QTY_SPIKE_CHECK, CLIENT_ACTIVE_CHECK, INSTRUMENT_TRADABLE_CHECK, MARKET_HOURS_CHECK, QUANTITY_POSITIVE_CHECK, LIMIT_PRICE_CHECK, CASH_BALANCE_CHECK, HOLDINGS_CHECK, ORDER_VALUE_SPIKE_CHECK)
- V3 — add `priority` column to `risk_rule_config`
- V4 — add metadata columns (`rule_name`, `rule_category`, `threshold_unit`, `action`) and create append-only `risk_rule_audit`
- V5 — add `MAX_ORDER_QTY` rule
- V6 — add `MAX_ORDER_VALUE` rule
- V7 — add `MARGIN_CALL_ACTIVE` rule
- V8 — seed client-status rules (`TRADING_ENABLED`, `KYC_VERIFIED`)
- V9 — seed stateful rules (`DAILY_ORDER_COUNT`, `DUPLICATE_ORDER`, `SELF_TRADE`, `SUFFICIENT_CASH`, `SUFFICIENT_HOLDINGS`)
- V10 — seed WARN rules (`ORDER_QTY_SPIKE_CHECK`, `ORDER_VALUE_SPIKE_CHECK`, `PRICE_DEVIATION_CHECK`, `NOTIONAL_VALUE_CHECK`)
- V11 — create `risk_warnings` table
- V12 — fix WARN rules: set `action='WARN'` and correct thresholds; all WARN rules set `enabled=false`
- V13 — create `risk_rule_pending_changes` table
- V14 — add `current_enabled` and `current_threshold` columns to `risk_rule_pending_changes`

## Integrations

**Kafka (consumed):**
- Topic: `risk.evaluation.requested` — group ID: `risk-service-risk.evaluation.requested-group` — payload: `RiskEvaluationRequest` (orderId, clientId, userId, symbol, side, quantity, limitPrice, orderType, buyingPower, lastPrice, holdingQuantity)
- Deserialized to `LinkedHashMap` via `JsonDeserializer` with `USE_TYPE_INFO_HEADERS=false`; trusted packages: `com.brokerdealer.*`
- Error handling: exponential backoff (1 s, 2 s, 4 s; max 3 attempts) via `DefaultErrorHandler` + `ExponentialBackOffWithMaxRetries`; failures routed to DLT

**Kafka (produced):**
- Topic: `risk.evaluation.result` — payload: `RiskEvaluationResult` (orderId, clientId, userId, result, violatedRuleCode, rejectionReason, timestamp) — key: `orderId.toString()`

**Redis:**
- Duplicate-order deduplication: `StringRedisTemplate` SET NX key `dup:{clientId}:{symbol}:{side}:{quantity}:{limitPrice|MARKET}`, 30 s TTL [code: `rule/DuplicateOrderRule.java`]
- `CacheManager` configured with 30 s TTL default [code: `config/RedisConfig.java`]
- Connection: `SPRING_REDIS_HOST` (default `localhost`) : `SPRING_REDIS_PORT` (default `6379`), timeout 2000 ms

**Actuator:**
- Exposed: `health`, `info`, `metrics` on `/actuator`
- Liveness/readiness probes enabled; `show-details: always`

**Resilience4j:**
- Circuit breaker (default instance): sliding window 10, failure rate threshold 50 %, wait in open state 10 s, half-open permitted calls 3
- Time limiter (default instance): timeout 2 s

## Configuration

| Variable | Default | Purpose |
|----------|---------|---------|
| `SPRING_DATASOURCE_URL` | — (required) | PostgreSQL JDBC URL; must target `risk_schema` |
| `SPRING_DATASOURCE_USERNAME` | `brokerdealer` | Database username |
| `SPRING_DATASOURCE_PASSWORD` | `brokerdealer` | Database password — never log |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker address |
| `SPRING_REDIS_HOST` | `localhost` | Redis host |
| `SPRING_REDIS_PORT` | `6379` | Redis port |
| `JWT_SECRET` | — | JWT signing secret; injected by docker-compose for gateway use; not consumed inside this service |

No Spring profiles defined. Flyway: `baseline-on-migrate: true`, schema `risk_schema`, migrations at `classpath:db/migration`.

## Tech debt

- 0 TODO or FIXME comments found in `src/main/java`.
- `StubRiskRule` is a deliberate forward-compatibility shim but also masks missing rule implementations silently; new rule codes added to the DB without a matching Java class will silently pass. This is a known trade-off documented in the Javadoc.
- Several rules tagged `rule_category = 'REDUNDANT'` in V4 (CLIENT_ACTIVE_CHECK, INSTRUMENT_TRADABLE_CHECK, MARKET_HOURS_CHECK, QUANTITY_POSITIVE_CHECK, LIMIT_PRICE_CHECK, CASH_BALANCE_CHECK, HOLDINGS_CHECK, ORDER_VALUE_SPIKE_CHECK) — the category name implies they may duplicate checks already performed by other services; no ADR found. 1 location: `V4__risk_rule_metadata_and_audit.sql`.

## Open questions

- What downstream service consumes `risk.evaluation.result`? (Not visible in this service's code.)
- The `rule_category = 'REDUNDANT'` label on 7+ rules suggests possible duplication with order-service or market-data-service validation — needs cross-service confirmation.
- No integration/contract tests found between the Kafka consumer payload shape and the order-service producer; a schema registry or Avro schema would guard against drift.
- `JWT_SECRET` is present in docker-compose for this service but the service does not parse JWTs; confirm whether the variable is vestigial or reserved for future in-service token validation.

— Plumbline 2.8.7 · /pl-onboard · claude-reference v1 · commit 4369a80 · judge pending · AI-drafted, posted after SME review
<!-- plumbline:end reference -->
