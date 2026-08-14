# auth-service -- Service Specification

## Purpose

auth-service is the identity and authentication boundary for the broker-dealer OMS. It issues and validates HS256 JWTs, maintains a token blacklist in Redis for logout invalidation, and manages the OMS user directory (create, list, activate/deactivate). All other services receive role identity via the `X-User-Role` header injected by the API Gateway after JWT validation; auth-service itself performs JWT validation only for its own `/api/v1/auth/**` endpoints.

## Port

8081 (application.yml:43 — `server.port: 8081`)

## Database Schema

Schema: `auth_schema` (application.yml:21 — `spring.flyway.schemas: auth_schema`; application.yml:18 — `hibernate.default_schema: auth_schema`)

Database: PostgreSQL (`application.yml:8` — `jdbc:postgresql://...?currentSchema=auth_schema`)

### Tables

| Table | Created in | Description |
|-------|-----------|-------------|
| `auth_schema.users` | V1__schema.sql:4 | Single user-account table; all columns added via sequential migrations |

### Column history (cumulative from all migrations)

| Column | Type | Constraint | Migration |
|--------|------|-----------| -----------|
| `id` | UUID | NOT NULL PRIMARY KEY DEFAULT gen_random_uuid() | V1__schema.sql:5 |
| `username` | VARCHAR(100) | NOT NULL UNIQUE | V1__schema.sql:6 |
| `password_hash` | VARCHAR(255) | NOT NULL | V1__schema.sql:7 |
| `role` | VARCHAR(20) | NOT NULL CHECK (role IN ('TRADER','OPERATIONS','ADMIN','VIEWER')) | V1__schema.sql:8, V6__add_viewer_role_and_seed.sql:9 |
| `created_at` | TIMESTAMPTZ | NOT NULL DEFAULT now() | V1__schema.sql:9 |
| `status` | VARCHAR(10) | NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')) | V4__add_user_status.sql:5 |
| `display_name` | VARCHAR(200) | NULL | V8__add_display_name_and_last_login.sql:3 |
| `last_login_at` | TIMESTAMPTZ | NULL | V8__add_display_name_and_last_login.sql:4 |
| `updated_at` | TIMESTAMPTZ | NOT NULL (backfilled from created_at, then set NOT NULL) | V10__add_updated_at_to_users.sql:3-10 |

Index: `idx_users_username ON auth_schema.users (username)` (V1__schema.sql:12)

## Kafka Produced

None found in scan. `KafkaTemplate` is configured (KafkaConfig.java:37) and wired into the `DeadLetterPublishingRecoverer` (KafkaConfig.java:61) for future consumer error handling, but no service class calls `kafkaTemplate.send(...)`. No topics are actively produced.

## Kafka Consumed

None found in scan. No `@KafkaListener` annotation appears in any source file. Consumer factory and container factory are configured (KafkaConfig.java:42-63) with consumer group `auth-service-group` (application.yml:27), exponential backoff (1s, 2s, 4s, max 3 attempts — KafkaConfig.java:58-60), and DLT recovery, but no listeners are registered.

## Redis Keys Owned

| Key Pattern | TTL | Purpose | File:Line |
|-------------|-----|---------| -----------|
| `auth:blacklist:<raw-jwt-token>` | Remaining token lifetime in seconds (computed as `expiry - now()` at logout time; dropped if already expired) | Stores `"blacklisted"` string to mark invalidated JWTs; checked on every `/api/v1/auth/me` call | JwtService.java:27 (constant `BLACKLIST_PREFIX = "auth:blacklist:"`), JwtService.java:64-69 (set with TTL via `TimeUnit.SECONDS`), JwtService.java:75 (read via `hasKey`) |

JWT expiry is fixed at 30 minutes from issuance (JwtService.java:28 — `EXPIRY_MINUTES = 30`; AuthService.java:55 — `Instant.now().plusSeconds(30 * 60L)`).

The `CacheManager` bean in RedisConfig.java:40-48 sets a default Spring Cache TTL of 30 seconds, but no `@Cacheable`/`@CachePut`/`@CacheEvict` annotations were found in any service class — the cache manager is configured but unused.

## REST Endpoints Exposed

