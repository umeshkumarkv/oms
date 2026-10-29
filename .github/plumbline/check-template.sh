#!/usr/bin/env bash
# Plumbline template check — deterministic, no model. Is a draft written on its template, and in its role's lane?
#   bash .github/plumbline/check-template.sh <template> <draft>      exit 0 = PASS
# Checks: (1) every "## " heading of the template is in the draft, exactly and in the same order;
# (2) every "Label: {{…}}" field of the template appears in the draft as "Label: <value>" ("- " / "* " bullets ok);
# (3) a hand-off comment's tag line is "[Plumbline /pl-<role>] YYYY-MM-DD";
# (4) no "{{" placeholder is left; (5) no heading is left empty (write "None");
# (6) the footer "— Plumbline <version> · /pl-<command> · commit <sha> · judge <result> · …" is present;
# (7) role lane: no draft line matches the template's "<!-- plumbline:forbid <regex> -->" (what other roles own);
# (8) template version: the draft names "<template> v<N>" from the template's "<!-- plumbline:template <name> vN -->"
#     line (in the footer of a hand-off, in the "Last updated" line of a CLAUDE.md file).
# Text read back from JIRA/Confluence is accepted as is: backslash escapes are ignored.
set -uo pipefail
t=${1:?usage: check-template.sh <template> <draft>}; d=${2:?usage: check-template.sh <template> <draft>}
[ -f "$t" ] || { echo "FAIL: no template $t"; exit 2; }
[ -f "$d" ] || { echo "FAIL: no draft $d"; exit 2; }
awk -v T="$t" -v D="$d" '
function trim(s) { sub(/[ \t\r]+$/, "", s); return s }
NR == FNR {
  line = trim($0)
  if (line ~ /^<!-- plumbline:template /) { tv = line; sub(/^<!-- plumbline:template /, "", tv); sub(/ -->$/, "", tv); next }
  if (line ~ /^<!-- plumbline:forbid /) { forbid = line; sub(/^<!-- plumbline:forbid /, "", forbid); sub(/ -->$/, "", forbid); next }
  if (!tstarted && line != "") { tstarted = 1; if (line ~ /^\[Plumbline /) handoff = 1 }
  if (line ~ /^## /) heads[++nh] = line
  if (line ~ /^— Plumbline /) footer = 1
  f = line; sub(/^[-*] /, "", f)
  if (f ~ /^[A-Z][A-Za-z ]*: \{\{/) { split(f, a, ": "); fields[++nf] = a[1] }
  next
}
{
  line = trim($0); gsub(/\\/, "", line)   # JIRA and Confluence escape [ ] ~ _ on read-back
  if (line ~ /^<!--/) next
  if (!started && line != "") { started = 1; first = line }
  if (line ~ /^\[Plumbline / && tag == "") tag = line
  if (line ~ /^## /) { dh[++nd] = line; if (prevhead != "" && !body) empty[prevhead] = 1; prevhead = line; body = 0 }
  else if (line != "") body = 1
  if (index(line, "{{")) bad = bad sprintf("FAIL: placeholder left on line %d: %s\n", FNR, substr(line, 1, 80))
  if (tv != "" && index(line, tv)) havetv = 1
  if (line ~ /^— Plumbline [0-9]+\.[0-9]+\.[0-9]+ · \/pl-[a-z-]+ · ([a-z-]+ v[0-9]+ · )?commit [0-9a-f]+ · judge [^·]+ · /) havefooter = 1
  else if (forbid != "" && line ~ forbid)
    bad = bad sprintf("FAIL: outside this role (line %d): %s\n", FNR, substr(line, 1, 80))
  f = line; sub(/^[-*] /, "", f)
  for (i = 1; i <= nf; i++) if (index(f, fields[i] ": ") == 1 && length(f) > length(fields[i]) + 2) have[fields[i]] = 1
}
END {
  if (prevhead != "" && !body) empty[prevhead] = 1
  if (handoff && tag !~ /^\[Plumbline (approve )?\/pl-[a-z-]+\] [0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]/)
    bad = bad "FAIL: tag line must be \"[Plumbline /pl-<role>] YYYY-MM-DD\", got: " substr(tag != "" ? tag : first, 1, 80) "\n"
  j = 1
  for (i = 1; i <= nh; i++) {
    k = j
    while (k <= nd && dh[k] != heads[i]) k++
    if (k > nd) bad = bad "FAIL: heading missing or out of order: " heads[i] "\n"
    else { if (heads[i] in empty) bad = bad "FAIL: empty section (write None): " heads[i] "\n"; j = k + 1 }
  }
  for (i = 1; i <= nf; i++) if (!(fields[i] in have)) bad = bad "FAIL: field missing or empty: " fields[i] ":\n"
  if (footer && !havefooter) bad = bad "FAIL: footer missing: \"— Plumbline <version> · /pl-<command> · <template> v<N> · commit <sha> · judge <result> · AI-drafted, posted after SME review\"\n"
  if (tv != "" && !havetv) bad = bad "FAIL: template version missing: the draft must name \"" tv "\" (footer or Last updated line)\n"
  if (bad != "") { printf "%s", bad; exit 1 }
  print "PASS: " D " matches " T
}' "$t" "$d"
