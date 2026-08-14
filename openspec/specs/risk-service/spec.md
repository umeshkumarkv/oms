# risk-service -- Service Specification

## Purpose

Pre-trade risk evaluation microservice for the broker-dealer OMS. The service:
1. Consumes order-evaluation requests from Kafka, runs a configurable ordered rule chain, and publishes APPROVED/REJECTED/WARNED results.
2. Exposes a REST API for administrators to view, propose (maker), and approve/reject (checker) changes to risk rules with a full audit trail.
3. Uses Redis for stateful duplicate-order detection with a 30-second TTL window.

## Port

`8085` (application.yml:46)

## Database Schema

Schema name: `risk_schema` (application.yml:24 `spring.flyway.schemas: risk_schema`; application.yml:21 `hibernate.default_schema: risk_schema`)

Tables (created by Flyway migrations in `src/main/resources/db/migration/`):

| Table | Introduced | Description |
|---|---|---|
| `risk_rule_config` | V1__schema.sql:4 | Master configuration for every risk rule |
| `risk_rule_audit` | V4__risk_rule_metadata_and_audit.sql:33 | Append-only field-level audit log for rule changes |
| `risk_warnings` | V11__create_risk_warnings.sql:1 | Persisted WARN-level risk results per order |
| `risk_rule_pending_changes` | V13__add_risk_rule_pending_changes.sql:1 | Maker-checker workflow: pending rule-change proposals |

## Kafka Produced

| Topic | Trigger | Key | Payload fields | File:line |
|---|---|---|---|---|
| `risk.evaluation.result` | After evaluating a `risk.evaluation.requested` event | `orderId` (UUID as String) | `orderId` (UUID), `clientId` (UUID), `userId` (UUID), `result` (String: APPROVED/REJECTED/WARNED), `violatedRuleCode` (String, nullable), `rejectionReason` (String, nullable), `timestamp` (Instant) | RiskResultProducer.java:14,19 |

Exact string literal: `"risk.evaluation.result"` (RiskResultProducer.java:14 `private static final String RISK_EVALUATION_RESULT = "risk.evaluation.result";`)

## Kafka Consumed

| Topic | Consumer group | Action | File:line |
|---|---|---|---|
| `risk.evaluation.requested` | `risk-service-risk.evaluation.requested-group` | Deserialise to `RiskEvaluationRequest`, invoke `RiskEvaluationService.evaluate()`, publish result to `risk.evaluation.result` | RiskEvaluationConsumer.java:26-27 |

Exact string literal: `"risk.evaluation.requested"` (RiskEvaluationConsumer.java:26 `topics = "risk.evaluation.requested"`)

Default consumer group-id from application.yml:31 is `risk-service-group`; the listener overrides this with the topic-specific group `risk-service-risk.evaluation.requested-group` declared inline at RiskEvaluationConsumer.java:27.

## Redis Keys Owned

| Key pattern | TTL | Purpose | File:line |
|---|---|---|---|
| `dup:{clientId}:{symbol}:{side}:{quantity}:{limitPrice\|MARKET}` | 30 seconds (`Duration.ofSeconds(30)`) | Duplicate-order detection via SET NX: if key exists, order is rejected as duplicate | DuplicateOrderRule.java:32-38 |

Redis is configured at application.yml:40-43 (`spring.data.redis.host`, `spring.data.redis.port: 6379`, `timeout: 2000ms`). No `@Cacheable`/`@CachePut`/`@CacheEvict` annotations are used; direct `StringRedisTemplate` calls only.

## REST Endpoints Exposed

Base path: `/api/v1/risk` (RiskRuleController.java:22)

| Method | Path | Auth/Role | Handler method | File:line |
|---|---|---|---|---|
| GET | `/api/v1/risk/rules` | None found in scan | `getRiskRules()` | RiskRuleController.java:29 |
| PATCH | `/api/v1/risk/rules/{ruleCode}` | None found in scan | `submitPendingChange()` | RiskRuleController.java:35 |
| GET | `/api/v1/risk/rules/audit` | None found in scan | `getAuditLog()` | RiskRuleController.java:45 |
| GET | `/api/v1/risk/rules/pending-changes` | None found in scan | `listPendingChanges()` (optional `?status` param) | RiskRuleController.java:51 |
| GET | `/api/v1/risk/rules/pending-changes/{changeId}` | None found in scan | `getPendingChange()` | RiskRuleController.java:58 |
| PATCH | `/api/v1/risk/rules/pending-changes/{changeId}/approve` | None found in scan | `approveChange()` | RiskRuleController.java:64 |
| PATCH | `/api/v1/risk/rules/pending-changes/{changeId}/reject` | None found in scan | `rejectChange()` | RiskRuleController.java:72 |

