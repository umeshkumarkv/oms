# Onboard Eval Loop — OpenSpec Brownfield Baseline

Onboard an existing service into OpenSpec by producing a full baseline
`openspec/specs/{service}/spec.md` — same depth as a hand-written service doc:
purpose, port, DB schema, Kafka produced/consumed, Redis keys with TTLs, REST
endpoints exposed AND called, entities with field-level detail, "must never"
boundaries, and business rules — every claim cited to actual code. Also
maintains ONE thin `openspec/config.yaml` (genuinely global facts only) and
ONE `openspec/specs/_global/service-directory.md` (port table, Kafka topic
contract, schema list — the cross-cutting reference every service points to
instead of restating).

Runs: Discovery → Spec Draft → Critic → Evaluator → (module:all only) Global
Consistency Matrices. Max 3 attempts on the per-service loop.

## Usage
```
/onboard-eval-loop service:{name}      ← one service, full baseline spec.md
/onboard-eval-loop service:all         ← every service, then cross-service
                                          consistency matrices
/onboard-eval-loop frontend            ← frontend baseline (different scan set)
```

**Output:**
- `openspec/specs/{service}/spec.md` — one per service, full baseline
- `openspec/specs/_global/service-directory.md` — once, shared reference
- `openspec/config.yaml` — once, thin, points to service-directory.md

This is baseline documentation of CURRENT behavior. It does not touch source
code and does not create new feature specs — that's a separate step once the
baseline exists.

---

## Setup — Required on Every Fresh Clone

```json
{
  "permissions": {
    "allow": [
      "Bash(find:*)", "Bash(grep:*)", "Bash(xargs:*)", "Bash(sed:*)",
      "Bash(sort:*)", "Bash(wc:*)", "Bash(cat:*)", "Bash(echo:*)",
      "Bash(mvn:*)", "Bash(node:*)", "Bash(npx:*)"
    ]
  }
}
```
Write Guard prompts are enforced by this skill itself — these permissions do
NOT bypass them.

---

## How It Works

```
Phase 0: DISCOVERY (automated scan, per service — no LLM)
  ↓
Phase 1: SPEC DRAFT     → compose spec.md sections from Phase 0 facts only
  ↓
Phase 2: CRITIC         → adversarial check, especially "must never" claims
  ↓
Phase 3: EVALUATOR      → per-service consistency checklist
  ↓ ALL PASS                    ↓ ANY GAP
  ↓                        Draft revises → Critic re-checks changed items
  ↓                              ↓ ALL PASS      ↓ ANY GAP (attempt 3)
  ↓                            loop             ESCALATE → human
  ↓
[service:all only] Phase 4: GLOBAL CONSISTENCY MATRICES
  (Kafka producer/consumer, OpenAPI vs code, frontend route coverage)
  ↓
Phase 5: WRITE GUARD → one approval per spec.md, then service-directory.md,
                        then config.yaml
```

---

## Agent Scope — Absolute Boundaries. No Exceptions.

This agent **OWNS**: producing per-service baseline `spec.md`, the global
`service-directory.md`, and a thin `config.yaml` — all describing CURRENT
behavior, evidenced by actual code.

This agent **NEVER**:
- Modifies source code
- Invents an entity field, business rule, Kafka topic, or "must never"
  boundary not evidenced in the scanned code
- States a negative claim ("never accesses X schema") as VERIFIED fact from
  absence alone — see "Must Never" handling below
- Writes a global consistency matrix cell as MATCH without having actually
  compared both sides
- Puts service-specific facts into `config.yaml` — see the earlier
  global/local split; this skill enforces that split automatically

---

## Phase 0 — Discovery (Per Service, Automated Scan)

**Step 0 — Stack + boundaries**
```bash
SERVICE={name}
[ -f "services/$SERVICE/pom.xml" ] && echo "STACK=java"
[ -f "$SERVICE/package.json" ]     && echo "STACK=react"
# unknown → stop, ask user
```

**Step 1 — Port + DB schema**
```bash
grep -r "server.port\|SERVER_PORT" services/$SERVICE/src/main/resources/ 2>/dev/null
grep -rh "spring.flyway.schemas\|DEFAULT_SCHEMA" services/$SERVICE/src/main/resources/ 2>/dev/null
find services/$SERVICE -name "V*.sql" | sort   # migrations, in order
```

