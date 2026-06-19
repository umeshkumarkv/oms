# Dev Eval Loop — BMAD

Implement a JIRA story with automated code-review evaluation and retry.
Runs implementation → evaluation → retry on failure — max 3 attempts.
On persistent failure after 3 attempts, escalates to human.

## Usage
```
/dev-eval-loop SCRUM-{n}
```

## How It Works

```
Attempt 1 → Implement → Evaluate (12 categories + mvn test + JaCoCo)
              ↓ PASS                    ↓ FAIL
          Commit/Push/PR         Record findings → Attempt 2
                                        ↓ PASS                    ↓ FAIL
                                    Commit/Push/PR         Record findings → Attempt 3
                                                                  ↓ PASS        ↓ FAIL (3/3)
                                                              Commit/Push/PR   ESCALATE → human
```

---

## JIRA Write Guard — Human Approval Required

Before performing ANY of the following operations, STOP and request explicit user approval:
- Adding or removing a label on any ticket
- Transitioning a ticket status

Approval format:
```
JIRA ACTION — AWAITING YOUR APPROVAL
  Ticket : SCRUM-{n}
  Action : {transition to "In Progress" | transition to "In Review" | etc.}
  Reason : {one-line reason}

Proceed? (yes / no)
```
Do NOT execute the JIRA write operation until the user types "yes".

---

## GitHub Write Guard — Human Approval Required

Before performing ANY of the following operations, STOP and request explicit user approval:
- `git push` to any remote branch
- `gh pr create` to raise a pull request

Approval format:
```
GITHUB ACTION — AWAITING YOUR APPROVAL
  Action  : {push branch "ai/SCRUM-{n}-{slug}" to origin | create PR "{title}" → develop}
  Branch  : ai/SCRUM-{n}-{slug}
  Reason  : {one-line reason}

Proceed? (yes / no)
```
Do NOT execute the git push or gh pr create until the user types "yes".

---

## Phase 0 — Pre-flight (all gates must pass before any code)

1. Fetch the JIRA story using `getJiraIssue(SCRUM-{n})`
2. Extract: summary, description, acceptance criteria, tech constraints, blocked-by links, labels

### Gate 1 — Definition of Ready
- Must have label `bmad-dor-ready` — if missing, STOP: "Run /ba-agent first."
- Must have label `bmad-arch-approved` — if missing, STOP: "Run /architect-agent SCRUM-{n} first."
- Must NOT have label `bmad-arch-blocked` — if present, STOP and show the blocking comment.

### Gate 2 — Blockers
3. Check every blocked-by ticket — if any is not Done, STOP and list the open blockers.

### Gate 3 — JIRA Transition
4. Apply the JIRA Write Guard, then transition JIRA: To Do → **In Progress**
5. Add JIRA comment: "Dev Eval Loop starting SCRUM-{n} — attempt 1 of 3."
6. Read `CLAUDE.md` fully (all sections)
7. Identify the service from the ticket
8. Read `services/{service}/CLAUDE.md` fully
9. Read `services/{service}/openapi.yaml` — extract exact endpoint path, HTTP method, request/response shape, field names, HTTP status codes
10. Check if branch `ai/SCRUM-{n}-*` already exists: `git branch -a | grep SCRUM-{n}`
    - If exists: `git checkout ai/SCRUM-{n}-{slug}`
    - If not: `git checkout develop && git pull origin develop && git checkout -b ai/SCRUM-{n}-{kebab-slug-of-summary}`

---

## Eval Loop State

Initialize before the loop:
```
attempt      = 1
max_attempts = 3
all_findings = []   ← accumulated FAIL items across attempts
loop_passed  = false
```

---

## LOOP — repeat until loop_passed = true or attempt > max_attempts

### Step A — Announce Attempt

