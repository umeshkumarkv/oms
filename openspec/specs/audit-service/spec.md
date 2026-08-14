# audit-service -- Service Specification

## Purpose

The audit-service is a read-write audit trail microservice in the OMS broker-dealer platform. It passively consumes Kafka events produced by other services (order state changes, execution completions) and persists immutable audit log records to its own PostgreSQL schema. It exposes REST endpoints that allow authorized clients to query the audit trail by order ID or by client ID with pagination. The service does not initiate any business actions itself; it is purely a record-keeper and query layer.

## Port

8087 (application.yml:43)

Note: Port table is also tracked in service-directory.md.

## Database Schema

Schema name: `audit_schema` (application.yml:21, `spring.flyway.schemas: audit_schema`; application.yml:18, `hibernate.default_schema: audit_schema`)

Tables (from migrations):

| Table | Source Migration |
|---|---|
| `audit_schema.audit_logs` | V1__schema.sql:4 |

Indexes on `audit_logs`:
- `idx_audit_entity_id` on `entity_id` (V1__schema.sql:16)
- `idx_audit_entity_type` on `entity_type` (V1__schema.sql:17)
- `idx_audit_event_timestamp` on `event_timestamp` (V1__schema.sql:18)
- `idx_audit_client_id` on `client_id` (V3__add_client_id.sql:3)

V2__seed.sql contains no data; the comment states logs are created by Kafka consumers only (V2__seed.sql:2).

Note: Schema list is also tracked in service-directory.md.

## Kafka Produced

None found in scan. No `kafkaTemplate.send()` calls exist in the main source tree.

Note: The Kafka error handler (`KafkaConfig.java:63`) configures `DeadLetterPublishingRecoverer`, which causes the Spring Kafka framework to implicitly publish failed messages to dead-letter topics (e.g., `order.state.changed.DLT`, `execution.completed.DLT`) after 3 exponential-backoff retry attempts (1 s, 2 s, 4 s). These DLT publications are framework-managed, not explicit application `kafkaTemplate.send()` calls. (KafkaConfig.java:60-63)

## Kafka Consumed

| Topic (exact literal) | Consumer Group | Trigger / Action | Key Payload Fields Extracted | file:line |
|---|---|---|---|---|
| `"order.state.changed"` | `"audit-service-order.state.changed-group"` | Persists an `AuditLog` record with `entityType="ORDER"` | `orderId`, `toState`, `fromState`, `clientId`, `userId`, `triggeredBy`, `timestamp`, `metadata.rejectionReason`, `metadata.violatedRuleCode` (for `RISK_REJECTED` state only) | AuditEventConsumer.java:27-28 |
| `"execution.completed"` | `"audit-service-execution.completed-group"` | Persists an `AuditLog` record with `entityType="EXECUTION"` | `orderId`, `fillType`, `clientId`, `symbol`, `side`, `executedQty`, `executedPrice`, `fillValue`, `executedAt` | AuditEventConsumer.java:92-93 |

Consumer configuration (application.yml:26-32):
- `auto-offset-reset: earliest`
- `key-deserializer: StringDeserializer`
- `value-deserializer: JsonDeserializer`
- `spring.json.trusted.packages: com.brokerdealer.*`

Idempotency: For `order.state.changed`, a duplicate check is performed before persisting: `existsByEntityIdAndNewStateAndEventTimestamp(orderId, toState, eventTs)` — duplicate events are silently skipped (AuditEventConsumer.java:51-54).

Note: Topic contracts are also tracked in service-directory.md.

## Redis Keys Owned

Redis infrastructure is configured (`RedisConfig.java`) with a default cache TTL of 30 seconds (`Duration.ofSeconds(30)`, RedisConfig.java:42) and a `RedisTemplate<String, Object>` bean (RedisConfig.java:25). However, no `@Cacheable`, `@CachePut`, `@CacheEvict`, or `redisTemplate.opsForValue()` calls were found in any service or controller class. The Redis infrastructure is present but no cache keys are actively used by this service in the current codebase.

## REST Endpoints Exposed

