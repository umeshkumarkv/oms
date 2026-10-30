---
description: Plumbline — run a story's acceptance criteria against the running system and record the evidence; checked by an independent judge
argument-hint: "<story key, e.g. CRS-12>"
---

# /pl-qa

Test one story's acceptance criteria against the **running** system — API calls and, for UI criteria, Playwright — and
record the evidence. A **runner** subagent executes the tests; an independent **judge** checks the evidence actually
proves each criterion. Up to 3 attempts, then it is handed to the user. QA reports what it observed; it never fixes or
diagnoses code.

Story: $ARGUMENTS

## How subagents are started
- **Claude Code:** the Agent tool with `subagent_type: plumbline-qa-runner` or `plumbline-judge` — in the foreground (`run_in_background: false`): wait for each result before the next step.
- **GitHub Copilot:** VS Code — `#runSubagent` (the `agent/runSubagent` tool); Copilot CLI — the `task` tool; with
  agent `plumbline-qa-runner` or `plumbline-judge` (`.github/agents/`). If subagents are not available, stop and say so.

Never do the runner's or the judge's work yourself. **Shell:** run only `git` (from the repository root — never `git -C`, which permission rules do not match), `bash .github/plumbline/check-template.sh` (and `gh` if present) yourself, plus
`curl` (`curl.exe` on Windows) for the health check and the start commands the user approves in Step 1. Do not research the code yourself — that is the
writer's/runner's and the judge's job; you route, check scope and hand over.
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

## Drafts
Evidence goes in `.plumbline-drafts/<KEY>/qa/`. Make sure `.plumbline-drafts/.gitignore` exists with the single line `*` (write it with your write tool; it keeps the folder out of git without touching `.git/`, which Claude Code protects).

## JIRA hand-off
Roles pass work to each other only through JIRA — tickets, comments and labels — never through files on someone's
machine, so no agent redoes what an earlier role settled. The PRD and each epic's HLD are Confluence pages (under the
`Confluence parent page` in the Plumbline settings), linked from the epic; every other hand-off is a ticket or comment.
The person running a command is that role's SME: their yes at the hand-over is the approval, and JIRA records who.

| Step | Posts | Template | Label added on the SME's yes |
|---|---|---|---|
| `/pl-pm` | PRD page; the epic, with summary + PRD link | `prd.md`, `epic.md` | `plumbline-prd-approved` |
| `/pl-ba` | stories under the epic — business criteria | `story.md` | `plumbline-dor-ready` |
| `/pl-arch <EPIC>` | HLD page under the PRD; epic comment with its link; decisions → CLAUDE.md docs PR | `hld.md`, `handoff-hld.md`, `pr.md` | `plumbline-hld-final` |
| `/pl-arch <STORY>` | story comment — the LLD; story points set | `handoff-arch.md` | `plumbline-arch-approved` |
| `/pl-dev` | PR description; story comment — branch, PR, test result | `pr.md`, `handoff-dev.md` | — (the PR is reviewed next) |
| `/pl-code-review` | the same text as the PR review and the story comment | `handoff-review.md` | `plumbline-review-passed` |
| `/pl-qa` | story comment — evidence per criterion | `handoff-qa.md` | `plumbline-qa-passed` |
| `/pl-doc` | docs PR updating CLAUDE.md + story comment | `pr.md`, `handoff-doc.md` | the architect approves the docs PR |
| `/pl-devops` | PR description; story comment | `pr.md`, `handoff-devops.md` | — |
| `/pl-sm` | sprint report; one comment per flagged ticket | `sprint-report.md`, `sm-flag.md` | — |

- **Input:** the ticket (description, labels, links), the latest `[Plumbline /pl-<role>]` comment of each kind (JIRA
  shows `\[Plumbline …\]`: ignore backslashes), and each PRD or HLD page they link at the version named (a story's HLD
  is linked from its epic; no version → compare last-modified times).
- **Gate:** start only when the previous step's label is on the ticket (a story's design also needs
  `plumbline-hld-final` on its epic). A hand-off edited after it was posted (comment `updated` later than `created`;
  PRD or HLD page past the pinned version) → `STOPPED: <what> changed after approval — re-run <command>`.
