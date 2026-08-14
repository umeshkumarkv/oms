# api-gateway -- Service Specification

## Purpose

The api-gateway is a Spring Cloud Gateway MVC (servlet-based) reverse proxy and security enforcement point for the broker-dealer OMS platform. It performs two responsibilities:

1. **JWT validation and header injection**: Every inbound HTTP request (except `/api/v1/auth/login` and `/actuator/**`) must carry a valid, non-expired, non-blacklisted JWT `Bearer` token. On success, the filter injects three downstream headers — `X-User-Id`, `X-Username`, `X-User-Role` — so backend services can trust identity without re-validating the token. (JwtValidationFilter.java:35-97)

2. **Request routing**: Validated requests are forwarded to the appropriate backend microservice based on path prefix, using Spring Cloud Gateway MVC route definitions in `application.yml`. One path (`GET /api/v1/notifications/stream`) is handled by a dedicated servlet filter (`SseProxyFilter`) that bypasses the MVC dispatcher to avoid response buffering and stream SSE events directly to the browser. (SseProxyFilter.java:44-111, application.yml:8-54)

The service has no database, no Kafka participation, no business logic, and no JPA entities.

## Port

`9090` (application.yml:63)

## Database Schema

None found in scan. No Flyway migrations (`V*.sql`) exist under this service. No `spring.flyway` configuration is present in `application.yml`.

## Kafka Produced

None found in scan. No `KafkaTemplate` or `kafkaTemplate.send` usage exists in the main source tree.

## Kafka Consumed

None found in scan. No `@KafkaListener` annotation exists in the main source tree.

## Redis Keys Owned

| Key Pattern | TTL | Purpose | Access Mode | File:Line |
|---|---|---|---|---|
| `auth:blacklist:{jwtToken}` | Not set by this service (read-only) | Blacklist check — if key exists, token is revoked and request is rejected with `TOKEN_REVOKED` | Read-only (`hasKey`) | JwtValidationFilter.java:38,82 |

Notes:
- The blacklist key prefix is declared as `private static final String BLACKLIST_PREFIX = "auth:blacklist:"` (JwtValidationFilter.java:38).
- Keys in this namespace are written by `auth-service` (the login/logout owner); api-gateway only reads them.
- No TTL is set or managed by this service. No `expire`, `Duration`, or `TimeUnit` calls exist in the main source.

## REST Endpoints Exposed

The api-gateway has no `@Controller` or `@*Mapping` annotated handlers. All inbound traffic is handled by two mechanisms:

### A. Spring Cloud Gateway MVC Route Table (application.yml:10-54)

Every route applies JWT validation (enforced by `JwtValidationFilter`) unless excluded by the two permit-all rules below.

| Route ID | Path Predicate | Upstream Host:Port | Auth Required |
|---|---|---|---|
| `auth-service` | `/api/v1/auth/**` | `http://auth-service:8081` | `/api/v1/auth/login` is permit-all; all other `/api/v1/auth/**` require valid JWT |
| `auth-service-users` | `/api/v1/users/**` | `http://auth-service:8081` | JWT required |
| `order-service` | `/api/v1/orders/**` | `http://order-service:8082` | JWT required |
| `order-service-dashboard` | `/api/v1/dashboard/**` | `http://order-service:8082` | JWT required |
| `client-service` | `/api/v1/clients/**` | `http://client-service:8083` | JWT required |
| `marketdata-service` | `/api/v1/market/**` | `http://marketdata-service:8084` | JWT required |
| `marketdata-service-watchlist` | `/api/v1/watchlists/**` | `http://marketdata-service:8084` | JWT required |
| `risk-service` | `/api/v1/risk/**` | `http://risk-service:8085` | JWT required |
| `execution-service` | `/api/v1/executions/**` | `http://execution-service:8086` | JWT required |
| `audit-service` | `/api/v1/audit/**` | `http://audit-service:8087` | JWT required |
| `notification-service` | `/api/v1/notifications/**` | `http://notification-service:8088` | JWT required |

Permit-all exceptions (SecurityConfig.java:27-28):
- `POST /api/v1/auth/login` — no JWT required
- `GET /actuator/**` — no JWT required

### B. SSE Proxy Path (SseProxyFilter.java:46, 64)

| Method | Path | Handler | Auth | File:Line |
|---|---|---|---|---|
| `GET` | `/api/v1/notifications/stream` | `SseProxyFilter` (servlet filter, bypasses MVC dispatcher) | JWT required (validated upstream by `JwtValidationFilter`) | SseProxyFilter.java:46,64 |

The SSE path is matched by `private static final String SSE_PATH = "/api/v1/notifications/stream"` (SseProxyFilter.java:46). The filter is ordered at `Ordered.LOWEST_PRECEDENCE - 1` to run after Spring Security but before `DispatcherServlet` (SseProxyFilter.java:42). It proxies to `${notification.service.url}/api/v1/notifications/stream` with a no-read-timeout `SimpleClientHttpRequestFactory` to support infinite SSE streams (GatewayConfig.java:19, SseProxyFilter.java:78-84).

