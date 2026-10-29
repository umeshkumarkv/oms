<!-- plumbline:template handoff-devops v1 -->
[Plumbline /pl-devops] {{YYYY-MM-DD}}

## CI failure
- CI run: {{link or id}}
- Category: {{code | test | coverage | pipeline | config | flaky}}

## Cause
{{what failed and why, with file:line}}

## Fix
{{the change on the branch, or the proposed fix for a person to make}}

## Results
- Local: {{command and result}}
- CI: {{result after the fix, or "not re-run"}}

— Plumbline {{version}} · {{/pl-command}} · handoff-devops v1 · commit {{short sha}} · judge {{pending | PASS n/3 | FAIL 3/3 | n/a}} · AI-drafted, posted after SME review
