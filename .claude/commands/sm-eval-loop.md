# SM Eval Loop — BMAD

Run a sprint health check with automated report completeness validation and retry.
Runs health checks → completeness evaluation → retry on incomplete data — max 2 attempts.
Escalates to human for unresolved blockers or DoD gate failures.

## Usage
```
/sm-eval-loop                — daily standup health check
/sm-eval-loop SCRUM-{n}      — single-ticket health check + DoD gate
/sm-eval-loop release-notes  — sprint-close: Release Notes → Confluence + Go/No-Go pack
```

## How It Works (daily mode)

```
Attempt 1 → Fetch sprint data → Run 7 checks → Completeness evaluation (8 items)
              ↓ ALL COMPLETE              ↓ ANY INCOMPLETE
          Post standup report +    Retry incomplete checks → Attempt 2
          JIRA comments                   ↓ ALL COMPLETE      ↓ ANY INCOMPLETE (2/2)
                                      Post standup report  ESCALATE → human (unresolved flags)
                                      JIRA comments
```

---

## Agent Scope — Absolute Boundaries. No Exceptions.

This agent **OWNS**: sprint health monitoring, velocity tracking, blocker detection, and DoD gate enforcement.

This agent **NEVER**:
- Modifies source code, design docs, or JIRA story descriptions
- Makes architecture or implementation decisions
- Closes a ticket without verifying DoD criteria: QA passed + PR merged + CI green

Escalate to human if: a ticket cannot be closed because DoD criteria are unmet.

---

## JIRA Write Guard — Human Approval Required

Before performing ANY of the following operations, STOP and request explicit user approval:
- Adding or removing a label on any ticket
- Transitioning a ticket status

Approval format:
```
JIRA ACTION — AWAITING YOUR APPROVAL
  Ticket : SCRUM-{n}
  Action : {transition to "Done" | add comment | etc.}
  Reason : {one-line reason}

Proceed? (yes / no)
```
Do NOT execute the JIRA write operation until the user types "yes".

---

## Daily Mode — /sm-eval-loop

### Phase 0 — Fetch Sprint State

Record the session start time before any work begins:
```bash
START_TIME=$(date +%s)
```

```
searchJiraIssuesUsingJql(
  "project = SCRUM AND sprint in openSprints() ORDER BY status ASC",
  fields: ["summary", "status", "assignee", "created", "updated", "blockedBy", "acceptanceCriteria", "labels"]
)
```

Group tickets by status: Done | In Review | In Progress | To Do

Fetch the previous sprint's Done count for velocity comparison.

---

### Eval Loop State

Initialize before the loop:
```
attempt      = 1
max_attempts = 2
incomplete   = []   ← checks that returned incomplete or errored data
loop_passed  = false
```

---

### LOOP — repeat until loop_passed = true or attempt > max_attempts

#### Step A — Announce Attempt

Print:
```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
SM EVAL LOOP — Attempt {attempt} / {max_attempts}
Date : {YYYY-MM-DD}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

If `incomplete` is not empty, print:
```
INCOMPLETE FROM PRIOR ATTEMPT — retry these checks:
{list of incomplete checks}
```

#### Step B — Run 7 Health Checks

Run every check below. A ticket that fails a check is "flagged".

**Check 1: Stuck In Progress (> 2 days)**
- For each In Progress ticket: calculate `today - updatedAt`
- If > 2 days: flag as AT RISK

**Check 2: Stuck In Review (> 1 day)**
- For each In Review ticket: calculate `today - updatedAt`
- If > 1 day: flag as NEEDS ATTENTION

**Check 3: Blocked Tickets**
- For each ticket with blocked-by links: check if the blocker ticket is Done
- If blocker is NOT Done: flag as BLOCKED
- Build the full blocker chain

**Check 4: Unassigned To Do Tickets**
- For each To Do ticket with no assignee: flag

**Check 5: Stories Without Acceptance Criteria**
- For each ticket where description contains no "Acceptance Criteria" section: flag

**Check 6: Definition of Done Gate**
For every In Review ticket, verify all 7 DoD items:
```
DEFINITION OF DONE — SCRUM-{n}
  [x] All ACs pass (QA Eval Loop report: PASSED)
  [x] PR approved (label: bmad-code-review-approved OR reviewDecision = APPROVED)
  [x] mvn test passes (0 failures on CI)
  [x] JaCoCo >= 80%
  [x] PR merged to develop
  [x] JIRA ticket in In Review (not skipped from In Progress)
  [x] No open follow-up bugs from QA
