---
description: Plumbline — document an existing codebase into CLAUDE.md files (one per unit + a master), each checked by an independent judge
argument-hint: "[unit name, or empty for all — existing files are refreshed, not rewritten]"
---

# /pl-onboard

Document this repository for people and AI agents: CLAUDE.md files per unit plus a master at the root. A **writer**
drafts each, an independent **judge** checks it; up to 3 attempts per file, then it goes to the user.

Target: $ARGUMENTS (empty = every unit)

## How subagents are started
- **Claude Code:** the Agent tool with `subagent_type: plumbline-onboard-writer` or `plumbline-judge` — in the foreground (`run_in_background: false`): wait for each result before the next step.
- **GitHub Copilot:** VS Code — `#runSubagent` (the `agent/runSubagent` tool); Copilot CLI — the `task` tool; with agent `plumbline-onboard-writer` or `plumbline-judge`
  (`.github/agents/`). If subagents are not available in this chat, stop and say so.

Each subagent starts with a clean context. Never do the writer's or the judge's work yourself, and never pass the
writer's reasoning or your own opinion to the judge. **Shell:** run only `git` (from the repository root — never `git -C`, which permission rules do not match), `bash .github/plumbline/check-template.sh` commands, and in Step 2b each
unit's own test command, yourself (they work in bash, PowerShell and cmd alike); find and read files with your file search/read tools — never `find`, `grep`, `ls`, `cat`.
If a helper replies with `FILE: <path>` and the content instead of writing the file, save that content to the path
unchanged, then continue — but only for a path you gave that helper; any other path is a FAIL finding, not a write.

## Every run
- **Judge replies:** only the judge's JSON counts. Not JSON → ask that judge once more, then count the attempt as FAIL
  ("judge reply unreadable"). Never decide PASS yourself or drop a finding before the next attempt.
- **Project memory:** no root `CLAUDE.md` → `STOPPED: run /pl-onboard first` (only `/pl-onboard` runs without one).
  Helpers work from the CLAUDE.md files (kept current by `/pl-doc`) and the JIRA/Confluence
  material, opening code only for what they change or review; never research the code yourself. List their "Add to
  CLAUDE.md" / "CLAUDE.md out of date" notes in the hand-over for `/pl-doc`.
- **Freshness:** for each unit CLAUDE.md a helper gets, run `git log --oneline <its Last updated commit>..HEAD --
  <unit folder> ':!**/CLAUDE*.md'` (CLAUDE.md's own edits do not make it stale). Commits listed → tell the helpers and the judge "CLAUDE.md may be stale for: <files those commits
  changed>", and say so in the hand-over.
- **Templates and lanes:** everything that leaves this machine — JIRA, Confluence, PR descriptions and reviews — and
  every CLAUDE.md file is written on its template in `.github/plumbline/templates/` (`claude-root.md`,
  `claude-unit.md`, `claude-reference.md` for those files; the JIRA hand-off table for the rest), and names the
  template's version. Give each writer the template, the version (`.github/plumbline/VERSION`) and
  `git rev-parse --short HEAD`; tell the judge the writer's agent name, so it checks that agent's *Your lane*. Before
  each judge and before posting run `bash .github/plumbline/check-template.sh <template> <draft>` (it also fails text
  outside the lane): FAIL = that attempt's finding, no judge. No bash → the judge checks it. Set the footer's `judge`.
- **Limits:** at most 3 attempts per item, one writer and one judge each. More than 6 items → show the list and ask;
  nobody to answer → do the first 6 and list the rest.
- **Nobody to answer** (scheduled/headless): where a step says ask, take its safe choice or stop that item — never
  guess; write nothing outside the repository and `.plumbline-drafts/`; list the unasked questions in the hand-over.
- **Hand-over:** first line `PASS` · `FAIL after 3 attempts` · `STOPPED: <reason>` · `UNVERIFIED: <what did not
  run>`; then the details; then each question for the user with the exact text or command it would send or run.

## Metrics
At the hand-over append to `.plumbline-drafts/metrics.csv` (header, if new:
`date,command,target,attempts,verdict,tests_run,minutes,human_decision`): date, command, story/branch/PR, attempts,
verdict, tests ran `yes`/`no`/`n/a`, minutes (`date +%s` at start and at hand-over), and `human_decision` once the user
answers: `approved`, `edited` or `rejected`. It stays local; `/pl-sm metrics` summarises it.
**Files:** before the first draft, create `.plumbline-drafts/.gitignore` containing `*` (the folder is never
committed). Create drafts and metrics with your write tool — never `mkdir`, `echo`, `printf` or redirection.

## Step 1 — Settings
Read the root `CLAUDE.md` if it exists. Missing **Plumbline settings** (JIRA project, Confluence space and parent
page, base branch, Workflow) → ask once. Suggest the base branch from `git symbolic-ref refs/remotes/origin/HEAD` (often
not `main`), and the Workflow from the JIRA statuses mapped to To Do → In Progress → In Review → Done.

## Step 2 — Find the units
Find build files with `git ls-files`: `pom.xml`, `build.gradle(.kts)`, `package.json` and other stacks' (not under
`node_modules`, `target`, `build`, `dist`). **Every folder with its own build file and source is a unit** — backend,
frontend, worker, library alike; a parent file that only lists modules is not. Skipping one needs the user's yes,
recorded in the root's *Decisions needed*. Each unit gets `<folder>/CLAUDE.md` + `CLAUDE.reference.md` (an app at the
repository root → `src/`). Show one table (name, folder, kind, mode — see Step 3) and continue.
**Secrets scan:** `git ls-files | grep -iE 'secret|credential|\.env($|\.)|\.pem$|\.key$|id_rsa'` and, in those and
the config files, values set for keys named `*password*`, `*secret*`, `*token*`, `*key*`. Keep each file and key —
**never a value** — for the root's `Secrets found`; placeholders (`<…>`, `${…}`, `changeme`) do not count.

