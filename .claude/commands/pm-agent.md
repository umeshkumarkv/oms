# Product Manager Agent — Broker Dealer OMS

Own the product roadmap, write PRDs, create Epics, and prioritize the backlog.
This agent runs BEFORE the BA Agent decomposes stories.

## Usage
```
/pm-agent "plain English feature idea"
/pm-agent roadmap
/pm-agent epic:SCRUM-{n}
```

---

## Phase 0 — Understand the Product Context

1. Fetch current roadmap and open Epics:
   ```
   searchJiraIssuesUsingJql("project = SCRUM AND issueType = Epic ORDER BY created DESC")
   ```
2. Fetch the current sprint to understand capacity:
   ```
   searchJiraIssuesUsingJql("project = SCRUM AND sprint in openSprints() ORDER BY status ASC")
   ```
3. Read `CLAUDE.md` — Section 3 (service boundaries), Section 18 (Out of Scope):
   - If the feature request maps to anything in Section 18 — STOP and tell the user it is out of scope for the POC.
4. Identify which domain the request falls into: Trading, Client, Market, Risk, Execution, Audit, Auth, Infrastructure.

---

## Phase 1 — PRD Authoring

For each new feature, produce a Product Requirements Document with these sections:

### 1a. Problem Statement
```
PROBLEM
  Who has this problem: {trader | ops | admin | system}
  What they cannot do today: {specific gap}
  Business impact if unsolved: {risk | inefficiency | compliance gap}
```

### 1b. Proposed Solution
```
SOLUTION SUMMARY
  What the system will do: {one paragraph}
  User-facing change: {what the user sees or can do differently}
  API / data change: {new endpoints, new fields, new events}
```

### 1c. Success Criteria
```
SUCCESS CRITERIA
  - {measurable outcome 1 — must be curl-verifiable or visually testable}
  - {measurable outcome 2}
```

### 1d. Out of Scope
```
OUT OF SCOPE (this PRD)
  - {anything excluded, referencing CLAUDE.md Section 18 if applicable}
```

### 1e. Dependencies
```
DEPENDENCIES
  - Blocked by: {Epic or Story key if a prerequisite exists}
  - Enables: {downstream Epic or Story if applicable}
```

---

## Phase 2 — Epic Creation

1. Search for an existing Epic that covers this theme:
   ```
   searchJiraIssuesUsingJql("project = SCRUM AND issueType = Epic AND summary ~ '{keyword}'")
   ```
2. If no matching Epic exists, create one:
   ```
   createJiraIssue(
     issueTypeName: "Epic",
     summary: "{domain}: {outcome in one line}",
     description: {full PRD from Phase 1 formatted as Confluence storage format},
     labels: ["bmad-prd-approved"]
   )
   ```
3. If an Epic already exists and this feature extends it, add a comment with the PRD delta — do NOT create a duplicate Epic.

---

## Phase 3 — Backlog Prioritization

After creating the Epic, produce a prioritized breakdown:

```
BACKLOG ORDER (recommended)
  P1 — Must have for MVP: {features/stories}
  P2 — Should have: {features/stories}
  P3 — Nice to have: {features/stories}
  OUT OF SCOPE: {features per CLAUDE.md Section 18}
```

Rules:
- P1 items are unblocked and deliver the core user value
- P2 items depend on P1 or add significant depth
- P3 items are polish or edge case handling
- NEVER include anything from CLAUDE.md Section 18 in P1/P2/P3

---

## Phase 4 — Hand-off to BA Agent

Post a JIRA comment on the Epic:
```
PM Agent ({date}): PRD approved. Epic ready for story decomposition.
Labels added: bmad-prd-approved
Next step: Run /ba-agent epic:{epic-key} to decompose into stories.
Prioritization: {P1/P2/P3 summary}
```

Add label `bmad-prd-approved` to the Epic.

---

## Phase 5 — Report to User

```
PM Agent — PRD Complete
Date: {ISO 8601 date}

EPIC
  {SCRUM-key}: {epic summary}
  Status: {new | updated}

PRD SECTIONS
  Problem: {one-line summary}
  Solution: {one-line summary}
  Success criteria: {n} items
  Out of scope: {n} items
  Dependencies: {none | list}

BACKLOG ORDER
  P1: {n} features
  P2: {n} features
  P3: {n} features

NEXT STEP
  Run: /ba-agent epic:{epic-key}
```

---

## Hard Stops — always pause and ask the user

- Feature request is entirely in CLAUDE.md Section 18 (Out of Scope for POC)
- Feature would require a new microservice not in CLAUDE.md Section 3
- Feature contradicts an existing architectural decision in CLAUDE.md
- Two Epics already exist that partially cover the request — clarify before creating a third
- Story points would exceed one sprint's capacity (> 20 pts in P1)
