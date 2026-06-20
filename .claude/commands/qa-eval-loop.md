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
Classify ACs → REST | UI | BOTH
       ↓
Attempt 1 → Execute REST ACs (curl) + Execute UI ACs (Playwright) → Evaluate combined
              ↓ ALL PASS                       ↓ ANY FAIL
          Post JIRA report           Record failures → Attempt 2
                                           ↓ ALL PASS             ↓ ANY FAIL
                                       Post JIRA report   Record failures → Attempt 3
                                                                ↓ ALL PASS   ↓ ANY FAIL (3/3)
                                                            Post JIRA report  ESCALATE → human
```

---

## Agent Scope — Absolute Boundaries. No Exceptions.

This agent **OWNS**: executing acceptance tests (curl and Playwright) against the running Docker system.

This agent **NEVER**:
- Modifies source code, configuration files, Dockerfiles, pom.xml, or any project file
- Makes architecture decisions or implementation choices
- Diagnoses root causes beyond "this AC returns {actual}, expected {expected}"
- Suggests specific code changes or fixes

### Design Gap Rule — HARD STOP

If an AC fails because of a design issue (e.g. wrong field used for ownership comparison,
incompatible UUID domains, RBAC misconfiguration, missing header propagation), the QA Agent MUST:

1. STOP immediately — do not touch any code
2. Post the failure evidence to JIRA (full curl output, HTTP status, response body)
3. Escalate to human with message: "Design gap found — run /arch-eval-loop SCRUM-{n}"
4. Do NOT attempt to fix the issue, suggest a workaround, or retry with a code change

A test failure caused by a design problem is not a QA failure. It is an architecture failure.
The Arch Agent must update the design. The Dev Agent implements. QA re-runs only after both.

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

5. Verify the frontend dev server is reachable:
```bash
curl -s -o /dev/null -w "%{http_code}" http://localhost:5173
```
- If returns `200` — frontend is running, proceed.
- If not reachable — note it; Playwright config will auto-start it via `npm run dev`
  (`reuseExistingServer: true` in `playwright.config.ts`). Do not manually start it.

6. Verify Playwright browsers are installed:
```bash
cd /Users/umeshkumarkotaladinnevaradaraj/code/broker-dealer/frontend && \
  npx playwright install chromium --dry-run 2>&1 | head -3
```
If browsers are missing: `npx playwright install chromium` — wait for completion.

7. Obtain JWT token for curl-based REST checks:
```bash
export TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"trader.john","password":"Demo@1234"}' | jq -r '.data.token')
```
Verify the token is not null/empty before proceeding.

---

## Phase 0b — AC Classification

Before entering the loop, classify every AC from the JIRA ticket:

| Label  | When to use                                                                          |
|--------|--------------------------------------------------------------------------------------|
| `REST` | AC describes HTTP status, JSON response body, envelope structure, or a field value   |
| `UI`   | AC describes what the user sees — form renders, button state, toast, navigation, label, error message displayed |
| `BOTH` | AC is a submit flow — test both the API response AND the resulting UI feedback       |

Print the classification table before entering the loop:
```
AC CLASSIFICATION — SCRUM-{n}
  AC-1 [REST] : POST /api/v1/orders returns 202 with orderId
  AC-2 [UI]   : Order Entry form shows Limit Price field when LIMIT is selected
  AC-3 [BOTH] : Submitting a valid order shows success toast and navigates to blotter
  ...
```

`REST` ACs → executed in Step B1 (curl).
`UI` and `BOTH` ACs → executed in Step B2 (Playwright).
`BOTH` ACs are also covered in Step B1 for the API assertion.

---

## Eval Loop State

Initialize before the loop:
```
attempt        = 1
max_attempts   = 3
failed_acs     = []   ← ACs that failed in current attempt (REST or UI)
all_failures   = {}   ← map of AC → failure evidence across attempts
loop_passed    = false
spec_file      = "frontend/e2e/qa-scrum-{n}-attempt-{attempt}.spec.ts"
```

---

## LOOP — repeat until loop_passed = true or attempt > max_attempts

### Step A — Announce Attempt

Print:
```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
QA EVAL LOOP — Attempt {attempt} / {max_attempts}
Story  : SCRUM-{n} — {summary}
Layers : REST (curl) + UI (Playwright/Chromium)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

