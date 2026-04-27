# Code Review Agent — SCRUM-61

Audit a PR diff against CLAUDE.md coding standards and post a structured GitHub review.

## Usage
```
/code-review SCRUM-{n}
/code-review pr:{n}
```

## Phase 0 — Setup

1. Read `CLAUDE.md` fully — all 19 sections. Do not skip any.
2. Identify the service from the ticket or PR
3. Read `services/{service}/CLAUDE.md`
4. Fetch the PR number if not provided: `gh pr list --head ai/SCRUM-{n}-*`
5. Fetch the full diff: `gh pr diff {pr-number}`
6. List all changed files: `gh pr view {pr-number} --json files --jq '.files[].path'`

## Phase 1 — Standards Audit

Work through every category below. Mark each item PASS or FAIL with specific file:line evidence.

### Category 1: Java Type Rules (CLAUDE.md Section 13a)

- [ ] No `double` or `float` for monetary values — must be `BigDecimal`
- [ ] No `Long` or `Integer` as primary key type — must be `UUID`
- [ ] No `Date`, `Calendar`, or `long` for timestamps — must be `Instant` or `LocalDate`
- [ ] All enum fields annotated `@Enumerated(EnumType.STRING)` — never `ORDINAL`
- [ ] BigDecimal comparisons use `compareTo()` — never `equals()` (scale issue)
- [ ] BigDecimal constructed from String or `BigDecimal.valueOf()` — never from `double`

### Category 2: Naming Conventions (CLAUDE.md Section 13b)

- [ ] All class names: PascalCase with correct layer suffix (Controller, Service, Repository, Request, Response, Mapper, Exception, Producer, Consumer, Config)
- [ ] All method and variable names: camelCase
- [ ] All constants and enum values: UPPER_SNAKE_CASE
- [ ] All DB table and column names in migrations: snake_case
- [ ] All REST paths: kebab-case plural nouns (`/api/v1/orders`, `/api/v1/risk-rules`)
- [ ] JSON fields: camelCase (`limitPrice`, `expiryDate`)
- [ ] Test class name: ends with `Test` (e.g. `OrderServiceTest`)

### Category 3: Lombok (CLAUDE.md Section 13d)

- [ ] Entities have: `@Data @Builder @NoArgsConstructor @AllArgsConstructor`
- [ ] Services/Consumers/Producers have: `@RequiredArgsConstructor @Slf4j`
- [ ] Controllers have: `@RequiredArgsConstructor`
- [ ] Response DTOs have: `@Value @Builder` (immutable)
- [ ] Request DTOs have: `@Data`
- [ ] No `@Autowired` on any field anywhere in the codebase

### Category 4: Dependency Injection (CLAUDE.md Section 13e)

- [ ] All dependencies injected via constructor (`@RequiredArgsConstructor`)
- [ ] No field injection (`@Autowired private ...`)
- [ ] No setter injection

### Category 5: Architecture Boundaries (CLAUDE.md Section 3)

- [ ] Controller contains zero business logic — only calls service and wraps response
- [ ] Service never returns JPA entities — always maps to DTO before returning
- [ ] No direct access to another service's database schema
- [ ] No `ApplicationEvent` or Spring event system — Kafka only for async
- [ ] No `Mono`, `Flux`, `Publisher`, `Subscriber`, or any Reactor import
- [ ] No `WebFlux` or reactive imports of any kind
- [ ] No `CompletableFuture` chains used to avoid blocking (just block — virtual threads handle it)

### Category 6: API Standards (CLAUDE.md Section 12)

- [ ] All success responses use envelope: `{ "data": {...}, "meta": { "timestamp": "...", "requestId": "..." } }`
- [ ] All error responses use envelope: `{ "error": { "code": "...", "message": "..." }, "meta": {} }`
- [ ] All monetary values serialised as JSON String — never as JSON number
- [ ] All timestamps in JSON: ISO 8601 UTC (`2026-04-16T09:30:00Z`)
- [ ] Base path: `/api/v1/{resource}` — resources are plural nouns

