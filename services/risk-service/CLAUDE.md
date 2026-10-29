<!-- plumbline:template claude-unit v1 -->
# Risk Service

<!-- plumbline:begin unit -->
Last updated: 4369a80 (2026-10-28, /pl-onboard, claude-unit v1)

## Purpose
Evaluates every inbound order against a prioritised, database-driven set of risk rules before the order is routed for execution. Consumes `risk.evaluation.requested` from Kafka, runs rules in priority order (fail-fast on REJECT, accumulate WARNs), then publishes the verdict to `risk.evaluation.result`. Also exposes a REST API so ADMIN users can inspect, update, and four-eyes-approve rule configuration changes.

## Build & run
```
# from repo root
./mvnw test -pl services/risk-service

# or from the service directory
mvn test
```
Port: **8085**. No active Spring profiles are set in docker-compose; the service runs with the default profile.

**depends_on (docker-compose.yml):**
- `kafka` — `condition: service_healthy`
- `redis` — `condition: service_started`
- `postgres` — `condition: service_started`

**Test recipe:** `mvn test` · requires Java 21 (`<java.version>21</java.version>` in parent pom.xml) · **not run** — Java 24 installed locally is incompatible with maven-compiler-plugin 3.11.0; first error: `Fatal error compiling: java.lang.ExceptionInInitializerError: com.sun.tools.javac.code.TypeTag :: UNKNOWN` · 2026-10-28

## Architecture & structure
**Stack:** Java 21, Spring Boot 3, Spring Data JPA (Hibernate), Spring Kafka, Spring Data Redis, Flyway, PostgreSQL 15, Redis 7, Lombok, MapStruct, Resilience4j, JJWT, Springdoc OpenAPI.

**Layers and patterns:**
- `filter/` — `RoleAuthorizationFilter`: validates `X-User-Role` header; no Spring Security dependency. [code: `filter/RoleAuthorizationFilter.java`]
- `controller/` — `RiskRuleController`: all routes under `/api/v1/risk`.
- `service/` — `RiskEvaluationService` (rule loading + evaluation loop), `RiskRuleService` (direct updates + audit), `PendingChangeService` (maker-checker workflow).
- `rule/` — stateless `RiskRule` implementations; reloaded via `@PostConstruct loadRules()` on every approved or direct update.
- `kafka/` — `RiskEvaluationConsumer` (listener) and `RiskResultProducer` (publisher).
- `domain/` — JPA entities (`RiskRuleConfig`, `RiskRuleAudit`, `RiskRulePendingChange`, `RiskWarning`) and `RiskResult`.
- `repository/`, `mapper/` — Spring Data JPA repositories; MapStruct `RiskRuleMapper` (entity → DTO).
- `config/` — `KafkaConfig` (exponential-backoff DLT handler), `RedisConfig` (StringRedisTemplate + CacheManager, 30 s TTL).
- `exception/` — `GlobalExceptionHandler` with per-exception HTTP status mapping.

**Schema:** `risk_schema` (PostgreSQL). Flyway migrations V1–V14. `ddl-auto: validate`.

## Security
- Authentication: no Spring Security; gateway injects `X-User-Role`, `X-User-Id`, `X-Username` headers. [code: `filter/RoleAuthorizationFilter.java`]
- Authorization: `ADMIN` role required for `/api/v1/risk/rules/**`; missing header → 401, wrong role → 403; actuator and OPTIONS excluded. [code: `filter/RoleAuthorizationFilter.java`]
- Maker-checker: approver must differ from submitter; `SelfApprovalNotAllowedException` thrown otherwise. [code: `service/PendingChangeService.java`]
- `JWT_SECRET` env var injected via docker-compose; token validation occurs at the gateway, not this service. [code: `docker-compose.yml`]

