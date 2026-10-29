<!-- plumbline:template claude-unit v1 -->
# Notification Service

<!-- plumbline:begin unit -->
Last updated: 4369a80 (2026-10-28, /pl-onboard, claude-unit v1)

## Purpose

The Notification Service is a stateless Spring Boot service that fans out real-time notifications to connected browser clients over Server-Sent Events (SSE). It consumes three Kafka topics — order state changes, trade execution completions, and risk evaluation results — converts each event into a typed `NotificationResponse`, then pushes it either to a specific TRADER's SSE stream or broadcasts it to all OPERATIONS/ADMIN streams. It holds no database and no cache; all state is in-process emitter maps for the lifetime of each connection.

## Build & run

```
./mvnw clean package -pl services/notification-service
./mvnw spring-boot:run  -pl services/notification-service
```

Port: **8088**

Profiles/environments: no Spring profiles declared; behaviour is driven entirely by environment variables (see Configuration in reference).

Docker (compose): `docker compose up notification-service` — depends on Kafka (`condition: service_healthy`).

**Test recipe:** `mvn test` · requires Java 21 (`<java.version>21</java.version>` in parent pom) · **not run** — Java 24 installed locally is incompatible with maven-compiler-plugin 3.11.0; first error: `Fatal error compiling: java.lang.ExceptionInInitializerError: com.sun.tools.javac.code.TypeTag :: UNKNOWN` · 2026-10-28

## Architecture & structure

**Stack:** Java 21, Spring Boot (Web MVC, Security, Actuator), Spring Kafka, JJWT, Lombok, springdoc-openapi. Build: Maven (parent pom at repo root). No database, no cache.

**Pattern:** Event-driven push service.

```
Kafka topics
  └─ consumer (kafka/consumer/)          ← typed @KafkaListener per topic
       └─ NotificationService (service/) ← routes to push or broadcast
            └─ SseEmitterRegistry        ← in-process emitter store
                 └─ SseController (controller/) ← HTTP GET /stream → SseEmitter
```

**Packages:**

| Package | Contents |
|---|---|
| `config` | `KafkaConfig`, `SecurityConfig`, `JwtAuthenticationFilter` |
| `controller` | `SseController` — single SSE endpoint |
| `kafka/consumer` | `ExecutionConsumer`, `OrderStateConsumer`, `RiskResultConsumer` |
| `service` | `NotificationService`, `SseEmitterRegistry` |
| `domain` | `NotificationType` enum |
| `dto/event` | Inbound event DTOs (`ExecutionCompletedEvent`, `OrderStateChangedEvent`, `RiskEvaluationResultEvent`) |
| `dto/response` | `NotificationResponse` (outbound SSE payload) |
| `exception` | `GlobalExceptionHandler`, `ValidationException` |

Virtual threads are enabled (`spring.threads.virtual.enabled: true`).

## Security

**Authentication:** JWT validated on every non-actuator request by `JwtAuthenticationFilter` (`config/JwtAuthenticationFilter.java`). The filter reads the `Authorization: Bearer <token>` header, verifies the HMAC-SHA signature with `app.jwt.secret`, then extracts `userId` (used as `Authentication.getName()`) and `role` claims.

**Authorization:** Spring Security stateless filter chain (`config/SecurityConfig.java`). `/actuator/**` is open; every other path requires a valid JWT. No method-level security annotations.

**Token storage:** Bearer token supplied per-request by the client; service is stateless (no session, `SessionCreationPolicy.STATELESS`).

**Role routing (not HTTP authorization):** TRADER emitters are keyed by userId in `ConcurrentHashMap`; OPERATIONS and ADMIN emitters go into a `CopyOnWriteArrayList` for broadcast. `[code: service/SseEmitterRegistry.java]`

**Secret source:** `JWT_SECRET` environment variable → `app.jwt.secret`. `[code: config/JwtAuthenticationFilter.java]`

## Rules

