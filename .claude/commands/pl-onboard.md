---
description: Plumbline — document an existing codebase into CLAUDE.md files (one per unit + a master), each checked by an independent judge
argument-hint: "[unit name, or empty for all]"
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
repository root → `src/`). Show one table (name, folder, kind, CLAUDE.md) and continue.

**Secrets scan:** `git ls-files | grep -iE 'secret|credential|\.env($|\.)|\.pem$|\.key$|id_rsa'` and, in those and
the config files, values set for keys named `*password*`, `*secret*`, `*token*`, `*key*`. Record each file and key —
**never a value** — for the root's *Decisions needed*; placeholders (`<…>`, `${…}`, `changeme`) do not count.

## Step 2b — Do the tests run here? (one run per unit)
Take each unit's test command from its build files (`./mvnw test`, `mvn test`, `./gradlew test`, `npm test --
--watch=false`; a README or existing CLAUDE.md wins) and the runtime they ask for. Run it once as `cd <folder> &&
<command>` (15-minute limit) and keep: the command, the runtime actually used (`java -version`, `node -v`), the result
(`N tests, M failed` or the first error) and the duration. Change no file to make it pass; if running needs something
you must not do (install software, unapproved network), do not run it and record why. The result is the unit's **test
recipe** — other commands rely on it, so record failures exactly.

## Step 3 — Each unit, one at a time (helpers run in the foreground)
For attempt = 1, 2, 3:
1. **Writer** — start `plumbline-onboard-writer` with: the unit name, folder, CLAUDE.md and reference paths, the test recipe
   from Step 2b, the current commit (`git rev-parse --short HEAD`) and today's date for its `Last updated` line, and on attempts 2–3 the judge's findings from the previous attempt (verbatim JSON).
2. **Scope check** — `git status --porcelain`: only this unit's `CLAUDE.md` / `CLAUDE.reference.md` changed (drafts are
   ignored). Anything else → `git checkout -- <file>` and that is the attempt's finding.
3. **Judge** — `plumbline-judge` with: *Review:* both paths, the unit folder, "`git diff -- <both paths>`: text outside
   the markers unchanged"; *Checklist:* the **Onboard checklist**, verbatim.
4. **PASS** → next unit. **FAIL** → next attempt with the findings.

After 3 failed attempts: leave the file as it is, mark the unit **ESCALATED**, keep its last findings for the summary,
and move on.

### Onboard checklist
1. Every endpoint/route the code defines is in the reference API section — search the mapping annotations or route
   definitions, compare the count, check at least 10 at random. None listed that the code does not define.
2. Frontend: every backend call with the endpoint it hits; calls no endpoint serves are listed as such. (N/A backend.)
3. Every entity/table is in the reference Data section with its key fields; none the code, migrations or SQL scripts
   do not define. Column names are fields, not tables.
4. Integrations: every outbound HTTP call, messaging topic and cache the code uses is listed; nothing invented.
5. Build & run, port and config match the build and config files. The **Test recipe** gives the command, runtime and
   the Step 2b result exactly (a failure is never reworded as success); absolute paths tests depend on are listed.
6. Security, as the code does it, with files: authentication, roles, token storage; **how identity reaches this unit
   and where it is verified**; and **whether the unit can be reached without that check** (published ports, k8s
   Service type). Trusting headers while directly reachable is stated as a risk, never as a design choice.
7. Every Rule is tagged `[code: <file>]` (and that file enforces it) or `[decided <source>]` (and that source says
   so); every Must-never is traceable to code or the root CLAUDE.md.
8. Tech debt states the TODO/FIXME count and top files; other debt as counts plus up to 10 locations. Debt recorded
   truthfully passes.
9. Core: 8 sections, at most **6 KB** (detail goes to the reference), the Reference lines' counts match the reference
   file. Reference: 6 sections, at most 30 KB. Both start with `Last updated: <commit> (<date>, /pl-onboard, <template> v<N>)`, with the given commit.
10. Text outside the plumbline markers is unchanged, in both files.
11. Decisions & trade-offs: each one names its source (code comment, README, ADR, commit message, config) — none is
    invented; "None found" is fine.

## Step 4 — Root master CLAUDE.md
Only if at least one unit passed. Writer: `plumbline-onboard-writer` with "root master file: `CLAUDE.md`, units: <the
table from Step 2 with results>, settings: <from Step 1>". Judge: `plumbline-judge` with the root file and this
checklist: (1) every unit is in the Units table with the right folder and CLAUDE.md; (2) How they connect matches the
units' Integrations/API sections; (3) Plumbline settings present (JIRA project, Confluence space and parent page, base
branch = the repository's default branch, Workflow, PR target, branch pattern, API URL, frontend URL — the URLs match the config); (4) text outside the markers unchanged;
(5) *Architecture* and *Decisions & trade-offs* (each with its source, none invented) present; (6) **Decisions needed** lists every place where the repository's own docs, the settings and the code disagree on how
work is done (base/PR branch, branch naming, ticket key, test command, environments) — each with both sides and their
files — or says "None"; (7) it also lists **every secret from the Step 2 scan** (file and key, never a value) and every
unit's security risk; (8) every unit from Step 2 is in the table with its own CLAUDE.md, or its skip is recorded;
(9) the root is at most 10 KB. Up to 3 attempts.

Then show the root *Solution* and ask "Is this the system you meant?" — no → stop; wrong repository or project.

## Step 4b — Settle the decisions
For each item in **Decisions needed**, ask the user to choose (show both sides and the files). Write each answer into
the Plumbline settings as the value plus `(decided <date>)`, and remove the item from the list — except a **security
item** (secret, bypassable check): the answer is recorded next to it, and it stays listed until the code no longer has it. Every other command
treats these settings as the rule. When nobody can answer (an unattended run), leave the items listed: other commands
then use the Plumbline settings value and name the open decision in their hand-over.

## Step 5 — Summary
One table: unit · result (PASS / ESCALATED) · attempts · file · test recipe result · open findings. If any unit's
tests did not run or failed on the untouched code, say so first: later `/pl-dev` runs on that unit will be
**UNVERIFIED** until it is fixed. Then: review with `git diff` (discard a file: `git checkout -- <file>`), and offer
to commit on a branch and open a PR described on `pr.md` — the exact text first, then a yes. Nothing else is written.
