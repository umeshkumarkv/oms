<!-- plumbline:template claude-unit v1 -->
# auth-service

<!-- plumbline:begin unit -->
Last updated: 4369a80 (2026-10-30, /pl-onboard, claude-unit v1)

## Purpose

auth-service is the authentication and user-management microservice of the Broker-Dealer OMS. It validates credentials against BCrypt-hashed passwords stored in PostgreSQL, issues and validates HS256 JWTs (30-minute TTL), and blacklists invalidated tokens in Redis. It also provides ADMIN-only user lifecycle endpoints (create, list, activate/deactivate). Other services do not call auth-service at runtime; the API Gateway validates JWTs autonomously after initial issuance.

## Build & run

Build: `mvn clean package -DskipTests`
Run: `mvn spring-boot:run` — listens on **port 8081**.
Docker (from repo root): `docker build -f services/auth-service/Dockerfile -t auth-service .` — Dockerfile COPYs root `pom.xml`; build context must be repo root; uses `amazoncorretto:21`.

Required environment variables at startup: `JWT_SECRET`, `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `SPRING_REDIS_HOST`, `SPRING_REDIS_PORT`. All have localhost defaults except `JWT_SECRET` which has no default and will cause startup failure if absent.

No profiles are defined; configuration is via environment variable overrides on `application.yml` defaults.

**Test recipe:** `mvn test` · Java 21 required (root pom.xml `<java.version>21</java.version>`) · Java 24 available (openjdk version "24") · **FAILED** — `Fatal error compiling: java.lang.ExceptionInInitializerError: com.sun.tools.javac.code.TypeTag :: UNKNOWN` — Lombok/MapStruct annotation processors are incompatible with Java 24; tests cannot run until Java 21 is used · 2026-10-30

## Architecture & structure

**Stack:** Java 21, Spring Boot 3 (web, data-jpa, security, data-redis, actuator, validation, kafka), Hibernate/JPA, Flyway, PostgreSQL (driver `org.postgresql`), Redis (`spring-data-redis`), JJWT 0.12.x (HS256), Lombok, MapStruct (declared, unused), Resilience4j, springdoc-openapi, Maven.

**Layers and patterns:**
- `controller/` — REST layer; builds envelope responses `{ data, meta }`.
- `service/` — business logic; `AuthService` owns login/logout/me flows; `JwtService` signs, validates, and blacklists tokens; `UserService` owns CRUD.
- `repository/` — Spring Data JPA; `UserRepository` extends `JpaRepository<User, UUID>`.
- `domain/` — JPA entity `User`; enums `UserRole` (VIEWER, TRADER, OPERATIONS, ADMIN) and `UserStatus` (ACTIVE, INACTIVE).
- `dto/` — request and response DTOs; no MapStruct mappers; `UserService.toResponse()` maps manually.
- `config/` — `SecurityConfig`, `RoleHeaderAuthenticationFilter`, `KafkaConfig`, `RedisConfig`.
- `exception/` — typed exception classes + `GlobalExceptionHandler` (`@RestControllerAdvice`).

**Key packages:** `com.brokerdealer.authservice.{controller, service, repository, domain, dto, config, exception}`

Schema managed by Flyway (11 versioned scripts, gap at V3: V1, V2, V4–V12); Hibernate `ddl-auto: validate`.

## Security

**Authentication model:** Stateless; no Spring Security `UserDetailsService`. The auth-service validates JWTs at the service layer (`AuthService`) for `/auth/*` endpoints. For `/users/*`, authorization is enforced via `@PreAuthorize("hasRole('ADMIN')")` backed by `RoleHeaderAuthenticationFilter`.

**`RoleHeaderAuthenticationFilter`** (`config/RoleHeaderAuthenticationFilter.java`): reads the `X-User-Role` header injected by the API Gateway and builds a `UsernamePasswordAuthenticationToken` with `ROLE_<value>` authority. All requests are `permitAll()` at the URL level; method security (`@EnableMethodSecurity`) enforces role gates.

**JWT** (`service/JwtService.java`): HS256 signed with `JWT_SECRET` (env var); 30-minute expiry; claims: `userId` (UUID string), `username`, `role`. Blacklisted tokens stored in Redis under key `auth:blacklist:<token>` with TTL equal to remaining lifetime.

**Password hashing:** BCrypt, strength 10 (`config/SecurityConfig.java`).

**Token storage:** Redis only (server-side blacklist). No session, no cookie.

**Self-deactivation guard** (`service/UserService.java`): an admin cannot set their own account to INACTIVE; enforced by comparing `X-User-Id` header with target userId.

## Rules

- Login must reject users whose `status != ACTIVE` with error code `ACCOUNT_INACTIVE`. [code: service/AuthService.java]
- Passwords must be stored as BCrypt hashes; plaintext must never be persisted. [code: service/UserService.java, config/SecurityConfig.java]
- JWT expiry is fixed at 30 minutes; there is no refresh-token mechanism. [code: service/JwtService.java]
- Blacklisted tokens must be rejected in `GET /auth/me`; logout blacklists immediately. [code: service/AuthService.java, service/JwtService.java]
- `GET /users`, `POST /users`, and `PATCH /users/{id}/status` require `ADMIN` role. [code: controller/UserController.java]
- An admin may not deactivate their own account. [code: service/UserService.java]
- Username uniqueness is case-insensitive at lookup; unique constraint is enforced at the database level. [code: repository/UserRepository.java, db/migration/V1__schema.sql]
- All responses (success and error) must include `meta.timestamp` and `meta.requestId`. [code: controller/AuthController.java, controller/UserController.java, exception/GlobalExceptionHandler.java]
- `JWT_SECRET` has no fallback default; service will fail to start if the variable is absent. [code: service/JwtService.java]
- Flyway manages schema under `auth_schema`; Hibernate `ddl-auto` is `validate` — never `create` or `update`. [decided: src/main/resources/application.yml]

## Must never

- Store or log plaintext passwords or raw JWT secrets.
- Switch `spring.jpa.hibernate.ddl-auto` away from `validate` — schema changes must be Flyway migrations.
- Accept a `X-User-Role` header from untrusted clients; this header is the only authorization signal and must originate from the API Gateway only (network-level control, not enforced in code).
- Remove the Redis blacklist check from `GET /auth/me`; omitting it would allow logged-out tokens to remain valid until expiry.
- Allow an admin to deactivate themselves (blocked by `UserService.updateUserStatus`).
- Bypass BCrypt by writing cleartext passwords to `password_hash`.

## Decisions & trade-offs

- **API Gateway owns JWT validation for downstream services; auth-service only validates its own endpoints.** Comment in `SecurityConfig.java`: "Token validation is handled at the service layer for all auth endpoints. The API Gateway validates JWTs for all other services." Keeps auth-service stateless and avoids a synchronous auth hop on every downstream request.
- **`X-User-Role` header trust model.** `RoleHeaderAuthenticationFilter` trusts the role header unconditionally. Comment in `SecurityConfig.java` implies network/gateway controls (not the filter) prevent external clients from spoofing this header.
- **No refresh tokens.** JWT TTL is 30 minutes; there is no refresh endpoint. Clients must re-authenticate after expiry. No source documents the reason; the fixed TTL and blacklist-on-logout pattern suggest simplicity is preferred over long-lived sessions.
- **MapStruct declared but manual mapping used.** `mapstruct` appears in `pom.xml` and JaCoCo exclusions reference `KafkaConfig` and `RedisConfig` but not a mapper. `UserService.toResponse()` maps inline. No source explains why MapStruct was not applied.
- **Virtual threads enabled.** `spring.threads.virtual.enabled: true` in `application.yml` — Java 21 virtual threads opted in for the Tomcat thread pool.

## Reference

Open CLAUDE.reference.md only for the section your task needs.
- **API** — 6 endpoints across 2 controllers.
- **Data** — 1 entity (`users`, 9 columns), 2 enums, 11 Flyway migrations (V1–V2, V4–V12).
- **Integrations** — Redis (blacklist), Kafka (wired, unused), PostgreSQL, Actuator, Resilience4j (wired, unused).
- **Configuration** — 11 config keys / env vars; secrets via environment.
- **Tech debt** — 0 TODO/FIXME; 6 structural debt items.
- **Open questions** — 6 items.
<!-- plumbline:end unit -->
