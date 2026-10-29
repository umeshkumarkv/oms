<!-- plumbline:template prd v1 -->
<!-- plumbline:forbid (GET|POST|PUT|PATCH|DELETE) /|/api/|[a-z]\.(java|ts|py|kt)|[A-Z][a-z]+(Service|Controller|Repository|Exception|Dto|Entity)|HTTP [1-5][0-9][0-9]|status [1-5][0-9][0-9]|customfield_|[Ss]tory points|[0-9]+ points -->
# {{Epic key or "new epic"}} — {{feature title}} — PRD

## Problem
{{who has it (a specific business role), what they cannot do today, the business impact}}

## Today
{{what users can and cannot do now, in business terms — learned from CLAUDE.md and the code}}

## Solution
{{what the user will be able to do and see, step by step, in business language — no APIs, services, data model or how to build it}}

## Success criteria
{{SC1, SC2, … — each observable by a user or measurable in the business (counts, time, money)}}

## Out of scope
{{explicit list; cite the root CLAUDE.md out-of-scope list where it applies}}

## Dependencies
{{business dependencies — other features, teams, decisions, regulations — or None}}

## Backlog
{{P1, P2, P3 — each item with a size S / M / L; P1 has no L (split it)}}

## Open questions
{{for the business, or None}}

— Plumbline {{version}} · {{/pl-command}} · prd v1 · commit {{short sha}} · judge {{pending | PASS n/3 | FAIL 3/3 | n/a}} · AI-drafted, posted after SME review