```

Verify each item:
```bash
gh pr list --search "SCRUM-{n}" --state merged --json number,mergedAt | jq .
```

**Check 7: Sprint Velocity**
- Count Done tickets and story points this sprint
- Compare to previous sprint Done count
- Calculate: On track / Ahead / Behind

#### Step C — Report Completeness Check (8 items)

Check every item. Mark COMPLETE or INCOMPLETE with reason.

```
REPORT COMPLETENESS CHECK — Attempt {attempt}

  [x/] Sprint data fetched — no JIRA API errors, all tickets returned
       {incomplete reason if failed}

  [x/] All 7 health checks executed — no check skipped due to data error
       {incomplete reason if failed}

  [x/] Every AT RISK ticket has a flagged comment ready to post
       {incomplete reason if failed}

  [x/] Every BLOCKED ticket has a flagged comment ready to post
       {incomplete reason if failed}

  [x/] Every UNASSIGNED ticket has a flagged comment ready to post
       {incomplete reason if failed}

  [x/] DoD gate ran for every In Review ticket
       {incomplete reason if failed}

  [x/] Velocity metric calculated (current + previous sprint counts)
       {incomplete reason if failed}

  [x/] Standup summary covers all 6 sections: Done, In Review, In Progress, Blocked, At Risk, Velocity
       {incomplete reason if failed}
```

#### Step D — Decision

Collect this attempt's incomplete items:
```
attempt_incomplete = [all report completeness items marked INCOMPLETE]
```

**PASS condition:** `attempt_incomplete` is empty (all 8 completeness items met)

Print completeness scorecard:
```
REPORT COMPLETENESS — Attempt {attempt}
  Sprint data fetched          : {COMPLETE | INCOMPLETE}
  All 7 checks executed        : {COMPLETE | INCOMPLETE}
  AT RISK comments ready       : {COMPLETE | INCOMPLETE — {n} tickets}
  BLOCKED comments ready       : {COMPLETE | INCOMPLETE — {n} tickets}
  UNASSIGNED comments ready    : {COMPLETE | INCOMPLETE — {n} tickets}
  DoD gate ran for all In Review : {COMPLETE | INCOMPLETE — {n} tickets}
  Velocity calculated          : {COMPLETE | INCOMPLETE}
  Standup covers all 6 sections: {COMPLETE | INCOMPLETE}

  VERDICT : {ALL COMPLETE — proceed to report | INCOMPLETE — {n} items}
```

**If ALL COMPLETE:**
- Set `loop_passed = true`
- Exit the loop → go to Phase 1

**If INCOMPLETE and attempt < max_attempts:**
- Append `attempt_incomplete` to `incomplete`
- Increment `attempt`
- Re-fetch data for the specific incomplete checks
- Return to LOOP start (Step A)

**If INCOMPLETE and attempt = max_attempts:**
- Go to Escalation (post report with incomplete items flagged)

---

### Phase 1 — Post Standup Report and JIRA Comments

Produce the standup summary:
```
Sprint Health — {YYYY-MM-DD}
Generated by SM Eval Loop — Attempt {attempt}/{max_attempts}

DONE
{List each Done ticket: - SCRUM-{n}: {summary}}
{If none: (none completed yet this sprint)}

IN REVIEW (awaiting merge)
{List each In Review ticket: - SCRUM-{n}: {summary} — PR open {n}h}
{If none: (none)}

