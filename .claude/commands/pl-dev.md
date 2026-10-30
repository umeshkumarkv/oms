---
description: Plumbline — implement a story with tests; checked by the build, a scope check and an independent judge
argument-hint: "<JIRA key, e.g. CRS-12, or a short story description>"
---

# /pl-dev

Implement one story on a new branch. A **writer** subagent makes the change; then three checks: the project's
**build/tests**, a **scope check** (only allowed files changed), and an independent **judge**. Up to 3 attempts, then
it is handed to the user. Nothing is committed, pushed or posted without the user's yes.

Story: $ARGUMENTS

## How subagents are started
- **Claude Code:** the Agent tool with `subagent_type: plumbline-dev-writer` or `plumbline-judge` — in the foreground (`run_in_background: false`): wait for each result before the next step.
- **GitHub Copilot:** VS Code — `#runSubagent` (the `agent/runSubagent` tool); Copilot CLI — the `task` tool; with agent `plumbline-dev-writer` or `plumbline-judge` (`.github/agents/`).
  If subagents are not available in this chat, stop and say so.

Never do the writer's or the judge's work yourself, and never pass the writer's reasoning to the judge. **Shell:** run
only `git` (from the repository root — never `git -C`, which permission rules do not match), `bash .github/plumbline/check-template.sh` and the project's build/test command yourself (they work in bash, PowerShell and cmd alike); find and read
files with your file search/read tools — never `find`, `grep`, `ls`, `cat`.
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

## Step 1 — The story
- Several keys (`CRS-301 CRS-302`): implement them in that order on one branch; everything below applies to each.
- A JIRA key: read the story (summary, description, acceptance criteria), and its latest `[Plumbline /pl-arch]` comment (the design) with the connected JIRA/Atlassian tools, and save both in `.plumbline-drafts/<KEY>/` for this run's
  writer and judge. No `plumbline-arch-approved` label → `STOPPED` (see JIRA hand-off). No JIRA tools → ask the user to
  paste the story and the design.
- A description: use it. No acceptance criteria at all → stop and ask; never invent them.

## Step 2 — Prepare
1. Read the root `CLAUDE.md` (base branch) and the CLAUDE.md files of the units the story touches.
2. Build/test command: from the units' CLAUDE.md "Build & run" (on Windows use the `.cmd` wrapper, e.g. `mvnw.cmd`).
3. `git status` clean, then `git switch -c ai/<KEY>-<short-slug> <base branch>`.
4. **Baseline:** run the build/test command once before any change. Failures now are pre-existing and do not count
   against the story — note which. If it cannot run at all, tell the user and continue: tests are still written, and
   the judge checks them by reading.

## Step 3 — Attempts (1, 2, 3)
1. **Writer** — start `plumbline-dev-writer` with: the story, the design path(s), the branch, the units and their
   CLAUDE.md paths, the build/test command; on attempts 2–3 also the previous findings and build output.
2. **Build/tests** — run the build/test command yourself. A failure that is not in the baseline → that is the finding;
   next attempt. Pre-existing failures do not count; say so in the hand-over.
3. **Scope check** — run `git diff --name-only <base branch>` and `git status --porcelain`. Fail if anything under
   `.claude/`, `.github/`, any `CLAUDE.md`/`AGENTS.md`, or CI/build settings that skip tests changed, or if an
   existing test was deleted. → next attempt with that as the finding.
4. **Judge** — `plumbline-judge` with exactly: *Review:* "the change on this branch: `git diff <base branch>`", the
   story, the design path(s), the CLAUDE.md paths; *Checklist:* the **Dev checklist**, verbatim. FAIL → next attempt.

After 3 failed attempts: stop, show the last findings and the branch, and hand over to the user.

### Dev checklist
1. Each acceptance criterion is implemented, and has a test that would fail without the change (name the test).
2. The change follows the rules and Must-never items in the root and unit CLAUDE.md files.
3. No test was deleted, skipped or weakened to pass; no check was switched off.
4. Inputs are validated; no secrets in code or logs; no SQL/command built by string concatenation; errors do not
   leak internals.
5. Only the code the story needs changed; nothing unrelated.
6. Any API or schema change is backward compatible, or the story says it may break — and callers in other units are
   updated or listed.
7. Every test the design's Tests section lists exists in the diff — answer as a list `design item → test name`. A
   missing one fails this item, unless the story itself moves it to another named story.
8. The change follows the LLD and HLD exactly (endpoints, order of checks, error codes, files, units, data
   ownership). Any difference fails this item — the fix is the design (`/pl-arch`), never a deviation.

## Step 4 — Hand over
Build/test command could not run (or only for some units) → the hand-over's first line is **UNVERIFIED — the tests
were written but not run (<reason>). Run `<command>` before merging.** Never call such work "tested".
Show the AC → tests and design → tests tables, files changed, build result and judge verdict. Then ask before each of:
commit (`<KEY>: <summary>`), push, open the PR described on `pr.md`, and the `[Plumbline /pl-dev]` comment on
`handoff-dev.md` — exact text first, then a yes.
