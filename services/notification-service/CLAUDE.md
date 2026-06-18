# CLAUDE.md — notification-service
# Part of Broker Dealer OMS POC
# Read root CLAUDE.md first. This file adds service-specific detail.

---

## 1. Service Purpose

The notification-service is the real-time push layer for the Broker Dealer OMS UI. It maintains
persistent Server-Sent Events (SSE) connections with browser clients using `SseEmitter`
(servlet-based, `spring-web`). It consumes Kafka events from order.state.changed,
execution.completed, and risk.evaluation.result topics and pushes targeted notifications
to connected UI clients.

This service has no database. It is stateless — it does not persist notifications.
It is a pure fan-out layer: Kafka event in → SSE push out.

---

## 2. Port

8088

---

## 3. Database Schema

None — notification-service has no database and no Flyway migrations.

---

## 4. Kafka Topics Produced

None — notification-service does not publish to any Kafka topic.

---

## 5. Kafka Topics Consumed

| Topic                    | Consumer Group ID                                    | Action on Receive                           |
|--------------------------|------------------------------------------------------|---------------------------------------------|
| order.state.changed      | notification-service-order.state.changed-group       | Push to TRADER SSE + broadcast to OPS/ADMIN |
| execution.completed      | notification-service-execution.completed-group       | Push to TRADER SSE + broadcast to OPS/ADMIN |
| risk.evaluation.result   | notification-service-risk.evaluation.result-group    | Push to TRADER SSE + broadcast to OPS/ADMIN |

---

## 6. Redis Cache Keys Owned

None — notification-service does not use Redis.

---

## 7. SSE Endpoints

| Endpoint                        | Method | Auth     | Description                                         |
|---------------------------------|--------|----------|-----------------------------------------------------|
| /api/v1/notifications/stream    | GET    | Bearer JWT | Long-lived SSE stream; produces text/event-stream |

Routed through api-gateway (`Path=/api/v1/notifications/**` → notification-service:8088).
JWT is validated by the api-gateway's `JwtValidationFilter` AND by notification-service's
own `JwtAuthenticationFilter` (`OncePerRequestFilter`) to extract userId and role.

### SSE Event Names

| Event name               | Sent to               | Triggered by                                  |
|--------------------------|-----------------------|-----------------------------------------------|
| ORDER_STATE_CHANGED      | TRADER (targeted)     | order.state.changed Kafka event               |
| EXECUTION_COMPLETED      | TRADER (targeted)     | execution.completed Kafka event               |
| RISK_EVALUATION_RESULT   | TRADER (targeted)     | risk.evaluation.result Kafka event            |
| ORDER_STATE_CHANGED      | OPERATIONS/ADMIN (broadcast) | order.state.changed Kafka event        |
| connected                | All                   | On SSE connection open                        |
| (SSE comment `:keepalive`) | All               | Every 20 s via @Scheduled heartbeat           |

### SseEmitterRegistry

- TRADER emitters: `ConcurrentHashMap<UUID, SseEmitter>` keyed by userId
- OPERATIONS/ADMIN emitters: `CopyOnWriteArrayList<SseEmitter>` (broadcast bucket)
- Emitter lifecycle: `onCompletion`, `onTimeout`, `onError` callbacks remove the emitter from the registry immediately
- Heartbeat: `@Scheduled(fixedRate=20_000)` sends `:keepalive` SSE comment to all active emitters

### Authentication

JWT token is passed as the `Authorization: Bearer {token}` HTTP header.
`JwtAuthenticationFilter` (`OncePerRequestFilter`) validates the token on every request to
`/api/v1/**` and sets `SecurityContextHolder` authentication with userId and role.
Returns 401 JSON (`{"error":{"code":"UNAUTHORIZED",...}}`) if token is missing or invalid.

---

## 8. REST Endpoints Exposed

| Method | Path                            | Auth Required | Description                          |
|--------|---------------------------------|---------------|--------------------------------------|
| GET    | /api/v1/notifications/stream    | Yes (JWT)     | Open SSE stream                      |
| GET    | /actuator/**                    | No            | Health probes only                   |

---

## 9. REST Endpoints Called (Downstream Dependencies)

None — notification-service has no downstream REST dependencies.

---

## 10. Gateway and Connectivity

SSE connections are routed through the api-gateway.

api-gateway route:
```yaml
- id: notification-service
  uri: http://notification-service:8088
  predicates:
    - Path=/api/v1/notifications/**
```

The gateway validates JWT via its own `JwtValidationFilter`. The notification-service also
validates JWT independently via `JwtAuthenticationFilter` for defence in depth and to extract
userId and role for SSE routing.

---

## 11. Entities Owned

None — no database, no JPA entities.

### In-memory DTOs only

NotificationResponse (sent to UI via SSE):
- type: NotificationType enum
- orderId: UUID (nullable)
- clientId: UUID (nullable)
- title: String
- message: String
- timestamp: Instant

---

## 12. What This Service Must NEVER Do

- Never persist notifications to any database
- Never call any other service synchronously
- Never publish to any Kafka topic
- Never use WebFlux, Reactor, Mono, or Flux — SSE is servlet-based via spring-boot-starter-web
- Never use spring-boot-starter-websocket — that dependency has been removed
- Never expose client PII in notification payloads — send IDs and states only
- Never allow unauthenticated SSE connections — validate JWT in JwtAuthenticationFilter
- Never use SseEmitter from Spring WebFlux — use org.springframework.web.servlet.mvc.method.annotation.SseEmitter

---

## 13. Key Design Rules

- Use SseEmitter (servlet-based, from spring-web) — no WebSocket/STOMP
- SseEmitterRegistry manages two buckets: TRADER (ConcurrentHashMap by userId) and broadcast (CopyOnWriteArrayList)
- JWT validated via JwtAuthenticationFilter (OncePerRequestFilter) on every /api/v1/** request
- SecurityConfig: STATELESS session, formLogin disabled, httpBasic disabled, custom 401 entry point
- Heartbeat: @Scheduled(fixedRate=20_000) in SseEmitterRegistry — sends SSE comment to all active emitters
- @EnableScheduling on NotificationServiceApplication
- Virtual threads: spring.threads.virtual.enabled=true — Tomcat servlet threads are virtual
- Do NOT use reactive WebSocket (WebSocketHandler from Spring WebFlux) — use servlet-based SseEmitter
