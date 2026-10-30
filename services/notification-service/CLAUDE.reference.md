<!-- plumbline:template claude-reference v1 -->
# notification-service — reference

<!-- plumbline:begin reference -->
Last updated: 4586741 (2026-10-30, /pl-onboard, claude-reference v1)

## API

### SseController — `com.brokerdealer.notificationservice.controller.SseController`

| Method | Path | Handler | Purpose |
|---|---|---|---|
| GET | `/api/v1/notifications/stream` | `SseController.stream` | Opens a long-lived SSE stream for the authenticated user. Returns `text/event-stream`. On connect, immediately sends a `connected` event with `{"userId":"<uuid>","role":"<role>"}`. TRADER connections are registered in the per-user emitter map; OPERATIONS/ADMIN connections go into the broadcast list. |

**SSE event names emitted to clients:**

| Event name | Trigger |
|---|---|
| `connected` | Immediately on SSE connection establishment |
| `ORDER_STATE_CHANGED` | Kafka `order.state.changed` consumed |
| `EXECUTION_COMPLETED` | Kafka `execution.completed` consumed |
| `RISK_RESULT` | Kafka `risk.evaluation.result` consumed (REJECTED only) |
| (SSE comment `keepalive`) | Heartbeat scheduler, every 20 seconds |

**Actuator endpoints (no auth required):**

| Path | Notes |
|---|---|
| `GET /actuator/health` | Liveness/readiness probes enabled; `show-details: always` |
| `GET /actuator/info` | Service info |
| `GET /actuator/metrics` | Metrics |

## Data

### No database

This service has no database or persistence layer. All state is held in-process in `SseEmitterRegistry`.

### Kafka event DTOs (inbound)

**`OrderStateChangedEvent`** — `com.brokerdealer.notificationservice.dto.event.OrderStateChangedEvent`

| Field | Type | Notes |
|---|---|---|
| `orderId` | `UUID` | Required; event skipped if null |
| `clientId` | `UUID` | Fallback push target when `userId` is null |
| `userId` | `UUID` | Primary push target (the authenticated trader); nullable |
| `fromState` | `String` | Previous order state |
| `toState` | `String` | New order state |
| `triggeredBy` | `String` | Actor that caused the transition |
| `timestamp` | `String` | ISO-8601 timestamp string |

**`ExecutionCompletedEvent`** — `com.brokerdealer.notificationservice.dto.event.ExecutionCompletedEvent`

| Field | Type | Notes |
|---|---|---|
| `executionId` | `UUID` | Execution record identifier |
| `orderId` | `UUID` | Required; event skipped if null |
| `clientId` | `UUID` | Required; event skipped if null; push target |
| `symbol` | `String` | Instrument symbol, e.g. "AAPL" |
| `side` | `String` | "BUY" or "SELL" |
| `executedQty` | `Integer` | Quantity filled in this execution |
| `executedPrice` | `String` | Price as string |
| `fillType` | `String` | "FULL" or "PARTIAL" |
| `remainingQuantity` | `Integer` | Quantity still open; included in message when PARTIAL |
| `executedAt` | `Instant` | Execution timestamp |

**`RiskEvaluationResultEvent`** — `com.brokerdealer.notificationservice.dto.event.RiskEvaluationResultEvent`

| Field | Type | Notes |
|---|---|---|
| `orderId` | `UUID` | Required; event skipped if null |
| `clientId` | `UUID` | Fallback push target when `userId` is null |
| `userId` | `UUID` | Primary push target; nullable |
| `result` | `String` | "PASSED" (silent drop) or "REJECTED" (triggers notification) |
| `ruleCode` | `String` | Code of the risk rule that triggered rejection |
| `reason` | `String` | Human-readable rejection reason |
| `timestamp` | `Instant` | Evaluation timestamp |

### Response DTO (outbound, pushed to SSE clients)

**`NotificationResponse`** — `com.brokerdealer.notificationservice.dto.response.NotificationResponse`

| Field | Type | Notes |
|---|---|---|
| `type` | `NotificationType` | Enum: `ORDER_STATE_CHANGED`, `EXECUTION_COMPLETED`, `RISK_RESULT` |
| `orderId` | `UUID` | Order identifier |
| `clientId` | `UUID` | Client identifier |
| `title` | `String` | Short notification title |
| `message` | `String` | Full notification message |
| `timestamp` | `Instant` | Time the notification was created (server clock) |

### Domain enum

**`NotificationType`** — `com.brokerdealer.notificationservice.domain.NotificationType`