Print:
```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
DEV EVAL LOOP — Attempt {attempt} / {max_attempts}
Story : SCRUM-{n} — {summary}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

If `all_findings` is not empty, prepend this block before implementing:
```
MANDATORY FIXES FROM PRIOR ATTEMPTS — resolve every item before this attempt is evaluated:
{numbered list of all_findings}
```

### Step B — Implement (always in this layer order)

1. **Domain** — add fields or new enum to JPA entity if needed
2. **Flyway migration** — `V{next}__description.sql` for any schema changes
3. **DTOs** — `dto/request/XxxRequest.java` and `dto/response/XxxResponse.java`
4. **Mapper** — MapStruct mapper in `mapper/XxxMapper.java`
5. **Repository** — new query methods in `repository/XxxRepository.java` if needed
6. **Service** — all business logic in `service/XxxService.java`; return DTOs, never entities
7. **Controller** — thin layer in `controller/XxxController.java`; delegate to service; use standard envelope
8. **Exception** — new `exception/XxxException.java` + update `GlobalExceptionHandler` if needed
9. **Config** — Resilience4j, RestTemplate, Redis config if this story requires external calls
10. **Tests** — unit tests for service + controller; cover every AC + every error path

#### Mandatory standards — enforce every line

- BigDecimal for ALL monetary values — never double, float, long, int
- UUID for ALL primary keys — never Long, Integer, String
- Instant for UTC timestamps, LocalDate for date-only — never Date or Calendar
- @Enumerated(EnumType.STRING) on every enum field — never ORDINAL
- @RequiredArgsConstructor for DI — never @Autowired on fields
- @Slf4j on every class that logs — log IDs and states only, never PII or monetary values
- Standard response envelope: `{ "data": {}, "meta": { "timestamp": "...", "requestId": "..." } }`
- Standard error envelope: `{ "error": { "code": "...", "message": "..." }, "meta": {} }`
- Controller is thin — no business logic
- Service never returns JPA entities — always map to DTO first
- Write blocking code — never Mono/Flux/Reactor

### Step C — Run Tests

```bash
cd services/{service}
JAVA_HOME=/Users/umeshkumarkotaladinnevaradaraj/Library/Java/JavaVirtualMachines/corretto-21.0.5/Contents/Home \
  mvn test 2>&1 | tail -40
```

If `mvn test` fails:
- Read the failure output carefully
- Fix the root cause
- Run `mvn test` again (one fix attempt per loop attempt)
- If still failing after fix: record as a finding, do NOT loop further — go to Step D

```bash
JAVA_HOME=/Users/umeshkumarkotaladinnevaradaraj/Library/Java/JavaVirtualMachines/corretto-21.0.5/Contents/Home \
  mvn verify 2>&1 | grep -E "BUILD|JaCoCo|coverage|COVERED"
