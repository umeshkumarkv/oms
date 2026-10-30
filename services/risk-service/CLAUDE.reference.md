<!-- plumbline:template claude-reference v1 -->
# risk-service — reference

<!-- plumbline:begin reference -->
Last updated: 4586741 (2026-10-30, /pl-onboard, claude-reference v1)

## API

All endpoints are under `RiskRuleController` (`controller/RiskRuleController.java`). All require `X-User-Role: ADMIN` (enforced by `RoleAuthorizationFilter`). All responses are wrapped in `{ "data": ..., "meta": { "timestamp": ..., "requestId": ... } }`.

### RiskRuleController — /api/v1/risk

| Method | Path | Handler method | Purpose |
|--------|------|----------------|---------|
| GET | /api/v1/risk/rules | `getRiskRules` | List all risk rules ordered by priority |
| PATCH | /api/v1/risk/rules/{ruleCode} | `submitPendingChange` | Submit a pending rule change (maker step); requires `X-User-Id`, `X-Username` headers |
| GET | /api/v1/risk/rules/audit | `getAuditLog` | List all rule audit records ordered by changedAt DESC |
| GET | /api/v1/risk/rules/pending-changes | `listPendingChanges` | List pending changes, optionally filtered by `?status=PENDING\|APPROVED\|REJECTED` |
| GET | /api/v1/risk/rules/pending-changes/{changeId} | `getPendingChange` | Get a single pending change by UUID |
| PATCH | /api/v1/risk/rules/pending-changes/{changeId}/approve | `approveChange` | Approve a pending change (checker step); requires `X-User-Id` header; self-approval blocked |
| PATCH | /api/v1/risk/rules/pending-changes/{changeId}/reject | `rejectChange` | Reject a pending change; requires `X-User-Id` header and `rejectionReason` in body |

**HTTP status codes:**
- 200 — success (GET, approve, reject)
- 202 — accepted (submit pending change)
- 400 — validation error (missing fields, invalid status filter)
- 401 — missing `X-User-Role` header
- 403 — role not ADMIN, or self-approval attempt
- 404 — rule or pending change not found
- 409 — change already reviewed

## Data

Schema: `risk_schema` (Flyway-managed). JPA DDL mode: `validate`.

### Tables

**risk_rule_config** — one row per rule code; the authoritative config loaded at startup.
- `rule_code` VARCHAR(100) PK
- `rule_name` VARCHAR(200) NOT NULL
- `rule_category` VARCHAR(50) NOT NULL (FINANCIAL_LIMIT, PRE_TRADE, FAT_FINGER, CLIENT, STATEFUL, REDUNDANT)
- `enabled` BOOLEAN NOT NULL
- `threshold_value` NUMERIC(18,4) nullable
- `threshold_unit` VARCHAR(50) nullable (QUANTITY, USD, PERCENT, MULTIPLIER, SHARES)
- `description` VARCHAR(500)
- `action` VARCHAR(20) NOT NULL (REJECT or WARN)
- `priority` INTEGER NOT NULL (lower = evaluated first)
- `updated_at` TIMESTAMPTZ NOT NULL (auto-set by `@PreUpdate`)

**risk_rule_audit** — append-only change log written on every approved rule update.
- `id` UUID PK (gen_random_uuid)
- `rule_code` VARCHAR(100) NOT NULL
- `field_changed` VARCHAR(100) NOT NULL
- `old_value` TEXT nullable
- `new_value` TEXT nullable
- `changed_by` VARCHAR(255) NOT NULL
- `change_reason` VARCHAR(1000) NOT NULL
- `changed_at` TIMESTAMPTZ NOT NULL
- Indexes: `idx_risk_rule_audit_rule_code`, `idx_risk_rule_audit_changed_at`

