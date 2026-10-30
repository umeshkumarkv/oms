<!-- plumbline:template claude-unit v1 -->
# notification-service

<!-- plumbline:begin unit -->
Last updated: 4369a80 (2026-10-30, /pl-onboard, claude-unit v1)

## Purpose

Delivers real-time notifications to browser clients via Server-Sent Events (SSE). Consumes three
Kafka topics (order state changes, execution completions, risk evaluation results) and routes each
event as a targeted push to the owning trader or a broadcast to OPERATIONS/ADMIN connections. Sends
a keepalive SSE comment every 20 seconds to prevent idle-connection drops through proxies.

## Build & run

```
mvn clean package          # build JAR (skips tests)
mvn test                   # run unit tests (requires Java 21)
java -jar target/*.jar     # start service
```

Port: **8088** (hardcoded in `src/main/resources/application.yml`). Virtual threads enabled. Single `application.yml`; no Spring profiles.

Environment variables required at startup:
- `JWT_SECRET` — HMAC-SHA secret for JWT validation (no default; service will not start without it)
- `SPRING_KAFKA_BOOTSTRAP_SERVERS` — Kafka broker address (default: `localhost:9092`)

**Test recipe:** `mvn test` · requires Java 21 (`<java.version>21</java.version>` in root pom.xml) ·
available runtime Java 24 (openjdk version "24") · **FAILED** —
`Fatal error compiling: java.lang.ExceptionInInitializerError: com.sun.tools.javac.code.TypeTag :: UNKNOWN` —
Lombok/MapStruct annotation processors are incompatible with Java 24; tests cannot run until Java 21 is used · 2026-10-30

## Architecture & structure

**Stack:** Java 21, Spring Boot 3.2.4, Spring Security, Spring Kafka, JJWT 0.12.3, Lombok 1.18.30, springdoc-openapi 2.3.0, JaCoCo 0.8.11.

**Pattern:** thin event-driven push service; no database, no outbound HTTP.

Flow: `kafka topics → consumer (kafka/consumer/) → NotificationService (service/) → SseEmitterRegistry → SseController ← SSE stream to browser`

**Main packages:**

| Package | Contents |
|---|---|
| `config` | `SecurityConfig`, `JwtAuthenticationFilter`, `KafkaConfig` |
| `controller` | `SseController` — single SSE endpoint |
| `service` | `NotificationService` (push/broadcast facade), `SseEmitterRegistry` (in-memory emitter store + heartbeat) |
| `kafka/consumer` | `OrderStateConsumer`, `ExecutionConsumer`, `RiskResultConsumer` |
| `domain` | `NotificationType` enum |
| `dto/event` | Inbound Kafka event DTOs |
| `dto/response` | `NotificationResponse` pushed to clients |
| `exception` | `GlobalExceptionHandler`, `ValidationException` |

`SseEmitterRegistry` uses a `ConcurrentHashMap<UUID, SseEmitter>` for TRADER connections and a `CopyOnWriteArrayList<SseEmitter>` for OPERATIONS/ADMIN broadcast connections; emitters are removed on completion, timeout, or error.

## Security

- **Authentication:** JWT (HMAC-SHA, secret `${app.jwt.secret}`) validated per-request from `Authorization: Bearer` header; claims `userId` and `role` set as principal/authority. [code: `config/JwtAuthenticationFilter.java`]
- **Authorization:** All endpoints except `/actuator/**` require authentication. [code: `config/SecurityConfig.java`]
- **Session policy:** STATELESS — no HTTP session created or used. [code: `config/SecurityConfig.java`]
- **Tokens:** JWT bearer only; never stored server-side. [code: `config/SecurityConfig.java`, `config/JwtAuthenticationFilter.java`]
- **Routing by role:** TRADER emitters keyed by userId (targeted push); OPERATIONS/ADMIN emitters in broadcast list. [code: `service/SseEmitterRegistry.java`]

## Rules