Notes:
- `PATCH /api/v1/risk/rules/{ruleCode}` reads headers `X-User-Id` and `X-Username` (defaultValue `SYSTEM`) for maker identity (RiskRuleController.java:38-39).
- `PATCH /api/v1/risk/rules/pending-changes/{changeId}/approve` and `reject` read `X-User-Id` header for checker identity (RiskRuleController.java:66, 75).
- No `@PreAuthorize` or Spring Security annotations were found on any controller method.
- Responses are wrapped: `{ "data": ..., "meta": { "timestamp": ..., "requestId": ... } }` (RiskRuleController.java:81-89).

## REST Endpoints Called

None found in scan. No `RestTemplate`, `WebClient`, or `@FeignClient` usage detected in `src/main`. The service receives all order context (client portfolio snapshot, KYC status, margin call flag, etc.) as fields embedded in the Kafka `RiskEvaluationRequest` event; it does not make outbound HTTP calls at evaluation time.

## Entities

### RiskRuleConfig

File: `src/main/java/com/brokerdealer/riskservice/domain/RiskRuleConfig.java`
Table: `risk_schema.risk_rule_config`

| Field | Java type | Column | Constraints | File:line |
|---|---|---|---|---|
| `ruleCode` | `String` | `rule_code` | `@Id`, `nullable=false`, `length=100` | RiskRuleConfig.java:18-19 |
| `ruleName` | `String` | `rule_name` | `nullable=false`, `length=200` | RiskRuleConfig.java:21-22 |
| `ruleCategory` | `String` | `rule_category` | `nullable=false`, `length=50` | RiskRuleConfig.java:24-25 |
| `enabled` | `Boolean` | `enabled` | `nullable=false` | RiskRuleConfig.java:27-28 |
| `thresholdValue` | `BigDecimal` | `threshold_value` | `precision=19`, `scale=4`, nullable | RiskRuleConfig.java:30-31 |
| `thresholdUnit` | `String` | `threshold_unit` | `length=50`, nullable | RiskRuleConfig.java:33-34 |
| `description` | `String` | `description` | `length=500`, nullable | RiskRuleConfig.java:36-37 |
| `action` | `String` | `action` | `nullable=false`, `length=20` | RiskRuleConfig.java:39-40 |
| `priority` | `Integer` | `priority` | `nullable=false` | RiskRuleConfig.java:42-43 |
| `updatedAt` | `Instant` | `updated_at` | `nullable=false`; set by `@PrePersist`/`@PreUpdate` | RiskRuleConfig.java:45-46,48-52 |

### RiskRuleAudit

File: `src/main/java/com/brokerdealer/riskservice/domain/RiskRuleAudit.java`
Table: `risk_schema.risk_rule_audit`

| Field | Java type | Column | Constraints | File:line |
|---|---|---|---|---|
| `id` | `UUID` | `id` | `@Id`, `@GeneratedValue(UUID)`, `nullable=false`, `updatable=false` | RiskRuleAudit.java:18-20 |
| `ruleCode` | `String` | `rule_code` | `nullable=false`, `length=100` | RiskRuleAudit.java:22-23 |
| `fieldChanged` | `String` | `field_changed` | `nullable=false`, `length=100` | RiskRuleAudit.java:25-26 |
| `oldValue` | `String` | `old_value` | `columnDefinition="TEXT"`, nullable | RiskRuleAudit.java:28-29 |
| `newValue` | `String` | `new_value` | `columnDefinition="TEXT"`, nullable | RiskRuleAudit.java:31-32 |
| `changedBy` | `String` | `changed_by` | `nullable=false`, `length=255` | RiskRuleAudit.java:34-35 |
| `changeReason` | `String` | `change_reason` | `nullable=false`, `length=1000` | RiskRuleAudit.java:37-38 |
| `changedAt` | `Instant` | `changed_at` | `nullable=false`, `updatable=false`; set by `@PrePersist` if null | RiskRuleAudit.java:40-41,43-48 |

### RiskRulePendingChange

File: `src/main/java/com/brokerdealer/riskservice/domain/RiskRulePendingChange.java`
Table: `risk_schema.risk_rule_pending_changes`