**risk_rule_pending_changes** — maker/checker workflow records.
- `id` UUID PK (application-generated via `GenerationType.UUID`)
- `rule_code` VARCHAR(100) NOT NULL FK → risk_rule_config(rule_code)
- `change_type` VARCHAR(30) NOT NULL (ENABLE, DISABLE, UPDATE_THRESHOLD)
- `proposed_enabled` BOOLEAN nullable
- `proposed_threshold` NUMERIC(19,4) nullable
- `current_enabled` BOOLEAN nullable (snapshot of rule state at submission time)
- `current_threshold` VARCHAR(255) nullable
- `maker_user_id` VARCHAR(200) NOT NULL
- `maker_username` VARCHAR(200) NOT NULL
- `status` VARCHAR(20) NOT NULL (PENDING, APPROVED, REJECTED)
- `maker_note` VARCHAR(500) nullable
- `rejection_reason` VARCHAR(500) nullable
- `checker_user_id` VARCHAR(200) nullable
- `submitted_at` TIMESTAMPTZ NOT NULL
- `reviewed_at` TIMESTAMPTZ nullable
- Indexes: `idx_risk_rule_pending_changes_status`, `idx_risk_rule_pending_changes_rule_code`

**risk_warnings** — one row per WARN result per order; persisted in batch after evaluation.
- `id` UUID PK (gen_random_uuid)
- `order_id` UUID NOT NULL
- `rule_code` VARCHAR(100) NOT NULL
- `reason` TEXT NOT NULL
- `created_at` TIMESTAMPTZ NOT NULL
- Index: `idx_risk_warnings_order_id`

### Flyway migrations (V1–V14)

| Version | File | Change |
|---------|------|--------|
| V1 | V1__schema.sql | Create risk_rule_config (minimal columns) |
| V2 | V2__seed.sql | Seed 13 initial rules (ON CONFLICT DO NOTHING) |
| V3 | V3__add_priority_to_risk_rule_config.sql | Add `priority` column; set priority 1 and 2 for first two rules |
| V4 | V4__risk_rule_metadata_and_audit.sql | Add rule_name, rule_category, threshold_unit, action columns; create risk_rule_audit table |
| V5 | V5__add_rule_max_order_qty.sql | Seed RULE_MAX_ORDER_QTY (50,000 shares, enabled, priority 9) |
| V6 | V6__add_rule_max_order_value.sql | Seed RULE_MAX_ORDER_VALUE ($500,000, enabled, priority 10) |
| V7 | V7__add_rule_margin_call_active.sql | Seed RULE_MARGIN_CALL_ACTIVE (enabled, priority 24) |
| V8 | V8__seed_client_status_rules.sql | Seed RULE_TRADING_ENABLED, RULE_KYC_VERIFIED, RULE_SUFFICIENT_CASH, RULE_SUFFICIENT_HOLDINGS |
| V9 | V9__seed_stateful_rules.sql | Seed RULE_DUPLICATE_ORDER (priority 20), RULE_SELF_TRADE (priority 21) |
| V10 | V10__seed_warn_rules.sql | Seed PRICE_DEVIATION_CHECK, NOTIONAL_VALUE_CHECK, ORDER_QTY_SPIKE_CHECK, ORDER_VALUE_SPIKE_CHECK, RULE_DAILY_ORDER_COUNT as WARN rules |
| V11 | V11__create_risk_warnings.sql | Create risk_warnings table |
| V12 | V12__fix_warn_rules_action_and_threshold.sql | Correct action=WARN, category=FAT_FINGER, thresholds for all 5 WARN rules; set enabled=false |
| V13 | V13__add_risk_rule_pending_changes.sql | Create risk_rule_pending_changes table |
| V14 | V14__add_current_state_to_pending_changes.sql | Add current_enabled and current_threshold columns to risk_rule_pending_changes |

### Seeded rule codes (as of V12)

