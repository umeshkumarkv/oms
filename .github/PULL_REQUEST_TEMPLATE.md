## JIRA Story
Link: BD-[story number]
Branch: [branch name]

## What Claude Code did
[Brief description of what was generated]

## Type of change
- [ ] Bootstrap / Infrastructure
- [ ] New feature
- [ ] Bug fix
- [ ] CLAUDE.md update

## Checklist — must all pass before merge
- [ ] CLAUDE.md conventions followed throughout
- [ ] Business logic matches JIRA acceptance criteria
- [ ] Order state machine transitions correct
- [ ] BigDecimal used for all monetary values
- [ ] No cross-service database access
- [ ] Unit tests written and passing
- [ ] JaCoCo coverage >= 80%
- [ ] No hardcoded credentials or secrets
- [ ] API response format matches openapi.yaml
- [ ] Kafka topic names match CLAUDE.md
- [ ] Redis cache keys match CLAUDE.md
- [ ] PR author reviewed own diff before requesting review

## Compliance checks (tick if applicable)
- [ ] Risk rules correctly implemented as pluggable components
- [ ] Market hours using America/New_York timezone
- [ ] Fat finger checks present for order submission
- [ ] Audit event published on every state transition

## Notes for reviewer
[Anything the reviewer should pay attention to]