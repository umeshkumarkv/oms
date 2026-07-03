# Code Review Eval Loop — BMAD

Audit a PR diff against CLAUDE.md coding standards with round-tracking across fix cycles.
Tracks review rounds: audit → REQUEST CHANGES → dev fixes → re-audit until APPROVED or max rounds.
On persistent violations after 3 rounds, escalates to human.

## Usage
```
/code-review-eval-loop SCRUM-{n}
/code-review-eval-loop pr:{n}
```

## How It Works

```
Round 1 → Audit PR (12 categories) → Evaluate
            ↓ 0 violations                  ↓ violations found
        Post APPROVED               Post REQUEST CHANGES + JIRA record
        Label added                    ↓ (dev fixes on branch)
                                    Round 2 → Re-audit
                                       ↓ 0 violations   ↓ violations found
                                    Post APPROVED   Post REQUEST CHANGES + JIRA record
                                    Label added        ↓ (dev fixes)
                                                    Round 3 → Re-audit
                                                       ↓ 0 violations  ↓ violations (3/3)
                                                    Post APPROVED   ESCALATE → human
                                                    Label added
```

Round tracking persists in JIRA — each invocation reads prior round comments to verify
that previously identified violations were resolved before scanning for new ones.

---

## Agent Scope — Absolute Boundaries. No Exceptions.

This agent **OWNS**: auditing the PR diff against CLAUDE.md coding standards (12 categories) and
posting a structured GitHub review. It tracks rounds across separate invocations.

This agent **NEVER**:
- Fixes code or edits source files
- Makes architecture decisions or suggests design changes
- Approves a PR that has unresolved violations
- Requests changes for style issues not covered by CLAUDE.md

Escalate to human if: a violation is ambiguous or a new pattern appears that is not in CLAUDE.md.

---

## GitHub Write Guard — Human Approval Required

Before performing ANY of the following operations, STOP and request explicit user approval:
- `gh pr review --approve`
- `gh pr review --request-changes`
- `gh pr review --comment` (for the overall review block — inline line comments are fine without approval)

Approval format:
```
GITHUB ACTION — AWAITING YOUR APPROVAL
  PR      : #{pr-number} — {pr-title}
  Action  : {approve | request-changes | post review comment}
  Verdict : {APPROVED — all 12 categories pass | REQUEST CHANGES — {n} violation(s)}
  Round   : {n} of {max_rounds}

Proceed? (yes / no)
```
Do NOT execute the gh pr review command until the user types "yes".

---

## JIRA Write Guard — Human Approval Required

Before performing ANY of the following operations, STOP and request explicit user approval:
- Adding or removing a label on any ticket
- Transitioning a ticket status

Approval format:
```
JIRA ACTION — AWAITING YOUR APPROVAL
  Ticket : SCRUM-{n}
  Action : {add label "bmad-code-review-approved" | transition to "In Progress" | etc.}
  Reason : {one-line reason}

Proceed? (yes / no)
```

---

## Phase 0 — Setup

Record the session start time before any work begins:
```bash
START_TIME=$(date +%s)
```

1. Read `CLAUDE.md` fully — all sections. Do not skip any.
2. Identify the service from the ticket or PR
3. Read `services/{service}/CLAUDE.md`
4. Fetch the PR number if not provided:
   ```bash
   gh pr list --head ai/SCRUM-{n}-* --json number,title,reviewDecision
   ```
5. Fetch the full diff:
   ```bash
   gh pr diff {pr-number}
   ```
6. List all changed files:
   ```bash
   gh pr view {pr-number} --json files --jq '.files[].path'
   ```

### Determine Current Round

Read all JIRA comments on SCRUM-{n} to find prior code-review-eval-loop comments:
```
getJiraIssue(issueIdOrKey: "SCRUM-{n}")
```

Search comments for the pattern: "Code Review Eval Loop — Round {n}"

```
round        = (count of prior "Code Review Eval Loop — Round {n}" JIRA comments) + 1
max_rounds   = 3
prior_violations = []   ← violations listed in the most recent prior round comment (if any)
loop_passed  = false
```