| Field | Java type | Column | Constraints | File:line |
|---|---|---|---|---|
| `id` | `UUID` | `id` | `@Id`, `@GeneratedValue(UUID)`, `nullable=false`, `updatable=false` | RiskRulePendingChange.java:19-21 |
| `ruleCode` | `String` | `rule_code` | `nullable=false`, `length=100` | RiskRulePendingChange.java:23-24 |
| `changeType` | `PendingChangeType` (enum) | `change_type` | `@Enumerated(EnumType.STRING)`, `nullable=false`, `length=30` | RiskRulePendingChange.java:26-28 |
| `proposedEnabled` | `Boolean` | `proposed_enabled` | nullable | RiskRulePendingChange.java:30-31 |
| `proposedThreshold` | `BigDecimal` | `proposed_threshold` | `precision=19`, `scale=4`, nullable | RiskRulePendingChange.java:33-34 |
| `makerUserId` | `String` | `maker_user_id` | `nullable=false`, `length=200` | RiskRulePendingChange.java:36-37 |
| `makerUsername` | `String` | `maker_username` | `nullable=false`, `length=200` | RiskRulePendingChange.java:39-40 |
| `status` | `PendingChangeStatus` (enum) | `status` | `@Enumerated(EnumType.STRING)`, `nullable=false`, `length=20` | RiskRulePendingChange.java:42-44 |
| `makerNote` | `String` | `maker_note` | `length=500`, nullable | RiskRulePendingChange.java:46-47 |
| `rejectionReason` | `String` | `rejection_reason` | `length=500`, nullable | RiskRulePendingChange.java:49-50 |
| `checkerUserId` | `String` | `checker_user_id` | `length=200`, nullable | RiskRulePendingChange.java:52-53 |
| `currentEnabled` | `Boolean` | `current_enabled` | nullable | RiskRulePendingChange.java:55-56 |
| `currentThreshold` | `String` | `current_threshold` | `length=255`, nullable | RiskRulePendingChange.java:58-59 |
| `submittedAt` | `Instant` | `submitted_at` | `nullable=false`, `updatable=false` | RiskRulePendingChange.java:61-62 |
| `reviewedAt` | `Instant` | `reviewed_at` | nullable | RiskRulePendingChange.java:64-65 |

### RiskWarning

File: `src/main/java/com/brokerdealer/riskservice/domain/RiskWarning.java`
Table: `risk_schema.risk_warnings`

| Field | Java type | Column | Constraints | File:line |
|---|---|---|---|---|
| `id` | `UUID` | `id` (implicit column name) | `@Id`, `@GeneratedValue(UUID)`, no explicit `@Column` | RiskWarning.java:27-28 |
| `orderId` | `UUID` | `order_id` | `nullable=false` | RiskWarning.java:29-30 |
| `ruleCode` | `String` | `rule_code` | `nullable=false`, `length=100` | RiskWarning.java:32-33 |
| `reason` | `String` | `reason` | `nullable=false` | RiskWarning.java:35-36 |
| `createdAt` | `Instant` | `created_at` | `nullable=false`, `updatable=false` | RiskWarning.java:38-39 |

## What This Service Must NEVER Do

1. [CONVENTION -- INFERRED, CONFIRM WITH TEAM] Make synchronous HTTP calls to other services during order evaluation. No `RestTemplate`, `WebClient`, or `@FeignClient` usage was found in `src/main`. All external state (KYC, cash balance, holdings, margin call status) is carried inbound in the `RiskEvaluationRequest` Kafka event (RiskEvaluationRequest.java:22-37).

2. [CONVENTION -- INFERRED, CONFIRM WITH TEAM] Write to or read from another service's PostgreSQL schema. Only `risk_schema` is referenced in JPA entities (RiskRuleConfig.java:14, RiskRuleAudit.java:14, RiskRulePendingChange.java:15, RiskWarning.java:22). No cross-schema SQL was found.

3. [CONVENTION -- INFERRED, CONFIRM WITH TEAM] Enable a risk rule without the maker-checker approval workflow. The `PATCH /api/v1/risk/rules/{ruleCode}` endpoint routes through `PendingChangeService.submitPendingChange()` (RiskRuleController.java:41), but no `@PreAuthorize` enforcement was found in code to prevent a single user from both submitting and approving their own change.

4. [CONVENTION -- INFERRED, CONFIRM WITH TEAM] Allow direct writes to `risk_rule_audit`. The comment in V4__risk_rule_metadata_and_audit.sql:32 states "append-only — no UPDATE or DELETE", but no database-level constraints or application-layer enforcement blocks such writes.

5. [CONVENTION -- INFERRED, CONFIRM WITH TEAM] Default new risk rules to enabled. V12__fix_warn_rules_action_and_threshold.sql:2 references `CLAUDE.md §9` requiring all rules default OFF; this is enforced only by seeding convention, not a database CHECK constraint.

