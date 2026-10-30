<!-- plumbline:template claude-unit v1 -->
# {{unit name}}

<!-- plumbline:begin unit -->
Last updated: {{commit}} ({{date}}, {{/pl-command}}, claude-unit v1)

## Purpose
{{what this unit does, in 3–5 lines}}

## Build & run
{{exact commands, port, profiles; then the Test recipe line: command · runtime · result · date; hard-coded paths}}

## Architecture & structure
{{stack and versions, layers and patterns, main packages and what lives where}}

## Security
{{authentication, roles, where tokens are stored — with the files}}

## Rules
{{one line each, tagged [code: <file>] or [decided <source>]}}

## Must never
{{things a change must not do here, grounded in code or the root CLAUDE.md}}

## Decisions & trade-offs
{{why the code is the way it is, each with its source}}

## Reference
{{one line per section of CLAUDE.reference.md with its size; "Open CLAUDE.reference.md only for the section your task needs."}}
<!-- plumbline:end unit -->