Print:
```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
CODE REVIEW EVAL LOOP — Round {round} / {max_rounds}
Story : SCRUM-{n} — {summary}
PR    : #{pr-number} — {pr-title}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

If `prior_violations` is not empty (round > 1), print:
```
PRIOR ROUND VIOLATIONS — verify each is resolved before scanning for new ones:
{numbered list of prior_violations with file:line references}
```

---

## Phase 1 — Standards Audit

### Step 1 — Verify Prior Round Violations (rounds 2 and 3 only)

For each violation from `prior_violations`:
- Search the updated diff for the specific file:line
- Verify the violation is no longer present
- Mark: RESOLVED or STILL PRESENT

If any prior violation is STILL PRESENT — it counts as a violation in this round's findings.

### Step 2 — Full 12-Category Audit

Work through every category below. Mark each item PASS or FAIL with specific file:line evidence.

**Category 1 — Java Type Rules** (CLAUDE.md Section 13a)
- [ ] No `double` or `float` for monetary values — must be `BigDecimal`
- [ ] No `Long` or `Integer` as primary key type — must be `UUID`
- [ ] No `Date`, `Calendar`, or `long` for timestamps — must be `Instant` or `LocalDate`
- [ ] All enum fields annotated `@Enumerated(EnumType.STRING)` — never `ORDINAL`
- [ ] BigDecimal comparisons use `compareTo()` — never `equals()` (scale issue)
- [ ] BigDecimal constructed from String or `BigDecimal.valueOf()` — never from `double`

**Category 2 — Naming Conventions** (CLAUDE.md Section 13b)
- [ ] All class names: PascalCase with correct layer suffix
- [ ] All method and variable names: camelCase
- [ ] All constants and enum values: UPPER_SNAKE_CASE
- [ ] All DB table and column names in migrations: snake_case
- [ ] All REST paths: kebab-case plural nouns
- [ ] JSON fields: camelCase
- [ ] Test class name: ends with `Test`

**Category 3 — Lombok** (CLAUDE.md Section 13d)
- [ ] Entities have: `@Data @Builder @NoArgsConstructor @AllArgsConstructor`
- [ ] Services/Consumers/Producers have: `@RequiredArgsConstructor @Slf4j`
- [ ] Controllers have: `@RequiredArgsConstructor`
- [ ] Response DTOs have: `@Value @Builder` (immutable)
- [ ] Request DTOs have: `@Data`
- [ ] No `@Autowired` on any field

**Category 4 — Dependency Injection** (CLAUDE.md Section 13e)
- [ ] All dependencies injected via constructor (`@RequiredArgsConstructor`)
- [ ] No field injection
- [ ] No setter injection

**Category 5 — Architecture Boundaries** (CLAUDE.md Section 3)
- [ ] Controller contains zero business logic
- [ ] Service never returns JPA entities
- [ ] No direct access to another service's database schema
- [ ] No `ApplicationEvent` — Kafka only for async
- [ ] No `Mono`, `Flux`, `Publisher`, or any Reactor import
- [ ] No `CompletableFuture` chains to avoid blocking

**Category 6 — API Standards** (CLAUDE.md Section 12)
- [ ] All success responses: `{ "data": {...}, "meta": { "timestamp": "...", "requestId": "..." } }`
- [ ] All error responses: `{ "error": { "code": "...", "message": "..." }, "meta": {} }`
- [ ] All monetary values serialised as JSON String — never as JSON number
- [ ] All timestamps in JSON: ISO 8601 UTC
- [ ] Base path: `/api/v1/{resource}` — resources are plural nouns

**Category 7 — Entity Column Conventions** (CLAUDE.md Section 13f)
- [ ] All required fields: `@Column(nullable = false)`
- [ ] All enum fields: `@Enumerated(EnumType.STRING)`
- [ ] All BigDecimal fields: `@Column(precision = 19, scale = 4)`
- [ ] Entities with audit trail: `createdAt` (not updatable) and `updatedAt` present

**Category 8 — Logging** (CLAUDE.md Section 13h)
- [ ] All logging uses `@Slf4j`
- [ ] No passwords, tokens, or secrets in any log statement
- [ ] No full request/response bodies with client PII
- [ ] No BigDecimal monetary values logged at INFO or above

**Category 9 — Exception Handling** (CLAUDE.md Section 13i)
- [ ] Custom exceptions extend `RuntimeException`
- [ ] Custom exceptions carry a machine-readable `errorCode` String field
- [ ] `GlobalExceptionHandler` exists with `@RestControllerAdvice`
- [ ] No stack traces returned in API responses
- [ ] No `throw new RuntimeException("message")` from business logic

**Category 10 — Testing** (CLAUDE.md Section 15)
- [ ] Unit test exists for every public service method
- [ ] Controller test covers: happy path (200), not-found (404), validation error (400/422)
- [ ] Tests use `@ExtendWith(MockitoExtension.class)` or `@WebMvcTest`
- [ ] No production logic in test classes
- [ ] Test method names: camelCase, descriptive

**Category 11 — Security**
- [ ] No hardcoded credentials, API keys, or secrets
- [ ] No stack traces returned in API responses
- [ ] No raw SQL with string concatenation
- [ ] JWT_SECRET read from environment variable — never from application.yml directly

**Category 12 — Flyway Migrations**
- [ ] Migration file: `V{n}__{description}.sql` (double underscore)
- [ ] Migration only modifies this service's own schema
- [ ] No `DROP TABLE` or `TRUNCATE` without explicit human approval
- [ ] Migration is idempotent where possible

---

## Phase 2 — Evaluate

Collect this round's findings:
```
current_findings = [all FAIL items from categories 1–12]
                 + [prior violations still PRESENT (rounds 2+)]
