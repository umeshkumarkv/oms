# CLAUDE.md — api-gateway
# Part of Broker Dealer OMS POC
# Read root CLAUDE.md first. This file adds service-specific detail.

---

## 1. Service Purpose

The api-gateway is the single ingress point for all external traffic into the Broker Dealer OMS.
It performs JWT validation on every inbound request (except /api/v1/auth/login and /actuator/**)
and routes requests to the correct downstream microservice using Spring Cloud Gateway. It must never
contain any business logic. Role-based access control is enforced here at the route level as a
first-pass filter; services also enforce roles via @PreAuthorize as defence in depth.

---

## 2. Port

8080

---

## 3. Database Schema

None — the api-gateway has no database. It is a stateless routing and filter layer.

---

## 4. Kafka Topics Produced

None — api-gateway does not publish to any Kafka topic.

---

## 5. Kafka Topics Consumed

None — api-gateway does not consume any Kafka topic.

---

## 6. Redis Cache Keys Owned

None — api-gateway does not own any Redis keys.
It may read auth:blacklist:{token} from Redis to check token validity (future implementation).

---

## 7. REST Endpoints Exposed

The gateway does not expose its own business endpoints. It proxies all routes.

| Predicate Path        | Upstream Service     | Port | JWT Required |
|-----------------------|----------------------|------|--------------|
| /api/v1/auth/**       | auth-service         | 8081 | No (login)   |
| /api/v1/users/**      | auth-service         | 8081 | Yes          |
| /api/v1/orders/**     | order-service        | 8082 | Yes          |
| /api/v1/dashboard/**  | order-service        | 8082 | Yes          |
| /api/v1/clients/**    | client-service       | 8083 | Yes          |
| /api/v1/market/**     | marketdata-service   | 8084 | Yes          |
| /api/v1/risk/**       | risk-service         | 8085 | Yes          |
| /api/v1/executions/** | execution-service    | 8086 | Yes          |
| /api/v1/audit/**      | audit-service        | 8087 | Yes          |
| /api/v1/notifications/stream | notification-service | 8088 | Yes (SSE — see note) |
| /api/v1/notifications/** | notification-service | 8088 | Yes         |
| /actuator/**          | gateway self         | 8080 | No           |

**SSE routing note**: `GET /api/v1/notifications/stream` is handled by `SseProxyFilter`
(not Spring Cloud Gateway MVC routing) because Gateway MVC 4.1.x buffers response bodies
before forwarding, which is incompatible with infinite SSE streams. `SseProxyFilter` runs at
`Ordered.LOWEST_PRECEDENCE - 1` (after Spring Security / JwtValidationFilter, before
DispatcherServlet) and pipes bytes directly to `HttpServletResponse.getOutputStream()`.

See openapi.yaml for full routing contract and auth requirements.

---

## 8. REST Endpoints Called (Downstream Dependencies)

The gateway routes to all 7 services. It does NOT call them directly — it proxies via Spring Cloud Gateway routes. In future, it will call auth-service to validate JWT blacklist via Redis.

---

## 9. Entities Owned

None — the gateway has no domain entities or database tables.

---

## 10. What This Service Must NEVER Do

- Never contain business logic of any kind
- Never read or write any service's database schema
- Never publish or consume Kafka messages
- Never issue JWT tokens — that is auth-service's responsibility
- Never modify request bodies — route and filter only
- Never expose internal service URLs to clients
- Never skip JWT validation for protected routes

---

## 11. Key Business Rules

- JWT validation must run as a OncePerRequestFilter with order -1 (highest priority)
- Login endpoint (/api/v1/auth/login) is the only path that bypasses JWT check
- Actuator endpoints bypass JWT check (internal health probes only)
- JWT algorithm: HS256 — secret from JWT_SECRET environment variable
- On invalid/missing JWT: return 401 with standard error envelope
- On blacklisted token: return 401 (future — reads auth:blacklist:{token} from Redis)
- Role enforcement is a future story — currently all valid JWTs are passed through
- All downstream service URLs use Docker/k8s service names, not localhost
- Spring Cloud Gateway MVC (servlet stack) with Java 21 virtual threads — do NOT introduce WebFlux/reactive code
- Virtual threads enabled via spring.threads.virtual.enabled=true — all servlet threads are virtual
- Filters extend OncePerRequestFilter (servlet) — not GlobalFilter (reactive)
- SseProxyFilter (LOWEST_PRECEDENCE - 1) handles GET /api/v1/notifications/stream:
  bypasses Gateway MVC response buffering by writing directly to HttpServletResponse
- GatewayConfig provides `sseRequestFactory` bean (SimpleClientHttpRequestFactory,
  connectTimeout=5000ms, readTimeout=0) for the SSE upstream connection