## Step 2b — Test recipe (one run per unit, reused when nothing changed)
**Reuse** the unit's existing recipe if its CLAUDE.md has one and no build file changed since its `Last updated`
commit (`git log --oneline <commit>..HEAD -- <build files>` is empty). Otherwise take the test command from the build
files (`./mvnw test`, `mvn test`, `./gradlew test`, `npm test -- --watch=false`; a README wins), install the
project's **own dependencies from its lockfile** if missing (`npm ci`; Maven/Gradle download them) — never tools or
runtimes — and run it once as `cd <folder> && <command>` (15-minute limit). Keep: command, runtime used
(`java -version`, `node -v`) and the one the build files require, result (`N tests, M failed` or the first error),
duration. Change no file to make it pass; record failures exactly — other commands rely on the recipe.

## Step 3 — Each unit, one at a time (helpers run in the foreground)
**Mode** per unit: **new** (no CLAUDE.md), **refresh** (both files exist), or **keep**. Refresh first costs no
writer: run `check-template.sh` on both files against the current templates, and the Freshness check. Both clean →
judge only (step 3 below, attempt 1); PASS → **keep** — the files are not touched. Otherwise the writer runs in
**update mode** with the template and judge findings: keep every line that is still true, fix what failed, add what
the template now requires; never rewrite the rest. For attempt = 1, 2, 3:
1. **Writer** — `plumbline-onboard-writer` with: mode, unit name, folder, both paths, the test recipe, the commit
   (`git rev-parse --short HEAD`) and today's date for `Last updated`; on a retry the judge's findings (verbatim).
2. **Scope check** — `git status --porcelain`: only this unit's `CLAUDE.md` / `CLAUDE.reference.md` changed (drafts are
   ignored). Anything else → `git checkout -- <file>` and that is the attempt's finding.
3. **Judge** — `plumbline-judge` with: *Review:* both paths, the unit folder, the deploy files (`docker-compose*.yml`,
   `k8s/`, Helm), "`git diff -- <both paths>`: text outside the markers unchanged"; *Checklist:* the file
   `.github/plumbline/checklists/onboard-unit.md` — the judge reads it.
4. **PASS** → next unit. **FAIL** → next attempt with the findings.
After 3 failed attempts: leave the file, mark the unit **ESCALATED**, keep its last findings, and move on.

## Step 4 — Root master CLAUDE.md
Only if at least one unit passed. Mode as in Step 3. Writer: `plumbline-onboard-writer` with "root master file:
`CLAUDE.md`", the units table with results, the settings, the secrets scan (files and keys only), and every
"Docs vs code" line the unit writers replied with. Judge: the root file, the deploy files, and the checklist file
`.github/plumbline/checklists/onboard-root.md`. Up to 3 attempts. Then show the root *Solution* and ask "Is this the
system you meant?" — no → stop: wrong repository or project.

## Step 4b — Settle the decisions
For each item in **Decisions needed**, ask the user (show both sides and the files). Write each answer into the
Plumbline settings as the value plus `(decided <date>)` and remove the item — except a **security item** (`Secrets
found`, reachable without the check): the answer goes next to it, and it stays until the code no longer has it. Other
commands treat these settings as the rule. Unattended → leave the items; other commands name them in hand-overs.

## Step 5 — Summary
One table: unit · mode · result (PASS / KEPT / ESCALATED) · attempts · test recipe result · open findings. Untested or
failing units first: `/pl-dev` on them will be **UNVERIFIED**. Then: review with `git diff` (discard a file:
`git checkout -- <file>`), and offer to commit on a branch and open a PR described on `pr.md` — the exact text first,
then a yes. Nothing else is written.