- Every `/api/v1/**` request must carry a valid JWT; unauthenticated requests receive HTTP 401 `{"error":{"code":"UNAUTHORIZED","message":"Missing or invalid token"},"meta":{}}`. [code: `config/SecurityConfig.java`]
- JWT claims `userId` and `role` are the sole identity source; no user store is consulted. [code: `config/JwtAuthenticationFilter.java`]
- Kafka consumer group IDs are per-topic: `notification-service-order.state.changed-group`, `notification-service-execution.completed-group`, `notification-service-risk.evaluation.result-group`. [code: `kafka/consumer/OrderStateConsumer.java`, `kafka/consumer/ExecutionConsumer.java`, `kafka/consumer/RiskResultConsumer.java`]
- Each consumer factory sets `USE_TYPE_INFO_HEADERS=false`; `__TypeId__` headers from producers are ignored and messages deserialise directly to the local DTO. [code: `config/KafkaConfig.java`]
- Failed Kafka messages are retried with exponential back-off (1 s initial, 2× multiplier, max 3 attempts) then published to a dead-letter topic. [code: `config/KafkaConfig.java`]
- RISK_RESULT events with `result == "PASSED"` are silently dropped. [code: `kafka/consumer/RiskResultConsumer.java`]
- Events with a null `orderId` are skipped (WARN logged, no notification sent). [code: `kafka/consumer/OrderStateConsumer.java`, `kafka/consumer/RiskResultConsumer.java`]
- Push target is `userId` falling back to `clientId`; if both null, targeted push is skipped but broadcast still fires. [code: `kafka/consumer/OrderStateConsumer.java`, `kafka/consumer/RiskResultConsumer.java`]
- SSE emitter timeout is `Long.MAX_VALUE`; connection lifetime managed by client disconnect or send error. [code: `controller/SseController.java`]
- A `keepalive` SSE comment heartbeat is sent to all active emitters every 20 seconds. [code: `service/SseEmitterRegistry.java`]
- JaCoCo line coverage minimum is 80 %; `config/*`, `exception/*`, and entry point are excluded. [code: `pom.xml` (root), `pom.xml` (service)]

## Must never

- Do not introduce server-side session state; the service is stateless by design.
- Do not query a database or make outbound HTTP calls; this service only consumes Kafka and pushes SSE.
- Do not expose `JWT_SECRET` in logs, responses, or configuration files checked into source control.
- Do not remove per-topic consumer group IDs from the `@KafkaListener` annotations; the application-level
  group-id in `application.yml` is a fallback only and must not replace the per-listener IDs.
- Do not send a push notification for a RISK_RESULT event that has `result == "PASSED"`.
- Do not bypass the `JwtAuthenticationFilter` by permitting `/api/**` paths in `SecurityConfig`.

## Decisions & trade-offs

- **In-process SSE registry:** Emitters kept in JVM memory (`ConcurrentHashMap` / `CopyOnWriteArrayList`); works for single-instance POC but connections do not survive a restart and events are lost across instances. Source: pom.xml comment ("POC") and absence of any distributed store dependency.
- **`USE_TYPE_INFO_HEADERS=false`:** Producer `__TypeId__` headers reference classes not on this service's classpath; stripping them avoids `ClassNotFoundException`. Source: `config/KafkaConfig.java` comment.
- **Virtual threads:** `spring.threads.virtual.enabled: true` lets SSE long-poll threads park cheaply. Source: `application.yml`.
- **`SseEmitter` timeout `Long.MAX_VALUE`:** Heartbeat keeps proxies alive; no app-level timeout needed. Source: `controller/SseController.java`.

## Reference

- API — 1 SSE endpoint, 1 controller (`SseController`). Open CLAUDE.reference.md only for the section your task needs.
- Data — 0 database tables; 3 Kafka event DTOs; 1 response DTO; 1 enum.
- Integrations — 3 Kafka topics consumed; 1 DLQ (dead-letter) topic produced; no outbound HTTP.
- Configuration — 2 env vars (`JWT_SECRET`, `SPRING_KAFKA_BOOTSTRAP_SERVERS`); 3 actuator endpoints exposed.
- Tech debt — 0 TODO/FIXME found.
- Open questions — None found in code.
<!-- plumbline:end unit -->
