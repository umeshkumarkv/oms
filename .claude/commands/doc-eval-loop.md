# Doc Eval Loop — BMAD

Read a merged PR, update openapi.yaml and service CLAUDE.md, then validate completeness with retry.
Runs diff analysis → doc update → completeness check → retry on gaps — max 2 attempts.
On persistent gaps after 2 attempts, escalates to human for API contract review.
Always raises a docs PR — never commits directly to develop.

## Usage
```
/doc-eval-loop SCRUM-{n}
/doc-eval-loop pr:{n}
```

## How It Works

```
Attempt 1 → Read PR diff → Update docs → Completeness Check (8 items)
              ↓ ALL COMPLETE               ↓ ANY GAP
          Raise docs PR              Record gaps → Attempt 2
                                          ↓ ALL COMPLETE    ↓ ANY GAP (2/2)
                                      Raise docs PR   ESCALATE → human
```

---

## Agent Scope — Absolute Boundaries. No Exceptions.

This agent **OWNS**: updating `openapi.yaml` and service `CLAUDE.md` to reflect what was merged.

This agent **NEVER**:
- Modifies Java source files, SQL migrations, pom.xml, or Docker configuration
- Raises PRs for code changes — only documentation PRs
- Commits directly to `develop` or `main` — always via a docs PR

Escalate to human if: the merged code contradicts the existing API contract in a way that requires architectural review.

---

## GitHub Write Guard — Human Approval Required

Before performing ANY of the following operations, STOP and request explicit user approval:
- `git push` — pushing the docs branch to origin
- `gh pr create` — raising the documentation PR

Approval format:
```
GITHUB ACTION — AWAITING YOUR APPROVAL
  Action  : {push branch "docs/SCRUM-{n}-update-docs" | create PR → develop}
  Branch  : docs/SCRUM-{n}-update-docs
  Reason  : {one-line reason}

Proceed? (yes / no)
```

---

## Phase 0 — Read the PR

Record the session start time before any work begins:
```bash
START_TIME=$(date +%s)
```

1. Find the PR for this ticket:
   ```bash
   gh pr list --search "SCRUM-{n}" --json number,title,headRefName,mergedAt
   ```
2. Fetch PR metadata and full diff:
   ```bash
   gh pr view {pr-number} --json title,body,files,headRefName,mergedAt
   gh pr diff {pr-number}
   ```
3. Fetch the linked JIRA story: `getJiraIssue(SCRUM-{n})`
4. Read `services/{service}/openapi.yaml` — note all existing paths and component schemas
5. Read `services/{service}/CLAUDE.md` — note current state of all sections

Analyse the diff and identify all of the following that changed:
- New REST endpoints (`@GetMapping`, `@PostMapping`, `@PatchMapping`, etc.)
- Changed response DTO fields
- New or changed HTTP error codes in GlobalExceptionHandler
- New custom exception classes (new error codes)
- New Kafka topics produced or consumed
- New Redis cache key patterns
- New Flyway migrations (schema changes)
- New Maven dependencies in pom.xml
- New environment variables

---

## Eval Loop State

Initialize before the loop:
```
attempt      = 1
max_attempts = 2
all_gaps     = []   ← accumulated completeness gaps across attempts
loop_passed  = false
```

---

## LOOP — repeat until loop_passed = true or attempt > max_attempts

### Step A — Announce Attempt

