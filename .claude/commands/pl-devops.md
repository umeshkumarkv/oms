---
description: Plumbline — diagnose a failing CI run and fix it (code, tests, coverage) or propose the fix (config, pipeline), checked by an independent judge
argument-hint: "<PR number or branch with a failing CI run>"
---

# /pl-devops

Get a failing CI run green the honest way. A **fixer** subagent classifies the failure and either fixes it (compile
error, failing test, coverage) or writes a diagnosis for you to approve (pipeline, container, migration, dependency,
secrets). An independent **judge** checks the fix addresses the cause and weakens nothing. Up to 3 attempts, then it
is handed to the user. Nothing is pushed without your yes.

Target: $ARGUMENTS

## How subagents are started
- **Claude Code:** the Agent tool with `subagent_type: plumbline-devops-fixer` or `plumbline-judge` — in the foreground (`run_in_background: false`): wait for each result before the next step.
- **GitHub Copilot:** VS Code — `#runSubagent` (the `agent/runSubagent` tool); Copilot CLI — the `task` tool; with
  agent `plumbline-devops-fixer` or `plumbline-judge` (`.github/agents/`). If subagents are not available, stop and say so.

Never do the fixer's or the judge's work yourself. **Shell:** run only `git` (from the repository root — never `git -C`, which permission rules do not match), `bash .github/plumbline/check-template.sh`, `gh` and the project's build/test
command yourself. Do not research the code yourself — the fixer and the judge do; you route,
check scope and hand over.
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
Logs and diagnoses go in `.plumbline-drafts/ci-<branch>/`. Make sure `.plumbline-drafts/.gitignore` exists with the single line `*` (write it with your write tool; it keeps the folder out of git without touching `.git/`, which Claude Code protects).

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

## Step 1 — The failure
1. Find the latest failed run for the PR/branch (`gh run list --branch <b>`, `gh run view <id> --log-failed`, or the
   connected GitHub tools) and save the failing log to `.plumbline-drafts/ci-<branch>/ci.log`. No `gh` and no GitHub
   tools → ask the user to paste the failing log.
2. Check out the branch (`git switch <branch>`; `git status` must be clean first).
3. **Pre-existing failures:** a failure that also fails on the base branch (its last CI run, or a log the user gives)
   is not this branch's problem — note it for the hand-over and fix only what this branch broke. Several categories
   *caused by this branch* is a stop-and-ask; one new failure plus old ones is not.

## Step 2 — Attempts (1, 2, 3)
1. **Fixer** — start `plumbline-devops-fixer` with: the log path, the branch, the build/test command (from the units'
   CLAUDE.md), the diagnosis path `.plumbline-drafts/ci-<branch>/diagnosis.md`; on attempts 2–3 the new log and the
   judge's findings.
2. **If the fixer wrote a diagnosis instead of a fix** (pipeline YAML, container file, migration already applied,
   dependency, secret): show it to the user. Changes under `.github/` are never made by the agent — they go in a
   separate PR a human makes. For other categories, if the user approves, start the fixer again with "approved:
   apply the diagnosis" (this does not use an attempt).
3. **Local check** — run the build/test command yourself; it must pass.
4. **Judge** — start `plumbline-judge` with exactly: *Review:* "`git diff` on branch <branch>", the diagnosis/log
   paths; *Checklist:* the **Fix checklist** below, verbatim. FAIL → next attempt.
5. **CI** — on judge PASS, ask the user before commit + push to the PR branch (show the message; never `main`/the base
   branch). After the push, check the new run (`gh run view`); still red → save the new log, next attempt.

### Fix checklist
1. The fix addresses the root cause named in the classification, not a symptom.
2. No test, check, coverage threshold or scanner was skipped, disabled, deleted or loosened.
3. The change is minimal — nothing unrelated to the failure.
4. Business behaviour is unchanged unless the failure was a real bug in it (then say so).
5. No secret, credential or environment-specific value was added.
6. The change can be rolled back by reverting this one commit.

**Stop and ask the user** if: the fix needs business-logic changes beyond the failing case; a migration was already
applied to a shared environment; this branch caused several failure categories at once; the change in behaviour looks
intended — a story or commit says so (then the user decides whether code or test is right; a commit called a
refactor or tidy-up did not intend to change output, so the tests are right); or the build fails with a new category after
a fix.

## Step 3 — Hand over
Show: category · cause · fix · local result · CI result · judge verdict. Then ask before each of: commit, push, the PR
described on `pr.md`, and the `[Plumbline /pl-devops]` comment on `handoff-devops.md` — exact text first, then a yes.
