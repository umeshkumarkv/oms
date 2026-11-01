# Plumbline templates

Everything Plumbline produces is written on one of these, so the same work looks the same in every project and to
every agent: whatever is posted to JIRA, Confluence or a PR, and the CLAUDE.md files themselves. Rules for writers:
keep every `## ` heading, in this order, with the exact text; replace every `{{…}}`; write `None` under a heading
with nothing to say; add `###` sub-headings or tables freely inside a section; plain `-` bullets only (JIRA shows
`- [ ]` as literal text); fields are bullets (`- Label: value`), because Confluence and JIRA merge plain lines into
one paragraph. Do not copy the `<!-- plumbline:… -->` lines at the top. `check-template.sh` checks the shape (and the
role lane) before any judge or post; the judge checks the content.

## Versions
Each template's first line is `<!-- plumbline:template <name> v<N> -->`, and every output names the version it was
written on — a hand-off in its footer (`… · /pl-ba · story v1 · commit …`), a CLAUDE.md file in its `Last updated`
line. `VERSIONS` locks each template's version to its content: the self-test fails if a template changes without its
version going up. To change one: edit it, bump `vN`, add an entry to `CHANGELOG.md` (what changed, how to read the
older version), run `bash tests/lock-templates.sh`. Commands read older hand-offs as written and never fail them for
an older version; only new output must use the current one.

| Template | Becomes | Written by |
|---|---|---|
| `prd.md` | Confluence page (or the epic description without Confluence) | `/pl-pm` writer |
| `epic.md` | JIRA epic description: summary + link to the PRD page | `/pl-pm` |
| `story.md` | JIRA story description | `/pl-ba` writer |
| `hld.md` | Confluence page under the PRD: the HLD | `/pl-arch <EPIC>` writer |
| `handoff-hld.md` | `[Plumbline /pl-arch]` comment on the epic: HLD link, summary, docs PR | `/pl-arch <EPIC>` |
| `handoff-arch.md` | `[Plumbline /pl-arch]` comment on the story: the LLD | `/pl-arch <STORY>` writer |
| `handoff-dev.md` … `handoff-devops.md` | `[Plumbline /pl-<role>]` comment on the story | each command, at the hand-over |
| `sprint-report.md` | sprint report comment or page | `/pl-sm` writer |
| `sm-flag.md` | `[Plumbline /pl-sm]` comment on each flagged ticket | `/pl-sm` |
| `pr.md` | every PR description: story, docs (`/pl-doc`, `/pl-arch <EPIC>`, `/pl-onboard`), CI fix | `/pl-dev`, `/pl-doc`, `/pl-devops`, `/pl-arch`, `/pl-onboard` |
| `claude-root.md`, `claude-unit.md`, `claude-reference.md` | the root CLAUDE.md, each unit's CLAUDE.md and CLAUDE.reference.md | `/pl-onboard`, `/pl-doc`, `/pl-arch <EPIC>` |

Every hand-off template ends with a one-line footer: `— Plumbline <version> · /pl-<command> · <template> v<N> ·
commit <sha> · judge <result> · AI-drafted, posted after SME review`. Who posted it — the SME whose yes approved it — is not typed in: JIRA records the author.
A template's first line may be `<!-- plumbline:forbid <regex> -->`: what other roles own (APIs and points in a story,
code in a design). The check fails any draft line that matches; writers do not copy that line.

| Role | Owns | Never |
|---|---|---|
| PM | problem, behaviour, success criteria, scope, priority, size S/M/L | APIs, units, data, points, how to build |
| BA | user story, Given/When/Then criteria, examples, business dependencies | APIs, status codes, units, data, points, branches |
| Architect (final authority) | HLD per epic, LLD per story: units, AC → technical check, design, blockers, story points | changing criteria or scope; production code |
| Dev, review, QA, doc, devops | follow the HLD and LLD exactly | changing them — a gap stops with `design gap — /pl-arch` |
| Repository settings | branch pattern, PR target | — |
