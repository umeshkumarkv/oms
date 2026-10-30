<!-- plumbline:template claude-unit v1 -->
# risk-service

<!-- plumbline:begin unit -->
Last updated: 4369a80 (2026-10-30, /pl-onboard, claude-unit v1)

## Purpose

Pre-trade risk engine for the Broker-Dealer OMS. Consumes order evaluation
requests from Kafka, evaluates each order against a priority-ordered set of
configurable risk rules, and publishes PASSED / REJECTED / WARNED results back
to Kafka. Provides a REST API for ADMIN users to manage rules via maker/checker
workflow. Rule state persists in PostgreSQL (`risk_schema`); Redis handles
duplicate-order detection with a 30-second TTL key.

## Build & run

```
mvn test
mvn spring-boot:run
```

Port: **8085** (`application.yml`). No active Spring profiles; config via env vars or `application.yml` defaults.

Required env vars: `SPRING_DATASOURCE_URL` (no default; startup fails without it), `SPRING_DATASOURCE_USERNAME` (default `brokerdealer`), `SPRING_DATASOURCE_PASSWORD` (default `brokerdealer`), `SPRING_KAFKA_BOOTSTRAP_SERVERS` (default `localhost:9092`), `SPRING_REDIS_HOST` (default `localhost`), `SPRING_REDIS_PORT` (default `6379`).

**Test recipe:** `mvn test` · Runtime required: Java 21 (root pom.xml `<java.version>21</java.version>`) · Runtime available: Java 24 (openjdk version "24") · Result: FAILED — `Fatal error compiling: java.lang.ExceptionInInitializerError: com.sun.tools.javac.code.TypeTag :: UNKNOWN` — Lombok/MapStruct annotation processors are incompatible with Java 24; tests cannot run until Java 21 is used.

## Architecture & structure

- Stack: Java 21, Spring Boot 3, PostgreSQL (Flyway, schema `risk_schema`), Redis, Kafka
- Libraries: Resilience4j, JJWT, Lombok, MapStruct, springdoc-openapi
- `controller/` — `RiskRuleController`: REST under `/api/v1/risk`
- `service/` — `RiskEvaluationService`, `RiskRuleService`, `PendingChangeService`
- `rule/` — `RiskRule` interface + 16 concrete rule classes + `StubRiskRule` fallback
- `kafka/consumer/` — `RiskEvaluationConsumer`: listens on `risk.evaluation.requested`
- `kafka/producer/` — `RiskResultProducer`: publishes to `risk.evaluation.result`
- `repository/` — 4 Spring Data JPA repos for rule config, audit, pending changes, warnings
- `domain/` — JPA entities, `RiskResult`, enums
- `dto/` — `RiskEvaluationRequest`, `RiskEvaluationResult`, `RiskContext`; request/response sub-packages
- `mapper/` — `RiskRuleMapper` (MapStruct): entity → response DTOs
- `filter/` — `RoleAuthorizationFilter`: lightweight RBAC without Spring Security
- `config/` — `KafkaConfig` (DLT + exponential back-off), `RedisConfig` (30 s TTL)

## Security

- No Spring Security. `RoleAuthorizationFilter` reads `X-User-Role` forwarded by the API gateway. [code: filter/RoleAuthorizationFilter.java]
- All `/api/v1/risk/rules/**` require role `ADMIN`; other roles → 403, missing header → 401. [code: filter/RoleAuthorizationFilter.java]
- `/actuator/**` and HTTP OPTIONS are excluded from role checks. [code: filter/RoleAuthorizationFilter.java]
- User identity via `X-User-Id` / `X-Username` headers; recorded in audit and pending-change records. [code: controller/RiskRuleController.java]
- JWT libs present but no JWT validation in this service — gateway is the trust boundary. [code: pom.xml]

## Rules

