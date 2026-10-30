<!-- plumbline:template claude-root v3 -->
# {{solution name}}

<!-- plumbline:begin solution -->
Last updated: {{commit}} ({{date}}, {{/pl-command}}, claude-root v3)

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

## Conventions
{{how code is written here — money, errors, logging, naming, tests — each from existing docs or a consistent code pattern, with its source; or None found}}

## Plumbline settings
{{JIRA project, Confluence space, Confluence parent page, Base branch, PR target, Branch pattern, Workflow (your status names for To Do → In Progress → In Review → Done), API URL, Frontend URL}}

## Decisions needed
- Secrets found: {{file:key for each secret the scan found — never a value — or none}}
{{security risks (reachable without the check), docs vs code, settings disagreements — both sides with files — or None}}

## Rules for every change
{{cross-cutting rules: security, data ownership, testing}}
<!-- plumbline:end solution -->