IN PROGRESS
{List each In Progress ticket: - SCRUM-{n}: {summary} — {n} day(s)}
{If none: (none)}

BLOCKED
{List each blocked ticket: - SCRUM-{n}: {summary} — blocked by SCRUM-{x} ({blocker status})}
{If none: (none)}

AT RISK
{List stuck tickets: - SCRUM-{n}: {summary} — In Progress {n} days, no update}
{If none: (none)}

UNASSIGNED
{List: - SCRUM-{n}: {summary}}
{If none: (none)}

VELOCITY
  This sprint : {n} tickets Done
  Last sprint : {n} tickets Done
  Trend       : {On track | Ahead | Behind}

FLAGS FOR ACTION
{Summarise what needs human attention today}
{If any DoD check failed: list ticket + missing DoD items}
```

For each flagged ticket, post the appropriate JIRA comment:
- AT RISK: "SM Eval Loop ({date}): In Progress for {n} days without update. Is there a blocker?"
- BLOCKED: "SM Eval Loop ({date}): Blocked by SCRUM-{x} ({status}). Chain: {full chain}."
- UNASSIGNED: "SM Eval Loop ({date}): No assignee. Please assign before next sprint check."
- NO ACS: "SM Eval Loop ({date}): No ACs found. BA or Architect must add ACs."

For In Review tickets where DoD PASSED:
- Add JIRA comment: "SM Eval Loop ({date}): DoD PASSED — all 7 gates verified. Closing as Done."
- Apply the **JIRA Write Guard**, then transition: In Review → **Done**

For In Review tickets where DoD FAILED:
- Add JIRA comment: "SM Eval Loop ({date}): DoD FAILED. Missing: {list items}. Cannot close as Done."
- Do NOT transition

---

### Phase 2 — Report to User

Print full standup summary.
List all JIRA comments posted.
Highlight the most urgent item needing human action today.

```
Execution time : {END_TIME=$(date +%s); elapsed=$((END_TIME-START_TIME)); printf '%dm %ds' $((elapsed/60)) $((elapsed%60))}
Tokens consumed: {session input + output tokens from the Claude Code usage counter if visible}
```

---

### Escalation — incomplete report after 2 attempts

```
SM EVAL LOOP — ESCALATION REQUIRED
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Sprint health check could not be completed after {max_attempts} attempts.

Incomplete items:
{numbered list with reasons}

Root cause: Sprint data is unavailable or a ticket has a state the agent cannot resolve.

Human action required:
{list specific items: which tickets have DoD failures, which data is missing}

Note: Partial standup report posted below with INCOMPLETE items flagged.
```

Print the partial standup report with INCOMPLETE items clearly marked.

---

## Hard Stops — daily mode

- JIRA API returns an error fetching sprint data
- Cannot determine which sprint is active (multiple open sprints)
- A blocker chain creates a circular dependency (A blocks B blocks A)

---

## Single-Ticket Mode — /sm-eval-loop SCRUM-{n}

Runs checks 1–6 against one specific ticket. No eval loop — single pass only (velocity requires sprint-wide data).

### Phase 0 — Fetch Ticket

```
getJiraIssue(issueIdOrKey: "SCRUM-{n}", fields: [...])
```

### Phase 1 — Run Applicable Checks

| Check | Applies when |
|-------|-------------|
| 1. Stuck In Progress (> 2 days) | status = In Progress |
| 2. Stuck In Review (> 1 day) | status = In Review |
| 3. Blocked | ticket has "is blocked by" issuelinks |
| 4. Unassigned | status = To Do and no assignee |
| 5. Missing ACs | description has no "Acceptance Criteria" section |
| 6. DoD Gate | status = In Review |

For DoD gate, run the full 7-item verification (same as daily mode Check 6).

### Phase 2 — Single-Ticket Report

```
SM Eval Loop — SCRUM-{n} — {YYYY-MM-DD}