Values: `ORDER_STATE_CHANGED`, `EXECUTION_COMPLETED`, `RISK_RESULT`

## Integrations

### Kafka topics consumed

| Topic | Consumer class | Consumer group ID | Listener factory |
|---|---|---|---|
| `order.state.changed` | `OrderStateConsumer` | `notification-service-order.state.changed-group` | `orderStateListenerContainerFactory` |
| `execution.completed` | `ExecutionConsumer` | `notification-service-execution.completed-group` | `executionListenerContainerFactory` |
| `risk.evaluation.result` | `RiskResultConsumer` | `notification-service-risk.evaluation.result-group` | `riskResultListenerContainerFactory` |

All three factories use `USE_TYPE_INFO_HEADERS=false` so that `__TypeId__` headers from producing
services (which carry foreign class names) are ignored and messages are deserialised directly to the
local DTO class.

### Kafka dead-letter topic (produced)

Failed messages (after 3 retry attempts with exponential back-off: initial 1 s, multiplier 2×) are
published to a dead-letter topic by `DeadLetterPublishingRecoverer`. The DLT topic name is
determined at runtime by Spring Kafka convention (typically `<source-topic>.DLT`).

### Outbound HTTP

None. This service makes no outbound HTTP calls.

### Caches / external services

None. No Redis, no external service calls.

## Configuration

### Environment variables

| Variable | Required | Default | Purpose |
|---|---|---|---|
| `JWT_SECRET` | Yes | none | HMAC-SHA secret for JWT signature verification. No default; service refuses to start without it. |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | No | `localhost:9092` | Kafka broker address(es). |

### application.yml keys

| Key | Value / default | Notes |
|---|---|---|
| `server.port` | `8088` | Fixed; not overridable by env var in current config. |
| `spring.application.name` | `notification-service` | Used in Kafka consumer group naming and actuator info. |
| `spring.threads.virtual.enabled` | `true` | Enables Java 21 virtual threads for all Spring-managed threads. |
| `spring.kafka.consumer.group-id` | `notification-service-group` | Application-level fallback; overridden per `@KafkaListener`. |
| `spring.kafka.consumer.auto-offset-reset` | `earliest` | Consumers start from the beginning of a partition if no committed offset exists. |
| `spring.kafka.consumer.properties.spring.json.trusted.packages` | `com.brokerdealer.*` | Allows JSON deserialiser to map to classes in that package. |
| `app.jwt.secret` | `${JWT_SECRET}` | Bound to `JwtAuthenticationFilter.jwtSecret` via `@Value`. |
| `management.endpoints.web.exposure.include` | `health,info,metrics` | Only these three actuator endpoints are exposed. |
| `management.endpoint.health.probes.enabled` | `true` | Enables `/actuator/health/liveness` and `/actuator/health/readiness` probes. |
| `management.endpoint.health.show-details` | `always` | Full health details returned without authentication. |
| `logging.level.com.brokerdealer` | `INFO` | Application-level logging. |
| `logging.level.org.springframework.kafka` | `WARN` | Kafka framework logging suppressed to WARN. |

## Tech debt

**TODO / FIXME count:** 0 — no TODO or FIXME comments found in any source file.

**Other debt observations:**

- **In-process SSE registry is not horizontally scalable.** `SseEmitterRegistry` uses a plain
  `ConcurrentHashMap` and `CopyOnWriteArrayList`. In a multi-instance deployment, events consumed by
  one instance cannot reach clients connected to another. A Redis Pub/Sub or shared message bus
  would be needed for multi-instance support.
  Location: `service/SseEmitterRegistry.java`

- **No TRADER-to-role validation after JWT parse.** The role is read directly from the JWT `role`
  claim and passed as-is to the registry. A malicious or misconfigured token with an arbitrary role
  value would be registered in the broadcast list (the else branch).
  Location: `service/SseEmitterRegistry.java` (register method)

- **One TRADER connection per userId.** `traderEmitters.put(userId, emitter)` silently replaces any
  prior emitter for the same userId (e.g. from a second browser tab). The old emitter is not
  completed or closed. Location: `service/SseEmitterRegistry.java`

## Open questions

- What is the expected DLT topic naming convention for this system? Spring Kafka defaults to
  `<source-topic>.DLT`; it is not explicitly set in `KafkaConfig`.
- Should RISK_RESULT PASSED events be silently dropped (current behaviour) or logged at INFO for
  audit purposes? The code logs at DEBUG only.
- Is a multi-instance deployment expected? If so, the in-process `SseEmitterRegistry` must be
  replaced with a distributed back-end.
<!-- plumbline:end reference -->
