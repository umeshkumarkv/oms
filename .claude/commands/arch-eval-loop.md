# Arch Eval Loop — BMAD

Produce a technical design for a JIRA story with automated DoR validation and retry.
Runs design → DoR check → retry on gaps — max 2 attempts.
On persistent gaps after 2 attempts, escalates to BA Agent.

## Usage
```
/arch-eval-loop SCRUM-{n}
```

## How It Works

```
Attempt 1 → Write Design → DoR Check (11 items)
               ↓ ALL GREEN              ↓ ANY GAP
           Add label +            Record gaps → Attempt 2
           Post JIRA                  ↓ ALL GREEN    ↓ ANY GAP (2/2)
                                  Add label +    ESCALATE → BA Agent
                                  Post JIRA
```

Max attempts is 2 — if gaps persist after 2 design iterations the story
has an underlying BA-level problem, not a design problem.

---

## JIRA Write Guard — Human Approval Required

Before performing ANY of the following operations, STOP and request explicit user approval:
- Adding or removing a label on any ticket
- Transitioning a ticket status

Approval format:
```
JIRA ACTION — AWAITING YOUR APPROVAL
  Ticket : SCRUM-{n}
  Action : {add label "bmad-arch-approved" | add label "bmad-arch-blocked" | etc.}
  Reason : {one-line reason}

Proceed? (yes / no)
```
Do NOT execute the JIRA write operation until the user types "yes".

---

## Phase 0 — Pre-flight

1. Fetch the JIRA story: `getJiraIssue(SCRUM-{n})`
2. Verify the story has label `bmad-dor-ready` or that ACs exist — if neither, STOP: "Run /ba-agent first."
3. Read root `CLAUDE.md` fully (all sections)
4. Identify the service from the ticket
5. Read `services/{service}/CLAUDE.md` fully
6. Read `services/{service}/openapi.yaml` — note existing endpoints, schemas, components
7. Check blocked-by tickets — if any is not Done, STOP and list open blockers

---

## Eval Loop State

Initialize before the loop:
```
attempt      = 1
max_attempts = 2
all_gaps     = []   ← accumulated DoR gaps across attempts
loop_passed  = false
```

---

## LOOP — repeat until loop_passed = true or attempt > max_attempts

### Step A — Announce Attempt

Print:
```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
ARCH EVAL LOOP — Attempt {attempt} / {max_attempts}
Story : SCRUM-{n} — {summary}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

If `all_gaps` is not empty, prepend:
```
GAPS FROM PRIOR ATTEMPT — address every gap in this design:
{numbered list of all_gaps}
```

### Step B — Write Technical Design

Produce a design covering every layer the story touches.

#### 1. Service and Endpoint Design
```
ENDPOINT DESIGN
  Method  : {GET | POST | PATCH}
  Path    : /api/v1/{resource}/{id?}
  Auth    : {Bearer JWT — role: TRADER | OPERATIONS | ADMIN | Any}
  Service : {service-name} port {port}
  Gateway : routed via api-gateway port 8080

REQUEST BODY (if POST/PATCH)
  {field}: {type} — {required|optional} — {constraint}

RESPONSE (200/201/202)
  data:
    {field}: {type} — {source}
  meta:
    timestamp: ISO 8601 UTC string
    requestId: UUID string

ERROR RESPONSES
  {HTTP status}: {error.code} — {condition}
```

#### 2. Database Schema Changes
```
SCHEMA CHANGES
  Service schema: {service}_schema
  Migration file: V{next-version}__{description}.sql

  NEW TABLE (if any): {DDL}
  NEW COLUMNS (if any): {ALTER statements}
  NO SCHEMA CHANGE (if applicable)
```

#### 3. Domain Layer
```
ENTITY CHANGES
  Entity  : {ClassName}
  Fields  : {field}: {Java type} — {JPA annotation}
  Enum    : {EnumName} — values: {V1, V2, V3}
  No entity change (if applicable)
```

#### 4. Service Layer Logic
```
SERVICE LOGIC
  Class  : {ServiceName}.java
  Method : {methodName}({params}): {return type}

  Steps:
  1. {Validate / fetch / compute}
  2. {Call external service via Resilience4j (if any)}
  3. {Persist via repository}
  4. {Publish Kafka event (if any)}
  5. {Return DTO — never entity}

  Error paths:
  - {condition} → throw {ExceptionClass}("ERROR_CODE", "message")
```

#### 5. External Service Calls
```
EXTERNAL CALLS (if any)
  {service-name} ({port}): {HTTP method} {path}
  Purpose   : {what data is needed}
  Resilience4j: CircuitBreaker + Retry (3 attempts, 1s/2s/4s backoff)
  Fallback  : {what happens if call fails}

  NONE (if no external calls)
```

#### 6. Kafka Events
```
KAFKA (if any)
  Produces: {topic.name} — payload fields: {field1, field2}
  Consumes: {topic.name} — triggered by: {condition}
  DLT: {topic.name}.DLT on 3 failures

  NONE (if no Kafka)
```

#### 7. Redis Cache
```
REDIS (if any)
  Key pattern : {domain}:{entity}:{id}
  TTL         : {n} seconds
  Invalidation: {trigger event}
  Strategy    : cache-aside

  NONE (if no Redis)
```

#### 8. Test Strategy
```
TEST PLAN
  Unit — ServiceTest:
    - {shouldReturnXWhenY — happy path}
    - {shouldThrowXWhenY — error path}
    - {edge case}

  Unit — ControllerTest (@WebMvcTest):
    - {returns 200/201/202 with correct envelope}
    - {returns 404 for unknown ID}
    - {returns 400/422 for validation failure}

  Coverage target: >= 80% line coverage (JaCoCo)