## REST Endpoints Called

| Target Service | Method | URL / Path | Circuit Breaker | File:Line |
|---|---|---|---|---|
| `notification-service` | `GET` | `${notification.service.url}/api/v1/notifications/stream` (default: `http://notification-service:8088/api/v1/notifications/stream`) | None found in scan | SseProxyFilter.java:54,78-84 |

Note: All other backend calls are made by the Spring Cloud Gateway MVC framework internally based on `application.yml` route definitions. No explicit `RestTemplate`, `WebClient`, or `@FeignClient` declarations exist in the main source (grep returned no results). The `ClientHttpRequestFactory` (`SimpleClientHttpRequestFactory`) is used exclusively by `SseProxyFilter` for the SSE stream path. No Resilience4j or `@CircuitBreaker` annotations found.

## Entities

None found in scan. No `@Entity` annotated classes exist under this service's source tree.

## What This Service Must NEVER Do

1. **Must never own or write to any relational database schema.** [CONVENTION -- INFERRED, CONFIRM WITH TEAM] No Flyway configuration, no JPA/Hibernate dependency, and no `@Entity` or `@Repository` classes found. The service has no persistence layer.

2. **Must never publish to or consume from Kafka.** [CONVENTION -- INFERRED, CONFIRM WITH TEAM] No Kafka dependency, no `KafkaTemplate`, and no `@KafkaListener` found in the source tree. The gateway is a pure routing and security enforcement layer.

3. **Must never perform application-level business logic (order validation, risk checks, execution).** [CONVENTION -- INFERRED, CONFIRM WITH TEAM] No domain model, no service classes with business rules, and no calculation logic found. All domain processing belongs to backend microservices.

4. **Must never bypass JWT validation on paths outside the explicit permit-all list.** [ENFORCED] `JwtValidationFilter.doFilterInternal` (JwtValidationFilter.java:60-63) only skips JWT validation for paths starting with `/api/v1/auth/login` or `/actuator`. All other paths hit the JWT parsing and blacklist-check branch.

5. **Must never set a TTL on `auth:blacklist:*` keys.** [CONVENTION -- INFERRED, CONFIRM WITH TEAM] This service performs only a `hasKey` read on the blacklist namespace (JwtValidationFilter.java:82). TTL lifecycle is owned by `auth-service`.

6. **Must never write to the `auth:blacklist:*` Redis namespace.** [CONVENTION -- INFERRED, CONFIRM WITH TEAM] Only `redisTemplate.hasKey` is called against this namespace; no `opsForValue().set`, `expire`, or `delete` calls exist in the source.

## Key Business Rules

1. **JWT must be passed as `Bearer` prefix in the `Authorization` header.** The filter checks `authHeader.startsWith("Bearer ")` using the constant `BEARER_PREFIX = "Bearer "` (JwtValidationFilter.java:35, 67). Requests without this prefix receive HTTP 401 with error code `UNAUTHORIZED`.

2. **JWT signing algorithm is HMAC-SHA256 (HS256) with a secret injected via environment variable `${JWT_SECRET}`.** The signing key is built as `Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8))` (JwtValidationFilter.java:122). The secret is not hardcoded in production configuration.

3. **Three downstream identity headers are injected on every valid request:** `X-User-Id` (JWT claim `userId`), `X-Username` (JWT claim `username`), `X-User-Role` (JWT claim `role`) — defined as package-visible constants at JwtValidationFilter.java:40-42. Backend services may rely on these headers for authorization decisions.

4. **Blacklisted tokens are rejected before the request reaches downstream services.** After signature and expiry verification, the filter calls `redisTemplate.hasKey("auth:blacklist:" + token)` (JwtValidationFilter.java:82). If the key exists, HTTP 401 is returned with error code `TOKEN_REVOKED`.

5. **Error response envelope format is standardized.** All 401 responses from `JwtValidationFilter` follow the JSON shape `{"error":{"code":"...","message":"...","details":[]},"meta":{"timestamp":"...","requestId":"..."}}` (JwtValidationFilter.java:114-117), where `requestId` is a fresh `UUID.randomUUID()` per rejection.

6. **SSE stream path uses infinite read timeout.** `GatewayConfig` creates a `SimpleClientHttpRequestFactory` with `readTimeout = 0` (GatewayConfig.java:19) to prevent the proxy from closing long-lived SSE connections. The connect timeout defaults to 5000 ms (`${notification.stream.connect-timeout-ms:5000}`, GatewayConfig.java:12-13).

7. **Spring Security session policy is STATELESS.** `SessionCreationPolicy.STATELESS` is enforced in `SecurityConfig` (SecurityConfig.java:25). No server-side session state is maintained.

8. **Virtual threads are enabled.** `spring.threads.virtual.enabled: true` (application.yml:5-6), meaning the service uses JDK 21 virtual threads for all servlet request handling.

> **Reviewer note (per Wissen policy):** This spec is assistive, not authoritative. A qualified Wissen reviewer must validate it against the live codebase before use in client-facing deliverables.
