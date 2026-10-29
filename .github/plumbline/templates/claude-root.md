<!-- plumbline:template claude-root v1 -->
# {{solution name}}

<!-- plumbline:begin solution -->
Last updated: {{commit}} ({{date}}, {{/pl-command}}, claude-root v1)

## Solution
{{what the system is, in a paragraph}}

## Units
{{table: unit, folder, kind, port, owns, its CLAUDE.md}}

## How they connect
{{which unit calls which: HTTP, messaging, shared database}}

## Architecture
{{overall shape, shared stack, cross-cutting patterns}}

## Decisions & trade-offs
{{system-wide choices and why, each with its source}}

## Plumbline settings
{{JIRA project, Confluence space, Confluence parent page, Base branch, PR target, Branch pattern, API URL, Frontend URL}}

## Decisions needed
{{where docs, settings and code disagree — both sides with files — or None}}

## Rules for every change
{{cross-cutting rules: security, data ownership, testing}}
<!-- plumbline:end solution -->
