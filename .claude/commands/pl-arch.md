---
description: Plumbline — the architect's final design: HLD for an epic, LLD for a story, checked by an independent judge before any code
argument-hint: "<epic key → HLD, or story key → LLD>"
---

# /pl-arch

The architect's final word, which every later role follows exactly: the **HLD** per epic, the **LLD** per story. A
**writer** drafts, an independent **judge** checks; up to 3 attempts; nothing is posted without the architect's yes.

Input: $ARGUMENTS

## How subagents are started
- **Claude Code:** the Agent tool with `subagent_type: plumbline-arch-writer` or `plumbline-judge` — in the foreground (`run_in_background: false`): wait for each result before the next step.
- **GitHub Copilot:** VS Code — `#runSubagent` (the `agent/runSubagent` tool); Copilot CLI — the `task` tool; with
  agent `plumbline-arch-writer` or `plumbline-judge` (`.github/agents/`). If subagents are not available, stop and say so.

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
Drafts go in `.plumbline-drafts/<KEY>/`. Make sure `.plumbline-drafts/.gitignore` exists with the single line `*` (write it with your write tool; it keeps the folder out of git without touching `.git/`, which Claude Code protects).
Save the story you read from JIRA (description, criteria, links) as `.plumbline-drafts/<KEY>/story.md` for the writer
and judge of this run.

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

## Step 1 — Pre-flight
**Epic** → **HLD** from the epic, PRD and stories (draft `.plumbline-drafts/<KEY>/hld.md`). **Story** → **LLD** from the
story and its epic's HLD (gate: `plumbline-hld-final`; no epic → root CLAUDE.md only; draft `…/<KEY>/design.md`). Read
the root and each involved unit's CLAUDE.md. **Stop and ask** if: no acceptance criteria; a blocker not Done; another
unit's database, a new unit, an out-of-scope feature, or a library the root CLAUDE.md does not allow would be needed.

## Step 2 — Attempts (1, 2, 3)
1. **Writer** — `plumbline-arch-writer` with: HLD or LLD, its template (`hld.md` / `handoff-arch.md`), the input and
   CLAUDE.md paths, the draft path; for an HLD also "add its decisions and rules to the CLAUDE.md files"; findings.
2. **Scope check (HLD)** — `git status --porcelain`: only `CLAUDE.md` files (and drafts) changed; else a finding.
3. **Judge** — `plumbline-judge` with exactly: *Review:* the draft, the input and CLAUDE.md paths, `git diff` (HLD),
   the unit folders (writer: `plumbline-arch-writer`); *Checklist:* the HLD or Design checklist, verbatim.

After 3 failed attempts: stop, show the last findings, hand over (usually the story needs the BA to clarify).

### HLD checklist
1. Every story of the epic is in *Story design order* with exactly one unit; units exist in the root CLAUDE.md.
2. Interactions name each call/event with its failure behaviour; consumed events have a producer.
3. Data: one owning unit each, no shared database. 4. Cross-cutting: security/roles, errors, idempotency, audit.
5. Every decision has its reason; nothing contradicts the Rules or **Must never** of the root and unit CLAUDE.md files
   (a deliberate exception names the rule and why); no production code.
6. CLAUDE.md diff adds only the HLD's decisions and `[decided <EPIC>]` rules — nothing not yet in the code.

### Design checklist
1. *Acceptance criteria → design* maps every AC id to how it is met **and checked** (endpoint, status, fields, error
   code, or UI behaviour) — none left out; no criterion is changed (questions for BA go under Open questions).
1b. Conforms to the epic's HLD: the unit, interactions, data ownership and decisions are the HLD's (a needed change
   → stop: the HLD must change first). Technical blockers named or None; story points 1/2/3/5/8.
2. Endpoint: method, path, roles, request/response fields with types; unit and port match the CLAUDE.md files.
3. Every error path has a status and an error code in the project's error format.
4. Data: the migration (next free version, in the unit's own schema) or "no schema change"; entity changes with types.
5. Service logic: class and method, ordered steps, error paths; keeps the unit's layering, Rules and **Must never**
   (a list or query the Must never limits states its bound and why it holds).
6. Calls to other units hit existing endpoints, with failure/timeout behaviour; every event has a producer and consumer.
7. Cache/config/env-var changes and the API-spec delta stated, or "none".
8. **Identity and access, exactly:** the ownership field, each identifier's kind (user id vs customer id, compared like
   with like), who may call each endpoint per role, and the headers/claims the gateway passes.
9. Tests: at least 3 cases per layer touched (logic, API/controller, UI), including the error paths.

## Step 3 — Hand over
Show the HLD or LLD, then ask before each — exact text first, then a yes. **HLD:** publish the page `<EPIC> — <title> —
HLD` under the PRD page; commit the CLAUDE.md changes on `docs/<EPIC>-hld`, push, open a docs PR on `pr.md` (the
architect owns it — merge it before stories are built); post `handoff-hld.md` on the epic with the page link, version and PR;
add `plumbline-hld-final`. **LLD:** post it on the story; add `plumbline-arch-approved`; set the story points and one
`Blocks` link per technical blocker. The architect's yes is final. Next: `/pl-arch <STORY>` in *Story design order*.
