<!-- plumbline:template claude-reference v1 -->
# api-gateway — reference

<!-- plumbline:begin reference -->
Last updated: 4369a80 (2026-10-30, /pl-onboard, claude-reference v1)

## API
The gateway defines no REST controllers of its own. All paths are transparent proxy routes. The table below lists every route from `application.yml` plus the SSE path handled by `SseProxyFilter`.

### Gateway MVC routes (application.yml `spring.cloud.gateway.mvc.routes`)

| Route id | Path predicate | Upstream | JWT required |
|---|---|---|---|
| auth-service | `/api/v1/auth/**` | `http://auth-service:8081` | No (login path is public; other auth paths do require JWT via JwtValidationFilter) |
| auth-service-users | `/api/v1/users/**` | `http://auth-service:8081` | Yes |
| order-service | `/api/v1/orders/**` | `http://order-service:8082` | Yes |
| order-service-dashboard | `/api/v1/dashboard/**` | `http://order-service:8082` | Yes |
| client-service | `/api/v1/clients/**` | `http://client-service:8083` | Yes |
| marketdata-service | `/api/v1/market/**` | `http://marketdata-service:8084` | Yes |
| marketdata-service-watchlist | `/api/v1/watchlists/**` | `http://marketdata-service:8084` | Yes |
| risk-service | `/api/v1/risk/**` | `http://risk-service:8085` | Yes |
| execution-service | `/api/v1/executions/**` | `http://execution-service:8086` | Yes |
| audit-service | `/api/v1/audit/**` | `http://audit-service:8087` | Yes |
| notification-service | `/api/v1/notifications/**` | `http://notification-service:8088` | Yes |

Notes:
- The route predicates use path-prefix matching only; no filters (header manipulation, rate-limiting, circuit-breaker) are configured in application.yml.
- `JwtValidationFilter` exempts only `/api/v1/auth/login` (exact prefix) and `/actuator` (prefix); all other paths under `/api/v1/auth/**` still require a valid JWT.

### SSE proxy path (SseProxyFilter — servlet filter, bypasses Gateway MVC route handler)

| Path | Method | Handler | Purpose |
|---|---|---|---|
| `/api/v1/notifications/stream` | GET | `SseProxyFilter.doFilterInternal` | Streams SSE events from notification-service to the browser; avoids Gateway MVC response buffering |

The SSE proxy intercepts this path at filter order `LOWEST_PRECEDENCE - 1`, after JWT validation, and before DispatcherServlet. The Authorization header is re-forwarded as-is to notification-service.

### Actuator endpoints (public, no JWT required)

| Path | Purpose |
|---|---|
| `/actuator/health` | Liveness / readiness probe (probes enabled, show-details: always) |
| `/actuator/info` | Application info |
| `/actuator/metrics` | Metrics |

## Data
The gateway owns no database and defines no entities or migrations.

### Redis blacklist (read-only access from the gateway's perspective)

| Key pattern | Value | Purpose |
|---|---|---|
| `auth:blacklist:<raw-jwt-token>` | Any truthy value | Revoked token marker written by auth-service; gateway checks with `redisTemplate.hasKey(...)` before forwarding |

Redis connection: `spring.data.redis.host` / `spring.data.redis.port` (defaults `localhost:6379`), read timeout 2000 ms.
Key serializer: `StringRedisSerializer`; value serializer: `GenericJackson2JsonRedisSerializer` with `JavaTimeModule` (dates as ISO strings).

## Integrations

### Downstream HTTP services (via Spring Cloud Gateway MVC proxy)

| Service | Host (Docker) | Port | Path prefix forwarded |
|---|---|---|---|
| auth-service | `auth-service` | 8081 | `/api/v1/auth/**`, `/api/v1/users/**` |
| order-service | `order-service` | 8082 | `/api/v1/orders/**`, `/api/v1/dashboard/**` |
| client-service | `client-service` | 8083 | `/api/v1/clients/**` |
| marketdata-service | `marketdata-service` | 8084 | `/api/v1/market/**`, `/api/v1/watchlists/**` |
| risk-service | `risk-service` | 8085 | `/api/v1/risk/**` |
| execution-service | `execution-service` | 8086 | `/api/v1/executions/**` |
| audit-service | `audit-service` | 8087 | `/api/v1/audit/**` |
| notification-service | `notification-service` | 8088 | `/api/v1/notifications/**` (route) and `/api/v1/notifications/stream` (SSE proxy direct) |

All upstream URIs are hard-coded as Docker service names in `application.yml`; `notification-service` URL is also overridable via `NOTIFICATION_SERVICE_URL` env var.

