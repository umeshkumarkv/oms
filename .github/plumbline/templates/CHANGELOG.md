# Template versions

Every output names the template version it was written on: a hand-off in its footer (`… · story v1 · …`), a
CLAUDE.md file in its `Last updated` line. When a template changes, its version goes up and an entry is added here.
Commands that read an older hand-off accept it as written: they look for the sections that version had (listed
below) and never fail a hand-off for being on an older template. Only new output must use the current version.

## claude-unit v2, claude-root v3 — Plumbline 2.8.10
`claude-unit` Security gains three required fields: `Identity`, `Verified at`, `Reachable without that check` (per
environment). `claude-root` gains a `## Conventions` section (before Plumbline settings) and a required
`Secrets found` field at the top of *Decisions needed*. Readers of v1 / v2 files: the fields may be missing — treat
reachability as unknown and say so; `/pl-onboard` in refresh mode adds them.

## claude-root v2 — Plumbline 2.8.8
*Plumbline settings* gains `Workflow`: the project's status names for To Do → In Progress → In Review → Done, which
commands use to move tickets. A v1 root CLAUDE.md has no `Workflow`: commands then use those four names and say so in
the hand-over; `/pl-onboard` adds it.

## v1 — Plumbline 2.8.6 (baseline)
All templates start at v1: `prd`, `epic`, `story`, `hld`, `handoff-hld`, `handoff-arch`, `handoff-dev`,
`handoff-review`, `handoff-qa`, `handoff-doc`, `handoff-devops`, `sprint-report`, `sm-flag`, `pr`, `claude-unit`,
`claude-reference`, `claude-root`. Output from Plumbline 2.8.0–2.8.5 has no template version in its footer; read it
as v1.
