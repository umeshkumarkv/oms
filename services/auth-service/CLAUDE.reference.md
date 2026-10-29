<!-- plumbline:template claude-reference v1 -->
# Auth Service — reference

<!-- plumbline:begin reference -->
Last updated: 4369a80 (2026-10-28, /pl-onboard, claude-reference v1)

## API

### AuthController — `/api/v1/auth`
| Method | Path | Handler | Purpose |
|--------|------|---------|---------|
| POST | `/api/v1/auth/login` | `AuthController.login` | Validate credentials, return JWT + expiry + role |
| POST | `/api/v1/auth/logout` | `AuthController.logout` | Blacklist the bearer token in Redis |
| GET | `/api/v1/auth/me` | `AuthController.me` | Return userId, username, role, displayName from the bearer token (checks blacklist) |

All three endpoints accept/return `Authorization: Bearer <token>` in the header. All endpoints are `permitAll` at the Spring Security level; token validation is enforced in `AuthService`.

### UserController — `/api/v1/users`
| Method | Path | Handler | Purpose |
|--------|------|---------|---------|
| GET | `/api/v1/users` | `UserController.listUsers` | List all users; optional `?role=` and `?status=` filters. ADMIN only. |
| POST | `/api/v1/users` | `UserController.createUser` | Create a new user; returns 201 with the created user. ADMIN only. |
| PATCH | `/api/v1/users/{userId}/status` | `UserController.updateUserStatus` | Activate or deactivate a user. ADMIN only. Reads `X-User-Id` header to block self-deactivation. |

Authorization for all three: `@PreAuthorize("hasRole('ADMIN')")` — role sourced from `X-User-Role` header set by API Gateway.

Response envelope (success): `{ data: <payload>, meta: { timestamp, requestId } }`
Response envelope (error): `{ error: { code, message, details[] }, meta: { timestamp, requestId } }`

## Data

### Table: `auth_schema.users`
| Column | Type | Constraints |
|--------|------|-------------|
| `id` | UUID | PK, `gen_random_uuid()` default |
| `username` | VARCHAR(100) | NOT NULL, UNIQUE |
| `password_hash` | VARCHAR(255) | NOT NULL |
| `role` | VARCHAR(20) | NOT NULL, CHECK IN ('TRADER','OPERATIONS','ADMIN','VIEWER') |
| `status` | VARCHAR(10) | NOT NULL DEFAULT 'ACTIVE', CHECK IN ('ACTIVE','INACTIVE') |
| `display_name` | VARCHAR(200) | NULL |
| `last_login_at` | TIMESTAMPTZ | NULL; updated on each successful login |
| `created_at` | TIMESTAMPTZ | NOT NULL DEFAULT now(), not updated |
| `updated_at` | TIMESTAMPTZ | NOT NULL; set to `created_at` on insert, to `now()` on update |

Index: `idx_users_username` on `username`.

### Flyway migrations (11 files, `classpath:db/migration`)
| Version | File | Change |
|---------|------|--------|
| V1 | `V1__schema.sql` | Create `auth_schema.users` with id, username, password_hash, role (TRADER/OPERATIONS/ADMIN), created_at |
| V2 | `V2__seed.sql` | Seed initial users |
| V4 | `V4__add_user_status.sql` | Add `status` column (ACTIVE/INACTIVE), default ACTIVE |
| V5 | `V5__fix_password_hash.sql` | Fix seed password hashes |
| V6 | `V6__add_viewer_role_and_seed.sql` | Add VIEWER to role constraint; seed viewer.user |
| V7 | `V7__add_risk_manager_user.sql` | Seed risk.manager user |
| V8 | `V8__add_display_name_and_last_login.sql` | Add `display_name` and `last_login_at`; backfill display names (SCRUM-78) |
| V9 | `V9__seed_brd_users.sql` | Seed BRD users |
| V10 | `V10__add_updated_at_to_users.sql` | Add `updated_at` column, backfill from `created_at` (SCRUM-79) |
| V11 | `V11__fix_v9_seed_password_hashes.sql` | Fix password hashes from V9 seed |
| V12 | `V12__add_admin02_user.sql` | Add admin02 seed user |

No V3 file exists in the migration folder (gap in version sequence).