- Kafka consumer errors retry with exponential back-off (1 s initial, ×2, 3 attempts max) then publish to a dead-letter topic. `[code: config/KafkaConfig.java]`
- Each Kafka topic uses its own typed `ConcurrentKafkaListenerContainerFactory` with `USE_TYPE_INFO_HEADERS=false` so producer `__TypeId__` headers are ignored and messages deserialize to local DTOs. `[code: config/KafkaConfig.java]`
- Events with null `orderId` (or null `clientId` for executions) are logged at WARN and dropped — never forwarded. `[code: kafka/consumer/ExecutionConsumer.java, OrderStateConsumer.java, RiskResultConsumer.java]`
- Risk events with result `"PASSED"` are silently discarded; only rejected orders generate a push. `[code: kafka/consumer/RiskResultConsumer.java]`
- All three consumers — `ExecutionConsumer`, `OrderStateConsumer`, and `RiskResultConsumer` — call both `pushToClient()` AND `broadcast()` for every non-dropped event: a targeted push goes to the owning trader (`userId`, falling back to `clientId` if null) AND an untargeted broadcast goes to all OPERATIONS/ADMIN streams simultaneously. `[code: kafka/consumer/ExecutionConsumer.java, OrderStateConsumer.java, RiskResultConsumer.java]`
- A keepalive SSE comment is sent to all active emitters every 20 seconds to prevent proxy idle-timeout drops. `[code: service/SseEmitterRegistry.java]`
- Actuator endpoints (`/actuator/health`, `/actuator/info`, `/actuator/metrics`) are unauthenticated. `[code: config/SecurityConfig.java]`
- Trusted Kafka packages are scoped to `com.brokerdealer.*`. `[code: config/KafkaConfig.java]`

## Must never

- Add a database or shared cache without updating the depends_on chain in docker-compose.yml and the connection pool config.
- Persist notifications to any store — this service is intentionally stateless; missed events are not replayed.
- Share JWT secret values in source control or logs; the value must come only from the `JWT_SECRET` environment variable.
- Remove the dead-letter error handler from any `ConcurrentKafkaListenerContainerFactory` — silent Kafka failures would cause invisible data loss.
- Authenticate or authorize Kafka consumers — Kafka access is network-controlled; adding Spring Security to consumer threads would break the listener factories.

## Decisions & trade-offs

- **In-process emitter maps instead of a pub-sub broker for SSE state:** `SseEmitterRegistry` uses `ConcurrentHashMap` (traders) and `CopyOnWriteArrayList` (broadcast). This keeps the service dependency-free but means SSE connections are node-local; horizontal scaling requires sticky sessions or a shared broker. Source: code comment in `service/SseEmitterRegistry.java` (field declarations).
- **`USE_TYPE_INFO_HEADERS=false` per topic:** Producer services embed their own fully-qualified class names in `__TypeId__` headers. Because notification-service does not have those classes on its classpath, it ignores headers and deserializes to its own local DTOs. Source: `config/KafkaConfig.java` comment line 64.
- **Virtual threads enabled:** `spring.threads.virtual.enabled: true` allows many concurrent SSE connections without a large thread pool. Source: `src/main/resources/application.yml`.
- **Separate `ConcurrentKafkaListenerContainerFactory` per topic:** Ensures type safety for deserialization and allows independent error-handler tuning per topic. Source: `config/KafkaConfig.java`.

## Reference

- API — 1 endpoint, 1 controller. Open CLAUDE.reference.md only for the section your task needs.
- Data — no tables or migrations; 1 in-memory registry, 3 event DTOs, 1 response DTO.
- Integrations — 3 consumed Kafka topics, dead-letter producer.
- Configuration — 2 environment variables, 3 actuator endpoints exposed.
- Tech debt — 0 TODO/FIXME markers found.
- Open questions — 1 item.

— Plumbline 2.8.7 · /pl-onboard · claude-unit v1 · commit 4369a80 · judge pending · AI-drafted, posted after SME review
<!-- plumbline:end unit -->