```

Record:
- test_result: PASS or FAIL (with failure count)
- jacoco_result: PASS (>= 80%) or FAIL (< 80%, with actual %)

### Step D — Evaluate (12-Category Inline Code Review)

Do NOT post to GitHub. This is a local evaluation only.
Check every category and mark PASS or FAIL with file:line evidence for each FAIL.

**Category 1 — Java Type Rules**
- [ ] No `double` or `float` for monetary values — must be `BigDecimal`
- [ ] No `Long` or `Integer` as primary key — must be `UUID`
- [ ] No `Date` or `Calendar` for timestamps — must be `Instant` or `LocalDate`
- [ ] All enum fields annotated `@Enumerated(EnumType.STRING)` — never `ORDINAL`
- [ ] BigDecimal comparisons use `compareTo()` — never `equals()`
- [ ] BigDecimal constructed from String — never from `double`

**Category 2 — Naming Conventions**
- [ ] All class names: PascalCase with correct layer suffix
- [ ] All method and variable names: camelCase
- [ ] All constants and enum values: UPPER_SNAKE_CASE
- [ ] All DB table/column names in migrations: snake_case
- [ ] All REST paths: kebab-case plural nouns
- [ ] JSON fields: camelCase

**Category 3 — Lombok**
- [ ] Entities: `@Data @Builder @NoArgsConstructor @AllArgsConstructor`
- [ ] Services/Consumers/Producers: `@RequiredArgsConstructor @Slf4j`
- [ ] Controllers: `@RequiredArgsConstructor`
- [ ] Response DTOs: `@Value @Builder` (immutable)
- [ ] Request DTOs: `@Data`
- [ ] No `@Autowired` on any field

**Category 4 — Dependency Injection**
- [ ] All dependencies via constructor (`@RequiredArgsConstructor`)
- [ ] No field injection
- [ ] No setter injection

**Category 5 — Architecture Boundaries**
- [ ] Controller contains zero business logic
- [ ] Service never returns JPA entities
- [ ] No cross-service database access
- [ ] No `ApplicationEvent` — Kafka only
- [ ] No `Mono`, `Flux`, or any Reactor type
- [ ] No `CompletableFuture` chains to avoid blocking

**Category 6 — API Standards**
- [ ] All success responses: `{ "data": {...}, "meta": {...} }`
- [ ] All error responses: `{ "error": {...}, "meta": {} }`
- [ ] Monetary values serialised as JSON String
- [ ] Timestamps: ISO 8601 UTC
- [ ] Base path: `/api/v1/{resource}` — plural nouns

**Category 7 — Entity Column Conventions**
- [ ] All required fields: `@Column(nullable = false)`
- [ ] All enum fields: `@Enumerated(EnumType.STRING)`
- [ ] All BigDecimal fields: `@Column(precision = 19, scale = 4)`
- [ ] Audit fields present: `createdAt` (not updatable) and `updatedAt`

**Category 8 — Logging**
- [ ] All logging uses `@Slf4j`
- [ ] No passwords, tokens, or secrets in any log statement
- [ ] No PII in log statements
- [ ] No monetary values logged at INFO or above

**Category 9 — Exception Handling**
- [ ] Custom exceptions extend `RuntimeException`
- [ ] Custom exceptions carry machine-readable `errorCode`
- [ ] `GlobalExceptionHandler` with `@RestControllerAdvice` exists
- [ ] No stack traces in API responses
- [ ] No `throw new RuntimeException("message")` from business logic

**Category 10 — Testing**
- [ ] Unit test for every public service method
- [ ] Controller test covers: 200/201/202, 404, 400/422
- [ ] Tests use `@ExtendWith(MockitoExtension.class)` or `@WebMvcTest`
- [ ] Test method names: camelCase, descriptive

**Category 11 — Security**
- [ ] No hardcoded credentials or secrets
- [ ] No stack traces in API responses (GlobalExceptionHandler)
- [ ] No raw SQL with string concatenation

**Category 12 — Flyway Migrations**
- [ ] Migration file: `V{n}__{description}.sql` (double underscore)
- [ ] Migration only modifies this service's own schema
- [ ] No `DROP TABLE` or `TRUNCATE`
- [ ] Idempotent where possible (`IF NOT EXISTS`, `ON CONFLICT DO NOTHING`)

### Step E — Decision

Collect this attempt's findings:
```
attempt_findings = [all FAIL items from categories 1–12 + test_result FAIL + jacoco FAIL]
```

**PASS condition** — ALL of the following:
- Categories 1–5: zero failures (zero-tolerance categories)
- Categories 6–12: zero failures
- test_result: PASS (0 test failures)
- jacoco_result: PASS (>= 80%)

Print evaluation scorecard:
```
EVAL SCORECARD — Attempt {attempt}
  Cat 1 Type Rules      : {PASS | FAIL — n violations}
  Cat 2 Naming          : {PASS | FAIL — n violations}
  Cat 3 Lombok          : {PASS | FAIL — n violations}
  Cat 4 DI              : {PASS | FAIL — n violations}
  Cat 5 Architecture    : {PASS | FAIL — n violations}
  Cat 6 API Standards   : {PASS | FAIL — n violations}
  Cat 7 Entity Cols     : {PASS | FAIL — n violations}
  Cat 8 Logging         : {PASS | FAIL — n violations}
  Cat 9 Exceptions      : {PASS | FAIL — n violations}
  Cat 10 Testing        : {PASS | FAIL — n violations}
  Cat 11 Security       : {PASS | FAIL — n violations}
  Cat 12 Migrations     : {PASS | FAIL — n violations}
  mvn test              : {PASS | FAIL}
  JaCoCo coverage       : {PASS — nn% | FAIL — nn%}

  VERDICT : {PASSED — proceed to commit/push | FAILED — {n} violations}
