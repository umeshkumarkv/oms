<!-- plumbline:template claude-unit v1 -->
# Auth Service

<!-- plumbline:begin unit -->
Last updated: 4369a80 (2026-10-28, /pl-onboard, claude-unit v1)

## Purpose
Issues and validates JWT tokens for the Broker-Dealer OMS. Handles user login, logout, and identity resolution (`/me`). Manages the `auth_schema.users` table and enforces user lifecycle (create, activate, deactivate). Blacklists invalidated tokens in Redis so other services can detect logged-out sessions. All JWT validation for every other service is performed by the API Gateway; auth-service only validates tokens for its own `/api/v1/auth` endpoints.

## Build & run
Build: `./mvnw package -pl services/auth-service -am` (or `mvnw.cmd` on Windows).
Run: `./mvnw spring-boot:run -pl services/auth-service` — listens on **port 8081**.
No active Spring profiles are set in docker-compose for auth-service; the default profile applies.
Flyway migrations run automatically on startup from `classpath:db/migration` against `auth_schema`.

Test recipe: `mvn test` · requires Java 21 (`<java.version>21</java.version>` in root pom.xml) · not run — Java 24 installed locally is incompatible with maven-compiler-plugin 3.11.0; first error: `Fatal error compiling: java.lang.ExceptionInInitializerError: com.sun.tools.javac.code.TypeTag :: UNKNOWN` · 2026-10-28

Startup dependencies (docker-compose `depends_on`): Kafka must be `service_healthy` before auth-service starts; Redis and Postgres both require only `service_started`. [code: docker-compose.yml, auth-service block]

## Architecture & structure
- **Stack:** Java 21, Spring Boot 3.2.4, Spring Security, Spring Data JPA (Hibernate), Flyway, Spring Data Redis, Spring Kafka, jjwt 0.12.3, Resilience4j 2.2.0, springdoc-openapi 2.3.0, Lombok, MapStruct 1.5.5.Final, PostgreSQL 15.
- **Layer pattern:** REST controller → service → repository. No MapStruct mapper classes found in source (dependency present but unused in this service).
- **Packages:**
  - `controller` — `AuthController` (`/api/v1/auth`), `UserController` (`/api/v1/users`)
  - `service` — `AuthService` (login/logout/me logic), `JwtService` (token generation, validation, Redis blacklist), `UserService` (CRUD and lifecycle)
  - `domain` — `User` entity, `UserRole` enum (VIEWER, TRADER, OPERATIONS, ADMIN), `UserStatus` enum (ACTIVE, INACTIVE)
  - `repository` — `UserRepository` (JPA, with role/status filter queries)
  - `config` — `SecurityConfig`, `RoleHeaderAuthenticationFilter`, `KafkaConfig`, `RedisConfig`
  - `exception` — `GlobalExceptionHandler` + typed exception classes
  - `dto/request` — `LoginRequest`, `CreateUserRequest`, `UpdateUserStatusRequest`
  - `dto/response` — `LoginData`, `LoginResponse`, `LogoutResponse`, `MeResponse`, `UserResponse`, `UserStatusResponse`

## Security
- **Authentication:** Stateless; no HTTP session. Spring Security session policy is `STATELESS`. [code: config/SecurityConfig.java]
- **JWT:** HS256, signed with `JWT_SECRET` (env var, injected into `JwtService`). Token TTL is 30 minutes. [code: service/JwtService.java]
- **Token blacklist:** On logout, the raw JWT is stored in Redis under key `auth:blacklist:<token>` with a TTL equal to remaining token lifetime. The `/me` endpoint checks this before returning user info. [code: service/JwtService.java, service/AuthService.java]
- **Role propagation:** The API Gateway forwards the authenticated user's role in the `X-User-Role` HTTP header. `RoleHeaderAuthenticationFilter` reads this header and builds a `UsernamePasswordAuthenticationToken` with `ROLE_<value>` granted authority, enabling `@PreAuthorize` on `UserController`. [code: config/RoleHeaderAuthenticationFilter.java]
- **Authorization:** `GET /api/v1/users`, `POST /api/v1/users`, `PATCH /api/v1/users/{userId}/status` are gated by `@PreAuthorize("hasRole('ADMIN')")`. All `auth` endpoints are open (permitAll) — token validation is enforced at service layer. [code: controller/UserController.java, config/SecurityConfig.java]
- **Password hashing:** BCrypt (strength default, ~10). [code: config/SecurityConfig.java]
- **Caller identity:** Admin identity passed via `X-User-Id` header on PATCH status; used solely to prevent self-deactivation. [code: controller/UserController.java]

