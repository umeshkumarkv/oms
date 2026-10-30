<!-- plumbline:template handoff-arch v1 -->
<!-- plumbline:forbid ^```(java|kotlin|typescript|ts|js|python|go)|public (class|interface) |@(Service|RestController|Entity|Test)$ -->
[Plumbline /pl-arch] {{YYYY-MM-DD}}

## Acceptance criteria → design
{{AC1 → how it is met and checked technically: endpoint, status, fields, error code or UI behaviour; one line per AC — QA tests against this}}

## Ownership and estimate
- Unit: {{the one unit that builds this story, as the epic's HLD assigns it}}
- Technical blockers: {{keys of stories/changes needed first, or None}}
- Story points: {{1 | 2 | 3 | 5 | 8 — set by the architect; 13+ means split the story}}

## Endpoint
{{method, path, auth and roles, unit and port, routing; request fields; response fields; every error (status, code, condition)}}

## Data
{{migration file (next free version) and DDL, or "no schema change"; entity fields and types}}

## Logic
{{class and method signature; numbered steps; error paths}}

## Calls and events
{{calls to other units (endpoint, timeout/fallback); events produced/consumed — or None}}

## Cache and config
{{keys, TTL, invalidation, new config or env vars — or None}}

## Identity and access
{{ownership field, identifier kinds, per-role access for every endpoint, gateway headers}}

## Tests
{{at least 3 named cases per layer touched, including error paths}}

## API-spec delta
{{paths and schemas to add or change, or "no change"}}

## Decisions
{{each design choice and why — /pl-doc copies these into CLAUDE.md "Decisions & trade-offs" — or None}}

## Open questions
{{anything the story or code does not answer, or None}}

— Plumbline {{version}} · {{/pl-command}} · handoff-arch v1 · commit {{short sha}} · judge {{pending | PASS n/3 | FAIL 3/3 | n/a}} · AI-drafted, posted after SME review
