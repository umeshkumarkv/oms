<!-- plumbline:template claude-unit v2 -->
# audit-service

<!-- plumbline:begin unit -->
Last updated: 39916bd (2026-10-30, /pl-onboard, claude-unit v2)

## Purpose

The audit-service is an immutable audit trail store for the Broker-Dealer OMS. It consumes Kafka events (`order.state.changed` and `execution.completed`) and persists every state transition and trade execution as an `AuditLog` record in PostgreSQL (`audit_schema`). It exposes two read-only GET endpoints so operators and other roles can query the audit trail by order or by client. No business logic modifies orders; the service only records what happened elsewhere.

## Build & run

Build: `mvn clean package -DskipTests` | Run: `mvn spring-boot:run` | Port: **8087** (`server.port=8087`)
Docker: `docker build -f services/audit-service/Dockerfile -t audit-service .` (run from repo root, multi-stage, Amazon Corretto 21).
No active-profile switching; config from environment variables (see Configuration in reference).

**Test recipe:** `mvn test` · Java 21 required (`<java.version>21</java.version>` in root pom.xml) · **FAILED** — `ExceptionInInitializerError: TypeTag::UNKNOWN` — Lombok/MapStruct annotation processors incompatible with Java 24; must switch to Java 21 · 2026-10-30

## Architecture & structure

**Stack:** Java 21 · Spring Boot 3.2.4 · Spring Data JPA (Hibernate, ddl-auto: validate) · Spring Kafka · Spring Data Redis · Flyway · PostgreSQL 15 · Lombok 1.18.30 · MapStruct 1.5.5.Final · Resilience4j 2.2.0 · springdoc-openapi 2.3.0

**Layers:** `controller/` (`AuditController`, GET-only) → `service/` (`AuditService`, manual DTO mapping) → `repository/` (`AuditLogRepository`, 4 queries) → `domain/` (`AuditLog` entity; `ActorType` enum). `kafka/consumer/` (`AuditEventConsumer`, 2 listeners, `Map<String, Object>`). `filter/` (`RoleAuthorizationFilter`). `config/` (`KafkaConfig` DLT, `RedisConfig` 30 s). `exception/` (`GlobalExceptionHandler`).

Database schema: `audit_schema`, Flyway-managed (V1 create, V2 no-op, V3 adds `client_id`). Virtual threads enabled.

## Security
- Identity: `X-User-Role` header from api-gateway; JJWT on classpath but unused here.
- Verified at: filter/RoleAuthorizationFilter.java
- Reachable without that check: docker-compose: yes, port 8087 published (docker-compose.yml); k8s: no, ClusterIP (k8s/services/audit-service-service.yaml).
- No Spring Security; roles VIEWER/OPERATIONS/ADMIN/TRADER/SYSTEM allowed; missing → 401. [code: filter/RoleAuthorizationFilter.java]
- Risk: stale Javadoc says "ADMIN excluded per BRD 12.2" but `AUDIT_READ_ROLES` includes ADMIN. [code: filter/RoleAuthorizationFilter.java]

## Rules

- `AuditLog` NOT NULL fields: `entityType`, `entityId`, `newState`, `actorType`, `eventTimestamp`; `actor_type` DB check: USER/SYSTEM. [code: db/migration/V1__schema.sql]
- Kafka consumer skips records with missing `orderId` or `toState`; duplicates detected via `existsByEntityIdAndNewStateAndEventTimestamp`. [code: kafka/consumer/AuditEventConsumer.java]
- `RISK_REJECTED` transitions add `rejectionReason` and `violatedRuleCode` to `metadata`; others do not. [code: kafka/consumer/AuditEventConsumer.java]
- `actorType = USER` only when `triggeredBy` starts with `"USER:"`; all other values → SYSTEM. [code: kafka/consumer/AuditEventConsumer.java]
- Kafka consumer errors trigger exponential-backoff DLT (1 s, 2 s, 4 s, 3 attempts). [code: config/KafkaConfig.java]
- All GET endpoints require `X-User-Role`; absent → 401. JaCoCo 80% gate. Flyway `baseline-on-migrate: true`. [code: filter/RoleAuthorizationFilter.java, pom.xml, application.yml]

## Must never

- Modify existing `AuditLog` records — no UPDATE path exists. [code: service/AuditService.java]
- Remove or weaken `RoleAuthorizationFilter` — sole auth control. [code: filter/RoleAuthorizationFilter.java]
- Change `actor_type` DB constraint without a Flyway migration — Hibernate validate will fail. [code: src/main/resources/application.yml]
- Add Spring Security without removing `RoleAuthorizationFilter`. [code: filter/RoleAuthorizationFilter.java]
- Bypass the duplicate-check in `AuditEventConsumer` — produces duplicate audit records. [code: kafka/consumer/AuditEventConsumer.java]
- Drop below 80% JaCoCo line coverage (excludes: `AuditServiceApplication`, `KafkaConfig`, `RedisConfig`). [code: pom.xml]

## Decisions & trade-offs

- No Spring Security — servlet filter reads gateway-forwarded `X-User-Role`. [code: filter/RoleAuthorizationFilter.java]
- Kafka DLT + exponential backoff — 3 retries then `DeadLetterPublishingRecoverer`. [code: config/KafkaConfig.java]
- `Map<String, Object>` Kafka deserialization — avoids `__TypeId__` coupling. [code: kafka/consumer/AuditEventConsumer.java]
- Redis wired but unused — `CacheManager` provisioned (30 s TTL), no `@Cacheable`. [code: config/RedisConfig.java]
- Manual DTO mapping — `AuditService.toResponse()` maps by hand; no MapStruct mapper. [code: service/AuditService.java]
- Virtual threads enabled. [code: application.yml]

## Reference

- API — 2 endpoints, 1 controller
- Data — 1 entity/table (`audit_logs`), 3 Flyway migrations
- Integrations — 2 Kafka topics consumed, DLT producer, Redis (wired, unused)
- Configuration — 6 environment variables + application.yml keys
- Tech debt — 0 TODO/FIXME; 5 structural issues (unused Redis, manual mapping, unused JJWT, dead query params, stale Javadoc)
- Open questions — 2 items (BRD 12.2 ADMIN exclusion, `from`/`to`/`entityType` filter implementation)

Open CLAUDE.reference.md only for the section your task needs.
<!-- plumbline:end unit -->
