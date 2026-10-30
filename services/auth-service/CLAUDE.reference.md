<!-- plumbline:template claude-reference v1 -->
# auth-service — reference

<!-- plumbline:begin reference -->
Last updated: 4586741 (2026-10-30, /pl-onboard, claude-reference v1)

## API

**AuthController** — base path `/api/v1/auth`

| Method | Path | Handler | Purpose |
|--------|------|---------|---------|
| POST | /api/v1/auth/login | AuthController.login | Validate credentials, issue HS256 JWT (30 min TTL) |
| POST | /api/v1/auth/logout | AuthController.logout | Blacklist the bearer token in Redis |
| GET  | /api/v1/auth/me | AuthController.me | Return userId/username/role/displayName for a valid token |

**UserController** — base path `/api/v1/users`

| Method | Path | Handler | Purpose |
|--------|------|---------|---------|
| GET  | /api/v1/users | UserController.listUsers | List users; optional `?role=` and `?status=` filters; ADMIN only |
| POST | /api/v1/users | UserController.createUser | Create a new user account; ADMIN only |
| PATCH | /api/v1/users/{userId}/status | UserController.updateUserStatus | Activate or deactivate a user; ADMIN only; reads `X-User-Id` header to block self-deactivation |

Total: 6 endpoints across 2 controllers.

All responses are wrapped: `{ "data": ..., "meta": { "timestamp": "<ISO-8601>", "requestId": "<UUID>" } }`.
Error responses: `{ "error": { "code", "message", "details" }, "meta": { ... } }`.

## Data

**Entity: `User`** — table `auth_schema.users`

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| id | UUID | PK, not null, gen_random_uuid() | Immutable after insert |
| username | VARCHAR(100) | NOT NULL, UNIQUE | Case-insensitive lookup; index `idx_users_username` |
| password_hash | VARCHAR(255) | NOT NULL | BCrypt strength 10 |
| role | VARCHAR(20) | NOT NULL, CHECK | TRADER, OPERATIONS, ADMIN, VIEWER |
| status | VARCHAR(10) | NOT NULL, CHECK, default ACTIVE | ACTIVE or INACTIVE |
| display_name | VARCHAR(200) | NULL | Falls back to username if blank |
| created_at | TIMESTAMPTZ | NOT NULL, immutable | Set by `@PrePersist` |
| last_login_at | TIMESTAMPTZ | NULL | Updated on each successful login |
| updated_at | TIMESTAMPTZ | NOT NULL | Set by `@PrePersist`; refreshed by `@PreUpdate` and on status change |

**Enums**

- `UserRole`: VIEWER, TRADER, OPERATIONS, ADMIN
- `UserStatus`: ACTIVE, INACTIVE

**Migrations** (Flyway, schema `auth_schema`)

| Version | File | Change |
|---------|------|--------|
| V1 | V1__schema.sql | Create `users` table; index on username; initial role check (TRADER, OPERATIONS, ADMIN) |
| V2 | V2__seed.sql | Seed trader.john, trader.sarah, ops.manager, admin.user |
| V4 | V4__add_user_status.sql | Add `status` column, default ACTIVE, CHECK (ACTIVE, INACTIVE) |
| V5 | V5__fix_password_hash.sql | Fix seed password hashes |
| V6 | V6__add_viewer_role_and_seed.sql | Extend role CHECK to include VIEWER; seed viewer.user |
| V7 | V7__add_risk_manager_user.sql | Add risk.manager (role ADMIN) |
| V8 | V8__add_display_name_and_last_login.sql | Add `display_name` and `last_login_at` columns; backfill display names |
| V9 | V9__seed_brd_users.sql | Seed BRD §16.3 users: trader01, trader02, admin01, viewer01 |
| V10 | V10__add_updated_at_to_users.sql | Add `updated_at` column; backfill from `created_at` |
| V11 | V11__fix_v9_seed_password_hashes.sql | Fix V9 seed hashes to match Demo@1234 |
| V12 | V12__add_admin02_user.sql | Add admin02 for maker-checker two-admin workflow |

Note: V3 is absent from the migrations directory (gap in numbering).

## Integrations

**Redis** — `spring.data.redis` (host/port via env vars)
- Token blacklist: keys `auth:blacklist:<raw-jwt>`, TTL = remaining JWT lifetime in seconds.
- `RedisConfig` also configures a `CacheManager` with a 30-second default TTL, but no `@Cacheable` annotations are present in the service layer — cache manager is provisioned but unused at the application layer.