## Key Business Rules

1. **Rule evaluation order is priority-ascending, fail-fast on REJECT.** Rules are loaded ordered by `priority ASC` (RiskEvaluationService.java:51); the first `REJECTED` result short-circuits remaining rules (RiskEvaluationService.java:73-76). WARNED results are collected and persisted after the full chain (RiskEvaluationService.java:86-92).

2. **Duplicate-order window is 30 seconds.** Redis SET NX with `Duration.ofSeconds(30)` (DuplicateOrderRule.java:38). Key pattern: `dup:{clientId}:{symbol}:{side}:{quantity}:{limitPrice|MARKET}`. MARKET orders or null limit price use the literal segment `"MARKET"` (DuplicateOrderRule.java:30-31).

3. **Seeded REJECT-action rules and their default thresholds (from migrations):**
   - `SINGLE_ORDER_QTY_LIMIT`: threshold 10,000 qty, enabled=true, priority=1 (V2__seed.sql:5, V3__add_priority:6)
   - `ORDER_VALUE_LIMIT`: threshold $500,000 USD, enabled=true, priority=2 (V2__seed.sql:6, V3__add_priority:7)
   - `RULE_MAX_ORDER_QTY`: threshold 50,000 shares, enabled=true, priority=9 (V5__add_rule_max_order_qty.sql:5-14)
   - `RULE_MAX_ORDER_VALUE`: threshold $500,000 USD, enabled=true, priority=10 (V6__add_rule_max_order_value.sql:5-14)
   - `RULE_DUPLICATE_ORDER`: no threshold, enabled=true, priority=20 (V9__seed_stateful_rules.sql:5-13)
   - `RULE_SELF_TRADE`: no threshold, enabled=true, priority=21 (V9__seed_stateful_rules.sql:14-22)
   - `RULE_MARGIN_CALL_ACTIVE`: no threshold, enabled=true, priority=24 (V7__add_rule_margin_call_active.sql:5-14)
   - `RULE_TRADING_ENABLED`: no threshold, enabled=true, priority=25 (V8__seed_client_status_rules.sql:6-14)
   - `RULE_KYC_VERIFIED`: no threshold, enabled=true, priority=26 (V8__seed_client_status_rules.sql:15-23)
   - `RULE_SUFFICIENT_CASH`: no threshold, enabled=true, priority=27 (V8__seed_client_status_rules.sql:24-32)
   - `RULE_SUFFICIENT_HOLDINGS`: no threshold, enabled=true, priority=28 (V8__seed_client_status_rules.sql:33-41)

4. **Seeded WARN-action rules (fat-finger checks, all default disabled per V12):**
   - `PRICE_DEVIATION_CHECK`: threshold 10% deviation from lastPrice, enabled=false, priority=30 (V12__fix_warn_rules_action_and_threshold.sql:8-16)
   - `NOTIONAL_VALUE_CHECK`: threshold $1,000,000 notional, enabled=false, priority=31 (V12:18-26)
   - `ORDER_QTY_SPIKE_CHECK`: threshold 10x multiplier vs avgOrderQty; skipped when avgOrderQty is 0 or null, enabled=false, priority=32 (V12:28-36)
   - `ORDER_VALUE_SPIKE_CHECK`: threshold 10x multiplier vs avgOrderValue, enabled=false, priority=33 (V12:39-49)
   - `RULE_DAILY_ORDER_COUNT`: no threshold, enabled=false, priority=34 (V12:51-54)

5. **Pending change status lifecycle.** `PendingChangeStatus` enum: `PENDING`, `APPROVED`, `REJECTED` (PendingChangeStatus.java:3-7). `PendingChangeType` enum: `ENABLE`, `DISABLE`, `UPDATE_THRESHOLD` (PendingChangeType.java:3-7).

6. **Rule chain is reloaded in-process after any direct update.** `RiskRuleService.updateRule()` calls `riskEvaluationService.loadRules()` after saving (RiskRuleService.java:79). Rules are held in a non-thread-safe `List<RiskRule>` field (RiskEvaluationService.java:47); reload under concurrent evaluation traffic is a potential race condition to confirm with team.

7. **Resilience4j circuit breaker is configured but no outbound HTTP calls exist.** The default circuit breaker instance (application.yml:61-69: sliding-window=10, failure-rate-threshold=50%, wait-duration=10s, timeout=2s) is defined but no `@CircuitBreaker` annotations were found in `src/main`.
