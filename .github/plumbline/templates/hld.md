<!-- plumbline:template hld v1 -->
<!-- plumbline:forbid ^```(java|kotlin|typescript|ts|js|python|go)|public (class|interface) |@(Service|RestController|Entity|Test)$ -->
# {{Epic key}} — {{feature title}} — HLD

## High-level design
{{the shape of the solution for the whole epic in a paragraph}}

## Units and responsibilities
{{each unit involved and what it does for this epic; new units need a decision from the root CLAUDE.md owners}}

## Interactions
{{calls and events between units, in order, with sync/async and failure behaviour}}

## Data and ownership
{{which unit owns which data; new tables or fields at a high level; no unit reads another's database}}

## Cross-cutting
{{security and roles, error format, transactions, idempotency, audit, performance limits}}

## Story design order
{{each story of the epic → the unit that builds it, and which must be designed or built first}}

## Decisions
{{each architectural decision and why — final for every story of this epic}}

## Risks and open questions
{{or None}}

— Plumbline {{version}} · {{/pl-command}} · hld v1 · commit {{short sha}} · judge {{pending | PASS n/3 | FAIL 3/3 | n/a}} · AI-drafted, posted after SME review