| Method | Path | Auth / Role | Handler | File:Line |
|--------|------|-------------|---------|-----------|
| POST | `/api/v1/auth/login` | None (open) | `AuthController#login` | AuthController.java:26-30 |
| POST | `/api/v1/auth/logout` | None (open — token extracted from `Authorization` header) | `AuthController#logout` | AuthController.java:32-36 |
| GET | `/api/v1/auth/me` | None (open — token extracted from `Authorization` header; blacklist checked in service) | `AuthController#me` | AuthController.java:39-49 |
| GET | `/api/v1/users` | `@PreAuthorize("hasRole('ADMIN')")` | `UserController#listUsers` | UserController.java:33-51 |
| POST | `/api/v1/users` | `@PreAuthorize("hasRole('ADMIN')")` | `UserController#createUser` | UserController.java:53-68 |
| PATCH | `/api/v1/users/{userId}/status` | `@PreAuthorize("hasRole('ADMIN')")` | `UserController#updateUserStatus` | UserController.java:70-88 |

Notes:
- Role authorization for `/api/v1/users/**` relies on the `X-User-Role` header set by the API Gateway, which is promoted to a `SecurityContext` authority by `RoleHeaderAuthenticationFilter` (RoleHeaderAuthenticationFilter.java:23-28). The header value is not validated against a JWT by auth-service itself on these paths.
- `SecurityConfig` permits all requests at the filter chain level (`anyRequest().permitAll()` — SecurityConfig.java:35); `@PreAuthorize` method security (enabled via `@EnableMethodSecurity` — SecurityConfig.java:17) enforces the ADMIN role.
- Optional query parameters for `GET /api/v1/users`: `role` (UserRole enum) and `status` (UserStatus enum) (UserController.java:35-36).
- `PATCH /{userId}/status` reads the caller's admin ID from the `X-User-Id` header (UserController.java:79).

## REST Endpoints Called

None found in scan. No `RestTemplate`, `WebClient`, or `@FeignClient` usages appear in any source file under `src/main`. auth-service does not make outbound HTTP calls to other services.

## Entities

### User (`auth_schema.users`)

Entity class: `src/main/java/com/brokerdealer/authservice/domain/User.java`

`@Entity @Table(name = "users", schema = "auth_schema")` (User.java:13-14)

| Field | Java Type | Column | Constraints / Annotations | File:Line |
|-------|-----------|--------|--------------------------|-----------|
| `id` | `UUID` | `id` | `@Id`, `@GeneratedValue(strategy = GenerationType.UUID)`, `@Column(name="id", updatable=false, nullable=false)` | User.java:18-20 |
| `username` | `String` | `username` | `@Column(name="username", nullable=false, unique=true, length=100)` | User.java:22-23 |
| `passwordHash` | `String` | `password_hash` | `@Column(name="password_hash", nullable=false)` | User.java:25-26 |
| `role` | `UserRole` (enum) | `role` | `@Enumerated(EnumType.STRING)`, `@Column(name="role", nullable=false, length=20)` | User.java:28-30 |
| `status` | `UserStatus` (enum) | `status` | `@Enumerated(EnumType.STRING)`, `@Column(name="status", nullable=false, length=10)` | User.java:32-34 |
| `displayName` | `String` | `display_name` | `@Column(name="display_name")` (nullable by default — no explicit `nullable=false`) | User.java:36-37 |
| `createdAt` | `Instant` | `created_at` | `@Column(name="created_at", nullable=false, updatable=false)` | User.java:39-40 |
| `lastLoginAt` | `Instant` | `last_login_at` | `@Column(name="last_login_at")` (nullable by default) | User.java:42-43 |
| `updatedAt` | `Instant` | `updated_at` | `@Column(name="updated_at", nullable=false)` | User.java:45-46 |

Lifecycle hooks:
- `@PrePersist` sets `createdAt = Instant.now()` and `updatedAt = createdAt` if null (User.java:48-56)
- `@PreUpdate` sets `updatedAt = Instant.now()` (User.java:58-61)

#### UserRole enum (UserRole.java:3-7)
Constants: `VIEWER`, `TRADER`, `OPERATIONS`, `ADMIN`

