# QA Agent — SCRUM-60

Validate all acceptance criteria for a JIRA story against the running system and post a structured pass/fail report to JIRA.
At sprint close, run `/qa-agent sprint` to produce the full sprint-level Test Plan and Test Results on Confluence.

## Usage
```
/qa-agent SCRUM-{n}   — per-story AC validation (run after Code Review approves)
/qa-agent sprint      — sprint-close: consolidated Test Plan + Test Results → Confluence
```

---

## Per-Story Mode — /qa-agent SCRUM-{n}

## Phase 0 — Setup (BMAD Gate — Code Review must pass before QA runs)

1. Fetch the JIRA story: `getJiraIssue(SCRUM-{n})`
2. Extract every acceptance criterion line by line from the description
3. Identify the service and endpoint(s) under test

### Gate — Code Review Approved
Find the PR for this story:
```bash
gh pr list --search "SCRUM-{n}" --json number,title,reviewDecision,headRefName | jq .
```
- If `reviewDecision` is NOT `"APPROVED"` — STOP:
  "PR for SCRUM-{n} has not been approved by Code Review Agent. Run /code-review SCRUM-{n} first."
- If no PR exists — STOP: "No PR found. Run /dev-agent SCRUM-{n} first."

4. Verify all Docker services are running:
   ```bash
   docker ps --format "table {{.Names}}\t{{.Status}}" | grep -E "api-gateway|client-service|order-service|auth-service"
   ```
   If any required service is not running or unhealthy, STOP and tell the user.
5. Obtain a JWT token:
   ```bash
   export TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
     -H "Content-Type: application/json" \
     -d '{"username":"trader.john","password":"Demo@1234"}' | jq -r '.data.token')
   ```
   Verify the token is not null/empty before proceeding.

## Phase 1 — Map and Execute Each AC

For each acceptance criterion:
1. Write the exact curl command that tests it
2. Execute the curl command: `curl -s -w "\nHTTP_STATUS:%{http_code}" ...`
3. Parse the response: extract HTTP status code, relevant fields
4. Assert: compare actual to expected
5. Mark PASS or FAIL
6. On FAIL: save the full response body as evidence

### Assertion rules
- HTTP status must match exactly (200, 404, 422, 401, etc.)
- Response must use the standard envelope: `{ "data": {}, "meta": { "timestamp", "requestId" } }`
- Error responses must use: `{ "error": { "code": "...", "message": "..." }, "meta": {} }`
- Monetary values in JSON must be Strings — not numbers (e.g. `"100.00"` not `100.00`)
- Timestamps must be ISO 8601 UTC format (e.g. `"2026-04-16T09:30:00Z"`)

## Phase 2 — Mandatory Edge Case Tests (always run, regardless of ACs)

These are run for every story, even if not explicitly in the ACs:

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
# Assert: any price/amount field value is a JSON string, not a JSON number
```

## Phase 3 — Build the Report

Compile all results into this format:

```
QA Agent Report — SCRUM-{n}
Date: {ISO 8601 timestamp}
Endpoint: {HTTP method} {path}
Service: {service-name}

ACCEPTANCE CRITERIA RESULTS
----------------------------
[PASS] {AC text} → HTTP {status}, field={value}
[FAIL] {AC text} → expected HTTP {expected}, got HTTP {actual}
[PASS] Edge: unknown ID → 404 CLIENT_NOT_FOUND
[PASS] Edge: missing auth → 401
[PASS] Edge: response envelope structure correct
[PASS] Edge: monetary values are Strings

SUMMARY: {n} passed, {n} failed

FAILURE EVIDENCE
----------------
AC: {failing AC text}
curl: {exact curl command}
Response: {full JSON response}
Expected: {what was expected}
Actual: {what was returned}
```

## JIRA Write Guard — Human Approval Required

Before performing ANY of the following operations, STOP and request explicit user approval:
- Adding or removing a label on any ticket
- Transitioning a ticket status

Approval format:
```
JIRA ACTION — AWAITING YOUR APPROVAL
  Ticket : SCRUM-{n}
  Action : {transition to "In Progress" | etc.}
  Reason : {one-line reason}

