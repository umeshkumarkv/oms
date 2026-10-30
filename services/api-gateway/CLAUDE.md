<!-- plumbline:template claude-unit v1 -->
# api-gateway

<!-- plumbline:begin unit -->
Last updated: 4586741 (2026-10-30, /pl-onboard, claude-unit v1)

## Purpose
The api-gateway is the single entry point for all Broker-Dealer OMS traffic on port 9090. It validates JWTs on every inbound request (except `/api/v1/auth/login` and `/actuator/**`), checks revoked tokens against a Redis blacklist, injects identity headers (`X-User-Id`, `X-Username`, `X-User-Role`) for downstream services, and routes to microservices via Spring Cloud Gateway MVC. SSE streaming from notification-service is handled by a dedicated servlet-level proxy filter.

## Build & run
Build: `mvn clean package -DskipTests` (from `services/api-gateway/` or root with `-pl services/api-gateway`)
Run: `mvn spring-boot:run` or `java -jar target/api-gateway-*.jar`
Port: 9090 (`application.yml`). Docker: `EXPOSE 8080` in Dockerfile (mismatch — service listens on 9090; Dockerfile not fixed).
Profiles: none; env vars drive config: `JWT_SECRET`, `SPRING_REDIS_HOST`, `SPRING_REDIS_PORT`, `NOTIFICATION_SERVICE_URL`.
Virtual threads: enabled (`spring.threads.virtual.enabled: true`).

Test recipe: `mvn test` · Java 21 required (`pom.xml`) · FAILED — `ExceptionInInitializerError: TypeTag::UNKNOWN` — Lombok/MapStruct incompatible with Java 24; use Java 21 · 2026-10-30

## Architecture & structure
Stack: Java 21, Spring Boot 3.2.4, Spring Cloud Gateway MVC 2023.0.1 (servlet, not WebFlux), Spring Security, JJWT 0.12.3, Spring Data Redis, Lombok 1.18.30. Build: Maven (parent pom at repo root).

Layers:
- `config/` — `GatewayConfig` (SSE `ClientHttpRequestFactory`, infinite read-timeout), `RedisConfig` (`RedisTemplate<String,Object>` JSON), `SecurityConfig` (stateless, CSRF disabled).
- `filter/JwtValidationFilter` — `OncePerRequestFilter`; validates HS256 JWT, checks Redis blacklist, injects identity headers via `MutableHttpServletRequest` inner class.
- `filter/SseProxyFilter` — `OncePerRequestFilter` at `LOWEST_PRECEDENCE - 1`; intercepts `GET /api/v1/notifications/stream`, streams bytes directly to avoid Gateway MVC buffering.
- Routes: declarative in `application.yml` (`spring.cloud.gateway.mvc.routes`); no Java DSL.

Packages: `com.brokerdealer.apigateway` (main), `.config` (3 configs), `.filter` (2 filters).

## Security
- JWT Bearer HS256 validated by `JwtValidationFilter`; secret from `JWT_SECRET` env var (no default). [code: filter/JwtValidationFilter.java]
- Redis blacklist key `auth:blacklist:<token>` checked before forwarding. [code: filter/JwtValidationFilter.java]
- Public paths (no JWT required): `/api/v1/auth/login`, `/actuator/**`. [code: filter/JwtValidationFilter.java, config/SecurityConfig.java]
- Session: stateless; CSRF disabled. [code: config/SecurityConfig.java]
- Identity headers `X-User-Id`, `X-Username`, `X-User-Role` injected from JWT claims; raw `Authorization` header not forwarded (SSE proxy re-forwards it). [code: filter/JwtValidationFilter.java]
- Role enforcement is in downstream services; gateway uses `anyRequest().permitAll()` after JWT check. [code: config/SecurityConfig.java]
- Reachable on port 9090; `/api/v1/auth/login` and `/actuator/**` bypass JWT — intentionally open, no additional network controls in dev.

## Rules
- All non-public requests require a valid, non-blacklisted Bearer JWT; gateway returns 401 otherwise. [code: filter/JwtValidationFilter.java]
- Error envelope: `{"error":{"code","message","details":[]},"meta":{"timestamp","requestId"}}`. [code: filter/JwtValidationFilter.java]
- SSE path `GET /api/v1/notifications/stream` is handled by `SseProxyFilter`; never reaches Gateway MVC route handler. [code: filter/SseProxyFilter.java]
- Redis `read-timeout` 2000 ms; SSE upstream `read-timeout` 0 (infinite). [code: application.yml, config/GatewayConfig.java]
- `JWT_SECRET` injected from env var only; no fallback. [code: filter/JwtValidationFilter.java]
- Virtual threads enabled; no `ThreadLocal`-incompatible middleware. [code: application.yml]

## Must never
- Do not enable Spring Security sessions; gateway is stateless. [code: config/SecurityConfig.java]
- Do not buffer SSE routes; `SseProxyFilter` bypasses Gateway MVC buffering. [code: filter/SseProxyFilter.java]
- Do not hardcode `JWT_SECRET` in source or config. [code: filter/JwtValidationFilter.java]
- Do not compile or test with Java 24; Lombok fails. Use Java 21. [code: pom.xml (root)]
- Do not add RBAC to the gateway; role enforcement belongs in downstream services. [code: config/SecurityConfig.java]

## Decisions & trade-offs
- Gateway MVC (servlet) over reactive Gateway: uses `spring-cloud-starter-gateway-mvc`, not `spring-cloud-starter-gateway`; keeps stack on servlet tier, avoids WebFlux. Source: `pom.xml`.
- SSE at servlet-filter level: Gateway MVC 4.1.x buffers all proxied responses; `SseProxyFilter` at `LOWEST_PRECEDENCE - 1` intercepts before DispatcherServlet and streams directly. Source: `SseProxyFilter.java` Javadoc (lines 27–40).
- `MutableHttpServletRequest` wrapper: Servlet spec forbids mutating request headers; wrapper required to inject identity headers. Source: `JwtValidationFilter.java` comment (lines 126–128).
- SSE `readTimeout = 0`: SSE streams are unbounded; infinite timeout required. Source: `GatewayConfig.java` comment (line 19).
- Redis blacklist prefix `auth:blacklist:`: shared convention with auth-service. Source: `JwtValidationFilter.java` constant `BLACKLIST_PREFIX`.

## Reference
API — 11 gateway routes + 1 SSE proxy path. See CLAUDE.reference.md API section.
Data — Redis blacklist key schema only; no owned database. See CLAUDE.reference.md Data section.
Integrations — 8 downstream HTTP services + Redis. See CLAUDE.reference.md Integrations section.
Configuration — 6 config keys / env vars. See CLAUDE.reference.md Configuration section.
Tech debt — TODO/FIXME count and locations. See CLAUDE.reference.md Tech debt section.
Open questions — Dockerfile port mismatch and other gaps. See CLAUDE.reference.md Open questions section.

Open CLAUDE.reference.md only for the section your task needs.
<!-- plumbline:end unit -->