Ticket  : SCRUM-{n} {summary}
Status  : {status}
Assignee: {name or UNASSIGNED}
Age     : {n} day(s) in current status

CHECKS
  Stuck In Progress  : {OK | AT RISK — {n} days | N/A}
  Stuck In Review    : {OK | NEEDS ATTENTION — {n} days | N/A}
  Blocked            : {None | BLOCKED by SCRUM-{x} ({blocker status})}
  Assignee           : {OK | UNASSIGNED}
  Acceptance Criteria: {Present | MISSING}
  DoD Gate           : {PASSED | FAILED — missing: [...] | N/A (not In Review)}
  Velocity           : N/A (sprint-wide metric — run /sm-eval-loop for full sprint)

ACTIONS TAKEN
  {List each JIRA comment posted or transition applied, or "No action needed"}
```

---

## Sprint Close Mode — /sm-eval-loop release-notes

Run at sprint close after `/qa-eval-loop sprint` and `/devops-eval-loop security-report`.
Assembles the full release pack for the Go/No-Go meeting. Decision is made by humans.

### Step 1 — Verify sprint is complete
```
searchJiraIssuesUsingJql(
  "project = SCRUM AND sprint in openSprints() AND status != Done AND status != 'In Review'"
)
```
If any story is In Progress or To Do — STOP.

### Step 2 — Collect all sprint stories and release artifacts from Confluence.

Search for:
- Sprint Test Plan
- Sprint Test Results
- Security Report

### Step 3 — Publish Release Notes to Confluence
```
createConfluencePage(
  spaceKey: "SCRUM",
  parentPageTitle: "Releases",
  title: "Release Notes — {sprint name} — {date}",
  body: {see template below}
)
```

Release Notes template:
```
h2. {Sprint Name} · {start date} → {end date}
Published: {ISO 8601 date}
Prepared by: SM Eval Loop · Human sign-off required before deployment

h2. What's New — Stories Delivered
|| Story || Summary || Service || Status ||
| SCRUM-{n} | {summary} | {service} | Done |

h2. Defects Fixed
{Stories tagged as bug-fix, or "None"}

h2. Known Issues / Deferred
{Stories moved out of sprint or not completed, or "None"}

h2. Release Artifacts
|| Document || Link || Status ||
| Sprint Test Plan | {url} | {Available / Missing} |
| Sprint Test Results | {url} | {Available / Missing} |
| Security Report | {url} | {Available / Missing} |

h2. Go/No-Go Checklist — For Human Meeting
|| # || Gate || Source || Status ||
| 1 | All sprint ACs validated and passed | Sprint Test Results | [ ] Confirmed |
| 2 | Regression suite: 0 failures | Sprint Test Results | [ ] Confirmed |
| 3 | Veracode SAST: no Critical or High findings | Security Report | [ ] Confirmed |
| 4 | BlackDuck SCA: no Critical CVEs | Security Report | [ ] Confirmed |
| 5 | JaCoCo ≥ 80% on all services — CI green | GitHub Actions | [ ] Confirmed |
| 6 | All sprint PRs merged to develop | GitHub | [ ] Confirmed |
| 7 | No open P1/P2 defects from QA | JIRA | [ ] Confirmed |
| 8 | Stakeholder sign-off received | Meeting | [ ] Confirmed |

h2. Decision
_To be completed in the Go/No-Go meeting._
GO [ ]  ·  NO-GO [ ]
Reason: _______________
Approved by: _______________
```

### Step 4 — Report to user
```
SM Eval Loop — Release Pack Complete
Sprint : {sprint name}
Stories: {n} delivered

Release Notes  : {confluence-url}
Test Plan      : {url or MISSING}
Test Results   : {url or MISSING}
Security Report: {url or MISSING}

NEXT STEP: Schedule Go/No-Go meeting.
```

## Hard Stops — release-notes mode

- Stories still In Progress — cannot produce release pack
- Confluence page creation fails — do not post JIRA comment without a valid page URL
