<!-- plumbline:template claude-unit v2 -->
# auth-service

<!-- plumbline:begin unit -->
Last updated: 39916bd (2026-10-30, /pl-onboard, claude-unit v2)

## Purpose

auth-service issues HS256 JWTs (30-minute TTL), validates login credentials against BCrypt hashes in PostgreSQL, and blacklists revoked tokens in Redis. It provides ADMIN-only user lifecycle endpoints. The API Gateway validates JWTs autonomously; no other service calls auth-service at runtime.

## Build & run

- Build: `mvn clean package -DskipTests`
- Run: `mvn spring-boot:run` — port **8081**
- Docker (repo root context): `docker build -f services/auth-service/Dockerfile -t auth-service .`

Required env vars: `JWT_SECRET` (no default; absent = startup failure), `SPRING_DATASOURCE_URL/USERNAME/PASSWORD`, `SPRING_REDIS_HOST/PORT`. All except `JWT_SECRET` have localhost defaults. No Spring profiles.

**Test recipe:** `mvn test` · Java 21 required · **FAILED** — `ExceptionInInitializerError: TypeTag::UNKNOWN` — Lombok/MapStruct incompatible with Java 24; use Java 21 · 2026-10-30

## Architecture & structure

**Stack:** Java 21, Spring Boot 3.2.4, Spring Security, Spring Data JPA/Redis, Flyway, PostgreSQL, Redis, JJWT 0.12.x (HS256), Lombok; Kafka/MapStruct/Resilience4j wired but unused, springdoc-openapi, Maven.

**Layers:** `controller/` → `service/` (`AuthService`, `JwtService`, `UserService`) → `repository/` (`UserRepository extends JpaRepository<User, UUID>`) → `domain/` (`User`, `UserRole`, `UserStatus`) · `dto/` (manual mapping) · `config/` (`SecurityConfig`, `RoleHeaderAuthenticationFilter`) · `exception/` (`GlobalExceptionHandler`).

Root package: `com.brokerdealer.authservice`. Flyway manages `auth_schema` (11 scripts, V1–V2, V4–V12; V3 absent); Hibernate `ddl-auto: validate`.

## Security

- Identity: Inbound requests from api-gateway. `/auth/*` endpoints use JWT Bearer HS256 validated by JwtService. `/users/*` endpoints trust `X-User-Role` header injected by api-gateway.
- Verified at: `service/JwtService.java` (JWT signature/expiry/blacklist); `config/RoleHeaderAuthenticationFilter.java` (role header)
- Reachable without that check: docker-compose: yes, port 8081 published to host (`docker-compose.yml`); k8s: no, ClusterIP (`k8s/services/auth-service-service.yaml`). Direct callers can inject arbitrary `X-User-Role` header without gateway.
- JWT validation: stateless; `JWT_SECRET` env var; 30-min expiry; claims: `userId`, `username`, `role`; blacklisted under `auth:blacklist:<token>` in Redis (TTL = remaining lifetime). [code: service/JwtService.java]
- User-endpoint authorization: `RoleHeaderAuthenticationFilter` reads gateway-injected `X-User-Role`, builds `UsernamePasswordAuthenticationToken(ROLE_<value>)`; `@PreAuthorize("hasRole('ADMIN')")` guards `/users/*`. [code: config/RoleHeaderAuthenticationFilter.java]
- Password hashing: BCrypt strength 10; no session, no cookie. [code: config/SecurityConfig.java]
- Self-deactivation guard: admin cannot set own account INACTIVE; `UserService.updateUserStatus` compares `X-User-Id` header with target userId. [code: service/UserService.java]

## Rules

- Reject login if `status != ACTIVE`; error code `ACCOUNT_INACTIVE`. [code: service/AuthService.java]
- Store passwords as BCrypt hashes only; no plaintext. [code: service/UserService.java, config/SecurityConfig.java]
- JWT TTL is 30 minutes; no refresh-token endpoint. [code: service/JwtService.java]
- `GET /auth/me` must check Redis blacklist; logout blacklists immediately. [code: service/AuthService.java, service/JwtService.java]
- `/users` endpoints require `ADMIN` role. [code: controller/UserController.java]
- Admin may not deactivate their own account. [code: service/UserService.java]
- Username uniqueness: case-insensitive lookup; DB unique constraint. [code: repository/UserRepository.java, db/migration/V1__schema.sql]
- All responses include `meta.timestamp` and `meta.requestId`. [code: controller/AuthController.java, controller/UserController.java, exception/GlobalExceptionHandler.java]
- `JWT_SECRET` has no default; absent = startup failure. [code: service/JwtService.java]
- Schema changes via Flyway only; `ddl-auto: validate`. [decided: src/main/resources/application.yml]

## Must never

- Store or log plaintext passwords or raw JWT secrets.
- Set `spring.jpa.hibernate.ddl-auto` to anything other than `validate`.
- Trust `X-User-Role` from callers other than the API Gateway (network control required; not enforced in code).
- Remove the Redis blacklist check from `GET /auth/me` — logged-out tokens would remain valid until expiry.
- Allow an admin to deactivate their own account.
- Write cleartext passwords to `password_hash`.

## Decisions & trade-offs

- Gateway owns downstream JWT validation; auth-service validates only its own endpoints — avoids synchronous auth hop on every downstream request. [code: SecurityConfig.java comment]
- `X-User-Role` trusted unconditionally by `RoleHeaderAuthenticationFilter` — gateway and network controls are the spoofing defence. [code: SecurityConfig.java comment]
- No refresh tokens — 30-minute TTL with blacklist-on-logout; no documented reason. [code: service/JwtService.java]
- MapStruct declared but unused — `UserService.toResponse()` maps manually; no documented reason. [code: pom.xml]
- Virtual threads enabled. [decided: application.yml]

## Reference

Open CLAUDE.reference.md only for the section your task needs.
- **API** — 6 endpoints across 2 controllers.
- **Data** — 1 entity (`users`, 9 columns), 2 enums, 11 Flyway migrations (V1–V2, V4–V12).
- **Integrations** — Redis (blacklist), Kafka (wired, unused), PostgreSQL, Actuator, Resilience4j (wired, unused).
- **Configuration** — 11 config keys / env vars; secrets via environment.
- **Tech debt** — 0 TODO/FIXME; 6 structural debt items.
- **Open questions** — 6 items.
<!-- plumbline:end unit -->
