<!-- plumbline:template claude-unit v1 -->
# api-gateway

<!-- plumbline:begin unit -->
Last updated: 4369a80 (2026-10-30, /pl-onboard, claude-unit v1)

## Purpose
The api-gateway is the single entry point for all Broker-Dealer OMS traffic. It runs on port 9090, validates JWTs on every inbound request (except `/api/v1/auth/login` and `/actuator/**`), checks revoked tokens against a Redis blacklist, injects identity headers (`X-User-Id`, `X-Username`, `X-User-Role`) for downstream services, and routes requests to the appropriate microservice via Spring Cloud Gateway MVC. It also handles SSE (Server-Sent Events) streaming from notification-service through a dedicated servlet-level proxy filter.

## Build & run
Build: `mvn clean package -DskipTests` (from `services/api-gateway/` or root with `-pl services/api-gateway`)
Run: `mvn spring-boot:run` or `java -jar target/api-gateway-*.jar`
Port: 9090 (set in `application.yml`)
Docker: `FROM amazoncorretto:21-alpine`; exposes port 9090 inside the container (matches `server.port` in `application.yml`).
Virtual threads: enabled (`spring.threads.virtual.enabled: true`).
Profiles: none defined; environment is driven by env vars (`JWT_SECRET`, `SPRING_REDIS_HOST`, `SPRING_REDIS_PORT`, `NOTIFICATION_SERVICE_URL`).

Test recipe: `mvn test` · Java 21 required (root pom.xml `<java.version>21</java.version>`) · Java 24 available (openjdk version "24") · FAILED — `Fatal error compiling: java.lang.ExceptionInInitializerError: com.sun.tools.javac.code.TypeTag :: UNKNOWN` — Lombok/MapStruct annotation processors are incompatible with Java 24; tests cannot run until Java 21 is used · 2026-10-30

## Architecture & structure
Stack: Java 21, Spring Boot 3.2.4, Spring Cloud Gateway MVC 2023.0.1 (servlet-based, NOT reactive/WebFlux), Spring Security, JJWT 0.12.3, Spring Data Redis, Lombok 1.18.30.
Build: Maven 3.9 (parent pom at repo root).

Layers and patterns:
- `config/` — Spring beans: `GatewayConfig` (SSE `ClientHttpRequestFactory`, no-timeout), `RedisConfig` (`RedisTemplate<String,Object>` with JSON serialization), `SecurityConfig` (filter chain, session-stateless, CSRF disabled).
- `filter/JwtValidationFilter` — `OncePerRequestFilter`; runs inside Spring Security filter chain; validates HS256 JWT, checks Redis blacklist (`auth:blacklist:<token>`), mutates the request to add identity headers via inner `MutableHttpServletRequest`.
- `filter/SseProxyFilter` — `OncePerRequestFilter` at `LOWEST_PRECEDENCE - 1`; intercepts `GET /api/v1/notifications/stream` after JWT validation; proxies the SSE byte stream directly to avoid Gateway MVC response buffering.
- Route configuration: purely declarative in `application.yml` under `spring.cloud.gateway.mvc.routes`; no Java route DSL.

Main packages:
- `com.brokerdealer.apigateway` — `ApiGatewayApplication` (`@EnableAsync`)
- `com.brokerdealer.apigateway.config` — three `@Configuration` classes
- `com.brokerdealer.apigateway.filter` — two `OncePerRequestFilter` implementations

## Security
Authentication: JWT Bearer token, HS256, signed with `JWT_SECRET` env var (required, no default). Parsed by `JwtValidationFilter` using JJWT 0.12.3. [code: filter/JwtValidationFilter.java]
Revocation: token blacklist checked in Redis under key `auth:blacklist:<raw-token>` before forwarding. [code: filter/JwtValidationFilter.java]
Public paths (bypass JWT): `/api/v1/auth/login` and `/actuator/**`. [code: filter/JwtValidationFilter.java, config/SecurityConfig.java]
Session: stateless (`SessionCreationPolicy.STATELESS`); no server-side session. [code: config/SecurityConfig.java]
CSRF: disabled (stateless API). [code: config/SecurityConfig.java]
Identity propagation: after successful validation, downstream headers `X-User-Id`, `X-Username`, `X-User-Role` are injected from JWT claims via `MutableHttpServletRequest`. Tokens are never stored by the gateway; Redis holds only blacklisted token strings. [code: filter/JwtValidationFilter.java]
Roles in JWT payload: `TRADER`, `OPERATIONS`, `ADMIN` (openapi.yaml documents this; role enforcement is in downstream services, not the gateway).

