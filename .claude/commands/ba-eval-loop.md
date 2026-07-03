# BA Eval Loop — BMAD

Decompose a business requirement into JIRA Stories with full acceptance criteria and automated quality validation.
Runs story drafting → quality check → retry on gaps — max 2 attempts per story.
On persistent gaps after 2 attempts, escalates to PM Eval Loop for Epic revision.

## Usage
```
/ba-eval-loop "plain English requirement"
/ba-eval-loop confluence:{pageId}
/ba-eval-loop epic:{epicKey}
```

## How It Works

```
For each Story:
Attempt 1 → Draft Story → Quality Check (9 items)
              ↓ ALL PASS             ↓ ANY GAP
          JIRA ticket +        Record gaps → Attempt 2
          DoR label                 ↓ ALL PASS    ↓ ANY GAP (2/2)
                                JIRA ticket +  ESCALATE → PM Eval Loop
                                DoR label
```

Max attempts is 2 per story — if story quality gaps persist after 2 drafts, the Epic
has an underlying definition problem that the PM Agent must resolve.

---

## Agent Scope — Absolute Boundaries. No Exceptions.

This agent **OWNS**: decomposing requirements into user stories with acceptance criteria and DoR checklist.

This agent **NEVER**:
- Writes technical design, chooses implementation patterns, or mentions specific code
- Modifies source files or JIRA tickets outside its own stories
- Invents acceptance criteria from thin air — ACs must be traceable to the requirement
- Tickets items that are in CLAUDE.md Section 18 (Out of Scope for POC)

Escalate to PM Eval Loop if: requirement scope is ambiguous or the PRD is missing.

---

## JIRA Write Guard — Human Approval Required

Before performing ANY of the following operations, STOP and request explicit user approval:
- Creating a JIRA issue (Epic or Story)
- Adding or removing a label on any ticket
- Transitioning a ticket status

Approval format:
```
JIRA ACTION — AWAITING YOUR APPROVAL
  Ticket : {SCRUM-key or "new Story/Epic"}
  Action : {create Story | add label "bmad-dor-ready" | etc.}
  Reason : {one-line reason}

Proceed? (yes / no)
```
Do NOT execute the JIRA write operation until the user types "yes".
If the user types "no", skip the action and note it in the final report.

---

## Phase 0 — Understand the Requirement

Record the session start time before any work begins:
```bash
START_TIME=$(date +%s)
```

1. Parse the input — if it is a Confluence page ID, fetch the page content first
2. Read `CLAUDE.md` fully — pay special attention to:
   - Section 2: Tech Stack (what is locked)
   - Section 3: Microservice boundaries (which service owns what)
   - Section 6: Two-Level Validation rules
   - Section 7: Kafka Topics
   - Section 18: Out of Scope (CRITICAL — do not ticket these)
3. Identify which microservice(s) are involved based on domain ownership
4. Search JIRA for related existing tickets:
   ```
   searchJiraIssuesUsingJql("project = SCRUM AND text ~ '{keyword}'")
   ```
5. For anything in the requirement that matches CLAUDE.md Section 18 — flag it explicitly to the user and do NOT create a ticket for it
6. Produce the list of Stories to be drafted (one per service boundary)

---

## Eval Loop State

Initialize per story (runs once per story in the decomposition):
```
attempt      = 1
max_attempts = 2
all_gaps     = []   ← accumulated quality gaps for this story across attempts
loop_passed  = false
```

Run the LOOP once per story before moving to the next.

---

## LOOP — repeat until loop_passed = true or attempt > max_attempts

### Step A — Announce Attempt

