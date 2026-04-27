# QA Agent — SCRUM-60

Validate all acceptance criteria for a JIRA story against the running system and post a structured pass/fail report.

## Usage
```
/qa-agent SCRUM-{n}
```

## Phase 0 — Setup

1. Fetch the JIRA story: `getJiraIssue(SCRUM-{n})`
2. Extract every acceptance criterion line by line from the description
3. Identify the service and endpoint(s) under test
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

## Phase 4 — JIRA Action

**If all tests PASS:**
1. Add JIRA comment with the full report
2. Add final line: "QA PASSED — {total}/{total} checks verified. Ready to merge."

**If any test FAILS:**
1. Add JIRA comment with the full report
2. Transition ticket: In Review → **In Progress**
3. Add comment: "QA FAILED — {n} checks failed. Ticket moved back to In Progress. See report above."
4. Tell the user which ACs failed and what the actual vs expected values were

## Hard Stops — always pause and ask the user

- Required Docker services are not running and cannot be started
- JWT token cannot be obtained (auth-service down)
- An AC is ambiguous and cannot be mapped to a testable assertion
- The endpoint returns a 500 error (unexpected server error — not an AC failure)