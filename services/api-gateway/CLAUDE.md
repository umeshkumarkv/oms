<!-- plumbline:template claude-unit v1 -->
# API Gateway

<!-- plumbline:begin unit -->
Last updated: 4369a80 (2026-10-28, /pl-onboard, claude-unit v1)

## Purpose
The API Gateway is the single ingress point for all client traffic in the Broker-Dealer OMS. It runs on port 9090 and reverse-proxies requests to nine downstream microservices using Spring Cloud Gateway MVC (servlet-based). Before forwarding any request it validates the caller's JWT, checks the token against a Redis blacklist, and injects identity headers (X-User-Id, X-Username, X-User-Role) for downstream services. Server-Sent Event (SSE) streams from notification-service are handled by a dedicated raw-servlet filter to avoid response buffering by the MVC dispatcher.

## Build & run
Build (from repo root): `mvn -pl services/api-gateway -am clean package`
Run standalone: `java -jar services/api-gateway/target/api-gateway-*.jar`
Docker (recommended): `docker-compose up api-gateway`
Port: 9090 (`server.port` in application.yml; `ports: "9090:9090"` in docker-compose.yml).
Profiles: default profile only; no Spring profile set in docker-compose.
Virtual threads enabled: `spring.threads.virtual.enabled: true`.
Infrastructure: Kafka must be healthy before the gateway starts (condition: service_healthy); Redis and Postgres use condition: service_started (docker-compose depends_on). The gateway holds no Postgres schema; it is listed as a dependency because the compose file declares it.

Test recipe: `mvn test` · requires Java 21 (build files set `<java.version>21</java.version>`) · **not run** — Java 24 installed locally is incompatible with maven-compiler-plugin 3.11.0; first error: `Fatal error compiling: java.lang.ExceptionInInitializerError: com.sun.tools.javac.code.TypeTag :: UNKNOWN` · 2026-10-28

## Architecture & structure
Stack: Java 21, Spring Boot 3.2.4, Spring Cloud Gateway MVC 2023.0.1, JJWT 0.12.3, Spring Data Redis (Lettuce), Spring Security 6, Lombok; Maven 3.9, fat JAR.

Filter chain order:
1. Spring Security FilterChainProxy (order -100) — permitAll at the Security level; all actual enforcement is in the filter below. [code: config/SecurityConfig.java]
2. JwtValidationFilter (OncePerRequestFilter) — verifies JWT, checks Redis blacklist, injects identity headers via MutableHttpServletRequest. [code: filter/JwtValidationFilter.java]
3. SseProxyFilter (OncePerRequestFilter, order LOWEST_PRECEDENCE - 1) — intercepts GET /api/v1/notifications/stream and proxies bytes directly to avoid MVC buffering. [code: filter/SseProxyFilter.java]
4. Spring Cloud Gateway MVC DispatcherServlet (LOWEST_PRECEDENCE) — routes remaining paths via application.yml route table.

Main packages:
- `com.brokerdealer.apigateway` — ApiGatewayApplication (@SpringBootApplication)
- `com.brokerdealer.apigateway.filter` — JwtValidationFilter, SseProxyFilter, MutableHttpServletRequest
- `com.brokerdealer.apigateway.config` — SecurityConfig, GatewayConfig (SSE factory), RedisConfig

