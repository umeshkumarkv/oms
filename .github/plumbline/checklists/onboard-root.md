# Onboard checklist — root CLAUDE.md (`/pl-onboard`, read by the judge)

1. Every unit found is in the Units table with its folder and its own CLAUDE.md — frontends included — or its skip is
   recorded in Decisions needed with the user's yes.
2. How they connect matches the units' Integrations and API sections.
3. Plumbline settings: JIRA project, Confluence space and parent page, base branch = the repository's default branch,
   PR target, branch pattern, Workflow, API and frontend URLs matching the config.
4. Text outside the markers is unchanged.
5. Architecture, Decisions & trade-offs (each with its source) and **Conventions** (how code is written here — money,
   errors, logging, naming, tests — each from existing docs or a consistent code pattern, with its source) are present.
6. Decisions needed: `Secrets found` lists every secret from the scan (file and key, never a value) or says none; every
   unit's "reachable without that check" risk; every "Docs vs code" item the unit writers reported; every place the
   repository's docs, settings and code disagree on how work is done — each with both sides and files — or "None".
   A security item stays listed (with any answer next to it) until the code no longer has it.
7. The root is at most 10 KB and points to unit files instead of repeating them.
