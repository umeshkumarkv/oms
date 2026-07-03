# DevOps Eval Loop — BMAD

Diagnose CI/CD pipeline failures, apply fixes, and verify the pipeline goes green — with retry.
Runs diagnose → fix → verify (local + CI) → retry on CI failure — max 3 attempts.
On persistent failure after 3 attempts, escalates to human with full log evidence.

## Usage
```
/devops-eval-loop pr:{n}              — fix a failing CI pipeline run
/devops-eval-loop check-pipeline      — check current pipeline state
/devops-eval-loop branch:{branch-name} — check a specific branch
/devops-eval-loop security-report     — sprint-close: Veracode + BlackDuck scan → Confluence
```

## How It Works

```
Attempt 1 → Classify failure → Apply fix → Verify local → Verify CI
              ↓ CI GREEN                        ↓ CI STILL FAILING
          Report FIXED               Record findings → Attempt 2
                                          ↓ CI GREEN          ↓ CI STILL FAILING
                                      Report FIXED    Record findings → Attempt 3
                                                           ↓ CI GREEN  ↓ CI FAILS (3/3)
                                                       Report FIXED   ESCALATE → human
```

---

## Agent Scope — Absolute Boundaries. No Exceptions.

This agent **OWNS**: CI/CD pipeline health — diagnosing failures and auto-fixing CI issues.

This agent **NEVER**:
- Changes business logic or modifies service source code in ways that alter behaviour
- Bypasses test gates or reduces coverage thresholds to make builds pass
- Modifies Dockerfiles or docker-compose.yml without explicit human approval

Escalate to human if: pipeline failure is caused by a business logic bug (not a CI configuration issue).

---

## GitHub Write Guard — Human Approval Required

Before performing ANY of the following operations, STOP and request explicit user approval:
- `git push` to any remote branch
- Docker file changes (`Dockerfile`, `docker-compose.yml`)
- GitHub Actions workflow changes (`.github/workflows/`)

Approval format:
```
GITHUB ACTION — AWAITING YOUR APPROVAL
  Action  : {push fix commit to "{branch}" | modify Dockerfile | update workflow {file}}
  Branch  : {branch}
  Reason  : {one-line reason}

Proceed? (yes / no)
```
Do NOT execute until the user types "yes".

---

## Phase 0 — Fetch Pipeline State

Record the session start time before any work begins:
```bash
START_TIME=$(date +%s)
```

```bash
# If PR number provided
gh pr view {n} --json headRefName --jq '.headRefName'

# List recent runs for the branch
gh run list --branch {branch} --limit 5 --json databaseId,status,conclusion,name,createdAt

# Find the latest failed run
gh run list --branch {branch} --limit 5 --json databaseId,conclusion \
  | jq '[.[] | select(.conclusion == "failure")] | first'

# Fetch full failure logs
gh run view {run-id} --log-failed
```

---

## Eval Loop State

Initialize before the loop:
```
attempt        = 1
max_attempts   = 3
all_findings   = []   ← accumulated fix attempts and results across attempts
loop_passed    = false
last_run_id    = {the failing run ID}
```

---

## LOOP — repeat until loop_passed = true or attempt > max_attempts

### Step A — Announce Attempt

Print:
```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
DEVOPS EVAL LOOP — Attempt {attempt} / {max_attempts}
Branch : {branch}
Run    : {run-id}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

If `all_findings` is not empty, print:
```
PRIOR ATTEMPTS — context for this attempt:
{numbered list of prior fix attempts and their results}
```

### Step B — Classify the Failure

Re-fetch the latest failure logs (if attempt > 1, fetch the new run-id):
```bash
gh run list --branch {branch} --limit 5 --json databaseId,conclusion \
  | jq '[.[] | select(.conclusion == "failure")] | first'