#### UserStatus enum (UserStatus.java:3-6)
Constants: `ACTIVE`, `INACTIVE`

## What This Service Must NEVER Do

1. [CONVENTION -- INFERRED, CONFIRM WITH TEAM] Write to or read from any database schema other than `auth_schema`. No code enforces cross-schema isolation; the datasource URL defaults to `currentSchema=auth_schema` (application.yml:8) but no schema-guard mechanism exists in code.

2. [CONVENTION -- INFERRED, CONFIRM WITH TEAM] Call other microservices directly over HTTP. No `RestTemplate`, `WebClient`, or `@FeignClient` was found in any source file, but there is no architectural guard preventing their addition.

3. [CONVENTION -- INFERRED, CONFIRM WITH TEAM] Embed order management, trade, or risk business logic. The service contains only identity/authentication domain classes; no order or trade entities or services were found in any source file.

4. [CONVENTION -- INFERRED, CONFIRM WITH TEAM] Issue JWTs with algorithm other than HS256. The `JwtService.generateToken` method hard-codes `Jwts.SIG.HS256` (JwtService.java:48), but this is a code choice, not an enforced policy constraint.

5. [CONVENTION -- INFERRED, CONFIRM WITH TEAM] Validate JWTs for non-auth endpoints. The comment in `SecurityConfig` (SecurityConfig.java:27-29) states that API Gateway validates JWTs for all other services; auth-service only validates tokens for its own `/api/v1/auth/**` paths. This is an architectural convention, not code-enforced.

## Key Business Rules

1. **JWT expiry is fixed at 30 minutes.** `EXPIRY_MINUTES = 30` (JwtService.java:28); confirmed in AuthService.java:55 (`Instant.now().plusSeconds(30 * 60L)`).

2. **JWT algorithm is HS256.** `Jwts.SIG.HS256` (JwtService.java:48). Secret injected from environment variable `JWT_SECRET` (JwtService.java:34).

3. **JWT claims:** token carries `userId` (UUID as string), `username`, and `role` (JwtService.java:24-26; JwtService.java:42-44).

4. **Logout blacklists the token in Redis** for its remaining TTL. If TTL is already <= 0 at logout time, the token is not added to Redis (JwtService.java:63).

5. **Inactive users cannot log in.** `AuthService.login` checks `user.getStatus() != UserStatus.ACTIVE` and throws `AuthException("ACCOUNT_INACTIVE", ...)` (AuthService.java:45-49).

6. **An admin cannot deactivate their own account.** `UserService.updateUserStatus` throws `CannotDeactivateSelfException` when `callerAdminId.equals(targetId)` and requested status is `UserStatus.INACTIVE` (UserService.java:76-79).

7. **Username uniqueness is case-insensitive at lookup.** `UserRepository.findByUsernameIgnoreCase` is used for both login (AuthService.java:32) and duplicate-check on create (UserService.java:53). The database column has a case-sensitive UNIQUE constraint (V1__schema.sql:6).

8. **New users are always created with status `ACTIVE`.** `UserService.createUser` hard-codes `status(UserStatus.ACTIVE)` (UserService.java:63).

9. **Password hashing uses BCrypt** (SecurityConfig.java:40 — `new BCryptPasswordEncoder()`).

10. **Username constraints on create:** 3-50 characters, pattern `^[a-zA-Z0-9_.]+$` (CreateUserRequest.java:14-15). Password minimum 8 characters (CreateUserRequest.java:26-27).

11. **Role enum values:** `VIEWER`, `TRADER`, `OPERATIONS`, `ADMIN` (UserRole.java:4-7). DB constraint added VIEWER in migration V6 (V6__add_viewer_role_and_seed.sql:9).

12. **displayName fallback:** `UserService.toResponse` returns `username` if `displayName` is null or blank (UserService.java:94-96).

13. **lastLoginAt is updated on every successful login** in `AuthService.login` (AuthService.java:51-52).

14. **Kafka infrastructure is wired but inactive.** Consumer group `auth-service-group` (application.yml:27) is declared. No topics are produced or consumed at this time. DLT error handler uses exponential backoff: initial 1s, multiplier 2.0, max 3 attempts (KafkaConfig.java:58-60).
