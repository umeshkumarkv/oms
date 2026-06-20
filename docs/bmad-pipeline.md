# BMAD Pipeline Diagrams
# Render at: https://mermaid.live  (paste each code block)

---

## Diagram 1 — High-Level Pipeline Overview

```mermaid
flowchart TD
    START(["📋 Business Requirement"])

    PM["① PM Agent\n/pm-agent 'requirement'\nWrites PRD · Creates Epic\n🏷 bmad-prd-approved"]
    HG1["👤 Human Gate\nReview Epic + PRD"]
    BA["② BA Agent\n/ba-agent epic:{key}\nStories · ACs · DoR checklist\n🏷 bmad-dor-ready"]
    HG2["👤 Human Gate\nReview Stories"]
    ARCH["③ Arch Eval Loop  ⟳ max 2 attempts\n/arch-eval-loop SCRUM-{n}\nTechnical design · 11 DoR items\n🏷 bmad-arch-approved"]
    HG3["👤 Human Gate\nReview Design Doc"]
    DEV["④ Dev Eval Loop  ⟳ max 3 attempts\n/dev-eval-loop SCRUM-{n}\n8 layers · 12-cat review · mvn test · JaCoCo ≥ 80%\nOn pass: git push + PR raised"]
    CR["⑤ Code Review Agent\n/code-review SCRUM-{n}\n12-category CLAUDE.md audit\n🏷 bmad-code-review-approved"]
    DO["⑥ DevOps Agent\n/devops-agent pr:{n}\nMonitor CI · auto-fix failures"]
    QA["⑦ QA Eval Loop  ⟳ max 3 attempts\n/qa-eval-loop SCRUM-{n}\ncurl every AC + 4 edge cases\nJIRA: In Review"]
    HG4["👤 Human Gate\nReview reports · Approve + Merge PR\n'merged code' → JIRA: Done"]
    SM["⑧ SM Agent  (daily)\n/sm-agent\nSprint health · velocity · DoD gate"]
    DOC["⑨ Doc Agent  (post-merge)\n/doc-agent SCRUM-{n}\nUpdates openapi.yaml + CLAUDE.md\nRaises docs PR"]
    DONE(["✅ Story Done"])

    START --> PM --> HG1 --> BA --> HG2 --> ARCH --> HG3 --> DEV --> CR
    CR -- "REQUEST CHANGES" --> DEV
    CR -- "APPROVE" --> DO --> QA --> HG4
    HG4 --> SM
    HG4 --> DOC --> DONE

    classDef agent   fill:#1d3557,stroke:#a8dadc,color:#fff
    classDef loop    fill:#0077b6,stroke:#90e0ef,color:#fff
    classDef gate    fill:#e9c46a,stroke:#f4a261,color:#000
    classDef terminal fill:#2d6a4f,stroke:#95d5b2,color:#fff
    classDef review  fill:#386641,stroke:#6a994e,color:#fff

    class PM,BA,SM,DOC agent
    class ARCH,DEV,QA loop
    class HG1,HG2,HG3,HG4 gate
    class START,DONE terminal
    class CR,DO review
```

---

## Diagram 2 — Eval Loop Retry Logic (all three loops)

```mermaid
flowchart LR
    subgraph ARCH["Arch Eval Loop — /arch-eval-loop — max 2 attempts"]
        A1["Write technical design\nendpoint · DB · Kafka · Redis · tests"]
        A2{"Validate\n11 DoR items"}
        A3(["bmad-arch-approved\n→ pipeline continues"])
        A4(["bmad-arch-blocked\nEscalate to BA Agent"])

        A1 --> A2
        A2 -->|"✅ pass"| A3
        A2 -->|"❌ attempt 1 — feed gaps"| A1
        A2 -->|"❌ attempt 2"| A4
    end

    subgraph DEV["Dev Eval Loop — /dev-eval-loop — max 3 attempts"]
        D1["Implement 8 layers\ndomain · migration · DTO · mapper\nrepo · service · controller · tests"]
        D2["12-cat code review\nmvn test\nJaCoCo ≥ 80%"]
        D3{"Eval pass?"}
        D4(["git push · gh pr create\nJIRA: In Review"])
        D5(["Escalate to Human\nviolation list on JIRA"])

        D1 --> D2 --> D3
        D3 -->|"✅ pass"| D4
        D3 -->|"❌ attempt < 3 — feed file:line violations"| D1
        D3 -->|"❌ attempt 3"| D5
    end

    subgraph QA["QA Eval Loop — /qa-eval-loop — max 3 attempts"]
        Q1["curl test every AC\n+ 4 mandatory edge cases\n(404 · 401 · BigDecimal · ISO 8601)"]
        Q2{"All ACs pass?"}
        Q3(["QA PASSED on JIRA\nJIRA: In Review"])
        Q4(["Escalate to Human\ncurl evidence on JIRA"])

        Q1 --> Q2
        Q2 -->|"✅ pass"| Q3
        Q2 -->|"❌ attempt < 3 — feed curl evidence"| Q1
        Q2 -->|"❌ attempt 3"| Q4
    end
```

---

## Diagram 3 — Human Governance Model

```mermaid
flowchart LR
    subgraph AGENTS["AI Agents Own"]
        A1["PM Agent — PRD + Epic"]
        A2["BA Agent — Stories + DoR"]
        A3["Arch Eval Loop — Technical design"]
        A4["Dev Eval Loop — Code + tests"]
        A5["Code Review Agent — Standards audit"]
        A6["DevOps Agent — CI pipeline"]
        A7["QA Eval Loop — AC verification"]
        A8["SM Agent — Sprint health"]
        A9["Doc Agent — Docs sync"]
    end

    subgraph HUMANS["Humans Own"]
        H1["Review Epic + PRD"]
        H2["Review Stories"]
        H3["Review Design Doc"]
        H4["Approve + Merge PR"]
        H5["Architecture decisions"]
        H6["JIRA → Done transition"]
        H7["Production deployment"]
    end

    A1 --> H1 --> A2 --> H2 --> A3 --> H3 --> A4
    A4 --> A5 --> A6 --> A7 --> H4
    H4 --> A8
    H4 --> A9
```