If `all_failures` is not empty, print:
```
PREVIOUSLY FAILED — watch these specifically this attempt:
{list of previously failed ACs and their failure reason}
```

---

### Step B1 — Execute REST ACs (curl)

For each AC classified `REST` or `BOTH`:
1. Write the exact curl command that tests it
2. Execute: `curl -s -w "\nHTTP_STATUS:%{http_code}" ...`
3. Parse: extract HTTP status code, relevant response fields
4. Assert: compare actual to expected
5. Mark PASS or FAIL with evidence

Assertion rules:
- HTTP status must match exactly
- Response must use envelope: `{ "data": {}, "meta": { "timestamp", "requestId" } }`
- Error responses must use: `{ "error": { "code": "...", "message": "..." }, "meta": {} }`
- Monetary values in JSON must be Strings — not numbers (`"100.00"` not `100.00`)
- Timestamps must be ISO 8601 UTC format

---

### Step B2 — Execute UI ACs (Playwright)

For each AC classified `UI` or `BOTH`, generate a Playwright spec file:

**Spec file path:**
```
frontend/e2e/qa-scrum-{n}-attempt-{attempt}.spec.ts
```

**Spec template:**
```typescript
import { test, expect } from '@playwright/test'
import { loginAsTrader, loginAsAdmin, loginAsOps } from './helpers/auth'

// QA Eval Loop — SCRUM-{n} — Attempt {attempt}
// Generated: {ISO 8601 timestamp}

test.describe('QA SCRUM-{n}: {story summary}', () => {

  // For each UI or BOTH AC, generate one test block:
  test('{ac-slug}: {ac text}', async ({ page }) => {
    // 1. Auth — choose role matching the AC (trader for order submission, ops for blotter)
    await loginAsTrader(page)   // or loginAsAdmin / loginAsOps

    // 2. Navigate
    await page.goto('{route}')
    await page.waitForLoadState('networkidle')

    // 3. Interact — fill fields, click buttons, submit forms
    // Use ONLY these locator strategies (in priority order):
    //   page.getByRole('button', { name: '...' })   — buttons
    //   page.getByLabel('...')                       — labelled inputs
    //   page.getByPlaceholder('...')                 — inputs with placeholder
    //   page.getByText('...', { exact: true })       — visible text
    //   page.locator('[data-testid="..."]')          — data-testid as last resort

    // 4. Assert
    await expect(...).toBeVisible()   // or toHaveValue / toBeDisabled / toHaveCSS

    // 5. Screenshot — ALWAYS capture, not just on failure
    await page.screenshot({
      path: 'test-results/qa-scrum-{n}-attempt-{attempt}/{ac-slug}.png',
      fullPage: false
    })
  })

})
```

**Playwright locator rules (match existing e2e patterns):**
- `SELL` button: use `{ name: 'SELL', exact: true }` — "SELL" is a substring of "SHORT SELL"
- For `SHORT SELL`: `page.getByRole('button', { name: 'SHORT SELL' })`
- Symbol field: `page.getByPlaceholder('e.g. AAPL')`
- Quantity field: `page.getByPlaceholder('Number of shares')`
- Limit price: `page.getByPlaceholder('0.00')`
- Submit button: `page.getByRole('button', { name: 'PLACE ORDER' })`

**Wait before asserting state changes:**
```typescript
await page.waitForLoadState('networkidle')   // after navigation
// OR
await expect(locator).toBeVisible({ timeout: 5000 })   // after async actions
```

**Run the spec:**
```bash
cd /Users/umeshkumarkotaladinnevaradaraj/code/broker-dealer/frontend && \
  npx playwright test e2e/qa-scrum-{n}-attempt-{attempt}.spec.ts \
    --reporter=list 2>&1
```

Parse the output:
- `✓` or `passed` → PASS for that test
- `✗` or `failed` → FAIL; capture the full error message (Playwright errors include the expected vs actual)
- Note the screenshot path for each test (pass or fail)

---

### Step C — Mandatory Edge Cases (always run, every attempt)

**REST edge cases (curl):**
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