## Security
Authentication: all requests except /api/v1/auth/login and /actuator/** require a Bearer JWT in the Authorization header; missing or invalid JWT returns 401. [code: filter/JwtValidationFilter.java]
Token validation: JJWT 0.12.3 verifies HMAC-SHA signature using JWT_SECRET; expired → 401 TOKEN_EXPIRED; malformed → 401 UNAUTHORIZED. [code: filter/JwtValidationFilter.java]
Token blacklist: after signature verification, `auth:blacklist:<token>` is looked up in Redis; a hit → 401 TOKEN_REVOKED. [code: filter/JwtValidationFilter.java]
Identity propagation: valid token causes gateway to inject X-User-Id, X-Username, X-User-Role into forwarded request. [code: filter/JwtValidationFilter.java]
Authorization (roles): gateway does not enforce RBAC; all valid-JWT requests pass through. Role enforcement is downstream. [code: config/SecurityConfig.java]
Session: stateless — no server-side session. CSRF disabled. [code: config/SecurityConfig.java]
JWT secret: supplied via JWT_SECRET environment variable (required). [code: filter/JwtValidationFilter.java, docker-compose.yml]

## Rules
- Requests outside /api/v1/auth/login and /actuator/** require a valid, non-blacklisted Bearer JWT or the gateway returns 401. [code: filter/JwtValidationFilter.java]
- JWT_SECRET environment variable is mandatory; the filter will fail to start without it. [code: filter/JwtValidationFilter.java]
- Identity headers (X-User-Id, X-Username, X-User-Role) are always overwritten by the gateway after JWT validation; client-supplied values are discarded. [code: filter/JwtValidationFilter.java]
- GET /api/v1/notifications/stream is intercepted at the servlet layer by SseProxyFilter, not by the MVC router. [code: filter/SseProxyFilter.java]
- SSE upstream connect timeout is 5000 ms; read timeout is 0 (infinite). [code: config/GatewayConfig.java]
- Actuator endpoints health, info, and metrics are exposed with permit-all. [code: application.yml, config/SecurityConfig.java]
- Error responses follow: `{error:{code,message,details},meta:{timestamp,requestId}}`. [code: filter/JwtValidationFilter.java]
- Gateway holds no database schema; connects to Redis only. [code: pom.xml, application.yml]

## Must never
- Never forward a request to a downstream service without JWT validation (only /api/v1/auth/login and /actuator/** bypass). [code: filter/JwtValidationFilter.java]
- Never log the raw JWT secret or token value at INFO level or above. [code: filter/JwtValidationFilter.java]
- Never add a JDBC/JPA dependency; this is a stateless proxy with Redis only. [code: pom.xml]
- Never allow a caller to inject X-User-Id, X-Username, or X-User-Role; the gateway always overwrites them. [code: filter/JwtValidationFilter.java]
- Never switch to reactive (WebFlux) Spring Cloud Gateway; the servlet-based MVC variant is required for synchronous filters and SSE proxying. [code: pom.xml]
- Never return a non-401 for an invalid or missing JWT. [code: filter/JwtValidationFilter.java]

## Decisions & trade-offs
- Spring Cloud Gateway MVC (servlet) over reactive: Gateway MVC 4.1.x buffers all proxied responses; SseProxyFilter works around this by proxying SSE byte-by-byte at the raw-servlet layer. Servlet stack keeps filters synchronous. [code: filter/SseProxyFilter.java — class-level Javadoc]
- SseProxyFilter at LOWEST_PRECEDENCE - 1: runs after JWT validation but before the MVC routing layer, avoiding both security bypass and response buffering. [code: filter/SseProxyFilter.java]
- MutableHttpServletRequest wrapper: Servlet spec makes request headers immutable; a custom wrapper is the only standards-compliant way to inject downstream headers inside a filter. [code: filter/JwtValidationFilter.java]
- JWT_SECRET via environment variable: secrets are never baked into application.yml or the image; docker-compose supplies a dev placeholder with a comment to replace in production. [code: docker-compose.yml]
- Virtual threads enabled: improves throughput under blocking I/O (SSE streams, Redis lookups, HTTP proxy). [code: application.yml]

## Reference
- API — 11 proxy route definitions, 1 SSE path, 3 actuator endpoints, 1 auth bypass path; see CLAUDE.reference.md §API
- Data — no owned tables; Redis blacklist key pattern only; see CLAUDE.reference.md §Data
- Integrations — 8 downstream HTTP services proxied; Redis (blacklist read); see CLAUDE.reference.md §Integrations
- Configuration — 13 application.yml keys, 7 environment variables; see CLAUDE.reference.md §Configuration
- Tech debt — 0 TODO/FIXME; 4 debt items; see CLAUDE.reference.md §Tech debt
- Open questions — 3 items; see CLAUDE.reference.md §Open questions

Open CLAUDE.reference.md only for the section your task needs.

— Plumbline 2.8.7 · /pl-onboard · claude-unit v1 · commit 4369a80 · judge pending · AI-drafted, posted after SME review
<!-- plumbline:end unit -->
