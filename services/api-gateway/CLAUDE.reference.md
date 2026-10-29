<!-- plumbline:template claude-reference v1 -->
# API Gateway — reference

<!-- plumbline:begin reference -->
Last updated: 4369a80 (2026-10-28, /pl-onboard, claude-reference v1)

## API
The gateway itself exposes no REST controllers. All routes are reverse-proxy pass-throughs defined in
`src/main/resources/application.yml` under `spring.cloud.gateway.mvc.routes`, plus one raw-servlet
SSE path handled by SseProxyFilter.

### Spring Cloud Gateway MVC routes (application.yml)

| Route id                   | Predicate path prefix         | Upstream service              | Upstream port |
|----------------------------|-------------------------------|-------------------------------|---------------|
| auth-service               | /api/v1/auth/**               | http://auth-service           | 8081          |
| auth-service-users         | /api/v1/users/**              | http://auth-service           | 8081          |
| order-service              | /api/v1/orders/**             | http://order-service          | 8082          |
| order-service-dashboard    | /api/v1/dashboard/**          | http://order-service          | 8082          |
| client-service             | /api/v1/clients/**            | http://client-service         | 8083          |
| marketdata-service         | /api/v1/market/**             | http://marketdata-service     | 8084          |
| marketdata-service-watchlist | /api/v1/watchlists/**       | http://marketdata-service     | 8084          |
| risk-service               | /api/v1/risk/**               | http://risk-service           | 8085          |
| execution-service          | /api/v1/executions/**         | http://execution-service      | 8086          |
| audit-service              | /api/v1/audit/**              | http://audit-service          | 8087          |
| notification-service       | /api/v1/notifications/**      | http://notification-service   | 8088          |

### SSE path handled by SseProxyFilter (raw servlet, not Gateway MVC router)

GET /api/v1/notifications/stream — SseProxyFilter — proxies the SSE stream from notification-service
  (http://notification-service:8088/api/v1/notifications/stream) byte-by-byte with infinite read timeout;
  intercepted before DispatcherServlet to avoid response buffering.

### Management endpoints (Spring Actuator — no JWT required)

GET /actuator/health — Spring Boot Actuator — liveness/readiness probes (show-details: always)
GET /actuator/info   — Spring Boot Actuator — application info
GET /actuator/metrics — Spring Boot Actuator — metrics

### Auth bypass paths (no JWT required)

POST /api/v1/auth/login — pass-through to auth-service:8081 without JWT validation

## Data
The API Gateway owns no database schema and has no JDBC/JPA dependency.

### Redis key pattern (read-only — written by auth-service)

`auth:blacklist:<jwt-token-string>` — presence indicates the token has been revoked (logged out);
  the gateway calls `redisTemplate.hasKey(key)` after a successful JWT signature check.
  TTL is set by auth-service; the gateway does not set or extend it.

### No migrations
The gateway does not run Flyway, Liquibase, or any SQL migration. The PostgreSQL `init.sql` at
`docker/postgres/init.sql` creates schemas for other services only (auth_schema, order_schema,
client_schema, market_schema, risk_schema, execution_schema, audit_schema) — none for the gateway.

## Integrations

### Outbound HTTP — proxied (Spring Cloud Gateway MVC)
All downstream calls are transparent HTTP proxy passes initiated by the Spring Cloud Gateway MVC
DispatcherServlet. The gateway adds identity headers before forwarding (set by JwtValidationFilter).

| Upstream service        | Base URL (docker-compose)              | Path prefixes proxied                            |
|-------------------------|----------------------------------------|--------------------------------------------------|
| auth-service            | http://auth-service:8081               | /api/v1/auth/**, /api/v1/users/**                |
| order-service           | http://order-service:8082              | /api/v1/orders/**, /api/v1/dashboard/**          |
| client-service          | http://client-service:8083             | /api/v1/clients/**                               |
| marketdata-service      | http://marketdata-service:8084         | /api/v1/market/**, /api/v1/watchlists/**         |
| risk-service            | http://risk-service:8085               | /api/v1/risk/**                                  |
| execution-service       | http://execution-service:8086          | /api/v1/executions/**                            |
| audit-service           | http://audit-service:8087              | /api/v1/audit/**                                 |
| notification-service    | http://notification-service:8088 (MVC route); ${NOTIFICATION_SERVICE_URL:http://notification-service:8088} (SseProxyFilter) | /api/v1/notifications/** (MVC route); GET /api/v1/notifications/stream (SseProxyFilter — direct HTTP, bypasses MVC router) |

### Caches / data stores
- Redis 7 — used only for token blacklist lookups (read-only from the gateway's perspective).
  Connection: host `${SPRING_REDIS_HOST:localhost}`, port `${SPRING_REDIS_PORT:6379}`, timeout 2000 ms.
  RedisTemplate uses StringRedisSerializer for keys, GenericJackson2JsonRedisSerializer (with JavaTimeModule)
  for values.

### Messaging
None. The gateway does not produce or consume Kafka topics. SPRING_KAFKA_BOOTSTRAP_SERVERS is set in
docker-compose.yml for this service but no Kafka dependency or consumer/producer exists in the source code
(the env var is likely inherited from a shared compose block and is unused).

### External services
None beyond the downstream microservices listed above.

## Configuration

### application.yml keys

| Key | Default / value | Effect |
|-----|-----------------|--------|
| `server.port` | `9090` | Gateway HTTP port |
| `spring.application.name` | `api-gateway` | Spring application name |
| `spring.threads.virtual.enabled` | `true` | Enables Java 21 virtual threads platform-wide |
| `spring.data.redis.host` | `${SPRING_REDIS_HOST:localhost}` | Redis host |
| `spring.data.redis.port` | `${SPRING_REDIS_PORT:6379}` | Redis port |
| `spring.data.redis.timeout` | `2000ms` | Redis connection timeout |
| `notification.service.url` | `${NOTIFICATION_SERVICE_URL:http://notification-service:8088}` | Base URL for SseProxyFilter upstream |
| `notification.stream.connect-timeout-ms` | `5000` | HTTP connect timeout for SSE upstream (ms); read timeout is 0 (infinite) |
| `management.endpoints.web.exposure.include` | `health,info,metrics` | Actuator endpoints exposed |
| `management.endpoint.health.show-details` | `always` | Health endpoint verbosity |
| `management.endpoint.health.probes.enabled` | `true` | Liveness/readiness probe endpoints |
| `logging.level.com.brokerdealer` | `INFO` | Application log level |
| `logging.level.org.springframework.cloud.gateway` | `WARN` | Gateway routing log level (suppressed) |

### Environment variables (docker-compose.yml)

| Variable | Required | Purpose |
|----------|----------|---------|
| `JWT_SECRET` | Yes | HMAC-SHA signing key for JWT verification; must be at least 32 bytes; set via environment variable or secrets manager — never hardcode |
| `SPRING_REDIS_HOST` | Yes (in Docker) | Overrides `spring.data.redis.host` |
| `SPRING_REDIS_PORT` | No | Overrides `spring.data.redis.port` |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | No (unused by gateway) | Set in docker-compose; no Kafka code in gateway |
| `NOTIFICATION_SERVICE_URL` | No | Overrides SseProxyFilter upstream base URL |
| `SPRING_DATASOURCE_USERNAME` | No (unused by gateway) | Set in docker-compose; gateway has no datasource |
| `SPRING_DATASOURCE_PASSWORD` | No (unused by gateway) | Set in docker-compose; gateway has no datasource |

### Where secrets come from
JWT_SECRET is supplied as a docker-compose environment variable in development. The placeholder value in
docker-compose must be replaced in production deployments via a secrets manager or Kubernetes Secret.
No secret is baked into application.yml or the image.

## Tech debt
TODO/FIXME count: 0 — no TODO or FIXME comments found anywhere in `services/api-gateway/src/`.

Other observed debt:

1. `anyRequest().permitAll()` in SecurityConfig — Spring Security is configured to allow all requests through;
   actual enforcement is entirely delegated to JwtValidationFilter. If JwtValidationFilter were ever bypassed,
   no Spring Security fallback would block unauthenticated access. [config/SecurityConfig.java line 29]

2. SPRING_KAFKA_BOOTSTRAP_SERVERS and SPRING_DATASOURCE_* env vars are set for the gateway container in
   docker-compose.yml but the gateway has no Kafka or JDBC dependencies; these vars are dead configuration
   that could mislead future developers. [docker-compose.yml lines 107-111]

3. No integration tests — the two test classes (JwtValidationFilterTest, SseProxyFilterTest) use
   MockitoExtension and Spring's MockHttpServlet* utilities without a full application context. No
   `@SpringBootTest` or Testcontainers test exercises the full gateway stack (Redis, Spring Security chain,
   Gateway MVC routing) end-to-end.

4. Dockerfile EXPOSE 8080 is wrong — the container actually listens on 9090 (set in application.yml and
   docker-compose ports). [services/api-gateway/Dockerfile line 20]

## Open questions
1. Role-based access control: the gateway passes all valid JWTs regardless of the `role` claim. It is unclear
   from the code whether any downstream service enforces role boundaries, or whether that enforcement exists
   only in the frontend.

2. JWT_SECRET rotation strategy: no code handles key rotation (e.g., multiple active signing keys). A
   compromised secret requires a full re-deployment and invalidation of all outstanding tokens; the blacklist
   alone cannot mitigate that.

3. SPRING_KAFKA_BOOTSTRAP_SERVERS in gateway docker-compose: the gateway has no Kafka consumer or producer.
   It is unclear whether this variable was intended for a future feature (e.g., audit event publishing) or is
   leftover from copying a service template.

— Plumbline 2.8.7 · /pl-onboard · claude-reference v1 · commit 4369a80 · judge pending · AI-drafted, posted after SME review
<!-- plumbline:end reference -->