gh run view {new-run-id} --log-failed
```

Classify into exactly one category:

| Category | Indicators | Auto-fix? |
|---|---|---|
| **Compilation error** | `COMPILATION ERROR`, `error: cannot find symbol` | Yes |
| **Test failure** | `Tests run:`, `AssertionError`, `NullPointerException in test` | Yes |
| **JaCoCo coverage** | `Coverage checks have not been met`, `Line coverage ... is below minimum` | Yes — add tests |
| **Docker build** | `docker build` step fails | Suggest fix only |
| **Flyway migration** | `FlywayException`, `checksum mismatch`, `wrong version order` | Suggest fix only |
| **GitHub Actions YAML** | `Invalid workflow file`, `Unexpected value` | Suggest fix only |
| **Dependency conflict** | `Could not resolve`, `version conflict` | Suggest fix only |
| **Environment/secret** | `Environment variable not set`, null config value | Suggest fix only |

Record:
```
failure_category = {category}
failure_detail   = {exact error: file, line, message}
```

### Step C — Apply Fix

#### Auto-fix: Compilation Error
1. Read the exact error: file, line, symbol
2. Open the file and fix (missing import, wrong type, missing method)
3. Run locally:
   ```bash
   cd services/{service}
   JAVA_HOME=/Users/umeshkumarkotaladinnevaradaraj/Library/Java/JavaVirtualMachines/corretto-21.0.5/Contents/Home \
     mvn compile -q
   ```
4. If compile succeeds, run tests:
   ```bash
   JAVA_HOME=... mvn test -q
   ```
5. If tests pass, apply the **GitHub Write Guard**, then commit and push:
   ```bash
   git add services/{service}/src/...
   git commit -m "fix(ci): resolve compilation error in {file} — attempt {attempt}/{max_attempts}

   Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>"
   git push
   ```

#### Auto-fix: Test Failure
1. Read the exact failure: class, method, assertion message
2. Determine if it is production code or test code
3. Fix the root cause — never comment out or delete failing tests
4. Run `mvn test` to verify
5. Apply the **GitHub Write Guard**, then commit and push

#### Auto-fix: JaCoCo Coverage
1. Identify which classes are below 80%:
   ```bash
   cat services/{service}/target/site/jacoco/index.html | grep -A2 "missed"
   ```
2. Add unit tests for uncovered lines
3. Run `mvn verify` to confirm coverage gate passes
4. Apply the **GitHub Write Guard**, then commit and push

#### Suggest Fix: Docker / Flyway / YAML / Dependency / Environment
Print structured diagnosis — do NOT auto-fix (require explicit human approval via GitHub Write Guard):
```
DevOps Eval Loop — {Category} Failure
Build step : {step name}
Error      : {exact error message}
File       : {file}:{line if applicable}

ROOT CAUSE
{explanation}

RECOMMENDED FIX
{exact change to make}

VERIFY WITH
{verification command}
```

If the user approves the fix: apply, commit, push with GitHub Write Guard.
If the user does not approve: record as unresolved in findings.

### Step D — Verify CI Green

After any push (auto-fix or approved suggest-fix):

Wait for the new CI run to start:
```bash
# Poll until a new run appears (newer than last_run_id)
gh run list --branch {branch} --limit 5 --json databaseId,status,conclusion,createdAt | jq .
```

Check the new run result:
```bash
gh run view {new-run-id} --json status,conclusion | jq .
```

- If `conclusion = "success"` → CI GREEN — record and go to Phase 1
- If `conclusion = "failure"` → CI STILL FAILING — record findings and continue loop
- If `status = "in_progress"` → wait up to 5 minutes, re-check

Record:
```
fix_result = {CI GREEN | CI STILL FAILING — {new failure category}}
```

### Step E — Decision

**PASS condition:** CI run conclusion = "success"

Print verification scorecard:
```
DEVOPS EVAL SCORECARD — Attempt {attempt}
  Failure category    : {category}
  Fix applied         : {description of fix}
  Local mvn test      : {PASS | FAIL | SKIPPED (suggest-fix)}
  CI run              : #{new-run-id} — {success | failure}
  New failure (if any): {category + detail}

  VERDICT : {CI GREEN — pipeline fixed | CI STILL FAILING — {n} issues remain}