## Rules
- All requests except `/api/v1/auth/login` and `/actuator/**` must carry a valid, non-blacklisted Bearer JWT or the gateway returns HTTP 401. [code: filter/JwtValidationFilter.java]
- Error responses from JWT failures use the envelope `{"error":{"code","message","details":[]},"meta":{"timestamp","requestId"}}`. [code: filter/JwtValidationFilter.java]
- Downstream services receive identity via `X-User-Id`, `X-Username`, `X-User-Role` headers — never the raw `Authorization` header (except SSE proxy which re-forwards `Authorization`). [code: filter/JwtValidationFilter.java, filter/SseProxyFilter.java]
- SSE path `GET /api/v1/notifications/stream` is handled by `SseProxyFilter` and never reaches the Gateway MVC route handler. [code: filter/SseProxyFilter.java]
- Redis `read-timeout` is 2000 ms; SSE upstream `read-timeout` is 0 (infinite). [code: application.yml, config/GatewayConfig.java]
- JWT secret is injected exclusively from the `JWT_SECRET` environment variable; no fallback default. [code: filter/JwtValidationFilter.java]
- Virtual threads are enabled; do not introduce `ThreadLocal`-dependent middleware that is incompatible with virtual threads. [code: application.yml]

## Must never
- Do not add servlet-session state or enable Spring Security sessions; the gateway is stateless. [code: config/SecurityConfig.java]
- Do not add response body buffering for SSE routes; the `SseProxyFilter` exists specifically to bypass Gateway MVC buffering. [code: filter/SseProxyFilter.java]
- Do not hardcode `JWT_SECRET` in any config file or code; it must remain an env-var-only secret. [code: filter/JwtValidationFilter.java]
- Do not compile or run tests with Java 24; Lombok annotation processing fails with `ExceptionInInitializerError: TypeTag::UNKNOWN`. Use Java 21. [code: pom.xml (root)]
- Do not add role-based access control to the gateway; role enforcement belongs in downstream services. [code: config/SecurityConfig.java — `anyRequest().permitAll()` comment]

## Decisions & trade-offs
- Spring Cloud Gateway MVC (servlet stack) instead of the reactive Gateway: the `spring-cloud-starter-gateway-mvc` artifact is used, not `spring-cloud-starter-gateway`. This avoids WebFlux and keeps the stack consistent with the other services. Source: `pom.xml` dependency choice, Dockerfile base image.
- SSE handled at servlet-filter level (`SseProxyFilter`) rather than through Gateway MVC routing: Gateway MVC 4.1.x buffers all proxied responses, which breaks streaming. The `SseProxyFilter` at `LOWEST_PRECEDENCE - 1` intercepts the path before DispatcherServlet and streams bytes directly. Source: `SseProxyFilter.java` Javadoc comment (lines 27–40).
- `MutableHttpServletRequest` inner class: Servlet spec makes `HttpServletRequest` headers immutable; a wrapper is required to inject identity headers. Source: `JwtValidationFilter.java` comment (line 126–128).
- SSE `ClientHttpRequestFactory` uses `SimpleClientHttpRequestFactory` with `readTimeout = 0` (infinite) because SSE streams are unbounded. Source: `GatewayConfig.java` comment (line 19).
- Redis blacklist prefix `auth:blacklist:` is the shared convention with auth-service for token revocation. Source: `JwtValidationFilter.java` constant `BLACKLIST_PREFIX`.

## Reference
API — 11 gateway routes + 1 SSE proxy path; see CLAUDE.reference.md API section.
Data — Redis blacklist key schema only; no owned database. See CLAUDE.reference.md Data section.
Integrations — 10 downstream HTTP services + Redis. See CLAUDE.reference.md Integrations section.
Configuration — 6 config keys / env vars. See CLAUDE.reference.md Configuration section.
Tech debt — TODO/FIXME count and locations. See CLAUDE.reference.md Tech debt section.
Open questions — Dockerfile port mismatch and other gaps. See CLAUDE.reference.md Open questions section.

Open CLAUDE.reference.md only for the section your task needs.
<!-- plumbline:end unit -->
