# notification-service -- Service Specification

## Purpose

Stateless real-time notification hub for the OMS broker-dealer platform. Consumes three Kafka topics produced by upstream services (order-service, execution-service, risk-service) and pushes corresponding Server-Sent Events (SSE) to connected browser clients. Traders receive targeted pushes keyed to their `userId`; OPERATIONS and ADMIN users receive all events via a broadcast channel. The service owns no persistent database and performs no synchronous REST calls to other services.

## Port

`8088` (application.yml:21)

## Database Schema

None found in scan. No `spring.datasource` or `spring.flyway` configuration is present in `application.yml`. No Flyway migration files (`V*.sql`) exist anywhere under the service tree. No JPA `@Entity` classes were found.

## Kafka Produced

None found in scan. A `KafkaTemplate<String, Object>` bean is defined in `KafkaConfig.java` and injected into the `DefaultErrorHandler`/`DeadLetterPublishingRecoverer` (KafkaConfig.java:109-112). This template is used exclusively for dead-letter routing on consumer errors; the service contains no `kafkaTemplate.send(...)` call in business logic.

DLQ note: on exhausting retries (3 attempts with exponential back-off starting at 1 000 ms, multiplier 2.0; KafkaConfig.java:110-112), failed messages are forwarded to the corresponding `.DLT` topic by `DeadLetterPublishingRecoverer`. The DLT topic names are inferred by Spring Kafka convention (topic + `.DLT`); they are not hard-coded in the source.

## Kafka Consumed

| Topic (exact literal) | Consumer Group | Container Factory | Event DTO | Action | File:line |
|---|---|---|---|---|---|
| `execution.completed` | `notification-service-execution.completed-group` | `executionListenerContainerFactory` | `ExecutionCompletedEvent` | Builds `NotificationResponse` (type=`EXECUTION_COMPLETED`); calls `pushToClient` + `broadcast` | ExecutionConsumer.java:21-26 |
| `order.state.changed` | `notification-service-order.state.changed-group` | `orderStateListenerContainerFactory` | `OrderStateChangedEvent` | Builds `NotificationResponse` (type=`ORDER_STATE_CHANGED`); calls `pushToClient` (userId fallback clientId) + `broadcast` | OrderStateConsumer.java:22-27 |
| `risk.evaluation.result` | `notification-service-risk.evaluation.result-group` | `riskResultListenerContainerFactory` | `RiskEvaluationResultEvent` | Skips events where `result == "PASSED"`; for rejected events builds `NotificationResponse` (type=`RISK_RESULT`); calls `pushToClient` + `broadcast` | RiskResultConsumer.java:22-27 |

Consumer deserialization: each topic uses a dedicated typed `ConsumerFactory` with `USE_TYPE_INFO_HEADERS=false` and `VALUE_DEFAULT_TYPE` set to the local DTO class (KafkaConfig.java:98-107). Trusted packages: `com.brokerdealer.*` (KafkaConfig.java:103).

### Consumed Event Payload Fields

**ExecutionCompletedEvent** (ExecutionCompletedEvent.java):
- `executionId`: `UUID`
- `orderId`: `UUID`
- `clientId`: `UUID`
- `symbol`: `String`
- `side`: `String`
- `executedQty`: `Integer`
- `executedPrice`: `String`
- `fillType`: `String` — distinguishes `"FULL"` vs partial fills (ExecutionConsumer.java:37)
- `remainingQuantity`: `Integer`
- `executedAt`: `Instant`

**OrderStateChangedEvent** (OrderStateChangedEvent.java):
- `orderId`: `UUID`
- `clientId`: `UUID`
- `userId`: `UUID` — auth user who owns the order; preferred push target over `clientId` (OrderStateConsumer.java:47)
- `fromState`: `String`
- `toState`: `String`
- `triggeredBy`: `String`
- `timestamp`: `String`

**RiskEvaluationResultEvent** (RiskEvaluationResultEvent.java):
- `orderId`: `UUID`
- `clientId`: `UUID`
- `userId`: `UUID` — preferred push target (RiskResultConsumer.java:51)
- `result`: `String` — `"PASSED"` events are silently dropped (RiskResultConsumer.java:33-36)
- `ruleCode`: `String`
- `reason`: `String`
- `timestamp`: `Instant`

## Redis Keys Owned

None found in scan. No `@Cacheable`, `@CachePut`, `@CacheEvict`, `redisTemplate`, or `StringRedisTemplate` usage exists in the main source tree.

## REST Endpoints Exposed

| Method | Path | Auth / Role | Handler | File:line |
|---|---|---|---|---|
| GET | `/api/v1/notifications/stream` | Any authenticated user (JWT required; `/actuator/**` is public) | `SseController#stream` — opens long-lived SSE connection; TRADER role keyed to `userId` in `traderEmitters`; OPERATIONS/ADMIN added to `broadcastEmitters` | SseController.java:30-31 |