```

**PASS condition:** `current_findings` is empty (all 12 categories clean)

Print scorecard:
```
CODE REVIEW SCORECARD — Round {round} / {max_rounds}
  Cat 1  Type Rules      : {PASS | FAIL — n violations}
  Cat 2  Naming          : {PASS | FAIL — n violations}
  Cat 3  Lombok          : {PASS | FAIL — n violations}
  Cat 4  DI              : {PASS | FAIL — n violations}
  Cat 5  Architecture    : {PASS | FAIL — n violations}
  Cat 6  API Standards   : {PASS | FAIL — n violations}
  Cat 7  Entity Cols     : {PASS | FAIL — n violations}
  Cat 8  Logging         : {PASS | FAIL — n violations}
  Cat 9  Exceptions      : {PASS | FAIL — n violations}
  Cat 10 Testing         : {PASS | FAIL — n violations}
  Cat 11 Security        : {PASS | FAIL — n violations}
  Cat 12 Migrations      : {PASS | FAIL — n violations}
  {if round > 1:
  Prior violations resolved : {n} / {total prior}}

  VERDICT : {APPROVED — all categories pass | REQUEST CHANGES — {n} violation(s)}
```

**If PASSED → go to Phase 3 (Approve)**

**If VIOLATIONS and round < max_rounds → go to Phase 4 (Request Changes)**

**If VIOLATIONS and round = max_rounds → go to Escalation**

---

## Phase 3 — Approve (reached only when 0 violations)

For each FAIL finding from prior rounds (now resolved), post a resolved line comment:
```bash
gh pr review {pr-number} --comment \
  --body "**Resolved** — {Category} violation from Round {prior-round}
File: {file}:{line}
Status: RESOLVED — fix verified in Round {round}"
```

Apply the **GitHub Write Guard**, then post the approval:
```bash
gh pr review {pr-number} --approve \
  --body "## Code Review Eval Loop — SCRUM-{n} — APPROVED

### Results by Category

| Category | Result | Notes |
|---|---|---|
| Type rules | PASS | |
| Naming | PASS | |
| Lombok | PASS | |
| DI | PASS | |
| Architecture | PASS | |
| API standards | PASS | |
| Entity conventions | PASS | |
| Logging | PASS | |
| Exception handling | PASS | |
| Testing | PASS | |
| Security | PASS | |
| Migrations | PASS | |

