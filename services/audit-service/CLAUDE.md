<!-- plumbline:template claude-unit v1 -->
# audit-service

<!-- plumbline:begin unit -->
Last updated: 4369a80 (2026-10-30, /pl-onboard, claude-unit v1)

## Purpose

The audit-service is a read-only immutable audit trail store for the Broker-Dealer OMS. It consumes Kafka events (`order.state.changed` and `execution.completed`) and persists every state transition and trade execution as an `AuditLog` record in PostgreSQL. It exposes two GET endpoints so operators and other roles can query the audit trail by order or by client. No business logic modifies orders — the service only records what happened elsewhere.

## Build & run

Build: `mvn clean package -DskipTests`
Run: `mvn spring-boot:run`
Port: **8087** (`server.port=8087` in application.yml)
No active-profile switching; config comes from environment variables (see Configuration in reference).
Docker (multi-stage, Java 21 / Amazon Corretto): `docker build -f services/audit-service/Dockerfile -t audit-service .` — run from repo root.

**Test recipe:** `mvn test` · Java 21 required (root pom.xml `<java.version>21</java.version>`) · Java 24 available (openjdk version "24") · **FAILED** — `Fatal error compiling: java.lang.ExceptionInInitializerError: com.sun.tools.javac.code.TypeTag :: UNKNOWN` — Lombok/MapStruct annotation processors incompatible with Java 24; tests cannot run until Java 21 is used · 2026-10-30

JaCoCo 80% line-coverage gate enforced on every `mvn test` run; `AuditServiceApplication`, `KafkaConfig`, and `RedisConfig` are excluded (`pom.xml` jacoco excludes).

## Architecture & structure

**Stack:** Java 21 · Spring Boot 3.2.4 · Spring Data JPA (Hibernate) · Spring Kafka · Spring Data Redis · Flyway · PostgreSQL · Lombok 1.18.30 · MapStruct 1.5.5.Final · Resilience4j 2.2.0 · JJWT 0.12.3 · springdoc-openapi 2.3.0

**Layers & patterns:**
- `controller/` — `AuditController`: thin REST layer, delegates to service; no request bodies (read-only).
- `service/` — `AuditService`: query logic and manual DTO mapping (no MapStruct mapper; mapping done inline via builder).
- `repository/` — `AuditLogRepository`: Spring Data JPA; four query methods.
- `domain/` — `AuditLog` JPA entity, `ActorType` enum (`USER`, `SYSTEM`).
- `dto/response/` — `AuditTrailResponse` (envelope with `data` + `meta`), `AuditLogResponse`, `ResponseMeta`.
- `kafka/consumer/` — `AuditEventConsumer`: two `@KafkaListener` methods; deserialises into `Map<String, Object>`.
- `filter/` — `RoleAuthorizationFilter` (`OncePerRequestFilter`): enforces `X-User-Role` header before every non-actuator request.
- `config/` — `KafkaConfig` (consumer factory with exponential-backoff DLT error handler), `RedisConfig` (cache TTL 30 s, JSON serialisation).
- `exception/` — `GlobalExceptionHandler` (`@RestControllerAdvice`), `ValidationException`.

Database schema: `audit_schema` (Flyway-managed, three migrations: V1 schema, V2 seed no-op, V3 adds `client_id` column).
Virtual threads enabled (`spring.threads.virtual.enabled: true`).

## Security

**Authentication:** No Spring Security dependency. The API gateway forwards the `X-User-Role` header; the service trusts that header without verifying a JWT locally (JJWT is on the classpath but no JWT parsing logic exists). [code: filter/RoleAuthorizationFilter.java]

**Authorization (roles):** `RoleAuthorizationFilter` checks `X-User-Role` on every request to `/api/v1/audit/**`.
- Allowed roles: `VIEWER`, `OPERATIONS`, `ADMIN`, `TRADER`, `SYSTEM`. [code: filter/RoleAuthorizationFilter.java]
- Missing or blank header → HTTP 401. [code: filter/RoleAuthorizationFilter.java]
- Role not in the allowed set → HTTP 403. [code: filter/RoleAuthorizationFilter.java]
- Actuator endpoints (`/actuator/**`) and OPTIONS requests are excluded. [code: filter/RoleAuthorizationFilter.java]

**Token storage:** None; role is read from a plain HTTP header injected by the upstream gateway.

