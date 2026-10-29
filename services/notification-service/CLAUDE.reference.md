<!-- plumbline:template claude-reference v1 -->
# Notification Service — Reference

<!-- plumbline:begin reference -->
Last updated: 4369a80 (2026-10-28, /pl-onboard, claude-reference v1)

## API

**Controller: SseController** (`controller/SseController.java`) — base path `/api/v1/notifications`

| Method | Path | Handler | Purpose |
|---|---|---|---|
| GET | `/api/v1/notifications/stream` | `SseController.stream` | Opens a long-lived SSE stream for the authenticated user. Returns `text/event-stream`. Sends an initial `connected` event with `{userId, role}`, then forwards notification events pushed by consumers. |

**SSE event names emitted on the stream:**

| Event name | When |
|---|---|
| `connected` | Immediately on connection established |
| `ORDER_STATE_CHANGED` | Order transitions to a new state |
| `EXECUTION_COMPLETED` | Trade execution (full or partial fill) |
| `RISK_RESULT` | Risk check rejected an order |

**Actuator endpoints (unauthenticated):**

| Path | Purpose |
|---|---|
| `/actuator/health` | Liveness/readiness probes |
| `/actuator/info` | Service info |
| `/actuator/metrics` | Metrics scrape |

## Data

No relational database. No migrations. No persistent storage.

**In-memory state (SseEmitterRegistry):**

| Structure | Type | Key/Element | Lifetime |
|---|---|---|---|
| `traderEmitters` | `ConcurrentHashMap<UUID, SseEmitter>` | keyed by userId (JWT `userId` claim) | duration of SSE connection |
| `broadcastEmitters` | `CopyOnWriteArrayList<SseEmitter>` | one entry per OPERATIONS/ADMIN connection | duration of SSE connection |

**Event DTOs (inbound from Kafka):**

`ExecutionCompletedEvent` — fields: `orderId` (UUID), `clientId` (UUID), `symbol`, `side`, `fillType` (`"FULL"` or partial), `executedQty`, `executedPrice`, `remainingQuantity`

`OrderStateChangedEvent` — fields: `orderId` (UUID), `clientId` (UUID), `userId` (UUID, nullable), `fromState`, `toState`

`RiskEvaluationResultEvent` — fields: `orderId` (UUID), `clientId` (UUID), `userId` (UUID, nullable), `result` (`"PASSED"` or other), `ruleCode`, `reason`

**Response DTO (outbound SSE payload):**

`NotificationResponse` — fields: `type` (NotificationType enum), `orderId` (UUID), `clientId` (UUID), `title` (String), `message` (String), `timestamp` (Instant)

**NotificationType enum:** `ORDER_STATE_CHANGED`, `EXECUTION_COMPLETED`, `RISK_RESULT`

## Integrations

**Kafka — consumed topics:**

| Topic | Consumer class | Group ID | Container factory | Notes |
|---|---|---|---|---|
| `order.state.changed` | `OrderStateConsumer` | `notification-service-order.state.changed-group` | `orderStateListenerContainerFactory` | Deserializes to `OrderStateChangedEvent`; ignores `__TypeId__` header |
| `execution.completed` | `ExecutionConsumer` | `notification-service-execution.completed-group` | `executionListenerContainerFactory` | Deserializes to `ExecutionCompletedEvent`; ignores `__TypeId__` header |
| `risk.evaluation.result` | `RiskResultConsumer` | `notification-service-risk.evaluation.result-group` | `riskResultListenerContainerFactory` | Deserializes to `RiskEvaluationResultEvent`; ignores `__TypeId__` header; PASSED events are silently dropped |

**Kafka — produced topics (dead-letter only):**

Consumer error handler (`KafkaConfig.buildErrorHandler`) uses `DeadLetterPublishingRecoverer` with a `KafkaTemplate<String, Object>`. After 3 retries (exponential back-off: 1 s initial, ×2), failed messages are published to the default dead-letter topic (`<original-topic>.DLT`).

**No outbound HTTP calls. No database connection. No cache.**

Bootstrap servers: `SPRING_KAFKA_BOOTSTRAP_SERVERS` env var (default `localhost:9092`).

## Configuration

| Key / Env var | Where set | Purpose |
|---|---|---|
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | env / `application.yml` default `localhost:9092` | Kafka broker address |
| `JWT_SECRET` | env (required, no default) | HMAC-SHA key for JWT signature verification |
| `spring.kafka.consumer.group-id` | `application.yml` | Base consumer group: `notification-service-group` (typed factories use per-topic group IDs) |
| `spring.kafka.consumer.auto-offset-reset` | `application.yml` | `earliest` — replay from start on new group |
| `spring.threads.virtual.enabled` | `application.yml` | `true` — enables Java 21 virtual threads |
| `management.endpoints.web.exposure.include` | `application.yml` | `health,info,metrics` |
| `management.endpoint.health.probes.enabled` | `application.yml` | `true` — liveness/readiness probes active |
| `logging.level.com.brokerdealer` | `application.yml` | `INFO` |
| `logging.level.org.springframework.kafka` | `application.yml` | `WARN` |

**Secret handling:** `JWT_SECRET` must be injected at runtime; it is not stored in application.yml. The docker-compose dev value must never be used in any non-dev environment.

## Tech debt

No TODO, FIXME, HACK, or XXX markers found in `src/main/java`.

**Structural notes (not marked as debt in code):**

- Horizontal scaling is blocked by in-process emitter maps (trader emitters are node-local). Multiple replicas would require sticky sessions or migrating emitter state to a shared broker (e.g., Redis Pub/Sub). This is an architectural limitation documented implicitly by the `ConcurrentHashMap` / `CopyOnWriteArrayList` choice.
- Dead-letter topic names follow Spring Kafka convention (`<topic>.DLT`) but no consumer for those topics exists in this service or is documented in the repo. Fate of dead-lettered messages is unknown.

## Open questions

1. **Dead-letter consumer:** No service is documented as consuming `order.state.changed.DLT`, `execution.completed.DLT`, or `risk.evaluation.result.DLT`. Who owns monitoring and replaying DLT messages? (For: Platform/DevOps or the originating service teams.)

— Plumbline 2.8.7 · /pl-onboard · claude-reference v1 · commit 4369a80 · judge pending · AI-drafted, posted after SME review
<!-- plumbline:end reference -->