| Rule code | Action | Enabled (post-V12) | Priority | Category |
|-----------|--------|--------------------|----------|----------|
| SINGLE_ORDER_QTY_LIMIT | REJECT | true | 1 | FINANCIAL_LIMIT |
| ORDER_VALUE_LIMIT | REJECT | true | 2 | FINANCIAL_LIMIT |
| RULE_MAX_ORDER_QTY | REJECT | true | 9 | PRE_TRADE |
| RULE_MAX_ORDER_VALUE | REJECT | true | 10 | PRE_TRADE |
| RULE_DUPLICATE_ORDER | REJECT | true | 20 | STATEFUL |
| RULE_SELF_TRADE | REJECT | true | 21 | STATEFUL |
| RULE_MARGIN_CALL_ACTIVE | REJECT | true | 24 | CLIENT |
| RULE_TRADING_ENABLED | REJECT | true | 25 | CLIENT |
| RULE_KYC_VERIFIED | REJECT | true | 26 | CLIENT |
| RULE_SUFFICIENT_CASH | REJECT | true | 27 | CLIENT |
| RULE_SUFFICIENT_HOLDINGS | REJECT | true | 28 | CLIENT |
| PRICE_DEVIATION_CHECK | WARN | false | 30 | FAT_FINGER |
| NOTIONAL_VALUE_CHECK | WARN | false | 31 | FAT_FINGER |
| ORDER_QTY_SPIKE_CHECK | WARN | false | 32 | FAT_FINGER |
| ORDER_VALUE_SPIKE_CHECK | WARN | false | 33 | FAT_FINGER |
| RULE_DAILY_ORDER_COUNT | WARN | false | 34 | FAT_FINGER |
| CLIENT_ACTIVE_CHECK | REJECT | false | 99 | REDUNDANT |
| INSTRUMENT_TRADABLE_CHECK | REJECT | false | 99 | REDUNDANT |
| MARKET_HOURS_CHECK | REJECT | false | 99 | REDUNDANT |
| QUANTITY_POSITIVE_CHECK | REJECT | false | 99 | REDUNDANT |
| LIMIT_PRICE_CHECK | REJECT | false | 99 | REDUNDANT |
| CASH_BALANCE_CHECK | REJECT | false | 99 | REDUNDANT |
| HOLDINGS_CHECK | REJECT | false | 99 | REDUNDANT |

## Integrations

### Kafka

| Direction | Topic | Group ID | Class | Purpose |
|-----------|-------|----------|-------|---------|
| Consumed | `risk.evaluation.requested` | `risk-service-risk.evaluation.requested-group` | `kafka/consumer/RiskEvaluationConsumer.java` | Receives order evaluation requests from order-service; triggers rule evaluation |
| Produced | `risk.evaluation.result` | — | `kafka/producer/RiskResultProducer.java` | Publishes evaluation outcome (PASSED/REJECTED/WARNED) keyed by `orderId` |

**Error handling:** `DefaultErrorHandler` with `DeadLetterPublishingRecoverer`; exponential back-off: 1 s initial, 2.0 multiplier, max 3 attempts. Failed messages routed to `risk.evaluation.requested.DLT`. [code: config/KafkaConfig.java]

Consumer deserializes to `java.util.LinkedHashMap` (type info headers disabled); converted to `RiskEvaluationRequest` via Jackson `ObjectMapper.convertValue`. [code: config/KafkaConfig.java, kafka/consumer/RiskEvaluationConsumer.java]

Trusted packages for JSON deserialization: `com.brokerdealer.*`.

### Redis

- Used by `DuplicateOrderRule` for dedup keys: `dup:{clientId}:{symbol}:{side}:{qty}:{limitPrice|MARKET}` with 30 s TTL via `StringRedisTemplate.opsForValue().setIfAbsent()`. [code: rule/DuplicateOrderRule.java]
- `RedisConfig` also configures a `RedisCacheManager` with 30 s default TTL and `@EnableCaching`. No `@Cacheable` annotations are present in the current codebase — cache manager is available but unused.
- Connection: `SPRING_REDIS_HOST` / `SPRING_REDIS_PORT`, timeout 2000 ms.

### PostgreSQL

