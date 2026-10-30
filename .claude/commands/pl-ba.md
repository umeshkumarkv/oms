---
description: Plumbline — split an epic or requirement into ready-to-build stories with testable acceptance criteria, each checked by an independent judge
argument-hint: "<epic key, e.g. CRS-40, or a requirement in plain English>"
---

# /pl-ba

Turn an approved epic (or a requirement) into user stories that a developer — human or agent — can build without
guessing: user story, testable acceptance criteria, scope, constraints, test data, points. A **writer** subagent drafts
each story; an independent **judge** checks it. Up to 3 attempts per story, then it is handed to the user. Stories are
created in JIRA only after the user says yes.

Input: $ARGUMENTS

## How subagents are started
- **Claude Code:** the Agent tool with `subagent_type: plumbline-ba-writer` or `plumbline-judge` — in the foreground (`run_in_background: false`): wait for each result before the next step.
- **GitHub Copilot:** VS Code — `#runSubagent` (the `agent/runSubagent` tool); Copilot CLI — the `task` tool; with
  agent `plumbline-ba-writer` or `plumbline-judge` (`.github/agents/`). If subagents are not available, stop and say so.

Never do the writer's or the judge's work yourself, and never pass the writer's reasoning to the judge. **Shell:** run
only `git` (from the repository root — never `git -C`, which permission rules do not match), `bash .github/plumbline/check-template.sh` yourself; read and search files with your file tools. Do not research the code yourself — that is the
writer's and the judge's job; you route, check scope and hand over.
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
Drafts go in `.plumbline-drafts/<KEY or slug>/`. Make sure `.plumbline-drafts/.gitignore` exists with the single line `*` (write it with your write tool; it keeps the folder out of git without touching `.git/`, which Claude Code protects).

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

## Step 1 — Input
- An epic key: fetch it (and its PRD, from the description or a linked Confluence page) with the connected
  JIRA/Atlassian tools. The epic must pass the JIRA hand-off gate for `plumbline-prd-approved`.
- Plain English: use it and continue. BA never creates epics: the stories are created without a parent unless the
  user names an epic at the hand-over (suggest `/pl-pm` for a new epic).
- No JIRA tools: ask the user to paste the epic/PRD.

Read the root `CLAUDE.md` (solution, rules, out-of-scope list); the writer may read unit CLAUDE.md files and code to
learn what exists today, but writes business language only.
Split the work into stories — the PRD's P1 items unless the user names other priorities — each one piece of user value
that can be checked on its own. Show the list (title, rough
size S/M/L) and continue. Which unit builds a story, its points and technical blockers are `/pl-arch`'s.

**Stop and ask the user** if a story is in the root CLAUDE.md out-of-scope list or contradicts a rule there.

## Step 2 — Each story, attempts (1, 2, 3)
1. **Writer** — start `plumbline-ba-writer` with: the requirement/PRD, the story title, the CLAUDE.md paths, the
   draft path `.plumbline-drafts/<KEY>/story-<n>.md`; on attempts 2–3 the judge's findings.
2. **Judge** — start `plumbline-judge` with exactly: *Review:* the story path and the root CLAUDE.md path (writer:
   `plumbline-ba-writer`);
   *Checklist:* the **Story checklist** below, verbatim. PASS → next story. FAIL → next attempt.

After 3 failed attempts: mark the story ESCALATED with its last findings and move on. A story with more than about 6
criteria after attempt 3 must be split — say so.

### Story checklist
1. User story is "As a <specific business role> / I want / So that"; *Today* states the current situation in business terms.
2. Acceptance criteria are numbered `AC1`, `AC2`, … and written as Given / When / Then about what a user does and sees;
   each can be checked on its own by watching the system.
3. They cover the normal case, missing or not found, invalid input, and who may not do it — where they apply.
4. Business language only: no API paths, status codes, services or units, data model, class names or points (the
   template check catches most; also judge wording such as "the endpoint returns").
5. Dependencies are named (other stories, business decisions) or "None".
6. Nothing from the root CLAUDE.md out-of-scope list; nothing that contradicts its rules.
7. Examples are concrete enough to check the criteria with, or "None" with a reason.
8. The story is one piece of user value: at most about 6 criteria; bigger → the hand-over says how to split it.

## Step 3 — Hand over
Show a table: story · size · result. For each passed story, show its checklist (items 1–8) and ask before creating it
in JIRA under the epic — show the exact content first and wait for a yes. **Writing to JIRA (once approved):** create
each story with its epic as `parent`, the first line as summary and the rest (the footer's judge filled in) as the
description; acceptance criteria as plain `-` bullets; one `Blocks` link per business dependency that is a story. Create stories in dependency order and replace each
"story <n>" reference with its new key. Do not set story points — the architect sets them in `/pl-arch`. Approval follows the JIRA hand-off.