All endpoints require the `X-User-Role` header forwarded by the API gateway. Authorization is enforced by `RoleAuthorizationFilter` (not Spring Security). Actuator endpoints (`/actuator/**`) and OPTIONS requests are excluded from role checks (RoleAuthorizationFilter.java:66-68).

| Method | Path | Allowed Roles | Handler Method | file:line |
|---|---|---|---|---|
| GET | `/api/v1/audit/orders/{id}` | VIEWER, OPERATIONS, ADMIN, TRADER, SYSTEM | `AuditController.getOrderAuditTrail(UUID id)` | AuditController.java:18-21 |
| GET | `/api/v1/audit/trail` | VIEWER, OPERATIONS, ADMIN, TRADER, SYSTEM | `AuditController.getAuditTrail(UUID clientId, String from, String to, String entityType, int page, int size)` | AuditController.java:23-32 |

Notes:
- The class-level `@RequestMapping("/api/v1/audit")` is at AuditController.java:12.
- The Javadoc comment in `RoleAuthorizationFilter.java:26` states "ADMIN excluded per BRD 12.2", but the actual runtime code at `RoleAuthorizationFilter.java:33` includes `"ADMIN"` in `AUDIT_READ_ROLES`. The code is the enforced behavior; the comment is inconsistent. Confirm with team which is correct.
- The `from`, `to`, and `entityType` query parameters are accepted but not forwarded to the service layer (AuditController.java:31 passes only `clientId`, `page`, `size` to `auditService.getAuditTrail`).
- The `GET /api/v1/audit/trail` endpoint supports pagination via `page` (default 0) and `size` (default 20) query parameters (AuditController.java:29-30).

Management endpoints exposed: `health`, `info`, `metrics` (application.yml:48-49). Health probes enabled with `show-details: always` (application.yml:51-53).

## REST Endpoints Called

None found in scan. No `RestTemplate`, `WebClient`, or `@FeignClient` usage exists in the main source tree. The service does not make outbound HTTP calls to any other service.

## Entities

### AuditLog

Table: `audit_schema.audit_logs` (AuditLog.java:14)
Class: `com.brokerdealer.auditservice.domain.AuditLog`

| Field | Java Type | Column Name | Constraints / Annotations | file:line |
|---|---|---|---|---|
| `id` | `UUID` | `id` | `@Id`, `@GeneratedValue(strategy = GenerationType.UUID)`, `@Column(updatable = false, nullable = false)` | AuditLog.java:17-20 |
| `entityType` | `String` | `entity_type` | `@Column(nullable = false, length = 100)` | AuditLog.java:22-23 |
| `entityId` | `UUID` | `entity_id` | `@Column(nullable = false)` | AuditLog.java:25-26 |
| `previousState` | `String` | `previous_state` | `@Column(length = 100)` — nullable (no `nullable = false`) | AuditLog.java:28-29 |
| `newState` | `String` | `new_state` | `@Column(nullable = false, length = 100)` | AuditLog.java:31-32 |
| `actorId` | `UUID` | `actor_id` | `@Column` — nullable (no `nullable = false`) | AuditLog.java:34-35 |
| `actorType` | `ActorType` (enum) | `actor_type` | `@Enumerated(EnumType.STRING)`, `@Column(nullable = false, length = 20)` | AuditLog.java:37-39 |
| `eventTimestamp` | `Instant` | `event_timestamp` | `@Column(nullable = false)` | AuditLog.java:41-42 |
| `clientId` | `UUID` | `client_id` | `@Column` — nullable (no `nullable = false`); added in V3__add_client_id.sql | AuditLog.java:44-45 |
| `metadata` | `String` | `metadata` | `@Column(columnDefinition = "TEXT")` — nullable | AuditLog.java:47-48 |

## What This Service Must NEVER Do

1. **Must never write to another service's database schema** [CONVENTION -- INFERRED, CONFIRM WITH TEAM]: No cross-schema SQL or JDBC connections to schemas other than `audit_schema` were found. The service uses a single datasource pointing to `audit_schema`. Absence of cross-schema writes does not constitute code-level enforcement; it is an architectural convention.