**Step 2 — REST endpoints exposed**
```bash
find services/$SERVICE/src -name "*Controller*.java" \
  -exec grep -H "@GetMapping\|@PostMapping\|@PutMapping\|@PatchMapping\|@DeleteMapping" {} \;
grep -rh "@PreAuthorize" services/$SERVICE/src --include="*.java"
```

**Step 3 — REST endpoints called (downstream)**
```bash
grep -rl "RestTemplate\|WebClient" services/$SERVICE/src/main --include="*.java"
grep -rB2 "getForObject\|exchange(\|\.get()\.uri" services/$SERVICE/src/main --include="*.java"
grep -rl "@CircuitBreaker" services/$SERVICE/src --include="*.java"
```

**Step 4 — Kafka produced / consumed**
```bash
grep -rB1 "kafkaTemplate.send\|KafkaTemplate" services/$SERVICE/src --include="*.java" -h
grep -rA3 "@KafkaListener" services/$SERVICE/src --include="*.java"
grep -rh "groupId" services/$SERVICE/src --include="*.java"
```

**Step 5 — Redis keys**
```bash
grep -rB2 "@Cacheable\|@CachePut\|@CacheEvict\|redisTemplate" \
  services/$SERVICE/src/main --include="*.java"
grep -rh "TimeUnit\|Duration.of" services/$SERVICE/src/main --include="*.java" | grep -i redis
```

**Step 6 — Entities**
```bash
find services/$SERVICE/src -name "*.java" -path "*/domain/*" -exec grep -l "@Entity" {} \;
# For each match: cat the file, extract every @Column-annotated field, its
# Java type, and any validation annotation (@NotNull, precision, scale, enum).
# Every field in the final spec MUST trace to one of these cat outputs.
```

**Step 7 — "Must Never" candidate list (inference, not fact)**
```bash
# What schemas does this service NOT touch? (candidate boundary)
find . -maxdepth 3 -name "V*.sql" | xargs grep -l "CREATE SCHEMA" | grep -v $SERVICE

# What service client beans exist? (what it DOES call — everything else is a candidate "never calls")
grep -rl "@FeignClient\|RestTemplate\|WebClient" services/$SERVICE/src --include="*.java"
```
Every item here is a **candidate**, not a fact. See Phase 2.

**Step 8 — Business rules**
```bash
# Enums (state machines, order types, thresholds)
find services/$SERVICE/src -name "*.java" -exec grep -l "enum " {} \;
# Numeric thresholds / constants
grep -rn "BigDecimal(\"" services/$SERVICE/src --include="*.java"
grep -rn "public static final" services/$SERVICE/src --include="*.java"
```

---

## Phase 1 — Spec Draft

Compose `spec.md` with these sections, each populated ONLY from Phase 0
output, each claim carrying its file:line:

```
## Purpose        (from class-level Javadoc / README if present — else ask)
## Port           (Step 1)
## Database Schema (Step 1)
## Kafka Produced  (Step 4 — topic, trigger, payload fields)
## Kafka Consumed   (Step 4 — topic, consumer group, action)
## Redis Keys Owned (Step 5 — pattern, TTL, purpose)
## REST Endpoints Exposed (Step 2 — method, path, auth/role)
## REST Endpoints Called  (Step 3 — service, endpoint, circuit breaker Y/N)
## Entities              (Step 6 — every field, type, constraint)
## What This Service Must NEVER Do   (Step 7 — see Phase 2 tagging)
## Key Business Rules     (Step 8 — cited constants/enums/thresholds)
```

## Phase 2 — Critic

Adversarial pass, with special handling for the two highest-risk sections:

**Entities:** re-open the actual entity file. Confirm every field, type, and
precision/scale matches exactly. Flag anything drafted from memory of "typical
Java entities" instead of the actual file.

