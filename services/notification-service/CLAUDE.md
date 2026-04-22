# CLAUDE.md — notification-service
# Part of Broker Dealer OMS POC
# Read root CLAUDE.md first. This file adds service-specific detail.

---

## 1. Service Purpose

The notification-service is the real-time push layer for the Broker Dealer OMS UI. It maintains
persistent WebSocket connections with browser clients using STOMP over WebSocket (with SockJS
fallback). It consumes Kafka events from order.state.changed, execution.completed, and
risk.evaluation.result topics and pushes targeted notifications to connected UI clients.

This service has no database. It is stateless — it does not persist notifications.
It is a pure fan-out layer: Kafka event in → WebSocket push out.

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
| order.state.changed      | notification-service-order.state.changed-group       | Push to /topic/notifications/{clientId}     |
| execution.completed      | notification-service-execution.completed-group       | Push to /topic/notifications/{clientId}     |
| risk.evaluation.result   | notification-service-risk.evaluation.result-group    | Push to /topic/notifications/{clientId}     |

---

## 6. Redis Cache Keys Owned

None — notification-service does not use Redis.

---

## 7. WebSocket Endpoints

| Endpoint    | Protocol        | Description                                 |
|-------------|-----------------|---------------------------------------------|
| /ws         | STOMP/WebSocket | SockJS-enabled STOMP endpoint for UI clients|

### STOMP Destination Prefixes

| Prefix  | Purpose                                      |
|---------|----------------------------------------------|
| /app    | Client-to-server messages (not used in POC)  |
| /topic  | Server-to-client broadcast destinations      |

### STOMP Subscription Destinations

| Destination                        | Audience         | Payload                          |
|------------------------------------|------------------|----------------------------------|
| /topic/notifications/{clientId}    | TRADER           | Order state changes, fills, risk |
| /topic/notifications/broadcast     | OPERATIONS/ADMIN | System-wide events               |

### Authentication

JWT token is passed as the `Authorization` header on the STOMP CONNECT frame.
The WebSocketAuthInterceptor validates the token on CONNECT and rejects with
a STOMP ERROR frame if the token is missing or invalid.

---

## 8. REST Endpoints Exposed

| Method | Path          | Auth Required | Description              |
|--------|---------------|---------------|--------------------------|
| GET    | /actuator/**  | No            | Health probes only       |

No business REST endpoints — all real-time delivery is via WebSocket.

---

## 9. REST Endpoints Called (Downstream Dependencies)

None — notification-service has no downstream REST dependencies.

---

## 10. Gateway and Connectivity

WebSocket connections bypass the api-gateway. The frontend connects directly to
notification-service on port 8088 for WebSocket. REST actuator endpoints are
not routed through the gateway.

Future story: Add WebSocket proxying through api-gateway.

---

## 11. Entities Owned

None — no database, no JPA entities.

### In-memory DTOs only

NotificationResponse (sent to UI):
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
- Never use WebFlux, Reactor, Mono, or Flux — WebSocket is servlet-based via spring-boot-starter-websocket
- Never expose client PII in notification payloads — send IDs and states only
- Never allow unauthenticated WebSocket connections — validate JWT on CONNECT

---

## 13. Key Design Rules

- Use Spring WebSocket with STOMP message broker (simple in-memory broker — no external broker)
- SockJS fallback enabled for browser compatibility
- STOMP endpoint: /ws
- Application destination prefix: /app
- Message broker prefix: /topic
- JWT validated on STOMP CONNECT frame via WebSocketAuthInterceptor (ChannelInterceptor)
- Route to /topic/notifications/{clientId} for TRADER-specific notifications
- Route to /topic/notifications/broadcast for OPERATIONS/ADMIN notifications
- Virtual threads: spring.threads.virtual.enabled=true — Tomcat servlet threads are virtual
- Do NOT use reactive WebSocket (WebSocketHandler from Spring WebFlux) — use servlet-based WebSocket