2. **Must never make synchronous HTTP calls to other microservices** [CONVENTION -- INFERRED, CONFIRM WITH TEAM]: No `RestTemplate`, `WebClient`, or `@FeignClient` was found in the main source tree. The service is entirely event-driven for ingestion. This is a design choice, not enforced by code.

3. **Must never produce Kafka messages as part of business logic** [CONVENTION -- INFERRED, CONFIRM WITH TEAM]: No `kafkaTemplate.send()` calls exist in the main source. The `KafkaTemplate` bean is only used by the framework-level `DeadLetterPublishingRecoverer` for error handling. This is a design pattern, not enforced by code.

4. **Must never mutate (UPDATE/DELETE) audit log records once written** [CONVENTION -- INFERRED, CONFIRM WITH TEAM]: The repository (`AuditLogRepository`) exposes `save()` (insert) and read methods only. No update or delete methods are defined. However, `JpaRepository` inherits `deleteById()` and `delete()` methods by default; there is no code-level prohibition on their use.

5. **Must never allow unauthenticated access to audit endpoints** [ENFORCED]: `RoleAuthorizationFilter.doFilterInternal()` returns HTTP 401 if `X-User-Role` header is missing or blank for all non-actuator, non-OPTIONS paths (RoleAuthorizationFilter.java:49-54).

6. **Must never allow non-GET operations on audit data via REST** [ENFORCED]: `RoleAuthorizationFilter.isAllowed()` returns `false` for any HTTP method other than GET on `/api/v1/audit/**` (RoleAuthorizationFilter.java:70-74). No POST, PUT, PATCH, or DELETE mappings exist in `AuditController`.

## Key Business Rules

1. **ActorType classification**: An event actor is classified as `ActorType.USER` if the `triggeredBy` field starts with the prefix `"USER:"`, otherwise it is classified as `ActorType.SYSTEM`. Enum values: `USER`, `SYSTEM` (ActorType.java:3-6; AuditEventConsumer.java:56-57).

2. **Duplicate event suppression for `order.state.changed`**: Before persisting, the consumer checks `existsByEntityIdAndNewStateAndEventTimestamp(orderId, toState, eventTs)`. If a matching record already exists, the event is silently skipped without error (AuditEventConsumer.java:51-54). No equivalent deduplication exists for `execution.completed`.

3. **RISK_REJECTED metadata enrichment**: When an `order.state.changed` event carries `toState = "RISK_REJECTED"`, the consumer additionally extracts `rejectionReason` and `violatedRuleCode` from the event's nested `metadata` map and stores them in the audit log's JSON metadata field (AuditEventConsumer.java:63-70).

4. **Kafka consumer error handling**: Failed consumer messages are retried with exponential backoff (initial interval 1000 ms, multiplier 2.0, max 3 attempts). After exhausting retries, the message is published to the dead-letter topic by `DeadLetterPublishingRecoverer` (KafkaConfig.java:60-63).

5. **Audit trail pagination**: `GET /api/v1/audit/trail` defaults to page 0, size 20. Records are always returned ordered by `event_timestamp` descending (AuditController.java:29-30; AuditService.java:29-33).

6. **Client-scoped audit trail**: When `clientId` query parameter is provided to `GET /api/v1/audit/trail`, the query is filtered by `client_id`; otherwise all records are returned (AuditService.java:27-34). The `from`, `to`, and `entityType` query parameters are declared in the controller but are currently not passed to the service layer and have no effect (AuditController.java:25-31).

7. **Timestamp fallback**: If the `timestamp` (for `order.state.changed`) or `executedAt` (for `execution.completed`) field is null or unparseable, `Instant.now()` is used as the event timestamp (AuditEventConsumer.java:155-162).

8. **Allowed roles for audit read**: `AUDIT_READ_ROLES = Set.of("VIEWER", "OPERATIONS", "ADMIN", "TRADER", "SYSTEM")` (RoleAuthorizationFilter.java:33). Note: the Javadoc comment at RoleAuthorizationFilter.java:26 contradicts the implementation by claiming ADMIN is excluded — the runtime set is authoritative.