**Kafka** — `spring.kafka.bootstrap-servers` (via env var `SPRING_KAFKA_BOOTSTRAP_SERVERS`)
- `KafkaConfig` declares a producer factory, a consumer factory, and a `ConcurrentKafkaListenerContainerFactory` with exponential back-off (1 s → 2 s → 4 s, 3 attempts) and dead-letter publishing.
- No `@KafkaListener` or `kafkaTemplate.send(...)` calls exist in the service code — Kafka infrastructure is wired but no topics are produced or consumed at present.

**PostgreSQL** — `auth_schema` on the shared `brokerdealer` database (env vars `SPRING_DATASOURCE_URL/USERNAME/PASSWORD`).

**Actuator** — exposes `/actuator/health` (with liveness/readiness probes), `/actuator/info`, `/actuator/metrics`.

**Resilience4j** — circuit-breaker and time-limiter beans configured in `application.yml` (sliding window 10, failure threshold 50 %, wait 10 s, timeout 2 s) but no `@CircuitBreaker` annotations are applied to service methods.

## Configuration

| Key / Env var | Default | Purpose |
|---------------|---------|---------|
| `JWT_SECRET` | — (required, no default) | HS256 signing key injected into `JwtService` |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/brokerdealer?currentSchema=auth_schema` | PostgreSQL JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | `brokerdealer` | DB username |
| `SPRING_DATASOURCE_PASSWORD` | `brokerdealer` | DB password |
| `SPRING_REDIS_HOST` | `localhost` | Redis hostname |
| `SPRING_REDIS_PORT` | `6379` | Redis port |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka brokers |
| `server.port` | `8081` | HTTP port (hard-coded in application.yml) |
| `spring.jpa.hibernate.ddl-auto` | `validate` | Schema managed by Flyway; Hibernate validates only |
| `spring.flyway.schemas` | `auth_schema` | Flyway migration schema |
| `logging.level.com.brokerdealer` | `INFO` | Application log level |

Secrets (`JWT_SECRET`, `SPRING_DATASOURCE_PASSWORD`) must be supplied as environment variables or an external secret store; no defaults are provided in code for `JWT_SECRET`.

## Tech debt

TODO/FIXME count: **0** — no TODO or FIXME comments found in `src/`.

Other debt:

- **Kafka wired but unused**: `KafkaConfig` configures full producer/consumer/DLT infrastructure; no listeners or sends exist in the service layer. Purpose is unknown. [src/main/java/com/brokerdealer/authservice/config/KafkaConfig.java]
- **Redis CacheManager unused at application layer**: `RedisConfig` provisions a `CacheManager` with 30 s TTL but no `@Cacheable` or `@CacheEvict` annotations appear in service classes. [src/main/java/com/brokerdealer/authservice/config/RedisConfig.java]
- **Resilience4j unconfigured on methods**: Circuit-breaker and time-limiter instances are declared in `application.yml` but no `@CircuitBreaker` or `@TimeLimiter` annotations are applied to service methods. [src/main/resources/application.yml]
- **V3 migration gap**: Flyway versions jump from V2 to V4; V3 is missing. Could indicate a rolled-back or out-of-order migration. [src/main/resources/db/migration/]
- **MapStruct declared as dependency but not used**: `mapstruct` artifact is in `pom.xml`; no `@Mapper` interfaces exist — `UserService.toResponse()` maps manually. [pom.xml]
- **`listUsers` pagination not implemented**: `GET /api/v1/users` returns all rows hardcoded as `page=1, pageSize=50` in meta but applies no actual database-level pagination. [src/main/java/com/brokerdealer/authservice/controller/UserController.java]

## Open questions

1. V3 migration is absent — was it applied and then deleted, or was it skipped intentionally?
2. Kafka is fully wired but no topics are produced or consumed — what events are planned and on which topics?
3. The Redis `CacheManager` (30 s TTL) is provisioned but no `@Cacheable` annotations exist — is caching planned for user lookups or another use case?
4. Resilience4j circuit-breaker and time-limiter instances are declared but not applied to any service method — which calls are intended to be wrapped?
5. The `openapi.yaml` `MeResponse` and `LoginResponse` schemas omit the `VIEWER` role from their `enum` values — is the spec stale?
6. `GET /api/v1/users` advertises pagination metadata (`page`, `pageSize`) but loads all rows with no LIMIT/OFFSET — is server-side pagination planned?
<!-- plumbline:end reference -->