### SSE upstream connection

- Factory: `SimpleClientHttpRequestFactory` (bean `sseRequestFactory` in `GatewayConfig`)
- Connect timeout: `notification.stream.connect-timeout-ms` (default 5000 ms)
- Read timeout: 0 (infinite — SSE streams are unbounded)
- Call site: `SseProxyFilter` opens a direct `GET http://notification-service:8088/api/v1/notifications/stream`

### Redis

- Purpose: JWT blacklist lookup only
- Client: Spring Data Redis (`RedisTemplate<String, Object>`)
- No caching or session storage

### Messaging / events

None — the gateway does not produce or consume any message topics.

## Configuration

| Key | Source | Default | Purpose |
|---|---|---|---|
| `JWT_SECRET` | Environment variable | **None — startup fails without it** | HMAC-SHA256 signing key for JWT validation (`JwtValidationFilter`) |
| `SPRING_REDIS_HOST` | Environment variable | `localhost` | Redis host |
| `SPRING_REDIS_PORT` | Environment variable | `6379` | Redis port |
| `NOTIFICATION_SERVICE_URL` | Environment variable | `http://notification-service:8088` | Base URL for the SSE proxy target |
| `notification.stream.connect-timeout-ms` | `application.yml` / env | `5000` | SSE upstream connect timeout in ms (`GatewayConfig`) |
| `spring.data.redis.timeout` | `application.yml` | `2000ms` | Redis command timeout |

Actuator endpoints exposed: `health`, `info`, `metrics` (set in `application.yml`; no separate env override).
Logging: `com.brokerdealer` at INFO, `org.springframework.cloud.gateway` at WARN (application.yml).
Virtual threads: always on (`spring.threads.virtual.enabled: true`); not configurable per environment.

No secrets are stored in config files. `JWT_SECRET` must be injected as an environment variable; there is no fallback.

## Tech debt

TODO/FIXME count: **0** — no TODO, FIXME, HACK, or XXX comments found in any source file under `src/`.

Other debt:

1. ~~**Dockerfile port mismatch**~~ — Fixed: `EXPOSE` updated to 9090 in Dockerfile (decided 2026-10-30).
2. **No retry or circuit-breaker on Gateway MVC routes** — upstream failures pass through to the client as raw errors; no Resilience4j or retry filter is configured in `application.yml`.
3. **No rate limiting** — the gateway has no request-rate-limiting filter; all route predicates are path-only.
4. **No request-ID propagation** — error responses generate a `requestId` UUID locally (`JwtValidationFilter`) but there is no correlation-ID header forwarded to downstream services for distributed tracing.
5. **`SecurityConfig.anyRequest().permitAll()`** — Spring Security permits all requests at the framework level; actual enforcement is entirely in `JwtValidationFilter`. A misconfiguration or filter ordering bug could silently bypass auth. [config/SecurityConfig.java line 29]
6. **SSE route duplication** — `/api/v1/notifications/**` exists as both a Spring Cloud Gateway MVC route (application.yml) and a special-cased path in `SseProxyFilter`. The Gateway MVC route would also match `/api/v1/notifications/stream` for non-SSE methods (e.g. POST), but GET is intercepted by `SseProxyFilter` before it reaches the route handler.
7. **No health check for upstream services** — Actuator health only reflects the gateway process itself; upstream service availability is not monitored at the gateway level.

## Open questions

1. ~~**Dockerfile EXPOSE 8080 vs. application port 9090**~~ — Resolved: Dockerfile updated to `EXPOSE 9090` (decided 2026-10-30).
2. **JWT_SECRET sharing convention** — The gateway and auth-service must share the same `JWT_SECRET`. How this is distributed in production (secrets manager, Kubernetes secret, Vault) is not documented in this service.
3. **Redis blacklist TTL** — The gateway only checks for key existence; the TTL policy for blacklisted tokens is set by auth-service and is unknown from this service's code alone.
4. **`/api/v1/auth/**` JWT requirement** — Only `/api/v1/auth/login` is explicitly public. The route predicate covers the entire `/api/v1/auth/**` tree, but `JwtValidationFilter` will block non-login auth paths without a token. Whether auth-service has other public endpoints (e.g. `/api/v1/auth/refresh`) is not visible here.
5. **`@EnableAsync` usage** — `ApiGatewayApplication` enables async processing but no `@Async` methods appear in the source. The annotation may be unused or intended for future work.
6. **openapi.yaml server URL** — `servers[0].url` is `http://localhost:8080` but the gateway runs on port 9090. The spec URL is incorrect.
<!-- plumbline:end reference -->