```

**If PASSED:**
- Set `loop_passed = true`
- Exit the loop → go to Phase 1 (Commit and Push)

**If FAILED and attempt < max_attempts:**
- Append `attempt_findings` to `all_findings`
- Increment `attempt`
- Add JIRA comment: "Dev Eval Loop attempt {prev_attempt} FAILED — {n} violations. Starting attempt {attempt}."
- Return to LOOP start (Step A)

**If FAILED and attempt = max_attempts:**
- Go to Escalation

---

## Phase 1 — Commit and Push (reached only on PASS)

Stage only the files changed for this ticket:

```bash
git add services/{service}/src/... [list specific files]
git commit -m "$(cat <<'EOF'
feat({service}): {short description} — SCRUM-{n}

- {bullet: what was added/changed}
- {bullet: what was added/changed}
- Eval loop: passed on attempt {attempt}/{max_attempts}

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

Apply the **GitHub Write Guard**, then:
```bash
git push -u origin ai/SCRUM-{n}-{slug}
```

---

## Phase 2 — Pull Request

Apply the **GitHub Write Guard**, then:

```bash
gh pr create \
  --title "feat({service}): {summary} — SCRUM-{n}" \
  --base develop \
  --body "$(cat <<'EOF'
## Summary

- [SCRUM-{n}]({jira-url}) — {one-line description}
- Eval loop: {attempt} attempt(s) — passed on attempt {attempt}/{max_attempts}
{if attempt > 1: list what was fixed between attempts}

## Test plan

- [ ] {AC 1 from ticket}
- [ ] {AC 2 from ticket}
- [ ] Unknown ID returns 404
- [ ] JaCoCo coverage >= 80%

## curl test guide

\`\`\`bash
export TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"trader.john","password":"Demo@1234"}' | jq -r '.data.token')

curl -s http://localhost:8080/api/v1/{resource}/{id} \
  -H "Authorization: Bearer $TOKEN" | jq .
\`\`\`

---
Generated by Dev Eval Loop for SCRUM-{n}
EOF
)"
```

---

## Phase 3 — JIRA Close-out

1. Apply the JIRA Write Guard, then transition JIRA: In Progress → **In Review**
2. Add JIRA comment: "Dev Eval Loop PASSED on attempt {attempt}/{max_attempts}. PR raised: {pr_url}"

---

## Phase 4 — Summary to User

```
DEV EVAL LOOP — SCRUM-{n} COMPLETE
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Story      : {summary}
Branch     : ai/SCRUM-{n}-{slug}
PR         : {pr_url}
JIRA       : In Review
Tests      : PASSED
Coverage   : >= 80%
Eval Loop  : Passed on attempt {attempt} / {max_attempts}
{if attempt > 1: Fixed between attempts: {list of what changed}}

Next step  : /qa-eval-loop SCRUM-{n}  (after code review approves PR)
```

---

## Escalation — reached when all 3 attempts fail

```
DEV EVAL LOOP — ESCALATION REQUIRED
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
SCRUM-{n} failed evaluation after {max_attempts} attempts.

Persistent violations (across all attempts):
{numbered list of all_findings with file:line references}

Human action required:
1. Review the violations above
2. Either fix manually or provide clarifying constraints
3. Re-run /dev-eval-loop SCRUM-{n} after clarification
```

Post to JIRA (apply JIRA Write Guard):
```
Dev Eval Loop ESCALATED — SCRUM-{n}
Failed after 3 attempts. Persistent violations:
{all_findings}
Human review required before retry.
```

STOP — do not push or raise PR.

---

## Hard Stops — always pause and ask the user

- Any blocked-by ticket is not Done
- Acceptance criteria are ambiguous or contradictory
- A new Maven dependency needs to be added (CLAUDE.md must be updated first)
- A schema change would affect another service's tables
- The ticket asks for something in CLAUDE.md Section 18 (Out of Scope)
- `mvn test` fails with a compilation error (not a logic failure) — cannot evaluate