**UI edge cases (Playwright — add to the generated spec file):**
```typescript
// UI-EDGE-1: Unauthenticated → redirects to /login (not just HTTP 401)
test('UI-EDGE: unauthenticated access redirects to login', async ({ page }) => {
  // No loginAs call — navigate without auth
  await page.goto('/{protected-route}')
  await expect(page).toHaveURL(/\/login/)
  await page.screenshot({ path: 'test-results/qa-scrum-{n}-attempt-{attempt}/ui-edge-unauth.png' })
})

// UI-EDGE-2: Required field left blank → form submit button remains disabled or inline error shown
test('UI-EDGE: incomplete form keeps submit disabled', async ({ page }) => {
  await loginAsTrader(page)
  await page.goto('/orders/new')
  await page.waitForLoadState('networkidle')
  // Do NOT fill any fields
  await expect(page.getByRole('button', { name: 'PLACE ORDER' })).toBeDisabled()
  await page.screenshot({ path: 'test-results/qa-scrum-{n}-attempt-{attempt}/ui-edge-incomplete.png' })
})

// UI-EDGE-3: Navigation — sidebar link reaches the correct page
test('UI-EDGE: sidebar navigation reaches target page', async ({ page }) => {
  await loginAsTrader(page)
  await page.goto('/dashboard')
  await page.waitForLoadState('networkidle')
  // Click the relevant sidebar link
  await page.getByRole('link', { name: '{page-name}' }).click()
  await expect(page).toHaveURL(/\/{route-slug}/)
  await page.screenshot({ path: 'test-results/qa-scrum-{n}-attempt-{attempt}/ui-edge-nav.png' })
})

// UI-EDGE-4: Role visibility — UI elements hidden for wrong role
// Only generate this test when the story involves role-based UI differences
test('UI-EDGE: restricted element not visible to wrong role', async ({ page }) => {
  await loginAsOps(page)  // ops cannot submit orders
  await page.goto('/orders/new')
  // Adjust assertion to match the role restriction in this story
  await page.screenshot({ path: 'test-results/qa-scrum-{n}-attempt-{attempt}/ui-edge-role.png' })
})
```

Skip a UI edge case if it is not applicable to the story under test (e.g. skip UI-EDGE-4 if the story has no role restriction). Note skipped edge cases in the scorecard.

---

### Step D — Evaluate

Collect this attempt's failures:
```
rest_failures = [REST/BOTH ACs that failed curl + failed REST edge cases]
ui_failures   = [UI/BOTH ACs that failed Playwright + failed UI edge cases]
attempt_failures = rest_failures + ui_failures
```

**PASS condition:** `attempt_failures` is empty (zero failures across both layers)

Print scorecard:
```
QA EVAL SCORECARD — Attempt {attempt}
  REST AC Results:
    [PASS] {AC text} → HTTP {status}
    [FAIL] {AC text} → expected {expected}, got {actual}
            Evidence: {curl response body}

  UI AC Results:
    [PASS] {AC text} → {assertion text}
            Screenshot: test-results/qa-scrum-{n}-attempt-{attempt}/{ac-slug}.png
    [FAIL] {AC text} → {playwright error message}
            Screenshot: test-results/qa-scrum-{n}-attempt-{attempt}/{ac-slug}.png

  REST Edge Cases:
    [PASS/FAIL] Unknown ID → 404
    [PASS/FAIL] Missing auth → 401
    [PASS/FAIL] Response envelope structure
    [PASS/FAIL] Monetary values as Strings

  UI Edge Cases:
    [PASS/FAIL/SKIP] Unauthenticated → login redirect
    [PASS/FAIL/SKIP] Incomplete form → submit disabled
    [PASS/FAIL/SKIP] Sidebar navigation → correct route
    [PASS/FAIL/SKIP] Role restriction → element hidden

  SUMMARY : {n} passed / {total} total ({rest_n} REST + {ui_n} UI)
  VERDICT : {ALL PASSED | FAILED — {n} failures ({r} REST, {u} UI)}
```

**If ALL PASSED:**
- Set `loop_passed = true`
- Delete the generated spec file: `rm frontend/e2e/qa-scrum-{n}-attempt-{attempt}.spec.ts`
- Exit the loop → go to Phase 1 (Post Report)

**If FAILED and attempt < max_attempts:**
- Merge `attempt_failures` into `all_failures`
- Increment `attempt` and update `spec_file` to use new attempt number
- Add JIRA comment: "QA Eval Loop attempt {prev} FAILED — {n} ACs failed ({r} REST, {u} UI). Starting attempt {attempt}."
- Wait 5 seconds, then return to LOOP start (Step A)
- The new spec file for attempt N replaces the previous one — delete the old spec before writing:
  `rm -f frontend/e2e/qa-scrum-{n}-attempt-{prev}.spec.ts`