- Rules evaluated in ascending `priority` order; first REJECTED short-circuits evaluation. [code: service/RiskEvaluationService.java]
- Disabled rules are skipped; `isEnabled()` checked per rule before `evaluate()`. [code: service/RiskEvaluationService.java]
- Unknown rule codes produce a `StubRiskRule` that always returns PASSED. [code: service/RiskEvaluationService.java, rule/StubRiskRule.java]
- WARN results do not short-circuit; all warnings are collected and persisted to `risk_warnings`. [code: service/RiskEvaluationService.java]
- Rule changes go through maker/checker workflow; PATCH `/rules/{ruleCode}` submits a pending change. [code: controller/RiskRuleController.java, service/PendingChangeService.java]
- Self-approval is prohibited; `approveChange()` throws `SelfApprovalNotAllowedException`. [code: service/PendingChangeService.java]
- `rejectionReason` required on reject; submit validates that at least `enabled` or `thresholdValue` is provided. [code: service/PendingChangeService.java]
- `risk_rule_audit` is append-only — no UPDATE or DELETE. [decided: V4__risk_rule_metadata_and_audit.sql comment]
- Duplicate-order detection uses Redis `SET NX` 30 s TTL; key `dup:{clientId}:{symbol}:{side}:{qty}:{limitPrice|MARKET}`. [code: rule/DuplicateOrderRule.java]
- All WARN rules default to `enabled=false`; ADMIN must enable explicitly. [decided: V12__fix_warn_rules_action_and_threshold.sql comment]
- On approved change, `RiskEvaluationService.loadRules()` reloads the in-memory rule list from DB. [code: service/RiskRuleService.java, service/PendingChangeService.java]
- Kafka error handler uses exponential back-off (1 s, 2 s, 4 s — 3 attempts) then routes to Dead Letter Topic. [code: config/KafkaConfig.java]

## Must never

- Do not add direct rule updates bypassing `PendingChangeService`; `RiskRuleService.updateRule()` exists but is not wired to the controller — do not re-expose without restoring maker/checker controls.
- Do not allow non-ADMIN roles to reach `/api/v1/risk/rules/**`; preserve `RoleAuthorizationFilter.isAllowed()`.
- Do not delete or update rows in `risk_rule_audit`; it is append-only.
- Do not call `riskEvaluationService.loadRules()` outside `RiskRuleService` or `PendingChangeService` without a committed DB transaction.
- Do not deploy on Java 24; Lombok/MapStruct annotation processors fail. Require Java 21.

## Decisions & trade-offs

- **Lightweight RBAC without Spring Security:** `RoleAuthorizationFilter` reads `X-User-Role` from gateway-forwarded headers; gateway is the trust boundary. [code comment: filter/RoleAuthorizationFilter.java]
- **In-memory rule list with reload:** Rules loaded at startup, reloaded synchronously on approved changes; avoids per-request DB queries. [code: service/RiskEvaluationService.java]
- **Maker/checker workflow:** Rule changes require a second ADMIN to approve; self-approval blocked in code. [code: service/PendingChangeService.java]
- **StubRiskRule fallback:** Unknown rule codes PASS rather than block, preventing outages from DB-only additions before code deploys. [code: rule/StubRiskRule.java]
- **Kafka DLT with exponential back-off:** 3 retries (1 s → 2 s → 4 s) then Dead Letter Topic; prevents consumer stalls. [code: config/KafkaConfig.java]
- **Redis 30 s TTL:** Default TTL in `RedisCacheConfiguration`; `DuplicateOrderRule` also uses 30 s independently. [code: config/RedisConfig.java, rule/DuplicateOrderRule.java]

## Reference

API — 7 endpoints, 1 controller. Open CLAUDE.reference.md only for the section your task needs.
Data — 4 tables, 14 Flyway migrations. Open CLAUDE.reference.md only for the section your task needs.
Integrations — 1 Kafka topic consumed, 1 produced, Redis. Open CLAUDE.reference.md only for the section your task needs.
Configuration — 6 env vars, Resilience4j, Redis TTL. Open CLAUDE.reference.md only for the section your task needs.
Tech debt — 0 TODO/FIXME. Open CLAUDE.reference.md only for the section your task needs.
<!-- plumbline:end unit -->