## Rules

- Every `AuditLog` must have `entityType`, `entityId`, `newState`, `actorType`, and `eventTimestamp` — all `NOT NULL` in the schema. [code: db/migration/V1__schema.sql]
- `actor_type` is constrained to `'USER'` or `'SYSTEM'` at the database level. [code: db/migration/V1__schema.sql]
- Kafka consumer for `order.state.changed` skips records with a missing or unparseable `orderId` or missing `toState` — no partial saves. [code: kafka/consumer/AuditEventConsumer.java]
- Duplicate `order.state.changed` events (same `entityId`, `newState`, `eventTimestamp`) are skipped via `existsByEntityIdAndNewStateAndEventTimestamp` before saving. [code: kafka/consumer/AuditEventConsumer.java]
- `RISK_REJECTED` transitions include `rejectionReason` and `violatedRuleCode` in `metadata` JSON; others do not. [code: kafka/consumer/AuditEventConsumer.java]
- `actorType` is set to `USER` only when `triggeredBy` starts with `"USER:"`; all other values produce `SYSTEM`. [code: kafka/consumer/AuditEventConsumer.java]
- Kafka consumer errors are rethrown after logging, triggering the exponential-backoff DLT handler (3 attempts: 1 s, 2 s, 4 s). [code: config/KafkaConfig.java]
- All GET endpoints require the `X-User-Role` header; absence returns 401. [code: filter/RoleAuthorizationFilter.java]
- JaCoCo line-coverage minimum is 80%; build fails below this threshold. [decided: pom.xml (jacoco check rule)]
- Flyway runs on schema `audit_schema` with `baseline-on-migrate: true`. [code: src/main/resources/application.yml]

## Must never

- Do not write to or modify existing `AuditLog` records; the table has no UPDATE path in the codebase.
- Do not remove or weaken the `RoleAuthorizationFilter`; it is the sole authorization control for this service.
- Do not change the `actor_type` DB constraint without a matching migration; Hibernate validate mode will fail startup.
- Do not add Spring Security without reconciling or removing `RoleAuthorizationFilter`.
- Do not allow the `order.state.changed` duplicate-check to be bypassed; doing so would produce duplicate audit records.
- Do not drop below 80% JaCoCo line coverage; the build gate will fail (excludes: `AuditServiceApplication`, `KafkaConfig`, `RedisConfig`).

## Decisions & trade-offs

- **No Spring Security:** Authorization is handled by a plain servlet filter reading a gateway-forwarded header. The filter comment states "No Spring Security dependency required." [code: filter/RoleAuthorizationFilter.java comment]
- **Kafka DLT with exponential backoff:** Failed consumer messages are retried three times then published to a Dead Letter Topic via `DeadLetterPublishingRecoverer`, preventing poison messages from blocking partitions. [code: config/KafkaConfig.java comment]
- **`Map<String, Object>` Kafka deserialization:** Events are consumed as generic maps rather than typed DTOs, decoupling the audit-service from producer schema changes. [code: kafka/consumer/AuditEventConsumer.java, config/KafkaConfig.java]
- **Redis wired but unused:** `RedisConfig` sets up a `CacheManager` with a 30 s TTL, but no `@Cacheable` annotations appear on service methods. Infrastructure is wired for future use. [code: config/RedisConfig.java]
- **Manual DTO mapping:** `AuditService.toResponse()` maps `AuditLog` to `AuditLogResponse` by hand despite MapStruct being on the classpath. [code: service/AuditService.java]
- **Virtual threads:** `spring.threads.virtual.enabled: true` for Java 21 virtual-thread support, consistent with the rest of the platform. [code: src/main/resources/application.yml]

## Reference

- API — 2 endpoints, 1 controller
- Data — 1 entity/table (`audit_logs`), 3 Flyway migrations
- Integrations — 2 Kafka topics consumed, 1 DLT producer, Redis cache (wired, unused)
- Configuration — 6 environment variables, 1 application.yml
- Tech debt — 0 TODO/FIXME; 5 structural issues (unused Redis cache, manual DTO mapping with MapStruct on classpath, unused JJWT dependency, unforwarded query params, missing `execution.completed` field extraction)
- Open questions — 2 items

Open CLAUDE.reference.md only for the section your task needs.
<!-- plumbline:end unit -->