Proceed? (yes / no)
```
Do NOT execute the JIRA write operation until the user types "yes".
If the user types "no", skip the action and note it in the final report.

---

## Phase 4 — JIRA Action

**If all tests PASS:**
1. Add JIRA comment with the full report
2. Add final line: "QA PASSED — {total}/{total} checks verified. Ready to merge."

**If any test FAILS:**
1. Add JIRA comment with the full report
2. Apply the JIRA Write Guard, then transition ticket: In Review → **In Progress**
3. Add comment: "QA FAILED — {n} checks failed. Ticket moved back to In Progress. See report above."
4. Tell the user which ACs failed and what the actual vs expected values were

## Hard Stops — always pause and ask the user

- Required Docker services are not running and cannot be started
- JWT token cannot be obtained (auth-service down)
- An AC is ambiguous and cannot be mapped to a testable assertion
- The endpoint returns a 500 error (unexpected server error — not an AC failure)

---

## Sprint Test Mode — /qa-agent sprint

Run at sprint close. Produces one consolidated Test Plan page and one Test Results page on Confluence covering every story in the sprint. This is the document package shared at the Go/No-Go meeting.

### Step 1 — Fetch all sprint stories
```
searchJiraIssuesUsingJql(
  "project = SCRUM AND sprint in openSprints() ORDER BY created ASC",
  fields: ["summary", "status", "acceptanceCriteria", "assignee"]
)
```
Only process stories in **Done** or **In Review** status. If any story is still In Progress — STOP and tell the user. Sprint testing cannot run until all stories are complete.

### Step 2 — Run all ACs for all stories
For each story, execute all AC tests exactly as Phase 1 above.
Record per-story: story key, summary, service, AC count, pass count, fail count.

### Step 3 — Run regression suite across all services
```bash
JAVA_HOME=/Users/umeshkumarkotaladinnevaradaraj/Library/Java/JavaVirtualMachines/corretto-21.0.5/Contents/Home \
  mvn test -pl services/order-service,services/client-service,services/risk-service,\
services/execution-service,services/marketdata-service,services/auth-service,\
services/audit-service,services/notification-service -q 2>&1 | tail -30
```
Record: total tests run, failures, services with failures.

### Step 4 — Publish Sprint Test Plan to Confluence
```
createConfluencePage(
  spaceKey: "SCRUM",
  parentPageTitle: "QA",
  title: "Sprint Test Plan — {sprint name} — {date}",
  body: {see template below}
)
```

Sprint Test Plan template:
```
h2. Sprint: {sprint name} · {start date} → {end date}
Published: {ISO 8601 date}
Environment: localhost (Docker Compose)
Executed by: QA Agent

h2. Stories in Scope
|| Story || Summary || Service || AC Count ||
| SCRUM-{n} | {summary} | {service} | {n} |

h2. Test Cases — Acceptance Criteria
For each story:
h3. SCRUM-{n}: {summary}
|| # || AC || Test Method || Expected Result ||
| 1 | {AC text} | curl {METHOD} {path} | HTTP {status} |

h2. Mandatory Edge Cases (all stories)
Unknown ID → 404 · Missing auth → 401 · Response envelope · Monetary fields as Strings

h2. Regression Scope
All 9 services · mvn test · Expected: 0 failures
```

### Step 5 — Publish Sprint Test Results to Confluence
```
createConfluencePage(
  spaceKey: "SCRUM",
  parentPageTitle: "Sprint Test Plan — {sprint name} — {date}",
  title: "Sprint Test Results — {sprint name} — {date}",
  body: {see template below}
)
```

Sprint Test Results template:
```
h2. Sprint: {sprint name}
Executed: {ISO 8601 timestamp}
Overall Verdict: *ALL PASSED* / *{n} FAILURES*

h2. Results by Story
|| Story || Summary || ACs || Passed || Failed || Verdict ||
| SCRUM-{n} | {summary} | {n} | {n} | {n} | PASS/FAIL |

h2. Regression Results
|| Service || Tests Run || Failures || Result ||
| order-service | {n} | 0 | PASS |
| client-service | {n} | 0 | PASS |
...

h2. Overall Regression: {PASSED / FAILED}
Total: {n} tests · {n} failures

h2. Failure Detail
{Full curl + response evidence for every failure, or "None — all tests passed"}
```

### Step 6 — Report to user
```
QA Agent — Sprint Test Run Complete
Sprint      : {sprint name}
Stories     : {n} tested
ACs         : {total} checked — {passed} passed · {failed} failed
Regression  : {PASSED / FAILED} — {n} tests across 9 services

Test Plan   : {confluence-url}
Test Results: {confluence-url}

Status: {READY FOR GO/NO-GO MEETING | FAILURES — resolve before meeting}
Next step: Run /devops-agent security-report, then /sm-agent release-notes
```