```

**If CI GREEN:**
- Set `loop_passed = true`
- Exit the loop → go to Phase 1

**If CI STILL FAILING and attempt < max_attempts:**
- Append `{ attempt, failure_category, fix_applied, fix_result }` to `all_findings`
- Increment `attempt`
- Update `last_run_id` to the new failing run ID
- Return to LOOP start (Step A)

**If CI STILL FAILING and attempt = max_attempts:**
- Go to Escalation

---

## Phase 1 — Report to User (reached only on CI GREEN)

```
DEVOPS EVAL LOOP — FIXED
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Branch    : {branch}
CI Run    : #{run-id} — SUCCESS
Eval Loop : Fixed on attempt {attempt} / {max_attempts}
{if attempt > 1: What changed between attempts: {list}}

FIX SUMMARY
  Original failure : {category — file:line}
  Fix applied      : {description}
  Commit           : {commit hash}

Next step : {CI is green — PR is ready for review | manually verify the PR}

Execution time : {END_TIME=$(date +%s); elapsed=$((END_TIME-START_TIME)); printf '%dm %ds' $((elapsed/60)) $((elapsed%60))}
Tokens consumed: {session input + output tokens from the Claude Code usage counter if visible}
```

---

## Escalation — reached when CI fails after 3 fix attempts

```
DEVOPS EVAL LOOP — ESCALATION REQUIRED
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Pipeline for branch {branch} failed after {max_attempts} fix attempts.

Fix history:
  Attempt 1: {category} — {fix applied} — {result}
  Attempt 2: {category} — {fix applied} — {result}
  Attempt 3: {category} — {fix applied} — {result}

Current failing CI run: #{run-id}
Latest error:
{full log excerpt of the current failure}

Human action required:
1. Review the fix history above — the failure may require business logic changes
2. If business logic change is needed, assign to the relevant dev
3. Re-run /devops-eval-loop pr:{n} after the fix is applied
```

STOP — do not push any further commits.

---

## Hard Stops — always pause and ask the user

- Auto-fix requires changes to production business logic (not CI config, compilation, or tests)
- Flyway migration has already been applied to a shared environment
- Docker or docker-compose.yml changes are needed (always require GitHub Write Guard)
- `.github/workflows/` YAML changes are needed (always require GitHub Write Guard)
- Multiple failure categories present simultaneously — diagnose all, fix none without approval
- `mvn test` fails after the auto-fix attempt with a new error category

---

## Sprint Security Report — /devops-eval-loop security-report

Run at sprint close against the `develop` branch. No eval loop — single pass.
Produces one Veracode SAST report and one BlackDuck SCA report published to Confluence.

### Step 1 — Build all services
```bash
JAVA_HOME=/Users/umeshkumarkotaladinnevaradaraj/Library/Java/JavaVirtualMachines/corretto-21.0.5/Contents/Home \
  mvn package -DskipTests -q
```
If build fails — STOP. Fix with `/devops-eval-loop check-pipeline` first.

### Step 2 — Run Veracode SAST
```bash
curl -sSO https://downloads.veracode.com/securityscan/pipeline-scan-LATEST.zip
unzip -o pipeline-scan-LATEST.zip pipeline-scan.jar
for service in order-service client-service risk-service execution-service marketdata-service auth-service audit-service notification-service api-gateway; do
  java -jar pipeline-scan.jar \
    --veracode_api_id "$VERACODE_API_ID" \
    --veracode_api_key "$VERACODE_API_KEY" \
    --file "services/${service}/target/${service}-*.jar" \
    --json_output true \
    --json_output_file "veracode-${service}.json"
done
```

### Step 3 — Run BlackDuck SCA
```bash
bash <(curl -s -L https://detect.synopsys.com/detect9.sh) \
  --blackduck.url="$BLACKDUCK_URL" \
  --blackduck.api.token="$BLACKDUCK_API_TOKEN" \
  --detect.project.name="broker-dealer" \
  --detect.project.version.name="sprint-{n}" \
  --detect.tools=DETECTOR
```

### Step 4 — Publish Security Report to Confluence

Template: same structure as devops-agent.md security-report template.

### Hard Stops — sprint security report
- `VERACODE_API_ID`, `VERACODE_API_KEY`, `BLACKDUCK_URL`, or `BLACKDUCK_API_TOKEN` not set
- Build fails — cannot scan without JARs