- JDBC driver: `org.postgresql`; schema: `risk_schema`; DDL mode: `validate` (Flyway owns schema evolution).
- Flyway baseline-on-migrate enabled; locations: `classpath:db/migration`.

## Configuration

| Key / Variable | Default | Purpose |
|----------------|---------|---------|
| `SPRING_DATASOURCE_URL` | none (required) | PostgreSQL JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | `brokerdealer` | DB username |
| `SPRING_DATASOURCE_PASSWORD` | `brokerdealer` | DB password |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker address |
| `SPRING_REDIS_HOST` | `localhost` | Redis host |
| `SPRING_REDIS_PORT` | `6379` | Redis port |
| `server.port` | `8085` | HTTP port (application.yml) |
| `spring.data.redis.timeout` | `2000ms` | Redis connection timeout |
| `spring.jpa.hibernate.ddl-auto` | `validate` | Prevents Hibernate from altering schema |
| `spring.kafka.consumer.auto-offset-reset` | `earliest` | Consumer offset behaviour |
| Resilience4j `sliding-window-size` | `10` | CB sliding window |
| Resilience4j `failure-rate-threshold` | `50` | CB open threshold (%) |
| Resilience4j `wait-duration-in-open-state` | `10s` | CB open wait |
| Resilience4j `timeout-duration` | `2s` | Time-limiter per call |
| Redis cache TTL (default) | `30s` | `RedisCacheConfiguration` in `RedisConfig` |
| Kafka consumer trusted packages | `com.brokerdealer.*` | JSON deserializer trust |
| Kafka DLT back-off | 1 s / ×2 / 3 attempts | `KafkaConfig` exponential back-off |
| `management.endpoints.web.exposure.include` | `health,info,metrics` | Exposed actuator endpoints |

Secrets (DB password, any JWT signing key) are expected via environment variables. No secrets are committed to `application.yml`.

## Tech debt

No TODO, FIXME, HACK, or XXX comments found in `src/` (0 occurrences).

**Structural observations (not TODO-tagged):**

- `RiskRuleService.updateRule()` is a direct rule-update path (no maker/checker) that is no longer wired to the controller — it remains live in the service and could be inadvertently re-exposed.
- `RedisConfig` configures a `CacheManager` with `@EnableCaching` but no `@Cacheable` / `@CacheEvict` annotations are present — the caching infrastructure is unused.
- `RoleAuthorizationFilter` hardcodes `"ADMIN"` as the only permitted role string (no constants or enum); a typo in caller would silently deny all access.
- The `RiskEvaluationService.loadRules()` method is not thread-safe: concurrent Kafka messages and an approval could race on the `rules` list field during reload.
- Redundant rules (CLIENT_ACTIVE_CHECK, INSTRUMENT_TRADABLE_CHECK, MARKET_HOURS_CHECK, QUANTITY_POSITIVE_CHECK, LIMIT_PRICE_CHECK, CASH_BALANCE_CHECK, HOLDINGS_CHECK, ORDER_VALUE_SPIKE_CHECK) are seeded disabled; corresponding `StubRiskRule` fallbacks always PASS — no cleanup of these dead rows.

## Open questions

- **`RiskRuleService.updateRule()` status:** This direct-update path bypasses maker/checker and is unused by the controller. It is unclear whether it should be removed or retained for an administrative/emergency bypass path. Decision needed from the engineering team.
- **Thread safety of `loadRules()`:** The `rules` list field in `RiskEvaluationService` is not volatile or synchronised. Under concurrent approvals the service may transiently evaluate against a stale or partially reloaded list.
- **Unused `CacheManager`:** Whether Spring caching is intended to be used (e.g. for `getAllRules()`) or the `RedisCacheManager` bean is accidental has not been documented.
- **JWT dependency without validation:** `jjwt-*` jars are on the classpath but no JWT parsing is performed. It is unclear whether this was intended for a future in-service token validation step or is a leftover dependency.
<!-- plumbline:end reference -->
