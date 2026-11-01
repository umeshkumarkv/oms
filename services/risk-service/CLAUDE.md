<!-- plumbline:template claude-unit v2 -->
# risk-service

<!-- plumbline:begin unit -->
Last updated: 39916bd (2026-10-30, /pl-onboard, claude-unit v2)

## Purpose

Pre-trade risk engine for the Broker-Dealer OMS. Consumes order evaluation requests from Kafka, evaluates each order against a priority-ordered set of configurable risk rules, and publishes PASSED / REJECTED / WARNED results back to Kafka. Provides a REST API for ADMIN users to manage rules via maker/checker workflow. Rule state persists in PostgreSQL (`risk_schema`); Redis handles duplicate-order detection with a 30-second TTL key.

## Build & run

```
mvn test
mvn spring-boot:run
```

Port: **8085** (`application.yml`). No active Spring profiles; config via env vars or `application.yml` defaults.

Required env vars: `SPRING_DATASOURCE_URL` (required), `SPRING_DATASOURCE_USERNAME/PASSWORD` (default `brokerdealer`), `SPRING_KAFKA_BOOTSTRAP_SERVERS` (default `localhost:9092`), `SPRING_REDIS_HOST/PORT` (default `localhost:6379`).

**Test recipe:** `mvn test` · Java 21 required (root pom.xml `<java.version>21</java.version>`) · Result: FAILED — `ExceptionInInitializerError: TypeTag::UNKNOWN` — Lombok/MapStruct incompatible with Java 24; run with Java 21 · 2026-10-30

## Architecture & structure

- Stack: Java 21, Spring Boot 3.2.4, PostgreSQL 15 (Flyway, schema `risk_schema`), Redis 7, Kafka (Confluent 7.5.0)
- Libraries: Resilience4j, JJWT, Lombok, MapStruct, springdoc-openapi
- `controller/` — `RiskRuleController`: REST under `/api/v1/risk`
- `service/` — `RiskEvaluationService`, `RiskRuleService`, `PendingChangeService`
- `rule/` — `RiskRule` interface + 16 concrete rule classes + `StubRiskRule` fallback
- `kafka/consumer/` — `RiskEvaluationConsumer`: listens on `risk.evaluation.requested`
- `kafka/producer/` — `RiskResultProducer`: publishes to `risk.evaluation.result`
- `repository/` — 4 Spring Data JPA repos (rule config, audit, pending changes, warnings)
- `domain/` — JPA entities, `RiskResult`, enums; `dto/` — request/response DTOs; `mapper/` — MapStruct entity→DTO
- `filter/` — `RoleAuthorizationFilter`: lightweight RBAC; `config/` — `KafkaConfig` (DLT + back-off), `RedisConfig` (30 s TTL)

## Security
- Identity: `X-User-Role` header from api-gateway; no JWT validation (gateway is trust boundary). [code: pom.xml]
- Verified at: filter/RoleAuthorizationFilter.java
- Reachable without that check: docker-compose: yes, port 8085 published (docker-compose.yml); k8s: no, ClusterIP (k8s/services/risk-service-service.yaml).
- No Spring Security; all `/api/v1/risk/rules/**` require ADMIN role; missing header → 401. [code: filter/RoleAuthorizationFilter.java]
- User identity via `X-User-Id`/`X-Username` headers; recorded in audit and pending-change records. [code: controller/RiskRuleController.java]

## Rules

- Rules evaluated in ascending `priority` order; first REJECTED short-circuits. Disabled rules skipped. [code: service/RiskEvaluationService.java]
- Unknown codes use `StubRiskRule` (always PASS). WARN results collected, not short-circuiting. [code: rule/StubRiskRule.java]
- Rule changes via maker/checker; PATCH submits pending change; self-approval prohibited. [code: service/PendingChangeService.java]
- `rejectionReason` required on reject; submit requires at least `enabled` or `thresholdValue`. [code: service/PendingChangeService.java]
- `risk_rule_audit` append-only — no UPDATE or DELETE. [code: db/migration/V4__risk_rule_metadata_and_audit.sql]
- Duplicate-order detection: Redis SET NX 30 s; key `dup:{clientId}:{symbol}:{side}:{qty}:{price}`. [code: rule/DuplicateOrderRule.java]
- WARN rules default `enabled=false`; ADMIN enables explicitly. On approval, in-memory rules reload. [code: db/migration/V12__fix_warn_rules_action_and_threshold.sql]
- Kafka DLT: 1→2→4 s, 3 attempts. [code: config/KafkaConfig.java]

## Must never

- Bypass `PendingChangeService` for rule updates; `RiskRuleService.updateRule()` is unwired intentionally. [code: service/PendingChangeService.java]
- Allow non-ADMIN roles to reach `/api/v1/risk/rules/**`. [code: filter/RoleAuthorizationFilter.java]
- DELETE or UPDATE `risk_rule_audit` rows — append-only. [code: db/migration/V4__risk_rule_metadata_and_audit.sql]
- Call `riskEvaluationService.loadRules()` outside a committed DB transaction. [code: service/RiskEvaluationService.java]
- Deploy on Java 24; Lombok/MapStruct fail. [code: pom.xml]

## Decisions & trade-offs

- No Spring Security — `RoleAuthorizationFilter` reads gateway-forwarded `X-User-Role`. [code: filter/RoleAuthorizationFilter.java]
- In-memory rule list with reload — loaded at startup, reloaded on approved changes. [code: service/RiskEvaluationService.java]
- Maker/checker — rule changes need a second ADMIN; self-approval blocked. [code: service/PendingChangeService.java]
- StubRiskRule fallback — unknown codes PASS rather than block. [code: rule/StubRiskRule.java]
- Kafka DLT — 3 retries (1→2→4 s) then Dead Letter Topic. [code: config/KafkaConfig.java]
- Redis 30 s TTL — `DuplicateOrderRule` uses 30 s independently. [code: config/RedisConfig.java]

## Reference
Open CLAUDE.reference.md only for the section your task needs.
- API — 7 endpoints, 1 controller.
- Data — 4 tables, 14 Flyway migrations.
- Integrations — 1 Kafka topic consumed, 1 produced, Redis.
- Configuration — 6 env vars, Resilience4j, Redis TTL.
- Tech debt — 0 TODO/FIXME.
<!-- plumbline:end unit -->
