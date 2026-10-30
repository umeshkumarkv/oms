<!-- plumbline:template claude-unit v1 -->
# audit-service

<!-- plumbline:begin unit -->
Last updated: 4586741 (2026-10-30, /pl-onboard, claude-unit v1)

## Purpose

The audit-service is an immutable audit trail store for the Broker-Dealer OMS. It consumes Kafka events (`order.state.changed` and `execution.completed`) and persists every state transition and trade execution as an `AuditLog` record in PostgreSQL (`audit_schema`). It exposes two read-only GET endpoints so operators and other roles can query the audit trail by order or by client. No business logic modifies orders; the service only records what happened elsewhere.

## Build & run

Build: `mvn clean package -DskipTests` | Run: `mvn spring-boot:run` | Port: **8087** (`server.port=8087`)
Docker: `docker build -f services/audit-service/Dockerfile -t audit-service .` (run from repo root, multi-stage, Amazon Corretto 21).
No active-profile switching; config from environment variables (see Configuration in reference).

**Test recipe:** `mvn test` · Java 21 required (`<java.version>21</java.version>` in root pom.xml) · **FAILED** — `ExceptionInInitializerError: TypeTag::UNKNOWN` — Lombok/MapStruct annotation processors incompatible with Java 24; must switch to Java 21 · 2026-10-30

JaCoCo 80% line-coverage gate on every `mvn test`; `AuditServiceApplication`, `KafkaConfig`, `RedisConfig` excluded (`pom.xml` jacoco excludes).

## Architecture & structure

**Stack:** Java 21 · Spring Boot 3.2.4 · Spring Data JPA (Hibernate, ddl-auto: validate) · Spring Kafka · Spring Data Redis · Flyway · PostgreSQL 15 · Lombok 1.18.30 · MapStruct 1.5.5.Final · Resilience4j 2.2.0 · springdoc-openapi 2.3.0

**Layers:** `controller/` (`AuditController`, GET-only) → `service/` (`AuditService`, manual DTO mapping) → `repository/` (`AuditLogRepository`, 4 queries) → `domain/` (`AuditLog` entity; `ActorType` enum). `kafka/consumer/` (`AuditEventConsumer`, 2 listeners, `Map<String, Object>`). `filter/` (`RoleAuthorizationFilter`). `config/` (`KafkaConfig` DLT, `RedisConfig` 30 s). `exception/` (`GlobalExceptionHandler`).

Database schema: `audit_schema`, Flyway-managed (V1 create, V2 no-op, V3 adds `client_id`). Virtual threads enabled.

## Security

No Spring Security. `RoleAuthorizationFilter` trusts the gateway-forwarded `X-User-Role` header (JJWT on classpath, unused). [code: filter/RoleAuthorizationFilter.java]
- Allowed roles: VIEWER, OPERATIONS, ADMIN, TRADER, SYSTEM; missing → 401; unknown → 403; `/actuator/**` and OPTIONS excluded.
- **Risk:** Javadoc says "ADMIN excluded per BRD 12.2" but `AUDIT_READ_ROLES` includes ADMIN — stale comment, code admits ADMIN. [code: filter/RoleAuthorizationFilter.java]
- Token storage: none; identity from HTTP header only.

## Rules

- `AuditLog` NOT NULL fields: `entityType`, `entityId`, `newState`, `actorType`, `eventTimestamp`; `actor_type` DB check: USER/SYSTEM. [code: db/migration/V1__schema.sql]
- Kafka consumer skips records with missing `orderId` or `toState`; duplicates detected via `existsByEntityIdAndNewStateAndEventTimestamp`. [code: kafka/consumer/AuditEventConsumer.java]
- `RISK_REJECTED` transitions add `rejectionReason` and `violatedRuleCode` to `metadata`; others do not. [code: kafka/consumer/AuditEventConsumer.java]
- `actorType = USER` only when `triggeredBy` starts with `"USER:"`; all other values → SYSTEM. [code: kafka/consumer/AuditEventConsumer.java]
- Kafka consumer errors trigger exponential-backoff DLT (1 s, 2 s, 4 s, 3 attempts). [code: config/KafkaConfig.java]
- All GET endpoints require `X-User-Role`; absent → 401. JaCoCo 80% gate. Flyway `baseline-on-migrate: true`. [code: filter/RoleAuthorizationFilter.java, pom.xml, application.yml]

## Must never

- Do not write to or modify existing `AuditLog` records; the table has no UPDATE path in the codebase.
- Do not remove or weaken `RoleAuthorizationFilter`; it is the sole authorization control for this service.
- Do not change the `actor_type` DB constraint without a Flyway migration; Hibernate validate mode will fail startup.
- Do not add Spring Security without reconciling or removing `RoleAuthorizationFilter`.
- Do not bypass the `order.state.changed` duplicate-check; doing so produces duplicate audit records.
- Do not drop below 80% JaCoCo line coverage (excludes: `AuditServiceApplication`, `KafkaConfig`, `RedisConfig`).

## Decisions & trade-offs

- **No Spring Security:** plain servlet filter reads gateway-forwarded `X-User-Role`. [code: filter/RoleAuthorizationFilter.java]
- **Kafka DLT + exponential backoff:** 3 retries then `DeadLetterPublishingRecoverer`. [code: config/KafkaConfig.java]
- **`Map<String, Object>` Kafka deserialization:** avoids `__TypeId__` coupling across services. [code: kafka/consumer/AuditEventConsumer.java]
- **Redis wired but unused:** `CacheManager` provisioned (30 s TTL), no `@Cacheable` annotations. [code: config/RedisConfig.java]
- **Manual DTO mapping:** `AuditService.toResponse()` maps by hand; no MapStruct mapper generated. [code: service/AuditService.java]
- **Virtual threads:** `spring.threads.virtual.enabled: true`. [code: application.yml]

## Reference

- API — 2 endpoints, 1 controller
- Data — 1 entity/table (`audit_logs`), 3 Flyway migrations
- Integrations — 2 Kafka topics consumed, DLT producer, Redis (wired, unused)
- Configuration — 6 environment variables + application.yml keys
- Tech debt — 0 TODO/FIXME; 5 structural issues (unused Redis, manual mapping, unused JJWT, dead query params, stale Javadoc)
- Open questions — 2 items (BRD 12.2 ADMIN exclusion, `from`/`to`/`entityType` filter implementation)

Open CLAUDE.reference.md only for the section your task needs.
<!-- plumbline:end unit -->
