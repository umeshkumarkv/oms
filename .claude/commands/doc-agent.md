# Documentation Agent — Broker Dealer OMS

Read a merged PR, identify what changed, and keep openapi.yaml and service CLAUDE.md accurate. Always raises a docs PR — never commits directly to develop.

## Usage
```
/doc-agent SCRUM-{n}
/doc-agent pr:{n}
```

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
  Action  : {push branch "docs/SCRUM-{n}-update-docs" to origin | create PR "docs({service}): sync openapi.yaml and CLAUDE.md for SCRUM-{n}" → develop}
  Branch  : docs/SCRUM-{n}-update-docs
  Reason  : {one-line reason}

Proceed? (yes / no)
```
Do NOT execute the git push or gh pr create until the user types "yes".
If the user types "no", stop at that step and tell the user what remains.

---

## Phase 0 — Read the PR

Record the session start time before any work begins:
```bash
START_TIME=$(date +%s)
```

1. If given a JIRA ticket, find the PR:
   ```bash
   gh pr list --search "SCRUM-{n}" --json number,title,headRefName,mergedAt
   ```
2. Fetch the PR metadata:
   ```bash
   gh pr view {pr-number} --json title,body,files,headRefName,baseRefName,mergedAt
   ```
3. Fetch the full diff:
   ```bash
   gh pr diff {pr-number}
   ```
4. Fetch the linked JIRA story for context: `getJiraIssue(SCRUM-{n})`
5. Analyse the diff and identify all of the following that changed:
   - New REST endpoints (new `@GetMapping`, `@PostMapping`, `@PatchMapping` etc.)
   - Changed response fields (new fields added to Response DTOs)
   - New or changed HTTP status codes in GlobalExceptionHandler
   - New error codes (new exception classes)
   - New Kafka topics produced or consumed
   - New Redis cache keys
   - New Flyway migrations (schema changes)
   - New Maven dependencies added to pom.xml
   - New environment variables used

## Phase 1 — Update openapi.yaml

File location: `services/{service}/openapi.yaml`

For each new or changed endpoint found in the diff:

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
4. **Validation rules for openapi.yaml:**
   - Monetary fields: `type: string` (NEVER `type: number`)
   - UUID fields: `type: string, format: uuid`
   - Timestamps: `type: string, format: date-time`
   - All response schemas reference `#/components/schemas/...`
   - All endpoints require `security: - bearerAuth: []`

## Phase 2 — Update Service CLAUDE.md

File location: `services/{service}/CLAUDE.md`

Check each section and add entries for anything new found in the diff:

### Endpoints section
If a new endpoint was added:
```markdown
| GET /api/v1/{resource}/{id} | Returns {description} | 200 / 404 |
```

### Kafka Topics section
If the service now produces or consumes a new topic:
```markdown
| {topic.name} | Producer/Consumer | {description} |
```

### Redis Cache Keys section
If a new cache key is used:
```markdown
| {domain}:{entity}:{id} | {TTL} | {invalidation trigger} |
```

### Dependencies section
If a new Maven dependency was added to pom.xml:
```markdown
| {groupId}:{artifactId} | {version} | {purpose} |
```

### Environment Variables section
If a new environment variable is read:
```markdown
| {VAR_NAME} | {description} | {required/optional} |
```

## Phase 3 — Root CLAUDE.md (only if architecture changed)

Only modify `CLAUDE.md` (root) if the PR introduced ONE of:
- A brand new microservice
- A new Kafka topic not previously in Section 7
- A new architectural pattern not previously described
- A change to the order lifecycle state machine (Section 5)

If none of the above: leave root CLAUDE.md completely unchanged.

## Phase 4 — Create Docs Branch and PR

```bash
# Start from latest develop
git checkout develop
git pull origin develop
git checkout -b docs/SCRUM-{n}-update-docs

# Edit the files (use Edit tool, not bash)
# Then stage only the docs files
git add services/{service}/openapi.yaml
git add services/{service}/CLAUDE.md
# Add CLAUDE.md (root) only if it was changed in Phase 3

git commit -m "$(cat <<'EOF'
docs({service}): update openapi.yaml and CLAUDE.md for SCRUM-{n}

- {bullet: what was added to openapi.yaml}
- {bullet: what was added to CLAUDE.md}

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

Apply the **GitHub Write Guard**, then:
```bash
git push -u origin docs/SCRUM-{n}-update-docs
```

Apply the **GitHub Write Guard**, then:
```bash
gh pr create \
  --title "docs({service}): sync openapi.yaml and CLAUDE.md for SCRUM-{n}" \
  --base develop \
  --body "## Documentation sync for SCRUM-{n}

Updates openapi.yaml and service CLAUDE.md to reflect the implementation merged in PR #{n}.

### Changes
- openapi.yaml: {what was added/updated}
- CLAUDE.md: {what was added/updated}

Generated by Documentation Agent
"
```

## Phase 5 — Report to User

```
Documentation Agent — SCRUM-{n}

FILES UPDATED
  services/{service}/openapi.yaml  — {n} endpoint(s) added/updated
  services/{service}/CLAUDE.md     — {what sections updated}

DOCS PR
  {pr-url}

NO CHANGES NEEDED
  {list any files that were checked but needed no update}

Execution time : {END_TIME=$(date +%s); elapsed=$((END_TIME-START_TIME)); printf '%dm %ds' $((elapsed/60)) $((elapsed%60))}
Tokens consumed: {session input + output tokens from the Claude Code usage counter if visible}
```

## Hard Stops — always pause and ask the user

- A new microservice appears in the diff — root CLAUDE.md section 3 needs updating, which requires human review before changing
- The diff includes Kafka topic changes that affect multiple services' CLAUDE.md files
- openapi.yaml has a conflict with the existing schema definition
- A new Maven dependency was added without a corresponding update to CLAUDE.md root Section 2 (Tech Stack)