### Category 7: Entity Column Conventions (CLAUDE.md Section 13f)

- [ ] All required fields have `@Column(nullable = false)`
- [ ] All enum fields have `@Enumerated(EnumType.STRING)`
- [ ] All BigDecimal monetary fields have `@Column(precision = 19, scale = 4)`
- [ ] Entities with audit trail have `createdAt` (not updatable) and `updatedAt` fields

### Category 8: Logging (CLAUDE.md Section 13h)

- [ ] All logging uses `@Slf4j` (Lombok)
- [ ] No passwords, tokens, or secrets in any log statement
- [ ] No full request/response bodies with client PII logged
- [ ] No BigDecimal monetary values logged at INFO or above
- [ ] Only IDs and states logged at INFO level

### Category 9: Exception Handling (CLAUDE.md Section 13i)

- [ ] Custom exceptions extend `RuntimeException`
- [ ] Custom exceptions carry a machine-readable `errorCode` String field
- [ ] `GlobalExceptionHandler` exists with `@RestControllerAdvice`
- [ ] No stack traces returned in API responses
- [ ] No `throw new RuntimeException("message")` from business logic — use domain exceptions

### Category 10: Testing (CLAUDE.md Section 15)

- [ ] Unit test exists for every public service method
- [ ] Controller test covers: happy path (200), not-found (404), validation error (400/422)
- [ ] Tests use `@ExtendWith(MockitoExtension.class)` or `@WebMvcTest` as appropriate
- [ ] No production logic in test classes
- [ ] Test method names: camelCase, descriptive (`shouldReturnClientWhenFound`)

### Category 11: Security

- [ ] No hardcoded credentials, API keys, or secrets anywhere in the diff
- [ ] No stack traces returned in API responses (double-check GlobalExceptionHandler)
- [ ] No raw SQL with string concatenation (SQL injection risk)
- [ ] JWT_SECRET read from environment variable — never from application.yml directly

### Category 12: Flyway Migrations

- [ ] Migration file follows naming: `V{n}__{description}.sql` (double underscore)
- [ ] Migration only modifies the service's own schema — never touches another service's schema
- [ ] No `DROP TABLE` or `TRUNCATE` without explicit human approval
- [ ] Migration is idempotent where possible (`IF NOT EXISTS`, `ON CONFLICT DO NOTHING`)

## Phase 2 — Post GitHub Review

For each FAIL finding, post a line-level comment on the exact file and line in GitHub:

```bash
gh pr review {pr-number} --comment \
  --body "**{Category} violation**
File: {file}:{line}
Issue: {specific violation}
Fix: {what should be done instead}
Reference: CLAUDE.md Section {n}"
```

After all line comments, post the overall review:

```bash
gh pr review {pr-number} \
  --{approve|request-changes} \
  --body "## Code Review Agent — SCRUM-{n}

### Results by Category

| Category | Result | Violations |
|---|---|---|
| Type rules | PASS/FAIL | {n} |
| Naming | PASS/FAIL | {n} |
| Lombok | PASS/FAIL | {n} |
| DI | PASS/FAIL | {n} |
| Architecture | PASS/FAIL | {n} |
| API standards | PASS/FAIL | {n} |
| Entity conventions | PASS/FAIL | {n} |
| Logging | PASS/FAIL | {n} |
| Exception handling | PASS/FAIL | {n} |
| Testing | PASS/FAIL | {n} |
| Security | PASS/FAIL | {n} |
| Migrations | PASS/FAIL | {n} |

### Decision
{APPROVED — all categories pass}
{REQUEST CHANGES — {n} violation(s) found, see line comments above}
"
```

## Hard Stops — always pause and ask the user

- A violation is ambiguous — unclear if it is intentional or an error
- A new pattern is used that is not covered by CLAUDE.md (may need CLAUDE.md update)
- A security concern is found that goes beyond coding standards (e.g. credential exposure)