**Must Never:** this is the hardest section — it's a claim about absence.
For each candidate from Step 7:
- If there's an actual code-level enforcement (e.g. no client bean exists for
  that service, no schema migration for it, a `@PreAuthorize` blocks it) →
  tag **ENFORCED**, cite the absence-evidence explicitly (e.g. "no
  `RiskServiceClient` bean found in the codebase").
- If it's just "nothing found, but nothing stops it either" → tag
  **CONVENTION — INFERRED, CONFIRM WITH TEAM**. Never silently promote this
  to a flat "Never does X" statement the way a VERIFIED fact would be stated.

**Kafka topic names:** confirm the exact string literal matches between this
service's producer/consumer code and any other service you've already
drafted a spec for. A one-character topic name mismatch is a real bug to
surface, not something to paper over by assuming they're the same topic.

## Phase 3 — Evaluator

```
[ ] Every entity field cites the actual file, no field invented
[ ] Every "Must Never" line is tagged ENFORCED or CONVENTION-INFERRED —
    none stated as flat fact without one of these tags
[ ] Every Kafka topic string matches exactly what was found in code (not
    "close enough")
[ ] Every REST endpoint in the table matches an actual annotation found in
    Step 2/3 — none pulled from an OpenAPI doc without confirming the code
[ ] Every business rule/threshold cites a specific constant, enum, or line
[ ] No content in this spec.md duplicates something that belongs in
    service-directory.md (port table, topic contract) — reference it instead
```

ALL PASS → proceed (or, in `service:all`, move to next service, then Phase 4).
ANY GAP → revise, retry, max 3 attempts, then escalate.

---

## Phase 4 — Global Consistency Matrices (`service:all` only)

Run once, after every service's draft has passed its own loop.

**Kafka Consistency Matrix**
```bash
grep -r "kafkaTemplate.send" services/*/src/main --include="*.java" -h \
  | grep -oE '"[a-z.A-Z]+"' | sort -u        # all topics produced
grep -r "@KafkaListener" services/*/src/main --include="*.java" -A3 \
  | grep "topics" | grep -oE '"[a-z.A-Z]+"' | sort -u   # all topics consumed
```
Produce:
```
Topic                     | Producer         | Consumers               | Verdict
order.state.changed       | order-service     | audit, client, notif    | MATCH / GAP
Orphan check: produced-no-consumer / consumed-no-producer : {list or None}
```

**OpenAPI-vs-Code Matrix** (if `openapi.yaml` exists per service)
```bash
find services -name "openapi.yaml" | while read f; do
  echo "=== $f ===" && grep -E "^  /" "$f" | sort
done
grep -r "@GetMapping\|@PostMapping\|@PutMapping\|@PatchMapping\|@DeleteMapping" \
  services/*/src/main --include="*.java" -h | sort -u
```

**Frontend Route Coverage**
```bash
grep -r "api/v1" frontend/src --include="*.tsx" --include="*.ts" -h \
  | grep -oE "/api/v1/[a-zA-Z0-9/_-]+" | sort -u
```
Cross-reference each path against every service's drafted spec.md endpoint
table. Undocumented and dead routes both get flagged.

Write all three matrices' findings into `openspec/specs/_global/service-directory.md`.

---

## Phase 5 — Write Guard

One approval per file, not batched:
```
ONBOARD ACTION — AWAITING YOUR APPROVAL
  Target : {openspec/specs/{service}/spec.md | .../service-directory.md | config.yaml}
  Action : {create new | overwrite existing}
  Change : {summary}
  Evidence: {file:line per claim, or "see attached matrix" for global file}

Proceed? (yes / no)
```

---

## Escalation — after 3 attempts

```
ONBOARD EVAL LOOP — ESCALATION REQUIRED
Service: {name}
Persistent gaps: {list}
Unresolved Must-Never tags (still ambiguous ENFORCED vs CONVENTION): {list}
Recommended: resolve manually, or confirm the CONVENTION tag stands as-is,
then re-run.
```

---

## Hard Stops

- `STACK=unknown` — ask, don't guess
- A Kafka topic string differs by even one character between producer and
  consumer scans — flag as a possible real bug, do not assume intent
- A "Must Never" claim can't be resolved to ENFORCED or CONVENTION after
  Critic — ask the user directly rather than guessing which it is
- Entity field found in code with no obvious business meaning (e.g. unclear
  precision on a money field) — flag, don't guess the intent
- Any secret/credential surfaced during discovery — stop, flag, don't write
  it into any spec file
