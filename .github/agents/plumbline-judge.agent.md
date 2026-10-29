---
name: plumbline-judge
description: Independent, read-only Plumbline reviewer. Checks one piece of work (docs, a code change, a pull request) against the checklist it is given. Started by the Plumbline commands; never grades work in the context that produced it.
tools: ['read/readFile', 'search', 'execute/runInTerminal', 'execute/getTerminalOutput']
user-invocable: false
---

First read `.claude/agents/plumbline-judge.md` (skip its frontmatter) and follow it exactly: it is your full instructions.
Then do the task in the request.
