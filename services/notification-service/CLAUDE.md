<!-- plumbline:template claude-unit v1 -->
# notification-service

<!-- plumbline:begin unit -->
Last updated: 4586741 (2026-10-30, /pl-onboard, claude-unit v1)

## Purpose

Delivers real-time notifications to browser clients via Server-Sent Events (SSE). Consumes three Kafka topics (order state changes, execution completions, risk evaluation results) and routes each event as a targeted push to the owning trader or a broadcast to OPERATIONS/ADMIN connections. Sends a keepalive SSE comment every 20 seconds to prevent idle-connection drops. No database; no outbound HTTP.

## Build & run

Build: `mvn clean package -DskipTests` | Run: `java -jar target/*.jar` | Tests: `mvn test`
Port: **8088** (`application.yml`). Virtual threads enabled. No Spring profiles.

Env vars: `JWT_SECRET` (required, no default), `SPRING_KAFKA_BOOTSTRAP_SERVERS` (default: `localhost:9092`).

**Test recipe:** `mvn test` · Java 21 required · **FAILED** — `ExceptionInInitializerError: TypeTag::UNKNOWN` (Lombok/MapStruct incompatible with Java 24) · 2026-10-30

## Architecture & structure

**Stack:** Java 21, Spring Boot 3.2.4, Spring Security, Spring Kafka, JJWT 0.12.3, Lombok 1.18.30, springdoc-openapi 2.3.0, JaCoCo 0.8.11.

**Flow:** `Kafka topics → kafka/consumer/ → NotificationService → SseEmitterRegistry → SseController → SSE stream`

| Package | Contents |
|---|---|
| `config` | `SecurityConfig`, `JwtAuthenticationFilter`, `KafkaConfig` |
| `controller` | `SseController` — single SSE endpoint |
| `service` | `NotificationService` (push/broadcast facade), `SseEmitterRegistry` (in-memory store + heartbeat) |
| `kafka/consumer` | `OrderStateConsumer`, `ExecutionConsumer`, `RiskResultConsumer` |
| `domain` | `NotificationType` enum |
| `dto/event` | Inbound Kafka event DTOs |
| `dto/response` | `NotificationResponse` pushed to clients |
| `exception` | `GlobalExceptionHandler`, `ValidationException` |

`SseEmitterRegistry`: `ConcurrentHashMap<UUID, SseEmitter>` for TRADERs; `CopyOnWriteArrayList<SseEmitter>` for OPERATIONS/ADMIN broadcast. Emitters removed on completion, timeout, or error.

## Security

- JWT (HS256, `${app.jwt.secret}`) validated per-request — this is the **sole downstream service** that validates JWTs directly (SSE stream bypasses the gateway proxy). Claims `userId` and `role` set as principal/authority. [code: `config/JwtAuthenticationFilter.java`]
- All endpoints except `/actuator/**` require auth; STATELESS session; bearer token only, never stored. [code: `config/SecurityConfig.java`]
- TRADER emitters keyed by userId (targeted); OPERATIONS/ADMIN in broadcast list. [code: `service/SseEmitterRegistry.java`]
- **Risk:** Role from JWT claim not validated against known values; arbitrary role goes into broadcast list. [code: `service/SseEmitterRegistry.java`]

## Rules

- Every `/api/v1/**` request must carry a valid JWT; unauthenticated requests get HTTP 401. [code: `config/SecurityConfig.java`]
- JWT claims `userId` and `role` are the sole identity source; no user store consulted. [code: `config/JwtAuthenticationFilter.java`]
- Per-topic consumer group IDs: `notification-service-{topic}-group`. `USE_TYPE_INFO_HEADERS=false`; deserialise to local DTOs. Kafka DLT: 1→2 s, 3 attempts. [code: `kafka/consumer/`, `config/KafkaConfig.java`]
- RISK_RESULT events with `result == "PASSED"` are silently dropped. [code: `kafka/consumer/RiskResultConsumer.java`]
- Events with null `orderId` are skipped (WARN logged). [code: `kafka/consumer/OrderStateConsumer.java`, `kafka/consumer/RiskResultConsumer.java`]
- Push target is `userId` falling back to `clientId`; if both null, targeted push skipped but broadcast fires. [code: `kafka/consumer/`]
- SSE emitter timeout is `Long.MAX_VALUE`; lifetime managed by client disconnect or send error. [code: `controller/SseController.java`]
- Keepalive SSE comment sent every 20 seconds to all active emitters. [code: `service/SseEmitterRegistry.java`]
- JaCoCo line coverage minimum 80 %; `config/*`, `exception/*`, and entry point excluded. [code: `pom.xml`]

## Must never

- Do not introduce server-side session state; the service is stateless by design.
- Do not query a database or make outbound HTTP calls.
- Do not expose `JWT_SECRET` in logs, responses, or committed config files.
- Do not remove per-topic consumer group IDs from `@KafkaListener`; the app-level group-id is a fallback only.
- Do not send a notification for a RISK_RESULT event with `result == "PASSED"`.
- Do not bypass `JwtAuthenticationFilter` by permitting `/api/**` in `SecurityConfig`.

## Decisions & trade-offs

- **In-process SSE registry:** Emitters in JVM memory; single-instance POC only — connections lost on restart, no cross-instance delivery. Source: pom.xml comment ("POC").
- **`USE_TYPE_INFO_HEADERS=false`:** Producer `__TypeId__` headers carry foreign class names; stripping avoids `ClassNotFoundException`. Source: `config/KafkaConfig.java` comment.
- **Virtual threads:** `spring.threads.virtual.enabled: true` — SSE long-poll threads park cheaply. Source: `application.yml`.
- **`SseEmitter` timeout `Long.MAX_VALUE`:** Heartbeat keeps proxies alive; no app-level timeout needed. Source: `controller/SseController.java`.

## Reference

- API — 1 SSE endpoint, 1 controller (`SseController`).
- Data — 0 tables; 3 Kafka event DTOs; 1 response DTO; 1 enum.
- Integrations — 3 Kafka topics consumed; 1 DLT produced; no outbound HTTP.
- Configuration — 2 env vars (`JWT_SECRET`, `SPRING_KAFKA_BOOTSTRAP_SERVERS`); 3 actuator endpoints.
- Tech debt — 0 TODO/FIXME; in-process registry not horizontally scalable; one emitter per userId (silent replace).
- Open questions — DLT naming convention not explicitly set; RISK_RESULT PASSED log level; multi-instance deployment plan.

Open CLAUDE.reference.md only for the section your task needs.
<!-- plumbline:end unit -->