```

#### 9. OpenAPI Delta
```
OPENAPI DELTA
  ADD path: {/api/v1/resource/{id}}
    operation: {get|post|patch}
    request schema: {inline or $ref}
    response 200: $ref '#/components/schemas/{ResponseName}'
    response 4xx: $ref '#/components/schemas/ErrorResponse'

  ADD component schema: {ResponseName}
    fields: {field: {type, format, example}}

  NO CHANGE (if applicable)
```

### Step C — DoR Validation (11-item checklist)

Check every item. Mark [x] PASS or [ ] GAP with specific reason for each GAP.

```
DEFINITION OF READY CHECKLIST — Attempt {attempt}

  [x/] Story has a clear user story (As a / I want / So that)
       {gap reason if failed}

  [x/] All acceptance criteria are curl-verifiable (each AC maps to an HTTP assertion)
       {gap reason if failed}

  [x/] Service boundary is clear — story touches exactly one service
       {gap reason if failed}

  [x/] No blocked-by tickets are open
       {gap reason if failed}

  [x/] No dependency on CLAUDE.md Section 18 (Out of Scope)
       {gap reason if failed}

  [x/] Endpoint path and HTTP verb confirmed
       {gap reason if failed}

  [x/] Response shape designed — all fields named and typed
       {gap reason if failed}

  [x/] Error codes defined for every failure path in the ACs
       {gap reason if failed}

  [x/] DB migration scoped — or explicitly confirmed not needed
       {gap reason if failed}

  [x/] External service calls identified — Resilience4j circuit breaker planned
       {gap reason if failed}

  [x/] Test strategy written — >= 3 unit test cases per layer (service + controller)
       {gap reason if failed}
```

### Step D — Decision

Collect this attempt's gaps:
```
attempt_gaps = [all DoR items marked as GAP]
```

**PASS condition:** `attempt_gaps` is empty (all 11 items checked)

Print DoR scorecard:
```
DoR SCORECARD — Attempt {attempt}
  User story clear             : {PASS | GAP}
  ACs curl-verifiable          : {PASS | GAP}
  Service boundary clear       : {PASS | GAP}
  No open blockers             : {PASS | GAP}
  Not out of scope             : {PASS | GAP}
  Endpoint path + verb         : {PASS | GAP}
  Response shape designed      : {PASS | GAP}
  Error codes defined          : {PASS | GAP}
  DB migration scoped          : {PASS | GAP}
  External calls + Resilience4j: {PASS | GAP}
  Test strategy written        : {PASS | GAP}

  VERDICT : {ALL PASSED — design approved | GAPS — {n} items incomplete}
```

**If ALL PASSED:**
- Set `loop_passed = true`
- Exit the loop → go to Phase 1 (JIRA Update)

**If GAPS and attempt < max_attempts:**
- Append `attempt_gaps` to `all_gaps`
- Increment `attempt`
- Return to LOOP start (Step A) — next attempt will address all gaps

**If GAPS and attempt = max_attempts:**
- Go to Escalation

---

## Phase 1 — JIRA Update (reached only on PASS)

1. Post full technical design as a JIRA comment on SCRUM-{n}:
```
Arch Eval Loop ({date}): Technical design complete — passed DoR on attempt {attempt}/{max_attempts}.

{paste full design from Step B}

DEFINITION OF READY: ALL 11 CHECKS PASSED
Story is ready for development. Run: /dev-eval-loop SCRUM-{n}
```

2. Apply the JIRA Write Guard, then add label `bmad-arch-approved` to the ticket.

---

## Phase 2 — Summary to User

```
ARCH EVAL LOOP — SCRUM-{n} COMPLETE
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Story      : {summary}
Service    : {service-name}
Eval Loop  : Passed on attempt {attempt} / {max_attempts}
{if attempt > 1: Gaps resolved: {list what changed}}

DESIGN COMPLETE
  Endpoint   : {METHOD} {path}
  DB change  : {yes — V{n}__migration.sql | no}
  Kafka      : {produces X | consumes Y | none}
  Redis      : {new key | none}
  Ext calls  : {service list | none}
  Tests      : {n} unit test cases planned

DoR STATUS : APPROVED (label: bmad-arch-approved)

Next step  : /dev-eval-loop SCRUM-{n}
```

---

## Escalation — reached when all 2 attempts have gaps

```
ARCH EVAL LOOP — ESCALATION REQUIRED
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
SCRUM-{n} has DoR gaps that could not be resolved in {max_attempts} design attempts.
This indicates a story-level problem, not a design problem.

Persistent gaps:
{numbered list of all_gaps with specific reason for each}

Root cause: The story as written by BA Agent is missing information
that the Architect cannot invent. A BA Agent revision is required.

Recommended action:
1. Run /ba-agent epic:{epic-key} to revisit the story
2. Specifically address: {list of gap areas}
3. Re-run /arch-eval-loop SCRUM-{n} after story is updated
```

Post to JIRA (apply JIRA Write Guard):
- Add comment with the escalation block above
- Apply JIRA Write Guard, then add label `bmad-arch-blocked`

STOP.

---

## Hard Stops — always pause and ask the user

- Story has no acceptance criteria — cannot design without them
- Blocked-by tickets are not Done
- Feature requires cross-service database access (violates CLAUDE.md Section 3)
- Feature requires a new microservice not listed in CLAUDE.md Section 3
- Feature is in CLAUDE.md Section 18 (Out of Scope for POC)
- Design requires a new Maven dependency — CLAUDE.md must be updated first