**If FAILED and attempt = max_attempts:**
- Go to Escalation

---

## Phase 1 — Post JIRA Report (reached only on PASS)

Compile full report:
```
QA Eval Loop Report — SCRUM-{n}
Date      : {ISO 8601 timestamp}
Service   : {service-name}
Eval Loop : Passed on attempt {attempt} / {max_attempts}

REST AC RESULTS
---------------
[PASS] {AC text} → HTTP {status}, field={value}
...

UI AC RESULTS
-------------
[PASS] {AC text}
       Assertion: {playwright assertion text}
       Screenshot: test-results/qa-scrum-{n}-attempt-{attempt}/{ac-slug}.png
...

REST EDGE CASES
---------------
[PASS] Unknown ID → 404, error.code={RESOURCE}_NOT_FOUND
[PASS] Missing auth → 401
[PASS] Response envelope: data + meta present
[PASS] Monetary values: all Strings

UI EDGE CASES
-------------
[PASS] Unauthenticated → redirected to /login
[PASS] Incomplete form → PLACE ORDER disabled
[PASS] Sidebar navigation → reached /{route}
[PASS/SKIP] Role restriction: {note}

SUMMARY: {n} passed / {n} total ({r} REST + {u} UI) — ALL PASSED

QA PASSED — {total}/{total} checks verified. Ready to merge.
```

Add JIRA comment with the full report.

The ticket is already **In Review** (set by Dev Eval Loop when the PR was raised).
Do NOT transition the status — it is correct as-is.
The SM Agent will transition In Review → Done after the PR is merged and DoD is verified.

---

## Phase 2 — Summary to User

```
QA EVAL LOOP — SCRUM-{n} COMPLETE
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Story        : {summary}
JIRA         : In Review
REST ACs     : {n} / {n} passed
UI ACs       : {n} / {n} passed
REST Edges   : 4 / 4 passed
UI Edges     : {n} / 4 passed (or SKIP noted)
Eval Loop    : Passed on attempt {attempt} / {max_attempts}
Screenshots  : frontend/test-results/qa-scrum-{n}-attempt-{attempt}/
{if attempt > 1: What changed between attempts: {list}}

Next step  : Merge PR → run /sm-agent to close sprint gate
```

---

## Escalation — reached when all 3 attempts fail

```
QA EVAL LOOP — ESCALATION REQUIRED
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
SCRUM-{n} failed QA after {max_attempts} attempts.

PERSISTENTLY FAILING — REST ACs:
{for each REST AC in all_failures:
  AC       : {ac text}
  Attempts : {n}
  Last curl: {full response body}
  Expected : {expected}
  Actual   : {actual}
}

PERSISTENTLY FAILING — UI ACs:
{for each UI AC in all_failures:
  AC         : {ac text}
  Attempts   : {n}
  Last error : {playwright error message — includes expected vs actual}
  Screenshot : test-results/qa-scrum-{n}-attempt-3/{ac-slug}.png
}
```

Post to JIRA (apply JIRA Write Guard):
```
QA Eval Loop ESCALATED — SCRUM-{n}
Failed after 3 attempts.

Failing REST ACs:
{rest failures summary with curl evidence}

Failing UI ACs:
{ui failures summary with Playwright error text}
Screenshots saved locally: frontend/test-results/qa-scrum-{n}-attempt-3/

Options:
1. Fix implementation and re-run /qa-eval-loop SCRUM-{n}
2. Re-clarify ACs with BA Agent if requirements are ambiguous
3. Check screenshots in test-results/ for UI failure context
```

Apply JIRA Write Guard, then transition ticket: In Review → **In Progress**

STOP — do not post passing report.

---

## Hard Stops — always pause and ask the user

- Required Docker services are not running and cannot be started
- JWT token cannot be obtained (auth-service down)
- An AC is ambiguous and cannot be mapped to a testable curl or Playwright assertion
- The endpoint returns a 500 error — server error, not an AC failure
- A FAIL appears caused by infrastructure (Docker networking, port conflict) not code
- Playwright cannot find an element and the locator strategy is ambiguous — stop and ask what the correct selector is rather than guessing
- The generated spec file causes a TypeScript compilation error — fix the spec before running, do not `--ignore-errors`