## Rules
- Rules run in ascending `priority` order; first REJECT stops evaluation immediately. [code: `service/RiskEvaluationService.java`]
- WARN results accumulate; all warnings are persisted to `risk_warnings` at the end of a passing evaluation. [code: `service/RiskEvaluationService.java`]
- `changeReason` is mandatory for every direct rule update. [code: `service/RiskRuleService.java`]
- `rejectionReason` is mandatory when rejecting a pending change. [code: `service/PendingChangeService.java`]
- A pending change not in `PENDING` status cannot be approved or rejected (`ChangeAlreadyReviewedException`). [code: `service/PendingChangeService.java`]
- Approved or direct rule change triggers `RiskEvaluationService.loadRules()` in-process. [code: `service/RiskRuleService.java`, `service/PendingChangeService.java`]
- `DuplicateOrderRule` uses Redis SET NX, 30 s TTL, key `dup:{clientId}:{symbol}:{side}:{quantity}:{limitPrice|MARKET}`. [code: `rule/DuplicateOrderRule.java`]
- Unknown rule codes map to `StubRiskRule` (always PASSED) to prevent unimplemented codes from blocking orders. [code: `service/RiskEvaluationService.java`, `rule/StubRiskRule.java`]
- All WARN (fat-finger) rules default to `enabled=false`; ADMIN must explicitly enable them. [decided V12__fix_warn_rules_action_and_threshold.sql comment]
- `PRICE_DEVIATION_CHECK` skipped for MARKET orders and when `limitPrice` or `lastPrice` is null. [code: `rule/PriceDeviationCheckRule.java`]
- `RULE_SUFFICIENT_CASH` applies only to BUY orders; skipped when `buyingPower` is null. [code: `rule/SufficientCashRule.java`]
- Kafka consumer uses exponential backoff (1 s, 2 s, 4 s; 3 attempts) then publishes to DLT. [code: `config/KafkaConfig.java`]
- Virtual threads enabled for all request and listener processing. [code: `src/main/resources/application.yml`]

## Must never
- Never bypass `RoleAuthorizationFilter`; all risk rule endpoints require `ADMIN`. [code: `filter/RoleAuthorizationFilter.java`]
- Never allow a maker to approve their own pending change. [code: `service/PendingChangeService.java`]
- Never write to `risk_rule_audit` outside a rule update; the table is append-only. [code: `service/RiskRuleService.java`, migration V4 comment]
- Never add a rule code without a Flyway migration seeding the `risk_rule_config` row; `loadRules()` only builds rules for codes in the database. [code: `service/RiskEvaluationService.java`]
- Never expose `JWT_SECRET` or `SPRING_DATASOURCE_PASSWORD` in logs or API responses. [code: `docker-compose.yml`]

## Decisions & trade-offs
- No Spring Security: role enforcement via raw servlet filter reading a gateway-set header, avoiding the full security filter chain. [code: `filter/RoleAuthorizationFilter.java` Javadoc]
- Rules cached in a JVM list at startup and reloaded on each change, trading memory for speed on the hot evaluation path. [code: `service/RiskEvaluationService.java`]
- Kafka consumer uses `USE_TYPE_INFO_HEADERS=false` (deserialise to `LinkedHashMap`), making the consumer resilient to producer-side type header changes. [code: `config/KafkaConfig.java` comment]
- `StubRiskRule` always passes for unrecognised codes so a DB row seeded ahead of a code deployment does not block orders. [code: `rule/StubRiskRule.java` Javadoc]
- Maker-checker workflow (`risk_rule_pending_changes`) enforces four-eyes approval for rule changes in a regulated environment. [code: `service/PendingChangeService.java`, migration V13]

## Reference
- API — 7 endpoints, 1 controller (`RiskRuleController`)
- Data — 4 tables (`risk_rule_config`, `risk_rule_audit`, `risk_rule_pending_changes`, `risk_warnings`), 14 Flyway migrations (V1–V14)
- Integrations — 1 Kafka topic consumed (`risk.evaluation.requested`), 1 topic produced (`risk.evaluation.result`), Redis (duplicate-order dedup key store + 30 s cache)
- Configuration — 7 environment variables (`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `SPRING_KAFKA_BOOTSTRAP_SERVERS`, `SPRING_REDIS_HOST`, `SPRING_REDIS_PORT`, `JWT_SECRET`)
- Tech debt — 0 TODO/FIXME in src/main/java

Open CLAUDE.reference.md only for the section your task needs.

— Plumbline 2.8.7 · /pl-onboard · claude-unit v1 · commit 4369a80 · judge pending · AI-drafted, posted after SME review
<!-- plumbline:end unit -->
