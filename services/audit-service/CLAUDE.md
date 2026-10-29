<!-- plumbline:template claude-unit v1 -->
# Audit Service

<!-- plumbline:begin unit -->
Last updated: 4369a80 (2026-10-28, /pl-onboard, claude-unit v1)

## Purpose

The Audit Service is the immutable event log for the Broker-Dealer OMS. It consumes Kafka events from order and execution pipelines, persists every state transition and execution completion as an `AuditLog` record in PostgreSQL (`audit_schema`), and exposes a read-only REST API so operators and viewers can retrieve audit trails by order ID or by client ID. It never initiates changes; writes happen exclusively through Kafka consumers.

## Build & run

```
# from repo root
mvn -pl services/audit-service test
mvn -pl services/audit-service spring-boot:run

# or from service folder
./mvnw test
./mvnw spring-boot:run
```

Port: **8087**

Profiles/environments: no Spring profiles declared; all config is via environment variables (see Configuration in reference).

Docker Compose `depends_on`:
- `kafka` — `condition: service_healthy`
- `redis` — `condition: service_started`
- `postgres` — `condition: service_started`

**Test recipe:** `mvn test` · requires Java 21 (`<java.version>21</java.version>` in parent pom) · not run — Java 24 installed locally is incompatible with maven-compiler-plugin 3.11.0; first error: `Fatal error compiling: java.lang.ExceptionInInitializerError: com.sun.tools.javac.code.TypeTag :: UNKNOWN` · 2026-10-28

## Architecture & structure

**Stack:** Java 21, Spring Boot (parent `broker-dealer-parent 1.0.0-SNAPSHOT`), Spring Data JPA, Spring Kafka, Spring Data Redis, Flyway, PostgreSQL 15, Redis 7, Lombok, springdoc-openapi.

**Layers / pattern:** Kafka consumer → repository (write path); controller → service → repository (read path). No outbound HTTP calls.

**Virtual threads:** `spring.threads.virtual.enabled: true` — all request threads are virtual.

**Kafka error handling:** exponential back-off (1 s → 2 s → 4 s, 3 attempts), then dead-letter publish via `DeadLetterPublishingRecoverer`. [code: config/KafkaConfig.java]

**Main packages:**
- `controller` — `AuditController` (REST endpoints)
- `service` — `AuditService` (query logic, DTO mapping)
- `kafka/consumer` — `AuditEventConsumer` (two `@KafkaListener` methods)
- `domain` — `AuditLog` entity, `ActorType` enum
- `repository` — `AuditLogRepository` (JPA)
- `config` — `KafkaConfig`, `RedisConfig`
- `filter` — `RoleAuthorizationFilter` (servlet filter, no Spring Security)
- `exception` — `GlobalExceptionHandler`, `ValidationException`
- `dto/response` — `AuditLogResponse`, `AuditTrailResponse`, `ResponseMeta`

**Database:** schema `audit_schema`, single table `audit_logs`. DDL managed by Flyway (V1, V2, V3). `ddl-auto: validate`.

**Redis:** used as cache (`RedisCacheManager`), 30-second TTL, null values not cached. [code: config/RedisConfig.java]

## Security

**Authentication:** none performed in this service. JWT validation is delegated to the API gateway.

**Authorization:** `RoleAuthorizationFilter` (servlet `OncePerRequestFilter`) reads the `X-User-Role` header forwarded by the API gateway. Roles permitted for `GET /api/v1/audit/**`: `VIEWER`, `OPERATIONS`, `ADMIN`, `TRADER`, `SYSTEM`. Any other role, or a missing header, returns 401/403. Actuator paths (`/actuator/**`) and `OPTIONS` requests bypass the filter. [code: filter/RoleAuthorizationFilter.java]

**Token storage:** tokens are not stored or validated in this service; the gateway forwards the resolved role via header.

## Rules

- Audit records are written only by Kafka consumers, never by REST callers. [code: AuditEventConsumer.java]
- Duplicate `order.state.changed` events are silently dropped (checked by `entityId + newState + eventTimestamp`). [code: AuditEventConsumer.java]
- `X-User-Role` header must be present on all non-actuator API calls; missing header → HTTP 401. [code: filter/RoleAuthorizationFilter.java]
- Only roles `VIEWER`, `OPERATIONS`, `ADMIN`, `TRADER`, `SYSTEM` may call `GET /api/v1/audit/**`; all other methods return 403. [code: filter/RoleAuthorizationFilter.java]
- `RISK_REJECTED` order state transitions include `rejectionReason` and `violatedRuleCode` in the metadata JSON. [code: AuditEventConsumer.java]
- Flyway DDL mode is `validate`; schema changes require a new migration script. [code: application.yml]
- Redis cache TTL is 30 seconds; null values are never cached. [code: config/RedisConfig.java]
- Kafka consumer failures use exponential back-off (3 attempts) then route to a dead-letter topic. [code: config/KafkaConfig.java]

## Must never

- Write audit records via REST — all writes must go through Kafka consumers.
- Validate or decode JWTs — that responsibility belongs to the API gateway.
- Modify the `audit_logs` table structure outside a numbered Flyway migration (V-prefix).
- Remove or relax the `X-User-Role` header check on audit endpoints.
- Cache or log the raw `metadata` TEXT in a way that exposes order-level PII in plaintext log output.

## Decisions & trade-offs

- **No Spring Security dependency:** role enforcement is done with a plain servlet filter reading the gateway-forwarded `X-User-Role` header. The filter comment states: "No Spring Security dependency required." [code: filter/RoleAuthorizationFilter.java]
- **Kafka-only write path:** the service has no REST write endpoints; all ingestion is event-driven, keeping the audit trail append-only and decoupled from the originating services.
- **Exponential back-off + DLT:** failed Kafka messages retry up to 3 times (1 s → 2 s → 4 s) then go to a dead-letter topic rather than blocking the consumer. The comment in `KafkaConfig` documents this. [code: config/KafkaConfig.java]
- **Virtual threads enabled:** `spring.threads.virtual.enabled: true` allows high I/O concurrency without increasing thread-pool size. [code: application.yml]

## Reference

- API — 2 endpoints, 1 controller
- Data — 1 table (`audit_logs`), 3 Flyway migrations
- Integrations — 2 Kafka topics consumed, 2 DLTs (via error handler), 1 Redis cache
- Configuration — 7 environment variables
- Tech debt — 0 TODO/FIXME; see `## Open questions`

Open CLAUDE.reference.md only for the section your task needs.

— Plumbline 2.8.7 · /pl-onboard · claude-unit v1 · commit 4369a80 · judge pending · AI-drafted, posted after SME review
<!-- plumbline:end unit -->