- **The architect's word is final:** later roles follow the HLD and LLD exactly. A role that cannot → `STOPPED:
  design gap — /pl-arch <KEY>`; never work around it. Do not redo an earlier role's work.
- **Output:** post what the table says, on its template, after the SME's yes with the exact text shown first; then
  add the label.
- **Status:** with the hand-over the SME approves, move the ticket via JIRA's transitions to the `Workflow` statuses
  (Plumbline settings): `/pl-dev` → In Progress (epic too), then In Review with the PR; a review or QA FAIL → In
  Progress; only `/pl-doc` → Done, after merge and `plumbline-qa-passed` (epic with its last story). Never force one.
- No Confluence tools or no parent page → the PRD goes in the epic description, the HLD in the epic comment. A comment must stay under 30,000
  characters (JIRA's limit is 32,767): longer → split. No JIRA tools → output as text to paste. Drafts are scratch.

## Metrics
At the hand-over append to `.plumbline-drafts/metrics.csv` (header, if new:
`date,command,target,attempts,verdict,tests_run,minutes,human_decision`): date, command, story/branch/PR, attempts,
verdict, tests ran `yes`/`no`/`n/a`, minutes (`date +%s` at start and at hand-over), and `human_decision` once the user
answers: `approved`, `edited` or `rejected`. It stays local; `/pl-sm metrics` summarises it.
**Files:** before the first draft, create `.plumbline-drafts/.gitignore` containing `*` (the folder is never
committed). Create drafts and metrics with your write tool — never `mkdir`, `echo`, `printf` or redirection.

## Step 1 — Setup
1. Read the story (business acceptance criteria) and the *Acceptance criteria → design* section of its latest
   `[Plumbline /pl-arch]` comment — the technical check for each AC — and save both as `.plumbline-drafts/<KEY>/story.md`. The branch in its latest `[Plumbline /pl-dev]` comment is the code under test.
2. **Gate:** `plumbline-review-passed` on the story (see JIRA hand-off); checked as the JIRA hand-off Gate says.
3. **Environment.** URLs from the root `CLAUDE.md` Plumbline settings (API URL, frontend URL); ask if missing. Check
   each answers with exactly `curl -s -o /dev/null -m 5 -w "%{http_code}" <url>` (any HTTP status means it is up; `000`
   means it is not). If not, offer:
   - **(a) start it here** — show the exact run command of each unit from its CLAUDE.md "Build & run" (with the
     branch under test checked out) and wait for a yes. Start each in the background, poll the URL for up to 3
     minutes, and stop what you started at the end of the run;
   - **(b) use another environment** — the user gives a staging/test URL; use it for this run only;
   - **(c) stop** — report "QA not run: system not reachable at <url>". Never mark a criterion PASS or FAIL without
     having run it.
   When nobody can answer (an unattended run), take (c).
4. Credentials come **only** from environment variables (`PLUMBLINE_DEMO_USER`, `PLUMBLINE_DEMO_PASSWORD`, or what the
   CLAUDE.md names). Never write a password or token into a file, a report or a comment.
5. Classify each criterion: **API** (status/body), **UI** (what the user sees), or **BOTH** (a submit flow); and
   **WRITES** (creates/changes data) or **READS**. Show the table and write it to `.plumbline-drafts/<KEY>/qa/plan.md`.

## Step 2 — Attempts (1, 2, 3)
1. **Runner** — start `plumbline-qa-runner` with: the plan path, the story path, the URLs, the names of the credential
   environment variables, the evidence path `.plumbline-drafts/<KEY>/qa/evidence.md`; on attempts 2–3 the judge's
   findings.
2. **Judge** — start `plumbline-judge` with exactly: *Review:* the evidence path, the plan and story paths;
   *Checklist:* the **QA evidence checklist** below, verbatim.
3. PASS (every criterion passed, judge PASS) → Step 3. Judge FAIL (evidence does not prove the criteria) → next
   attempt. A criterion that genuinely **fails** is not retried away: it is the result — go to Step 3 with it.

### QA evidence checklist
1. Every criterion has a result (PASS / FAIL) with the exact request or UI steps, the expected and the actual outcome.
2. Each PASS is proven by checking the field and value the criterion names, not only the status code.
3. Writes: the evidence shows the data really changed (a follow-up read, or the database/event when the plan has
   access).
4. Standard edge cases were run where they apply: unknown id → 404, no credentials → 401/redirect, invalid input →
   400/422, response format as the CLAUDE.md files describe.
5. UI checks assert the behaviour the criterion describes, not just that an element exists.
6. Nothing was skipped without a stated infrastructure reason; no password or token appears in the evidence.
7. Failures are reported as observed (request, expected, actual, body) — no guesses at the cause.

## Step 3 — Hand over
Show: criterion · type · result. For each failure, exactly four fields: request/steps · expected · actual · response
body. If a failure looks like a design problem (wrong owner field, role, missing header), say "design gap — run
`/pl-arch <KEY>`" and nothing more. Ask before posting the report as a JIRA comment (`[Plumbline /pl-qa] <date>`), the approval label as the JIRA hand-off says (`plumbline-qa-passed`) when every
criterion passed, and the status move it names (a failure → In Progress) — exact text first, then a yes.