Print:
```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
BA EVAL LOOP — Story {story_index} of {total_stories} — Attempt {attempt} / {max_attempts}
Story : {draft summary}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

If `all_gaps` is not empty, prepend:
```
GAPS FROM PRIOR ATTEMPT — address every gap in this story draft:
{numbered list of all_gaps}
```

### Step B — Draft the Story

Produce ALL of the following sections for this story:

#### Summary format
`{Verb} {HTTP method} /api/v1/{resource} — {brief outcome description}`

Example: `Implement GET /api/v1/orders/{id} — order detail endpoint via API Gateway`

#### User Story
```
As a {role: trader | operations | admin}
I want {feature}
So that {business outcome}
```

#### AI Context
- Service name and port
- Whether skeleton exists or needs creation
- Seed data available (table name, row count, key values)
- Call path: Frontend → API Gateway (8080) → {service} ({port})
- Response fields and their source columns
- Any external service calls required

#### Acceptance Criteria
Each AC must be:
- Testable with a single curl command against localhost:8080
- Specific about HTTP status code, response field, and value
- Cover: happy path, not-found (404), validation error (400/422)

Format:
```
- [ ] GET http://localhost:8080/api/v1/{resource}/{id} returns 200 with {field}={value}
- [ ] Unknown {id} returns 404 with error code {CODE}
- [ ] Standard response envelope: { data: {}, meta: { timestamp, requestId } }
```

#### Out of Scope
Explicit list of what is NOT in this ticket, including:
- Features that belong to other services
- Features listed in CLAUDE.md Section 18
- Features that are blocked by unimplemented dependencies

#### Tech Constraints
```
- Service: {service-name} (port {port}) — exposed via API Gateway port 8080
- Read CLAUDE.md and services/{service}/CLAUDE.md before writing any code
- Follow openapi.yaml exactly for response shape
- Branch: ai/SCRUM-{n}-{slug} from develop
- PR target: develop
- Blocked by: SCRUM-{x} (if applicable — or "None")
```

#### Story Points
Estimate: 1 (trivial) | 2 (simple DB read) | 3 (service call) | 5 (complex logic) | 8 (multi-layer)

---

### Step C — Story Quality Check (9 items)

Check every item. Mark [x] PASS or [ ] GAP with specific reason for each GAP.

```
STORY QUALITY CHECKLIST — Story {story_index} — Attempt {attempt}

  [x/] User story format correct — "As a / I want / So that" with a specific role
       {gap reason if failed}

  [x/] All ACs are curl-verifiable — each specifies HTTP method, path, status code,
       response field name, and expected value or error code
       {gap reason if failed}

  [x/] Single service boundary — story lives entirely within one microservice
       {gap reason if failed}

  [x/] Story points estimated — one of: 1 / 2 / 3 / 5 / 8 (not 0, not 13+)
       {gap reason if failed}

  [x/] Blocked-by links set — or explicitly confirmed none
       {gap reason if failed}

  [x/] No Section 18 items anywhere in the story description or ACs
       {gap reason if failed}

  [x/] Tech constraints complete — service, port, branch pattern, PR target,
       CLAUDE.md read instruction all present
       {gap reason if failed}

  [x/] Seed / test data identified — table name or synthetic values described
       so the Dev Agent knows what data to use for testing
       {gap reason if failed}

  [x/] AI Context section complete — service name, port, call path,
       response field sources, and external calls all named
       {gap reason if failed}
```

### Step D — Decision

Collect this attempt's gaps:
```
attempt_gaps = [all Story Quality items marked as GAP]
```

**PASS condition:** `attempt_gaps` is empty (all 9 items checked)

Print quality scorecard:
```
STORY QUALITY SCORECARD — Story {story_index} — Attempt {attempt}
  User story format           : {PASS | GAP}
  ACs curl-verifiable         : {PASS | GAP}
  Single service boundary     : {PASS | GAP}
  Story points estimated      : {PASS | GAP}
  Blocked-by set              : {PASS | GAP}
  No Section 18 items         : {PASS | GAP}
  Tech constraints complete   : {PASS | GAP}
  Seed data identified        : {PASS | GAP}
  AI Context complete         : {PASS | GAP}

  VERDICT : {ALL PASSED — story approved | GAPS — {n} items incomplete}