Print:
```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
DOC EVAL LOOP — Attempt {attempt} / {max_attempts}
PR     : #{pr-number} — {pr-title}
Story  : SCRUM-{n}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

If `all_gaps` is not empty, prepend:
```
GAPS FROM PRIOR ATTEMPT — address every gap in this doc update:
{numbered list of all_gaps}
```

### Step B — Update openapi.yaml

File: `services/{service}/openapi.yaml`

For each new or changed endpoint in the diff:

1. Check if the path already exists in openapi.yaml
2. **If missing — add the full path entry:**
   ```yaml
   /api/v1/{resource}/{id}:
     get:
       summary: {Brief description}
       operationId: {operationId in camelCase}
       security:
         - bearerAuth: []
       parameters:
         - name: {id}
           in: path
           required: true
           schema:
             type: string
             format: uuid
       responses:
         '200':
           description: Success
           content:
             application/json:
               schema:
                 $ref: '#/components/schemas/{ResponseSchema}'
         '404':
           description: Not Found
           content:
             application/json:
               schema:
                 $ref: '#/components/schemas/ErrorResponse'
   ```
3. **If changed — update the existing entry** to match the actual implementation
4. For each new Response DTO field: add to the corresponding component schema
5. For each new error code: add a response entry with the error code documented

openapi.yaml field type rules (non-negotiable):
- Monetary fields: `type: string` (NEVER `type: number`)
- UUID fields: `type: string, format: uuid`
- Timestamps: `type: string, format: date-time`
- All response schemas reference `#/components/schemas/...`
- All endpoints require `security: - bearerAuth: []`

### Step C — Update Service CLAUDE.md

File: `services/{service}/CLAUDE.md`

Add entries for everything new found in the diff:

- **New endpoint:** add to Endpoints section
  `| {METHOD} /api/v1/{resource}/{id} | {description} | {status codes} |`

- **New Kafka topic (produced or consumed):** add to Kafka Topics section
  `| {topic.name} | Producer/Consumer | {description} |`

- **New Redis cache key:** add to Redis Cache Keys section
  `| {domain}:{entity}:{id} | {TTL} | {invalidation trigger} |`

- **New Maven dependency:** add to Dependencies section
  `| {groupId}:{artifactId} | {version} | {purpose} |`

- **New environment variable:** add to Environment Variables section
  `| {VAR_NAME} | {description} | required/optional |`

### Step D — Root CLAUDE.md (only if architecture changed)

Only modify root `CLAUDE.md` if the PR introduced ONE of:
- A brand new microservice
- A new Kafka topic not previously in Section 7
- A new architectural pattern not previously described
- A change to the order lifecycle state machine (Section 5)

If none of the above: leave root `CLAUDE.md` completely unchanged.

### Step E — Completeness Check (8 items)

Check every item. Mark [x] COMPLETE or [ ] GAP with specific reason for each GAP.

```
DOC COMPLETENESS CHECKLIST — Attempt {attempt}

  [x/] Every new @GetMapping/@PostMapping/@PatchMapping in the diff has an openapi.yaml path entry
       {gap reason: list missing endpoints}

  [x/] Every new Response DTO field appears in the corresponding openapi.yaml component schema
       {gap reason: list missing fields}

  [x/] Every new HTTP error code (GlobalExceptionHandler additions) has an openapi.yaml response entry
       {gap reason: list missing error codes}

  [x/] Every new Kafka topic (produced or consumed) is documented in service CLAUDE.md
       {gap reason: list missing topics}

  [x/] Every new Redis cache key pattern is documented in service CLAUDE.md
       {gap reason: list missing keys}

  [x/] Every new environment variable is documented in service CLAUDE.md
       {gap reason: list missing vars}

  [x/] Every new Maven dependency is documented in service CLAUDE.md dependencies section
       {gap reason: list missing dependencies}

  [x/] openapi.yaml field types are correct — monetary = string, UUID = string+format:uuid,
       timestamp = string+format:date-time (no monetary fields as type: number)
       {gap reason: list incorrect field types}
```

### Step F — Decision

Collect this attempt's gaps:
```
attempt_gaps = [all completeness items marked as GAP]
```

**PASS condition:** `attempt_gaps` is empty (all 8 completeness items met)

Print completeness scorecard:
```
DOC COMPLETENESS SCORECARD — Attempt {attempt}
  New endpoints documented        : {COMPLETE | GAP — {n} missing}
  New DTO fields in schema        : {COMPLETE | GAP — {n} missing}
  New error codes documented      : {COMPLETE | GAP — {n} missing}
  New Kafka topics in CLAUDE.md   : {COMPLETE | GAP — {n} missing}
  New Redis keys in CLAUDE.md     : {COMPLETE | GAP — {n} missing}
  New env vars in CLAUDE.md       : {COMPLETE | GAP — {n} missing}
  New dependencies in CLAUDE.md   : {COMPLETE | GAP — {n} missing}
  openapi.yaml field types correct: {COMPLETE | GAP — {n} incorrect}

  VERDICT : {ALL COMPLETE — raise docs PR | GAPS — {n} items incomplete}
```

