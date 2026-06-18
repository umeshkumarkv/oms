# Developer Agent — SCRUM-58

Implement a JIRA story end-to-end: branch → code → test → commit → PR → JIRA update.

## Usage
```
/dev-agent SCRUM-{n}
```

## JIRA Write Guard — Human Approval Required

Before performing ANY of the following operations, STOP and request explicit user approval:
- Adding or removing a label on any ticket
- Transitioning a ticket status (To Do → In Progress, In Progress → In Review, In Review → Done)

Approval format:
```
JIRA ACTION — AWAITING YOUR APPROVAL
  Ticket : SCRUM-{n}
  Action : {transition to "In Progress" | transition to "In Review" | transition to "Done" | etc.}
  Reason : {one-line reason}

Proceed? (yes / no)
```
Do NOT execute the JIRA write operation until the user types "yes".
If the user types "no", skip the action and note it in the final report.

---

## Phase 0 — Pre-flight (BMAD Gates — all must pass before any code)

1. Fetch the JIRA story using `getJiraIssue(SCRUM-{n})`
2. Extract: summary, description, acceptance criteria, tech constraints, blocked-by links, labels

### Gate 1 — Definition of Ready
Check the ticket labels:
- Must have label `bmad-dor-ready` — if missing, STOP: "Run /ba-agent to complete story decomposition first."
- Must have label `bmad-arch-approved` — if missing, STOP: "Run /architect-agent SCRUM-{n} to produce the technical design first."
- Must NOT have label `bmad-arch-blocked` — if present, STOP and show the architect's blocking comment.

### Gate 2 — Blockers
3. Check every blocked-by ticket status — if any is not Done, STOP and tell the user which blockers are open.

### Gate 3 — JIRA Transition
4. Apply the JIRA Write Guard, then transition JIRA: To Do → **In Progress**
5. Add JIRA comment: "Developer Agent starting work on SCRUM-{n}. DoR and architect design verified. Reading CLAUDE.md and planning implementation."
6. Read `/Users/umeshkumarkotaladinnevaradaraj/code/broker-dealer/CLAUDE.md` fully (all 19 sections)
7. Identify the service from the ticket (client-service, order-service, etc.)
8. Read `services/{service}/CLAUDE.md` fully
9. Read `services/{service}/openapi.yaml` — extract exact endpoint path, HTTP method, request/response shape, field names, HTTP status codes
10. Check if branch `ai/SCRUM-{n}-*` already exists: `git branch -a | grep SCRUM-{n}`
    - If exists: `git checkout ai/SCRUM-{n}-{slug}`
    - If not: proceed to Phase 1

## Phase 1 — Branch

```bash
git checkout develop
git pull origin develop
git checkout -b ai/SCRUM-{n}-{kebab-slug-of-summary}
```

## Phase 2 — Implementation (always in this layer order)

1. **Domain** — add fields or new enum to JPA entity if needed
2. **Flyway migration** — `V{next}__description.sql` for any schema changes
3. **DTOs** — `dto/request/XxxRequest.java` and `dto/response/XxxResponse.java`
4. **Mapper** — MapStruct mapper in `mapper/XxxMapper.java` (entity ↔ DTO)
5. **Repository** — new query methods in `repository/XxxRepository.java` if needed
6. **Service** — all business logic in `service/XxxService.java`; return DTOs, never entities
7. **Controller** — thin layer in `controller/XxxController.java`; delegate to service; use standard envelope
8. **Exception** — new `exception/XxxException.java` + update `GlobalExceptionHandler` if new error codes needed
9. **Config** — Resilience4j, RestTemplate, Redis config if this story requires external calls
10. **Tests** — unit tests in `src/test/` for service + controller; cover every AC + error path

### Mandatory standards — enforce every line

- BigDecimal for ALL monetary values — never double, float, long, int
- UUID for ALL primary keys — never Long, Integer, String
- Instant for UTC timestamps, LocalDate for date-only — never Date or Calendar
- @Enumerated(EnumType.STRING) on every enum field — never ORDINAL
- @RequiredArgsConstructor for DI — never @Autowired on fields
- @Slf4j on every class that logs — log IDs and states only, never PII or monetary values
- Standard response envelope: `{ "data": {}, "meta": { "timestamp": "...", "requestId": "..." } }`
- Standard error envelope: `{ "error": { "code": "...", "message": "..." }, "meta": {} }`
- Controller is thin — no business logic, no if/else beyond delegation
- Service never returns JPA entities — always map to DTO first
- Virtual threads are already configured — write blocking code, never Mono/Flux/Reactor

## Phase 3 — Verify

```bash
cd services/{service}
mvn test
```

If `mvn test` fails:
- Read the failure output carefully
- Fix the root cause
- Run `mvn test` again
- If still failing after 2 attempts: STOP and tell the user what is failing and why

```bash
mvn verify
```

JaCoCo must pass the 80% line coverage gate. If below 80%, add tests for uncovered paths.

**Never push a branch where tests are failing.**

## Phase 4 — Commit and Push

Stage only the files changed for this ticket — never `git add -A` or `git add .`

```bash
git add services/{service}/src/... [list specific files]
git commit -m "$(cat <<'EOF'
feat({service}): {short description} — SCRUM-{n}

- {bullet: what was added/changed}
- {bullet: what was added/changed}

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
git push -u origin ai/SCRUM-{n}-{slug}
```

## Phase 5 — Pull Request

```bash
gh pr create \
  --title "feat({service}): {summary} — SCRUM-{n}" \
  --base develop \
  --body "$(cat <<'EOF'
## Summary

- [SCRUM-{n}]({jira-url}) — {one-line description}
- {bullet: key change}
- {bullet: key change}

## Test plan

- [ ] {AC 1 from ticket}
- [ ] {AC 2 from ticket}
- [ ] Unknown ID returns 404 CLIENT_NOT_FOUND
- [ ] JaCoCo coverage >= 80%

## curl test guide

\`\`\`bash
# Step 1 — get token
export TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"trader.john","password":"Demo@1234"}' | jq -r '.data.token')

# Step 2 — test the endpoint
curl -s http://localhost:8080/api/v1/{resource}/{id} \
  -H "Authorization: Bearer $TOKEN" | jq .
\`\`\`

---
Generated by Developer Agent for SCRUM-{n}
EOF
)"
```

## Phase 6 — JIRA Close-out

1. Apply the JIRA Write Guard, then transition JIRA: In Progress → **In Review**
2. Add JIRA comment: "PR raised: {pr_url} — awaiting review and merge."
3. Add JIRA worklog entry for time spent

## Phase 7 — Summary to User

Print:
```
SCRUM-{n}  : {summary}
Branch     : ai/SCRUM-{n}-{slug}
PR         : {pr_url}
JIRA       : In Review
Tests      : PASSED
Coverage   : >= 80%
```

## On PR Merge (when user tells you it was merged)

1. Apply the JIRA Write Guard, then transition JIRA: In Review → **Done**
2. Confirm: "SCRUM-{n} closed as Done."

## Hard Stops — always pause and ask the user

- Any blocked-by ticket is not Done
- `mvn test` still failing after 2 fix attempts
- Acceptance criteria are ambiguous or contradictory
- A new Maven dependency needs to be added (CLAUDE.md must be updated first)
- A schema change would affect another service's tables
- The ticket asks for something listed in CLAUDE.md Section 18 (Out of Scope)