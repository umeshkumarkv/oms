# QA Eval Loop — BMAD

Validate all acceptance criteria against the running system with automated retry.
Runs AC execution → evaluation → retry on failure — max 3 attempts.
On persistent failure after 3 attempts, escalates to human.

## Usage
```
/qa-eval-loop SCRUM-{n}
```

## How It Works

```
Attempt 1 → Execute ACs → Evaluate results
              ↓ ALL PASS                 ↓ ANY FAIL
          Post JIRA report       Record failures → Attempt 2
                                      ↓ ALL PASS             ↓ ANY FAIL
                                  Post JIRA report    Record failures → Attempt 3
                                                            ↓ ALL PASS   ↓ ANY FAIL (3/3)
                                                        Post JIRA report  ESCALATE → human
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
  Action : {transition to "In Review" | transition to "In Progress" | etc.}
  Reason : {one-line reason}

Proceed? (yes / no)
```
Do NOT execute the JIRA write operation until the user types "yes".

---

## Phase 0 — Setup (BMAD Gate — Code Review must be approved first)

1. Fetch the JIRA story: `getJiraIssue(SCRUM-{n})`
2. Extract every acceptance criterion line by line from the description
3. Identify the service and endpoint(s) under test

### Gate — Code Review Approved

```bash
gh pr list --search "SCRUM-{n}" --json number,title,reviewDecision,headRefName | jq .
```

- If `reviewDecision` is NOT `"APPROVED"` — STOP: "Run /code-review SCRUM-{n} first."
- If no PR exists — STOP: "No PR found. Run /dev-eval-loop SCRUM-{n} first."

4. Verify required Docker services are running:
```bash
docker ps --format "table {{.Names}}\t{{.Status}}" | grep -E "api-gateway|client-service|order-service|auth-service"
```
If any required service is not running — STOP and tell the user.

5. Obtain JWT token:
```bash
export TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"trader.john","password":"Demo@1234"}' | jq -r '.data.token')
```
Verify the token is not null/empty before proceeding.

---

## Eval Loop State

Initialize before the loop:
```
attempt        = 1
max_attempts   = 3
failed_acs     = []   ← ACs that failed in current attempt
all_failures   = {}   ← map of AC → failure evidence across attempts
loop_passed    = false
```

---

## LOOP — repeat until loop_passed = true or attempt > max_attempts

### Step A — Announce Attempt

Print:
```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
QA EVAL LOOP — Attempt {attempt} / {max_attempts}
Story : SCRUM-{n} — {summary}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

If `all_failures` is not empty, print:
```
PREVIOUSLY FAILED ACs — watch these specifically this attempt:
{list of previously failed ACs and their failure reason}
```

### Step B — Execute All Acceptance Criteria

For each AC extracted from the JIRA ticket:
1. Write the exact curl command that tests it
2. Execute: `curl -s -w "\nHTTP_STATUS:%{http_code}" ...`
3. Parse: extract HTTP status code, relevant response fields
4. Assert: compare actual to expected
5. Mark PASS or FAIL
6. On FAIL: capture full response body as evidence

Assertion rules:
- HTTP status must match exactly
- Response must use envelope: `{ "data": {}, "meta": { "timestamp", "requestId" } }`
- Error responses must use: `{ "error": { "code": "...", "message": "..." }, "meta": {} }`
- Monetary values in JSON must be Strings — not numbers (`"100.00"` not `100.00`)
- Timestamps must be ISO 8601 UTC format

### Step C — Execute Mandatory Edge Cases (always run)

```bash
# 1. Unknown ID → 404
curl -s -w "\nHTTP_STATUS:%{http_code}" \
  http://localhost:8080/api/v1/{resource}/00000000-0000-0000-0000-000000000000 \
  -H "Authorization: Bearer $TOKEN"
# Assert: HTTP 404, error.code = {RESOURCE}_NOT_FOUND

# 2. Missing auth → 401
curl -s -w "\nHTTP_STATUS:%{http_code}" \
  http://localhost:8080/api/v1/{resource}/{id}
# Assert: HTTP 401

