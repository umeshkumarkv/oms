# Onboard checklist — one unit (`/pl-onboard`, read by the judge)

1. Every endpoint/route the code defines is in the reference API section — search the mapping annotations or route
   definitions, compare the count, check at least 10 at random. None listed that the code does not define.
2. Frontend: every backend call with the endpoint it hits; calls no endpoint serves are listed as such. (N/A backend.)
3. Every entity/table is in the reference Data section with its key fields; none the code, migrations or SQL scripts
   do not define. Column names are fields, not tables.
4. Integrations: every outbound HTTP call, messaging topic and cache the code uses is listed; nothing invented.
5. Build & run, port and config match the build and config files. The **Test recipe** gives the command, runtime and
   the result exactly as given (a failure is never reworded as success); a wrong runtime names the one required.
6. **Security fields are true:** *Identity* says how the user reaches this unit; *Verified at* names the file that
   checks it (or "not verified here"); *Reachable without that check* covers every environment the repository
   deploys to (docker-compose published ports, k8s Service type, ingress) with files. Trusting headers while
   directly reachable anywhere is stated as reachable — "the gateway is the trust boundary" alone fails this item.
7. Every Rule is tagged `[code: <file or class>]` (and it enforces the rule), `[decided <source>]` or
   `[decided docs: <file §section>]` (and that source says so); every Must-never is traceable to code or docs.
8. Tech debt states the TODO/FIXME count and top files; other debt as counts plus up to 10 locations.
9. Core: the template's sections, at most **6 KB**, the Reference counts match the reference file. Reference: at most
   30 KB. Both start with `Last updated: <commit> (<date>, /pl-onboard, <template> v<N>)` with the given commit.
10. Text outside the plumbline markers is unchanged, in both files.
11. Decisions & trade-offs: each names its source (code comment, README, ADR, commit, config, docs) — none invented.
12. **Existing docs:** every technical claim in this unit's text outside the markers that the code contradicts is in
    the writer's reply as "Docs vs code: <claim> — <what the code does> — <files>" (for the root's Decisions needed);
    conventions the code does not contradict are kept as `[decided docs: …]` rules or left where they are.
