# DevOps Agent — SCRUM-62

Monitor GitHub Actions pipeline failures, diagnose root cause, and auto-fix where possible.

## Usage
```
/devops-agent pr:{n}
/devops-agent check-pipeline
/devops-agent branch:{branch-name}
```

## Phase 0 — Fetch Pipeline State

```bash
# If PR number provided
gh pr view {n} --json headRefName --jq '.headRefName'
# Use the branch name to find runs

# List recent runs for the branch
gh run list --branch {branch} --limit 5 --json databaseId,status,conclusion,name,createdAt

# Find the latest failed run
gh run list --branch {branch} --limit 5 --json databaseId,conclusion \
  | jq '[.[] | select(.conclusion == "failure")] | first'

# Fetch full failure logs
gh run view {run-id} --log-failed
```

## Phase 1 — Classify the Failure

Read the log output and classify into exactly one category:

| Category | Indicators | Auto-fix? |
|---|---|---|
| **Compilation error** | `COMPILATION ERROR`, `error: cannot find symbol`, `package does not exist` | Yes |
| **Test failure** | `Tests run:`, `BUILD FAILURE` after test phase, `AssertionError`, `NullPointerException in test` | Yes |
| **JaCoCo coverage** | `Coverage checks have not been met`, `Line coverage ... is below minimum` | Yes — add tests |
| **Docker build** | `docker build` step fails, `FROM` error, `COPY failed` | Suggest fix |
| **Flyway migration** | `FlywayException`, `Validate failed`, `checksum mismatch`, `wrong version order` | Suggest fix |
| **GitHub Actions YAML** | `Invalid workflow file`, `Unexpected value`, `mapping values are not allowed` | Suggest fix |
| **Dependency conflict** | `Could not resolve`, `version conflict`, `incompatible versions` in Maven output | Suggest fix |
| **Environment/secret** | `Environment variable not set`, `null` config value at startup | Suggest fix |

## Phase 2A — Auto-fix: Compilation Error

1. Read the exact error: file, line, symbol
2. Open the file and fix the issue (missing import, wrong type, missing method, etc.)
3. Run locally:
   ```bash
   cd services/{service}
   mvn compile -q
   ```
4. If compile succeeds, run tests:
   ```bash
   mvn test -q
   ```
5. If tests pass, commit and push:
   ```bash
   git add services/{service}/src/...
   git commit -m "fix(ci): resolve compilation error in {file} — {description}

   Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>"
   git push
   ```
6. Report to user: "Fixed compilation error in {file}:{line}. Pushed to branch. CI will re-run."

## Phase 2B — Auto-fix: Test Failure

1. Read the exact test failure: test class, test method, assertion message
2. Determine if the failure is in:
   - **Production code** (wrong logic) → fix the service/controller
   - **Test code** (wrong mock/assertion) → fix the test
3. Run `mvn test` to verify the fix
4. Never comment out or delete failing tests — fix the underlying issue
5. Commit and push the fix (same format as above)

## Phase 2C — Auto-fix: JaCoCo Coverage

1. Identify which classes are below 80% line coverage from the report:
   ```bash
   cat services/{service}/target/site/jacoco/index.html | grep -A2 "missed"
   ```
2. Add unit tests for the uncovered lines
3. Run `mvn verify` to confirm coverage gate passes
4. Commit and push the new tests

## Phase 2D — Suggest Fix: Docker Build

Provide this structured diagnosis and do NOT auto-fix (Docker changes need human review):

```
DevOps Agent — Docker Build Failure

Build step : {step name}
Error      : {error message}
File       : {Dockerfile or docker-compose.yml}:{line}

ROOT CAUSE
{explanation}

RECOMMENDED FIX
{exact change to make in the file}

VERIFY WITH
docker build -t broker-dealer/{service}:test services/{service}/
```

## Phase 2E — Suggest Fix: Flyway Migration

```
DevOps Agent — Flyway Migration Failure

Error      : {FlywayException message}
Migration  : {V{n}__{name}.sql}

ROOT CAUSE
{checksum mismatch | version out of order | SQL syntax error}

RECOMMENDED FIX
Option A (development only): Delete the migration file and recreate with correct content
Option B (if already merged): Create a new migration V{n+1} that corrects the state
Option C (checksum only): Run flyway repair to update the checksum in the schema_history table

WARNING: Never modify a migration that has been applied to a shared environment.
```

## Phase 2F — Suggest Fix: GitHub Actions YAML

```
DevOps Agent — GitHub Actions YAML Error

File  : .github/workflows/{workflow}.yml
Line  : {line}
Error : {error message}

ROOT CAUSE
{explanation of YAML syntax or config issue}

RECOMMENDED FIX
{exact YAML change}

VERIFY WITH
# Use GitHub's workflow validator or push to a test branch
```

## Phase 3 — Report to User

Always end with a summary regardless of whether auto-fix was applied:

```
DevOps Agent — Pipeline Report
Branch  : {branch}
Run     : {run-id}
Status  : {FIXED and pushed | REQUIRES MANUAL ACTION}

Category     : {failure category}
Root Cause   : {one-line summary}
Action Taken : {what was fixed | what is recommended}
Next Step    : {CI will re-run | human must apply fix}
```

## Hard Stops — always pause and ask the user

- Auto-fix changes production logic (not just compilation or tests)
- Flyway migration has already been applied to a shared environment
- Docker changes are needed (always requires human review)
- The failure is in `.github/workflows/` YAML (CI config changes need human approval)
- Multiple failure categories present simultaneously (diagnose all, fix none without approval)
- `mvn test` fails after the auto-fix attempt