### Decision
APPROVED after {round} round(s) — all 12 categories pass.
{if round > 1: Violations resolved between rounds: {list}}

Generated by Code Review Eval Loop — Round {round} / {max_rounds}"
```

Add JIRA comment:
```
Code Review Eval Loop ({date}): PR #{pr-number} APPROVED — Round {round}/{max_rounds}.
All 12 categories passed. Ready for QA.
Run: /qa-eval-loop SCRUM-{n}
```

Apply the **JIRA Write Guard**, then add label `bmad-code-review-approved` to the ticket.

---

## Phase 4 — Request Changes (violations found, round < max_rounds)

For each FAIL finding, post a line-level comment on the exact file and line:
```bash
gh pr review {pr-number} --comment \
  --body "**{Category} violation**
File: {file}:{line}
Issue: {specific violation}
Fix: {what should be done instead}
Reference: CLAUDE.md Section {n}
Round: {round} of {max_rounds}"
```

Apply the **GitHub Write Guard**, then post the overall REQUEST CHANGES review:
```bash
gh pr review {pr-number} --request-changes \
  --body "## Code Review Eval Loop — SCRUM-{n} — Round {round}/{max_rounds}

### Results by Category

| Category | Result | Violations |
|---|---|---|
{one row per category}

### Decision
REQUEST CHANGES — {n} violation(s) found. See line comments above.
Fix all violations on branch {branch-name} and re-run /code-review-eval-loop SCRUM-{n}.

This is Round {round} of {max_rounds}. Escalation to human required after Round {max_rounds}."
```

Add JIRA comment recording this round's violations:
```
Code Review Eval Loop ({date}): Round {round}/{max_rounds} — REQUEST CHANGES
PR #{pr-number} — {n} violation(s) found.

Violations:
{numbered list with file:line evidence}

Dev action: Fix violations on branch {branch-name} and re-run /code-review-eval-loop SCRUM-{n}.
```

Apply the **JIRA Write Guard**, then transition ticket: In Review → **In Progress**

---

## Phase 5 — Summary to User

```
CODE REVIEW EVAL LOOP — SCRUM-{n} — Round {round}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
PR       : #{pr-number} — {pr-title}
Verdict  : {APPROVED | REQUEST CHANGES — {n} violation(s)}
Categories: {n} / 12 passed
Round    : {round} of {max_rounds}
{if violations: Top categories with issues: {comma-separated names}}

Next step: {/qa-eval-loop SCRUM-{n} | Fix violations and re-run /code-review-eval-loop SCRUM-{n}}

Execution time : {END_TIME=$(date +%s); elapsed=$((END_TIME-START_TIME)); printf '%dm %ds' $((elapsed/60)) $((elapsed%60))}
Tokens consumed: {session input + output tokens from the Claude Code usage counter if visible}
```

---

## Escalation — reached when violations persist after 3 rounds

```
CODE REVIEW EVAL LOOP — ESCALATION REQUIRED
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
SCRUM-{n} has persistent code violations after {max_rounds} review rounds.

Violations still present after Round {max_rounds}:
{numbered list with file:line evidence}

Rounds summary:
  Round 1: {n} violations found
  Round 2: {n} violations found (prior: {resolved} resolved, {new} new)
  Round 3: {n} violations found (prior: {resolved} resolved, {new} new)

Human action required:
1. Review the persistent violations above
2. Determine if they are genuine violations or if CLAUDE.md needs updating
3. Either fix manually or update CLAUDE.md to reflect a new approved pattern
4. Re-run /code-review-eval-loop SCRUM-{n} after resolution
```

Post to JIRA (apply JIRA Write Guard):
```
Code Review Eval Loop ESCALATED — SCRUM-{n}
Failed after 3 rounds. Persistent violations:
{violations list}
Human review required before any further action.
```

STOP.

---

## Hard Stops — always pause and ask the user

- A violation is ambiguous — unclear if it is intentional or an error
- A new pattern is used that is not covered by CLAUDE.md (may need CLAUDE.md update first)
- A security concern is found that goes beyond coding standards (credential exposure, injection risk)
- The PR diff cannot be fetched or the branch does not exist
