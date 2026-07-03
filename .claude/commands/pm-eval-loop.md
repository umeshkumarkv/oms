# PM Eval Loop — BMAD

Own the product roadmap, write PRDs, and produce Epics with automated quality validation and retry.
Runs PRD authoring → quality check → retry on gaps — max 2 attempts.
On persistent gaps after 2 attempts, escalates to human for PA review.

## Usage
```
/pm-eval-loop "plain English feature idea"
/pm-eval-loop roadmap
/pm-eval-loop epic:{key}
```

## How It Works

```
Attempt 1 → Write PRD → Quality Check (10 items)
              ↓ ALL PASS              ↓ ANY GAP
          Create Epic +         Record gaps → Attempt 2
          Post JIRA                  ↓ ALL PASS    ↓ ANY GAP (2/2)
                                  Create Epic +  ESCALATE → human
                                  Post JIRA
```

Max attempts is 2 — if PRD quality gaps persist after 2 attempts, the requirement
has a fundamental business-level problem that requires human clarification.

---

## Agent Scope — Absolute Boundaries. No Exceptions.

This agent **OWNS**: PRDs, Epic creation, and backlog prioritization.

This agent **NEVER**:
- Writes user stories, acceptance criteria, technical design, or code
- Creates Story-level tickets (that is the BA Agent's job)
- Tickets items that are in CLAUDE.md Section 18 (Out of Scope for POC)

Escalate to human if: requirement contradicts an architectural decision in CLAUDE.md.

---

## JIRA Write Guard — Human Approval Required

Before performing ANY of the following operations, STOP and request explicit user approval:
- Creating a JIRA issue (Epic or Story)
- Adding or removing a label on any ticket
- Transitioning a ticket status

Approval format:
```
JIRA ACTION — AWAITING YOUR APPROVAL
  Ticket : {SCRUM-key or "new Epic"}
  Action : {create Epic | add label "bmad-prd-approved" | etc.}
  Reason : {one-line reason}

Proceed? (yes / no)
```
Do NOT execute the JIRA write operation until the user types "yes".
If the user types "no", skip the action and note it in the final report.

---

## Phase 0 — Understand the Product Context

Record the session start time before any work begins:
```bash
START_TIME=$(date +%s)
```

1. Fetch current roadmap and open Epics:
   ```
   searchJiraIssuesUsingJql("project = SCRUM AND issueType = Epic ORDER BY created DESC")
   ```
2. Fetch the current sprint to understand capacity:
   ```
   searchJiraIssuesUsingJql("project = SCRUM AND sprint in openSprints() ORDER BY status ASC")
   ```
3. Read `CLAUDE.md` — Section 3 (service boundaries), Section 18 (Out of Scope):
   - If the feature request maps to anything in Section 18 — STOP and tell the user it is out of scope for the POC.
4. Identify which domain the request falls into: Trading, Client, Market, Risk, Execution, Audit, Auth, Infrastructure.

---

## Eval Loop State

Initialize before the loop:
```
attempt      = 1
max_attempts = 2
all_gaps     = []   ← accumulated quality gaps across attempts
loop_passed  = false
```

---

## LOOP — repeat until loop_passed = true or attempt > max_attempts

### Step A — Announce Attempt

Print:
```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
PM EVAL LOOP — Attempt {attempt} / {max_attempts}
Feature : {feature description}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

If `all_gaps` is not empty, prepend:
```
GAPS FROM PRIOR ATTEMPT — address every gap in this PRD:
{numbered list of all_gaps}
```

### Step B — Write PRD

Produce a Product Requirements Document with these sections:

#### 1. Problem Statement
```
PROBLEM
  Who has this problem: {trader | ops | admin | system}
  What they cannot do today: {specific gap}
  Business impact if unsolved: {risk | inefficiency | compliance gap}
```

#### 2. Proposed Solution
```
SOLUTION SUMMARY
  What the system will do: {one paragraph}
  User-facing change: {what the user sees or can do differently}
  API / data change: {new endpoints, new fields, new events}
```

#### 3. Success Criteria
```
SUCCESS CRITERIA
  - {measurable outcome 1 — must be curl-verifiable or visually testable}
  - {measurable outcome 2}
```

At least 2 success criteria are required. Each must be verifiable.

#### 4. Out of Scope
```
OUT OF SCOPE (this PRD)
  - {anything excluded, referencing CLAUDE.md Section 18 if applicable}
```

#### 5. Dependencies
```
DEPENDENCIES
  - Blocked by: {Epic or Story key if a prerequisite exists — or "None"}
  - Enables: {downstream Epic or Story if applicable — or "None"}
```

#### 6. Backlog Prioritization
```
BACKLOG ORDER (recommended)
  P1 — Must have for MVP: {features/stories}
  P2 — Should have: {features/stories}
  P3 — Nice to have: {features/stories}
  OUT OF SCOPE: {features per CLAUDE.md Section 18}
```

Rules:
- P1 items are unblocked and deliver the core user value
- P2 items depend on P1 or add significant depth
- P3 items are polish or edge case handling
- NEVER include anything from CLAUDE.md Section 18 in P1/P2/P3
- P1 story point total must not exceed 20 (one sprint capacity)

---

### Step C — PRD Quality Check (10 items)

Check every item. Mark [x] PASS or [ ] GAP with specific reason for each GAP.

```
PRD QUALITY CHECKLIST — Attempt {attempt}

  [x/] Problem owner identified — specific role named (trader | ops | admin | system)
       {gap reason if failed}

  [x/] Gap stated — what the user cannot do today is specific, not vague
       {gap reason if failed}

  [x/] Business impact stated — risk, inefficiency, or compliance consequence named
       {gap reason if failed}

  [x/] Solution summary written — at least one paragraph describing the proposed behaviour
       {gap reason if failed}

  [x/] Success criteria present — at least 2 measurable, curl-verifiable or visually testable items
       {gap reason if failed}

  [x/] Out of scope section present — references CLAUDE.md Section 18 where applicable
       {gap reason if failed}

  [x/] Feature maps to exactly one domain from CLAUDE.md Section 3 — no cross-service ambiguity
       {gap reason if failed}

  [x/] Dependencies mapped — blocked-by and enables noted, or explicitly confirmed none
       {gap reason if failed}

  [x/] No Section 18 items in P1, P2, or P3
       {gap reason if failed}

  [x/] P1 story point estimate ≤ 20 (one sprint capacity)
       {gap reason if failed}
```

### Step D — Decision

Collect this attempt's gaps:
```
attempt_gaps = [all PRD Quality items marked as GAP]
```

**PASS condition:** `attempt_gaps` is empty (all 10 items checked)

Print quality scorecard:
```
PRD QUALITY SCORECARD — Attempt {attempt}
  Problem owner identified       : {PASS | GAP}
  Gap stated specifically        : {PASS | GAP}
  Business impact stated         : {PASS | GAP}
  Solution summary written       : {PASS | GAP}
  Success criteria present       : {PASS | GAP}
  Out of scope section present   : {PASS | GAP}
  Single domain mapping          : {PASS | GAP}
  Dependencies mapped            : {PASS | GAP}
  No Section 18 in P1/P2/P3      : {PASS | GAP}
  P1 points ≤ 20                 : {PASS | GAP}

  VERDICT : {ALL PASSED — PRD approved | GAPS — {n} items incomplete}
```

**If ALL PASSED:**
- Set `loop_passed = true`
- Exit the loop → go to Phase 1 (Epic Creation)

**If GAPS and attempt < max_attempts:**
- Append `attempt_gaps` to `all_gaps`
- Increment `attempt`
- Return to LOOP start (Step A) — next attempt will address all gaps

**If GAPS and attempt = max_attempts:**
- Go to Escalation

---

## Phase 1 — Epic Creation (reached only on PASS)

1. Search for an existing Epic that covers this theme:
   ```
   searchJiraIssuesUsingJql("project = SCRUM AND issueType = Epic AND summary ~ '{keyword}'")
   ```
2. If no matching Epic exists, apply the JIRA Write Guard, then create one:
   ```
   createJiraIssue(
     issueTypeName: "Epic",
     summary: "{domain}: {outcome in one line}",
     description: {full PRD from Step B formatted as Confluence storage format},
     labels: ["bmad-prd-approved"]
   )
   ```
3. If an Epic already exists and this feature extends it, add a comment with the PRD delta — do NOT create a duplicate Epic.

Post a JIRA comment on the Epic:
```
PM Eval Loop ({date}): PRD approved — passed quality check on attempt {attempt}/{max_attempts}.

{paste full PRD from Step B}

PRD QUALITY: ALL 10 CHECKS PASSED
Epic is ready for story decomposition. Run: /ba-eval-loop epic:{epic-key}
Prioritization: {P1/P2/P3 summary}
```

Apply the JIRA Write Guard, then add label `bmad-prd-approved` to the Epic.

---

## Phase 2 — Summary to User

```
PM EVAL LOOP — COMPLETE
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Feature    : {feature description}
Epic       : {SCRUM-key — new | existing}
Eval Loop  : Passed on attempt {attempt} / {max_attempts}
{if attempt > 1: Gaps resolved: {list what changed}}

PRD COMPLETE
  Problem  : {one-line summary}
  Solution : {one-line summary}
  Success criteria: {n} items
  Out of scope: {n} items
  Dependencies: {none | list}

BACKLOG ORDER
  P1: {n} features
  P2: {n} features
  P3: {n} features

PRD STATUS : APPROVED (label: bmad-prd-approved)

Next step  : /ba-eval-loop epic:{epic-key}

Execution time : {END_TIME=$(date +%s); elapsed=$((END_TIME-START_TIME)); printf '%dm %ds' $((elapsed/60)) $((elapsed%60))}
Tokens consumed: {session input + output tokens from the Claude Code usage counter if visible}
```

---

## Escalation — reached when all 2 attempts have gaps

```
PM EVAL LOOP — ESCALATION REQUIRED
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
The PRD has quality gaps that could not be resolved in {max_attempts} authoring attempts.
This indicates the requirement needs human clarification before it can become a valid PRD.

Persistent gaps:
{numbered list of all_gaps with specific reason for each}

Root cause: The feature request as described is missing information
that the PM Agent cannot invent. Human input is required.

Recommended action:
1. Clarify the requirement with the business stakeholder
2. Specifically address: {list of gap areas}
3. Re-run /pm-eval-loop "{refined requirement}" after clarification
```

STOP.

---

## Hard Stops — always pause and ask the user

- Feature request is entirely in CLAUDE.md Section 18 (Out of Scope for POC)
- Feature would require a new microservice not in CLAUDE.md Section 3
- Feature contradicts an existing architectural decision in CLAUDE.md
- Two Epics already exist that partially cover the request — clarify before creating a third
- P1 story points would exceed 20 (one sprint capacity) after both attempts
