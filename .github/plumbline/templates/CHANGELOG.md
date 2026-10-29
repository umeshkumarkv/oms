# Template versions

Every output names the template version it was written on: a hand-off in its footer (`… · story v1 · …`), a
CLAUDE.md file in its `Last updated` line. When a template changes, its version goes up and an entry is added here.
Commands that read an older hand-off accept it as written: they look for the sections that version had (listed
below) and never fail a hand-off for being on an older template. Only new output must use the current version.

## v1 — Plumbline 2.8.6 (baseline)
All templates start at v1: `prd`, `epic`, `story`, `hld`, `handoff-hld`, `handoff-arch`, `handoff-dev`,
`handoff-review`, `handoff-qa`, `handoff-doc`, `handoff-devops`, `sprint-report`, `sm-flag`, `pr`, `claude-unit`,
`claude-reference`, `claude-root`. Output from Plumbline 2.8.0–2.8.5 has no template version in its footer; read it
as v1.
