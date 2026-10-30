<!-- plumbline:template handoff-dev v1 -->
[Plumbline /pl-dev] {{YYYY-MM-DD}}

## Change
- Branch: {{branch}}
- PR: {{link, or "not opened"}}
- Tests: {{ran — N passed, M failed (pre-existing: …) | UNVERIFIED — not run: <why>}}

## Acceptance criteria → tests
{{AC1 → TestClass.testName; one line per AC}}

## Design tests → tests
{{design test → test name; one line each}}

## Design gaps
{{None — a gap means the work stopped and goes back to /pl-arch}}

## Notes for /pl-doc
{{"Add to CLAUDE.md: …" and "CLAUDE.md out of date: …" lines from the helpers, or None}}

— Plumbline {{version}} · {{/pl-command}} · handoff-dev v1 · commit {{short sha}} · judge {{pending | PASS n/3 | FAIL 3/3 | n/a}} · AI-drafted, posted after SME review
