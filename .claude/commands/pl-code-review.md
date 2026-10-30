---
description: Plumbline — independent review of a branch or pull request against the project's CLAUDE.md rules
argument-hint: "<PR number, branch name, or empty for the current branch>"
---

# /pl-code-review

Review a change with an independent **judge** subagent that never saw how the change was written. Read-only: the
review is posted only if the user says yes.

Target: $ARGUMENTS (empty = the current branch)

## How the judge is started
- **Claude Code:** the Agent tool with `subagent_type: plumbline-judge` — in the foreground (`run_in_background: false`): wait for each result before the next step.
- **GitHub Copilot:** VS Code — `#runSubagent` (the `agent/runSubagent` tool); Copilot CLI — the `task` tool; with agent `plumbline-judge` (`.github/agents/`). If subagents are not available
  in this chat, stop and say so.

Do not review the change yourself first and do not pass your opinion to the judge. **Shell:** run only `git` (from the repository root — never `git -C`, which permission rules do not match), `bash .github/plumbline/check-template.sh` (and
`gh` if present) yourself; read files with your file read/search tools.

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

## Step 1 — What to review
- Base branch: from the root `CLAUDE.md` Plumbline settings (else the repository's default branch).
- A PR number: `git fetch origin pull/<n>/head:pr-<n>` and review `pr-<n>`; or use the connected GitHub tools to read
  the PR. A branch: review it against the base. Empty: the current branch.
- The diff to review: `git diff <base>...<branch>`. Empty diff → say so and stop.
- The rulebook: the root `CLAUDE.md` and the CLAUDE.md of every unit the diff touches.
- **Scope:** the story keys in the branch name, PR title or the stories' `[Plumbline /pl-dev]` comments — use **all**
  of them. From JIRA take each story's criteria and its latest `[Plumbline /pl-arch]` comment (the design), and save them in
  `.plumbline-drafts/review-<branch>/` for the judge. No story found → ask which stories it implements; no answer or no
  story → review against the CLAUDE.md files only and say so in the report.

## Step 2 — Judge
Start `plumbline-judge` with exactly:
- *Review:* "`git diff <base>...<branch>` in this repository", the list of CLAUDE.md files that apply, and the story
  and design files in scope (or "no story given");
- *Checklist:* the **Review checklist** below, verbatim.

### Review checklist
1. **Correctness** — the code does what its name, story or PR title says; edge cases (null, empty, limits) handled.
2. **Project rules** — every rule and Must-never in the applicable CLAUDE.md files is respected.
3. **Security** — input validation, authorization on new endpoints, no secrets in code or logs, no SQL/command
   string concatenation, no sensitive data in errors.
4. **Data** — migrations are safe on existing data; no breaking schema change without a plan.
5. **APIs** — contract changes are backward compatible or flagged; callers in other units updated.
6. **Tests** — new behaviour is tested; no test deleted, skipped or weakened.
7. **Errors and logging** — failures handled, logged with IDs not secrets.
8. **Performance** — no N+1 queries, unbounded queries or loops over remote calls on a hot path.
9. **Scope** — no unrelated changes; no edits to `.claude/`, `.github/` or CLAUDE.md files unless that is the point
   of the change.
10. **Stories and designs** — every acceptance criterion of the stories in scope is implemented, every test their
    LLDs list exists, and the diff does nothing those stories do not ask for. N/A when no story is given.
11. **Design is final** — the code matches the LLD and the epic's HLD (endpoints, error codes, units, data ownership);
    any difference is a major finding "differs from the design", whatever its merit.
Judge only what this diff introduces; pre-existing issues are out of scope.

## Step 3 — Report
Show the verdict and a table: severity · file:line · finding · fix. Write it once on `handoff-review.md`; then offer,
each with the exact text and a yes: post that text as the PR review (connected GitHub tools or `gh pr review`) and as
the story comment on each story in scope; on PASS, its approval label as the JIRA hand-off says (`plumbline-review-passed`). Never approve or
merge.