**If ALL COMPLETE:**
- Set `loop_passed = true`
- Exit the loop → go to Phase 1 (Raise Docs PR)

**If GAPS and attempt < max_attempts:**
- Append `attempt_gaps` to `all_gaps`
- Increment `attempt`
- Return to LOOP start (Step A) — next attempt will fill all gaps

**If GAPS and attempt = max_attempts:**
- Go to Escalation

---

## Phase 1 — Create Docs Branch and PR (reached only on PASS)

```bash
git checkout develop
git pull origin develop
git checkout -b docs/SCRUM-{n}-update-docs

git add services/{service}/openapi.yaml
git add services/{service}/CLAUDE.md
# Add CLAUDE.md (root) only if it was changed in Step D

git commit -m "$(cat <<'EOF'
docs({service}): update openapi.yaml and CLAUDE.md for SCRUM-{n}

- {bullet: what was added to openapi.yaml}
- {bullet: what was added to CLAUDE.md}
- Doc Eval Loop: passed on attempt {attempt}/{max_attempts}

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

Apply the **GitHub Write Guard**, then push:
```bash
git push -u origin docs/SCRUM-{n}-update-docs
```

Apply the **GitHub Write Guard**, then create PR:
```bash
gh pr create \
  --title "docs({service}): sync openapi.yaml and CLAUDE.md for SCRUM-{n}" \
  --base develop \
  --body "$(cat <<'EOF'
## Documentation sync for SCRUM-{n}

Updates openapi.yaml and service CLAUDE.md to reflect the implementation merged in PR #{n}.
Doc Eval Loop: passed on attempt {attempt}/{max_attempts}.

### Changes
- openapi.yaml: {what was added/updated}
- CLAUDE.md: {what was added/updated}

### Completeness verified
All 8 doc completeness checks passed.

Generated by Doc Eval Loop
EOF
)"
```

---

## Phase 2 — Summary to User

```
DOC EVAL LOOP — SCRUM-{n} COMPLETE
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Eval Loop  : Passed on attempt {attempt} / {max_attempts}
{if attempt > 1: Gaps resolved: {list}}

FILES UPDATED
  services/{service}/openapi.yaml  — {n} endpoint(s) + {n} schema(s) added/updated
  services/{service}/CLAUDE.md     — {what sections updated}
  {CLAUDE.md (root) updated: {what} | CLAUDE.md (root): no changes}

DOCS PR
  {pr-url}

NO CHANGES NEEDED
  {list any files checked but needing no update}

Execution time : {END_TIME=$(date +%s); elapsed=$((END_TIME-START_TIME)); printf '%dm %ds' $((elapsed/60)) $((elapsed%60))}
Tokens consumed: {session input + output tokens from the Claude Code usage counter if visible}
```

---

## Escalation — reached when gaps persist after 2 attempts

```
DOC EVAL LOOP — ESCALATION REQUIRED
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Documentation for SCRUM-{n} has completeness gaps that could not be resolved in {max_attempts} attempts.
This may indicate a conflict with the existing API contract that requires architectural review.

Persistent gaps:
{numbered list of all_gaps with specific items missing}

Root cause: The merged code may contradict the existing API contract, or the diff contains
patterns that need clarification before documentation can be written accurately.

Recommended action:
1. Review the persistent gaps above
2. If the merged code changes an existing API contract — run /arch-eval-loop SCRUM-{n}
3. Re-run /doc-eval-loop SCRUM-{n} after the contract is clarified
```

STOP — do not raise a partial docs PR.

---

## Hard Stops — always pause and ask the user

- A new microservice appears in the diff — root CLAUDE.md Section 3 needs human review first
- The diff includes Kafka topic changes affecting multiple services' CLAUDE.md files
- openapi.yaml has a schema conflict with an existing definition
- A new Maven dependency was added without a corresponding CLAUDE.md Section 2 update (Tech Stack)
- The merged code contradicts the existing openapi.yaml spec in a way that cannot be auto-resolved