## Rules
- Reject login if account status is not ACTIVE. [code: service/AuthService.java]
- Usernames are matched case-insensitively at login and must be unique (case-insensitive lookup at creation). [code: service/AuthService.java, service/UserService.java]
- A user with `JWT_SECRET` unset will fail to start (Spring `@Value` injection fails). [code: service/JwtService.java]
- An admin cannot set their own account to INACTIVE (`CannotDeactivateSelfException`). [code: service/UserService.java]
- Flyway baseline-on-migrate is enabled; DDL is never managed by Hibernate (`ddl-auto: validate`). [code: resources/application.yml]
- All error responses follow the envelope `{ error: { code, message, details }, meta: { timestamp, requestId } }`. [code: exception/GlobalExceptionHandler.java]
- All success responses follow the envelope `{ data: ..., meta: { timestamp, requestId } }`. [code: controller/AuthController.java, controller/UserController.java]
- Token must start with `Bearer ` prefix; missing or malformed bearer strings are rejected immediately. [code: service/AuthService.java]

## Must never
- Never validate JWTs for services other than this one's own endpoints — that is the API Gateway's responsibility. [code: config/SecurityConfig.java comment]
- Never store the JWT_SECRET value in source code, migration scripts, or application.yml defaults.
- Never expose `password_hash` in any response DTO.
- Never allow `ddl-auto` other than `validate` — schema changes must go through Flyway migrations.
- Never bypass the Redis blacklist check on `/me` — a logged-out token must be rejected even if cryptographically valid.

## Decisions & trade-offs
- Token blacklisting is done in Redis rather than a database table; TTL mirrors the token expiry so no cleanup job is needed. The implementation calls `redisTemplate.opsForValue().set(BLACKLIST_PREFIX + token, "blacklisted", ttlSeconds, TimeUnit.SECONDS)` with TTL derived from the token's `exp` claim. [code: service/JwtService.java]
- Spring Security `permitAll` is used at the HTTP layer for all routes; actual auth checks for `/api/v1/auth/**` are done programmatically in the service layer, because auth endpoints need to inspect the token themselves. [code: config/SecurityConfig.java comment]
- `RoleHeaderAuthenticationFilter` trusts the `X-User-Role` header from the API Gateway. This design assumes the API Gateway is the only network ingress — no direct external access to this service. [code: config/RoleHeaderAuthenticationFilter.java]
- Kafka consumer/producer and Resilience4j are wired up but no Kafka listeners or producers are implemented in this service at this commit. Infrastructure is present for future use. [code: config/KafkaConfig.java]
- Virtual threads are enabled (`spring.threads.virtual.enabled: true`) to improve throughput under blocking I/O without tuning thread pool sizes. [code: resources/application.yml]

## Reference
- API — 6 endpoints, 2 controllers
- Data — 1 table (`auth_schema.users`), 11 Flyway migrations
- Integrations — Redis (token blacklist + cache), Kafka (configured, no active topics produced/consumed), PostgreSQL
- Configuration — 9 env vars / config keys
- Tech debt — 0 TODO/FIXME
- Open questions — 2 items

Open CLAUDE.reference.md only for the section your task needs.

— Plumbline 2.8.7 · /pl-onboard · claude-unit v1 · commit 4369a80 · judge pending · AI-drafted, posted after SME review
<!-- plumbline:end unit -->