## Integrations

### Redis
- **Purpose:** JWT token blacklist; also provides Spring Cache (`@EnableCaching`).
- **Blacklist key pattern:** `auth:blacklist:<raw-jwt-token-string>`
- **Blacklist TTL:** Remaining seconds until the token's `exp` claim; entry expires automatically.
- **Cache default TTL:** 30 seconds (set in `RedisConfig`). No `@Cacheable` annotations found on methods at this commit.
- **Config:** `SPRING_REDIS_HOST` (default `localhost`), `SPRING_REDIS_PORT` (default `6379`), `spring.data.redis.timeout: 2000ms`.
- **Serialization:** Keys: `StringRedisSerializer`; values: `GenericJackson2JsonRedisSerializer` with `JavaTimeModule`.

### Kafka
- **Infrastructure:** `KafkaConfig` defines producer factory, consumer factory, `KafkaTemplate`, and `ConcurrentKafkaListenerContainerFactory` with exponential backoff (1 s, 2 s, 4 s, max 3 attempts) and dead-letter publishing.
- **Topics produced:** None at this commit.
- **Topics consumed:** None at this commit (`@KafkaListener` not found in source).
- **Config:** `SPRING_KAFKA_BOOTSTRAP_SERVERS` (default `localhost:9092`), consumer group-id `auth-service-group`.

### PostgreSQL
- **Database:** `brokerdealer`, schema `auth_schema`.
- **Config:** `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`.
- DDL managed by Flyway; Hibernate runs in `validate` mode only.

## Configuration

| Key / Env var | Source | Default | Purpose |
|---------------|--------|---------|---------|
| `JWT_SECRET` | Env var (required) | None — startup fails if absent | HMAC-SHA256 signing key for JWTs |
| `SPRING_DATASOURCE_URL` | Env var | `jdbc:postgresql://localhost:5432/brokerdealer?currentSchema=auth_schema` | PostgreSQL JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | Env var | `brokerdealer` | DB username |
| `SPRING_DATASOURCE_PASSWORD` | Env var | `brokerdealer` | DB password |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Env var | `localhost:9092` | Kafka broker address |
| `SPRING_REDIS_HOST` | Env var | `localhost` | Redis host |
| `SPRING_REDIS_PORT` | Env var | `6379` | Redis port |
| `spring.data.redis.timeout` | application.yml | `2000ms` | Redis connection timeout |
| `management.endpoints.web.exposure.include` | application.yml | `health,info,metrics` | Actuator endpoints exposed |

Resilience4j circuit breaker defaults: sliding window 10, failure threshold 50%, open-state wait 10 s, half-open calls 3, timeout 2 s. No named instances override the default in application.yml.

Secrets (`JWT_SECRET`, `SPRING_DATASOURCE_PASSWORD`) are supplied via environment variables. In docker-compose, the JWT_SECRET value is a dev placeholder; it must be replaced with a secret of at least 32 characters in production.

## Tech debt
- 0 TODO or FIXME comments found across all source files.
- Flyway version sequence has a gap: V3 is missing. Versions jump from V2 to V4.
- MapStruct dependency is declared in pom.xml but no mapper interfaces or implementations exist in source; manual `toResponse()` mapping is used in `UserService` instead.
- Kafka infrastructure (producer, consumer, DLT error handler) is fully configured but produces and consumes no topics at this commit — dead configuration weight.
- `GET /api/v1/users` returns a hardcoded `page: 1, pageSize: 50` in the meta block but no actual pagination is implemented (returns all matching rows). [code: controller/UserController.java]
- `UserRole` enum includes `VIEWER` but V1 schema SQL does not; added in V6 migration. The Java enum and DB constraint are now in sync.

## Open questions
- No Kafka topics are produced or consumed by auth-service at this commit. Were Kafka listeners planned and deferred, or is the Kafka configuration carried forward from a template and not needed here?
- The `listUsers` endpoint has hardcoded pagination metadata (`page: 1, pageSize: 50`) with no actual page/size query parameters. Is real pagination required?

— Plumbline 2.8.7 · /pl-onboard · claude-reference v1 · commit 4369a80 · judge pending · AI-drafted, posted after SME review
<!-- plumbline:end reference -->