```

**If ALL PASSED:**
- Set `loop_passed = true`
- Exit the loop for this story → proceed to Phase 1 (Create in JIRA)

**If GAPS and attempt < max_attempts:**
- Append `attempt_gaps` to `all_gaps`
- Increment `attempt`
- Return to LOOP start (Step A) — next attempt will address all gaps

**If GAPS and attempt = max_attempts:**
- Go to Escalation for this story — flag it in the final report

---

## Phase 1 — Create in JIRA (reached only on PASS)

For each story that passed:

1. Create Epic if needed: `createJiraIssue(issueTypeName: "Epic", ...)`
2. Apply the JIRA Write Guard, then create the story linked to the Epic:
   `createJiraIssue(issueTypeName: "Story", parent: {epicKey}, ...)`
3. Set blocked-by links using `createIssueLink` where dependencies exist
4. Assign to the Principal Architect (accountId: 712020:a0088ae5-186b-4263-bf8d-41ed37df1f95)

---

## Phase 2 — DoR Checklist + Label

For every story created, output the DoR checklist. A story is NOT ready for development
until ALL items are checked.

```
DEFINITION OF READY — SCRUM-{n}
  [x] User story written (As a / I want / So that)
  [x] Acceptance criteria present and curl-verifiable
  [x] Single microservice boundary — no cross-service DB access
  [x] Story points estimated (1/2/3/5/8)
  [x] Blocked-by links set (or confirmed none)
  [x] No out-of-scope items per CLAUDE.md Section 18
  [x] Tech constraints section complete (service, port, branch, PR target)
  [x] Seed data or test data identified
  [ ] Architect design doc approved (label: bmad-arch-approved) ← run /arch-eval-loop SCRUM-{n}
```

Apply the JIRA Write Guard, then add label `bmad-dor-ready` to each story that passed.

---

## Phase 3 — Summary to User

```
BA EVAL LOOP — DECOMPOSITION COMPLETE
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Epic
  {SCRUM-key}: {epic summary}

Stories Created
  {SCRUM-key}: {story summary} [{points} pts] — Eval Loop: {attempt}/{max} attempts — DoR: 8/8 checks (arch pending)
  ...

Stories Escalated (require PM Eval Loop revision)
  {story summary} — persistent gaps: {list}

Blocked-By Links
  {SCRUM-key} blocked by {SCRUM-key}

Out of Scope (flagged, not ticketed)
  - {item}: reason per CLAUDE.md Section 18

Next Step
  Run /arch-eval-loop SCRUM-{n} for each story before development begins.

Execution time : {END_TIME=$(date +%s); elapsed=$((END_TIME-START_TIME)); printf '%dm %ds' $((elapsed/60)) $((elapsed%60))}
Tokens consumed: {session input + output tokens from the Claude Code usage counter if visible}
```

---

## Escalation — reached when a story has persistent gaps after 2 attempts

```
BA EVAL LOOP — STORY ESCALATION REQUIRED
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Story "{summary}" has quality gaps that could not be resolved in {max_attempts} draft attempts.
This indicates the Epic or requirement needs revision at the PM level.

Persistent gaps:
{numbered list of all_gaps with specific reason for each}

Root cause: The story cannot be written correctly because the requirement
is incomplete or contradictory. The PM Eval Loop must revise the Epic first.

Recommended action:
1. Run /pm-eval-loop epic:{epic-key} to revisit the PRD
2. Specifically address: {list of gap areas}
3. Re-run /ba-eval-loop epic:{epic-key} after the PRD is updated
```

Do NOT create a JIRA ticket for an escalated story. Flag it in the Phase 3 report.

---

## Hard Stops — always pause and ask the user

- Requirement mentions anything in CLAUDE.md Section 18 (Out of Scope for POC)
- Cannot determine which microservice owns a domain concept
- Requirement would require cross-service database access
- Requirement would require a new microservice not in CLAUDE.md Section 3
- Story points estimate is 13+ after both attempts (story too large — needs splitting)