# 3. Response envelope structure
# Assert: response has "data" key and "meta" key with "timestamp" and "requestId"

# 4. Monetary values are Strings
# Assert: any price/amount field is a JSON string, not a JSON number
```

### Step D — Evaluate

Collect this attempt's failures:
```
attempt_failures = [all ACs that returned FAIL + edge cases that returned FAIL]
```

**PASS condition:** `attempt_failures` is empty (zero failures)

Print scorecard:
```
QA EVAL SCORECARD — Attempt {attempt}
  AC Results:
    [PASS] {AC text} → HTTP {status}
    [FAIL] {AC text} → expected {expected}, got {actual}

  Edge Case Results:
    [PASS/FAIL] Unknown ID → 404
    [PASS/FAIL] Missing auth → 401
    [PASS/FAIL] Response envelope structure
    [PASS/FAIL] Monetary values as Strings

  SUMMARY : {n} passed / {total} total
  VERDICT : {ALL PASSED | FAILED — {n} failures}
```

**If ALL PASSED:**
- Set `loop_passed = true`
- Exit the loop → go to Phase 1 (Post Report)

**If FAILED and attempt < max_attempts:**
- Merge `attempt_failures` into `all_failures`
- Increment `attempt`
- Add JIRA comment: "QA Eval Loop attempt {prev_attempt} FAILED — {n} ACs failed. Starting attempt {attempt}."
- Wait 5 seconds, then return to LOOP start (Step A) — allow any transient state to settle

**If FAILED and attempt = max_attempts:**
- Go to Escalation

---

## Phase 1 — Post JIRA Report (reached only on PASS)

Compile full report:
```
QA Eval Loop Report — SCRUM-{n}
Date      : {ISO 8601 timestamp}
Endpoint  : {HTTP method} {path}
Service   : {service-name}
Eval Loop : Passed on attempt {attempt} / {max_attempts}

ACCEPTANCE CRITERIA RESULTS
----------------------------
[PASS] {AC text} → HTTP {status}, field={value}
[PASS] Edge: unknown ID → 404
[PASS] Edge: missing auth → 401
[PASS] Edge: response envelope structure correct
[PASS] Edge: monetary values are Strings

SUMMARY: {n} passed / {n} total — ALL PASSED

QA PASSED — {total}/{total} checks verified. Ready to merge.
```

Add JIRA comment with the full report.
Apply JIRA Write Guard, then transition ticket: In Progress → **In Review**

---

## Phase 2 — Summary to User

```
QA EVAL LOOP — SCRUM-{n} COMPLETE
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Story      : {summary}
JIRA       : In Review
ACs Passed : {n} / {n}
Edge Cases : 4 / 4
Eval Loop  : Passed on attempt {attempt} / {max_attempts}
{if attempt > 1: What changed between attempts: {list}}

Next step  : Merge PR → run /sm-agent to close sprint gate
```

---

## Escalation — reached when all 3 attempts fail

```
QA EVAL LOOP — ESCALATION REQUIRED
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
SCRUM-{n} failed QA after {max_attempts} attempts.

Persistently failing ACs:
{for each AC in all_failures:
  AC: {ac text}
  Attempts failed: {n}
  Last response: {full curl response body}
  Expected: {expected}
  Actual: {actual}
}
```

Post to JIRA (apply JIRA Write Guard):
```
QA Eval Loop ESCALATED — SCRUM-{n}
Failed after 3 attempts. Persistent AC failures:
{all_failures summary}

Options:
1. Fix the implementation and re-run /qa-eval-loop SCRUM-{n}
2. Re-clarify the ACs with BA Agent if requirements are ambiguous
```

Apply JIRA Write Guard, then transition ticket: In Review → **In Progress**

STOP — do not post passing report.

---

## Hard Stops — always pause and ask the user

- Required Docker services are not running and cannot be started
- JWT token cannot be obtained (auth-service down)
- An AC is ambiguous and cannot be mapped to a testable curl assertion
- The endpoint returns a 500 error — this is a server error, not an AC failure
- A FAIL appears to be caused by infrastructure (Docker networking, port conflict) not code