Notes:
- `produces = MediaType.TEXT_EVENT_STREAM_VALUE` (SseController.java:30)
- `SseEmitter` timeout is `Long.MAX_VALUE` (SseController.java:38) — effectively infinite; proxy-level idle timeouts are mitigated by a scheduled keepalive heartbeat every 20 seconds (SseEmitterRegistry.java:94).
- Authentication is stateless JWT; session policy is `STATELESS` (SecurityConfig.java:29).
- JWT claims parsed: `userId` (String/UUID) and `role` (String) from `app.jwt.secret` (env var `${JWT_SECRET}`; JwtAuthenticationFilter.java:43-49).
- No `@PreAuthorize` role restrictions on the stream endpoint itself; role-based routing is handled in application logic (SseEmitterRegistry.java:26).

## REST Endpoints Called

None found in scan. No `RestTemplate`, `WebClient`, or `@FeignClient` usage exists anywhere in the main source tree.

## Entities

None found in scan. No classes annotated with `@Entity` exist in the service.

## What This Service Must NEVER Do

1. **Must never write to a relational database or own a database schema.** [CONVENTION -- INFERRED, CONFIRM WITH TEAM] No `spring.datasource`, `spring.jpa`, or `spring.flyway` configuration is present, and no JPA entities exist. This is absence of code, not an active enforcement.

2. **Must never call another microservice synchronously over HTTP.** [CONVENTION -- INFERRED, CONFIRM WITH TEAM] No `RestTemplate`, `WebClient`, or `@FeignClient` usage was found. This is absence of code, not an active enforcement (e.g. no compile-time guard).

3. **Must never produce business Kafka events (only DLQ routing).** [CONVENTION -- INFERRED, CONFIRM WITH TEAM] The `KafkaTemplate` bean exists solely in the error handler path (KafkaConfig.java:109-112). No `kafkaTemplate.send(...)` call exists in any business logic class. Absence of code only.

4. **Must never store notification history or user state.** [CONVENTION -- INFERRED, CONFIRM WITH TEAM] The `SseEmitterRegistry` holds in-memory JVM maps (`ConcurrentHashMap`, `CopyOnWriteArrayList`; SseEmitterRegistry.java:17-18) with no persistence. All state is lost on restart. Absence of persistence code, not active enforcement.

5. **Must never cache data in Redis or any external cache.** [CONVENTION -- INFERRED, CONFIRM WITH TEAM] No caching annotations or cache client dependencies were found in the main source.

## Key Business Rules

1. **RISK_RESULT events with `result == "PASSED"` are silently dropped** and do not generate a push notification. Only rejected (non-PASSED) risk results produce a notification. (RiskResultConsumer.java:33-36 — `if ("PASSED".equals(event.getResult())) { return; }`)

2. **Events missing mandatory IDs are skipped.** `ExecutionConsumer` skips events where `clientId` or `orderId` is null (ExecutionConsumer.java:27-29). `OrderStateConsumer` skips events where `orderId` is null (OrderStateConsumer.java:28-30). `RiskResultConsumer` skips events where `orderId` is null (RiskResultConsumer.java:28-30).

3. **Push target resolution: `userId` is preferred over `clientId` for targeted pushes.** In `OrderStateConsumer` and `RiskResultConsumer`, if `event.getUserId()` is non-null it is used as the SSE push target; otherwise `event.getClientId()` is used as fallback (OrderStateConsumer.java:47, RiskResultConsumer.java:51).

4. **TRADER role receives targeted (unicast) pushes; OPERATIONS and ADMIN receive broadcast.** Routing is applied at both registration (`SseEmitterRegistry#register`, SseEmitterRegistry.java:26) and delivery (`NotificationService#pushToClient` vs `#broadcast`, NotificationService.java:21-38). `"EXECUTION_COMPLETED"` notifications additionally call both `pushToClient` AND `broadcast` regardless of role (ExecutionConsumer.java:57-58).

5. **`NotificationType` enum values are `ORDER_STATE_CHANGED`, `EXECUTION_COMPLETED`, `RISK_RESULT`** and these values are used verbatim as SSE event names (`notification.getType().name()`; NotificationService.java:24, 35). (NotificationType.java:3-7)

6. **Kafka consumer error handling uses exponential back-off with 3 max attempts**, starting at 1 000 ms with a multiplier of 2.0 before dead-letter routing (KafkaConfig.java:110-112).

7. **SSE keepalive heartbeats are sent every 20 seconds** to all active trader and broadcast emitters to prevent proxy/load-balancer idle-connection timeouts (SseEmitterRegistry.java:94 — `@Scheduled(fixedRate = 20_000)`).
