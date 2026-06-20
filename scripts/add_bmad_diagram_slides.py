"""
Rebuild slides 4, 5, 6 in BMAD.pptx.
Root-cause fix: ALL EMU coordinates cast to int() to avoid float-in-XML crash.
Run: python3 scripts/add_bmad_diagram_slides.py
"""
from lxml import etree
from pptx import Presentation
from pptx.util import Inches, Pt, Emu
from pptx.dml.color import RGBColor
from pptx.enum.text import PP_ALIGN

PPTX = "/Users/umeshkumarkotaladinnevaradaraj/code/broker-dealer/BMAD.pptx"
LOGO = "/tmp/bmad_logo.png"

# ── EMU helper — ALL positions must be int EMU ────────────────────────────────
def I(x): return int(x)          # coerce any float EMU to int
def In(x): return int(Inches(x)) # inches → int EMU

# ── Palette ────────────────────────────────────────────────────────────────────
C_AGENT  = RGBColor(0x1D, 0x35, 0x57)
C_LOOP   = RGBColor(0x00, 0x77, 0xB6)
C_GATE   = RGBColor(0xE9, 0xC4, 0x6A)
C_TERM   = RGBColor(0x2D, 0x6A, 0x4F)
C_REVIEW = RGBColor(0x38, 0x66, 0x41)
RED      = RGBColor(0xC7, 0x3E, 0x1D)
NAVY     = RGBColor(0x0A, 0x22, 0x46)
BG       = RGBColor(0xF4, 0xF7, 0xFB)
PALE     = RGBColor(0xDC, 0xE6, 0xF2)
WHITE    = RGBColor(0xFF, 0xFF, 0xFF)
GREY     = RGBColor(0x5A, 0x5A, 0x5A)
GREEN_CH = RGBColor(0x2E, 0x9E, 0x5B)

# ── Primitives (all positions passed as int EMU) ───────────────────────────────

def rect(slide, x, y, w, h, fill, line=None, line_pt=0.75, rounded=False):
    s = slide.shapes.add_shape(5 if rounded else 1,
                                I(x), I(y), I(w), I(h))
    s.fill.solid(); s.fill.fore_color.rgb = fill
    if line:
        s.line.color.rgb = line; s.line.width = Pt(line_pt)
    else:
        s.line.fill.background()
    return s


def tb(slide, text, x, y, w, h, size, bold, color,
       align=PP_ALIGN.CENTER, italic=False):
    s = slide.shapes.add_textbox(I(x), I(y), I(w), I(h))
    s.fill.background(); s.line.fill.background()
    tf = s.text_frame; tf.word_wrap = True
    p = tf.paragraphs[0]; p.alignment = align
    r = p.add_run(); r.text = text
    r.font.name = "Calibri"; r.font.size = Pt(size)
    r.font.bold = bold; r.font.italic = italic
    r.font.color.rgb = color
    return s


def hline(slide, x, y, w, color=NAVY, pt=0.8):
    """Thin horizontal divider using a zero-height rect."""
    s = slide.shapes.add_shape(1, I(x), I(y), I(w), In(0.02))
    s.fill.solid(); s.fill.fore_color.rgb = color
    s.line.fill.background()
    return s


def varrow(slide, cx, y1, y2, color=NAVY):
    """Draw a ▼ text arrow between two vertical y positions."""
    mid = I((y1 + y2) / 2)
    tb(slide, "▼", I(cx) - In(0.12), mid - In(0.12),
       In(0.24), In(0.24), 9, True, color)


def harrow(slide, x1, cy, x2, color=NAVY):
    """Draw a ▶ arrowhead at the destination end of a horizontal connector."""
    tb(slide, "▶", I(x2) - In(0.19), I(cy) - In(0.11),
       In(0.20), In(0.22), 9, True, color)


def frame(slide, title, num):
    rect(slide, In(0.50), In(0.42), In(0.13), In(0.50), NAVY)
    tb(slide, title, In(0.78), In(0.40), In(10.4), In(0.55),
       27.5, True, NAVY, PP_ALIGN.LEFT)
    slide.shapes.add_picture(LOGO, In(11.16), In(0.36), In(1.55), In(0.23))
    tb(slide, "Driving Intelligence Faster",
       In(0.50), In(7.05), In(4.0), In(0.30), 11, True, NAVY, PP_ALIGN.LEFT)
    tb(slide, "Confidential. Do not distribute without permission.",
       In(4.50), In(7.05), In(4.3), In(0.30), 11, False, GREY, PP_ALIGN.CENTER)
    tb(slide, num,
       In(12.4), In(7.05), In(0.6), In(0.30), 11, False, GREY, PP_ALIGN.RIGHT)


# ── SLIDE 4 — Pipeline Overview (Mermaid flowchart TD structure) ───────────────
# 3-row snake layout matching the Mermaid TD diagram exactly:
#   Row 1 (L→R): START → PM → HG1 → BA → HG2
#   Row 2 (L→R): ARCH → HG3 → DEV → CR   (wrap via right-margin L-bend)
#   Row 3 (L→R): DO → QA → HG4 → DOC → DONE  (wrap via right-margin L-bend, APPROVE)
#                              └─ SM (fork down from HG4)
# REQUEST CHANGES arc above Row 2: CR ──arc──▶ DEV

def build_slide4(slide):
    frame(slide, "BMAD Pipeline — High-Level Overview", "04")
    # ── Swimlane geometry ──────────────────────────────────────────────────────
    LBL_X     = In(0.25)
    LBL_W     = In(0.78)
    OUTER_TOP = In(1.02)
    OUTER_BOT = I(In(1.02) + In(5.78))
    _r1_bot   = I(In(1.08) + In(1.10))      # R1_Y + BH ≈ 2.18 in
    _r2_bot   = I(In(2.92) + In(1.10))      # R2_Y + BH ≈ 4.02 in
    L1_BOT    = I((_r1_bot + In(2.92)) // 2)  # midpoint(R1_bot, R2_Y) ≈ 2.55 in
    L2_BOT    = I((_r2_bot + In(4.46)) // 2)  # midpoint(R2_bot, R3_Y) ≈ 4.24 in
    CONT_X    = I(LBL_X + LBL_W)
    CONT_W    = I(In(12.85) - LBL_W)

    # All lanes: same neutral grey. Label column: solid navy.
    rect(slide, CONT_X, OUTER_TOP, CONT_W, I(L1_BOT - OUTER_TOP), BG)
    rect(slide, CONT_X, L1_BOT,    CONT_W, I(L2_BOT - L1_BOT),    BG)
    rect(slide, CONT_X, L2_BOT,    CONT_W, I(OUTER_BOT - L2_BOT), BG)
    rect(slide, LBL_X, OUTER_TOP, LBL_W, I(L1_BOT - OUTER_TOP), NAVY)
    rect(slide, LBL_X, L1_BOT,    LBL_W, I(L2_BOT - L1_BOT),    NAVY)
    rect(slide, LBL_X, L2_BOT,    LBL_W, I(OUTER_BOT - L2_BOT), NAVY)

    # Lane labels (centred vertically in each band)
    LBL_TH = In(0.50)
    tb(slide, "①\nPLAN",
       LBL_X, I(OUTER_TOP + (L1_BOT - OUTER_TOP - LBL_TH) // 2),
       LBL_W, LBL_TH, 7.5, True, WHITE)
    tb(slide, "②\nBUILD",
       LBL_X, I(L1_BOT + (L2_BOT - L1_BOT - LBL_TH) // 2),
       LBL_W, LBL_TH, 7.5, True, WHITE)
    tb(slide, "③\nSHIP",
       LBL_X, I(L2_BOT + (OUTER_BOT - L2_BOT - LBL_TH) // 2),
       LBL_W, LBL_TH, 7.5, True, WHITE)

    # Lane dividers
    hline(slide, LBL_X, L1_BOT, In(12.85), NAVY, 0.5)
    hline(slide, LBL_X, L2_BOT, In(12.85), NAVY, 0.5)

    SX  = In(1.08)
    TW  = In(11.70)
    BH  = In(1.10)      # taller boxes → 8/7pt fonts match slides 5-6
    CT  = In(0.025)
    RM  = In(12.82)

    # Row 1-2 gap = 0.74 in: holds L-bend (upper) + ARCH FAIL (middle) + REQUEST CHANGES (lower)
    # Row 2-3 gap = 0.44 in: holds L-bend (lower) + QA REJECT (upper)
    R1_Y = In(1.08)     # row 1 bottom = 2.18
    R2_Y = In(2.92)     # row 2 bottom = 4.02
    R3_Y = In(4.46)     # row 3 bottom = 5.56
    SM_Y = In(5.78)
    SM_H = In(0.60)

    GATE_TXT   = RGBColor(0x3D, 0x2B, 0x00)
    LOOP_STRIP = C_AGENT   # slightly lighter navy for eval loop retry strip

    def row_positions(widths):
        n = len(widths)
        gap = (TW - sum(widths)) // (n - 1) if n > 1 else 0
        xs, _x = [], SX
        for w in widths:
            xs.append(_x); _x += w + gap
        return xs

    r1_w = [In(1.10), In(1.80), In(1.20), In(1.80), In(1.20)]
    r1_x = row_positions(r1_w)
    r2_w = [In(2.10), In(1.20), In(2.20), In(2.10)]
    r2_x = row_positions(r2_w)
    r3_w = [In(1.80), In(1.80), In(1.20), In(1.80), In(1.20)]
    r3_x = row_positions(r3_w)

    # ── Node helpers (8pt title / 7pt body — same scale as slides 5-6) ────────
    def node(nx, ny, nw, nh, fill, lc, title, body, tc, bc):
        rect(slide, nx, ny, nw, nh, fill, line=lc, line_pt=0.6, rounded=True)
        tb(slide, title, nx+In(0.05), ny+In(0.04), nw-In(0.10), In(0.32),
           8, True, tc, PP_ALIGN.LEFT)
        if body:
            tb(slide, body, nx+In(0.05), ny+In(0.36), nw-In(0.10), nh-In(0.40),
               7, False, bc, PP_ALIGN.LEFT)

    def loop_node(nx, ny, nw, nh, title, body, retry_txt):
        rect(slide, nx, ny, nw, nh, NAVY,
             line=WHITE, line_pt=0.6, rounded=True)
        rect(slide, nx+In(0.04), ny+nh-In(0.26), nw-In(0.08), In(0.22), LOOP_STRIP)
        tb(slide, title, nx+In(0.05), ny+In(0.04), nw-In(0.10), In(0.32),
           8, True, WHITE, PP_ALIGN.LEFT)
        tb(slide, body, nx+In(0.05), ny+In(0.36), nw-In(0.10), nh-In(0.64),
           7, False, PALE, PP_ALIGN.LEFT)
        tb(slide, retry_txt, nx+In(0.08), ny+nh-In(0.25), nw-In(0.12), In(0.22),
           6, True, WHITE, PP_ALIGN.LEFT)

    # ── Connectors first ───────────────────────────────────────────────────────

    # Intra-row: lines only (arrowheads drawn last, after all boxes)
    for (xs, ws, ry) in [(r1_x, r1_w, R1_Y), (r2_x, r2_w, R2_Y), (r3_x, r3_w, R3_Y)]:
        cy = ry + BH // 2
        for i in range(len(xs) - 1):
            x1, x2 = xs[i] + ws[i], xs[i + 1]
            rect(slide, x1, cy - CT // 2, x2 - x1, CT, NAVY)

    # Row 1→2 L-bend  (UPPER zone: g12_y = 0.12 in below row 1 bottom)
    r1cy    = R1_Y + BH // 2
    g12_y   = I(R1_Y + BH) + In(0.12)
    r1r     = r1_x[4] + r1_w[4]
    arch_cx = r2_x[0] + r2_w[0] // 2
    rect(slide, r1r, r1cy - CT // 2, RM - r1r, CT, NAVY)
    rect(slide, RM - CT // 2, r1cy, CT, g12_y - r1cy, NAVY)
    rect(slide, arch_cx, g12_y - CT // 2, RM - arch_cx, CT, NAVY)
    rect(slide, arch_cx - CT // 2, g12_y, CT, R2_Y - g12_y, NAVY)

    # ARCH FAIL arc  (MIDDLE zone: 0.28 in below row 1 bottom)
    # ARCH top → up → BA bottom  (ba_cx is right of arch_cx, no crossing with L-bend horizontal)
    r2cy        = R2_Y + BH // 2
    ba_cx       = r1_x[3] + r1_w[3] // 2
    arch_fail_y = I(R1_Y + BH) + In(0.28)
    rect(slide, arch_cx - CT // 2, arch_fail_y, CT, R2_Y - arch_fail_y, RED)
    rect(slide, ba_cx   - CT // 2, I(R1_Y + BH), CT, I(arch_fail_y - R1_Y - BH), RED)
    rect(slide, arch_cx, arch_fail_y - CT // 2, ba_cx - arch_cx, CT, RED)
    tb(slide, "ARCH FAIL",
       arch_cx + In(0.06), arch_fail_y + In(0.03),
       ba_cx - arch_cx - In(0.10), In(0.18),
       6, True, RED, PP_ALIGN.CENTER)

    # REQUEST CHANGES arc  (LOWER zone: 0.27 in above row 2 top)
    arc_y  = R2_Y - In(0.27)
    dev_cx = r2_x[2] + r2_w[2] // 2
    cr_cx  = r2_x[3] + r2_w[3] // 2
    rect(slide, cr_cx  - CT // 2, arc_y, CT, R2_Y - arc_y, RED)
    rect(slide, dev_cx - CT // 2, arc_y, CT, R2_Y - arc_y, RED)
    rect(slide, dev_cx, arc_y - CT // 2, cr_cx - dev_cx, CT, RED)
    tb(slide, "↩ REQUEST CHANGES",
       dev_cx + In(0.06), arc_y + In(0.03),
       cr_cx - dev_cx - In(0.08), In(0.18),
       6, True, RED, PP_ALIGN.CENTER)

    # Row 2→3 L-bend  (LOWER zone: 0.14 in above row 3 top)
    g23_y = R3_Y - In(0.14)
    r2r   = r2_x[3] + r2_w[3]
    do_cx = r3_x[0] + r3_w[0] // 2
    rect(slide, r2r, r2cy - CT // 2, RM - r2r, CT, NAVY)
    rect(slide, RM - CT // 2, r2cy, CT, g23_y - r2cy, NAVY)
    rect(slide, do_cx, g23_y - CT // 2, RM - do_cx, CT, NAVY)
    rect(slide, do_cx - CT // 2, g23_y, CT, R3_Y - g23_y, NAVY)
    tb(slide, "✅ APPROVE",
       RM - In(0.90), I((r2cy + g23_y) // 2) - In(0.10),
       In(0.88), In(0.22), 7, True, C_TERM, PP_ALIGN.RIGHT)

    # QA REJECT arc  (UPPER zone of row 2-3 gap: midpoint of gap)
    qa_cx  = r3_x[1] + r3_w[1] // 2
    qa_arc = I((I(R2_Y + BH) + R3_Y) // 2)
    rect(slide, qa_cx  - CT // 2, qa_arc, CT, R3_Y - qa_arc, RED)
    rect(slide, dev_cx - CT // 2, I(R2_Y + BH), CT, I(qa_arc - R2_Y - BH), RED)
    rect(slide, qa_cx, qa_arc - CT // 2, dev_cx - qa_cx, CT, RED)
    tb(slide, "QA REJECT",
       qa_cx + In(0.06), qa_arc + In(0.03),
       dev_cx - qa_cx - In(0.10), In(0.18),
       6, True, RED, PP_ALIGN.CENTER)

    # SM fork
    hg4_cx = r3_x[2] + r3_w[2] // 2
    rect(slide, hg4_cx - CT // 2, R3_Y + BH, CT, SM_Y - R3_Y - BH, NAVY)

    # ── Row 1 ──────────────────────────────────────────────────────────────────
    rect(slide, r1_x[0], R1_Y, r1_w[0], BH, NAVY,
         line=WHITE, line_pt=0.8, rounded=True)
    tb(slide, "📋\nBusiness\nRequirement",
       r1_x[0]+In(0.04), R1_Y+In(0.12), r1_w[0]-In(0.08), BH-In(0.14),
       8, True, WHITE, PP_ALIGN.CENTER)

    node(r1_x[1], R1_Y, r1_w[1], BH, NAVY, WHITE,
         "① PM Agent  /pm-agent",
         "Writes PRD · Creates Epic\n/pm-agent 'requirement'\n🏷 bmad-prd-approved", WHITE, PALE)
    node(r1_x[2], R1_Y, r1_w[2], BH, C_GATE, RGBColor(0xCC, 0xAA, 0x40),
         "👤 Human Gate 1", "Review Epic + PRD\n→ approve to proceed", GATE_TXT, GATE_TXT)
    node(r1_x[3], R1_Y, r1_w[3], BH, NAVY, WHITE,
         "② BA Agent  /ba-agent",
         "Stories · ACs · DoR\n8/9 DoR items checked\n🏷 bmad-dor-ready", WHITE, PALE)
    node(r1_x[4], R1_Y, r1_w[4], BH, C_GATE, RGBColor(0xCC, 0xAA, 0x40),
         "👤 Human Gate 2", "Review Stories\n→ ACs complete?", GATE_TXT, GATE_TXT)

    # ── Row 2 ──────────────────────────────────────────────────────────────────
    loop_node(r2_x[0], R2_Y, r2_w[0], BH,
              "③ Arch Eval Loop ⟳  max 2",
              "/arch-eval-loop SCRUM-{n}\n11 DoR items · Design doc\n🏷 bmad-arch-approved",
              "↺ retry with gap list · ARCH FAIL → BA · escalate if max")
    node(r2_x[1], R2_Y, r2_w[1], BH, C_GATE, RGBColor(0xCC, 0xAA, 0x40),
         "👤 Human Gate 3", "Review Design Doc\n→ arch sign-off", GATE_TXT, GATE_TXT)
    loop_node(r2_x[2], R2_Y, r2_w[2], BH,
              "④ Dev Eval Loop ⟳  max 3",
              "/dev-eval-loop SCRUM-{n}\n8 layers · JaCoCo ≥ 80%\ngit push + PR on pass",
              "↺ retry with file:line list · escalate if max")
    node(r2_x[3], R2_Y, r2_w[3], BH, NAVY, WHITE,
         "⑤ Code Review Agent",
         "/code-review SCRUM-{n}\n12-cat CLAUDE.md audit\n🏷 bmad-code-review-approved", WHITE, PALE)

    # ── Row 3 ──────────────────────────────────────────────────────────────────
    node(r3_x[0], R3_Y, r3_w[0], BH, NAVY, WHITE,
         "⑥ DevOps Agent",
         "/devops-agent pr:{n}\nMonitor CI · auto-fix\nTriggered on CI failure", WHITE, PALE)
    loop_node(r3_x[1], R3_Y, r3_w[1], BH,
              "⑦ QA Eval Loop ⟳  max 3",
              "/qa-eval-loop SCRUM-{n}\ncurl every AC + 4 edges\nJIRA: In Review on pass",
              "↺ retry with curl evidence · escalate if max")
    node(r3_x[2], R3_Y, r3_w[2], BH, C_GATE, RGBColor(0xCC, 0xAA, 0x40),
         "👤 Human Gate 4", "Approve + Merge PR\nJIRA → Done", GATE_TXT, GATE_TXT)
    node(r3_x[3], R3_Y, r3_w[3], BH, NAVY, WHITE,
         "⑨ Doc Agent (post-merge)",
         "/doc-agent SCRUM-{n}\nopenapi.yaml + CLAUDE.md\nRaises docs PR", WHITE, PALE)

    rect(slide, r3_x[4], R3_Y, r3_w[4], BH, NAVY,
         line=WHITE, line_pt=0.8, rounded=True)
    tb(slide, "✅ Story Done\nJIRA: Done\nCI: ✓ Green",
       r3_x[4]+In(0.04), R3_Y+In(0.10), r3_w[4]-In(0.08), BH-In(0.12),
       7.5, True, WHITE, PP_ALIGN.CENTER)

    # SM node
    sm_w = In(1.65)
    sm_x = hg4_cx - sm_w // 2
    rect(slide, sm_x, SM_Y, sm_w, SM_H, NAVY, line=WHITE, line_pt=0.4, rounded=True)
    tb(slide, "⑧ SM Agent (daily)\n/sm-agent\nSprint health · DoD gate",
       sm_x+In(0.04), SM_Y+In(0.05), sm_w-In(0.08), SM_H-In(0.08),
       7, True, WHITE, PP_ALIGN.CENTER)

    # ── Human also owns strip ──────────────────────────────────────────────────
    foot_y = In(6.40)
    rect(slide, In(0.35), foot_y, In(12.50), In(0.20),
         BG, line=NAVY, line_pt=0.3)
    tb(slide,
       "👤 Human also owns:   Architecture decisions  ·  "
       "JIRA → Done transition  ·  Production deployment (Minikube only)",
       In(0.48), foot_y + In(0.02), In(12.20), In(0.18),
       7, False, NAVY, PP_ALIGN.CENTER)

    # ── Legend — 3 colours only ────────────────────────────────────────────────
    items = [("AI Agent / Eval Loop", NAVY), ("Human Gate", C_GATE), ("Failure arc", RED)]
    leg_y = In(6.64)
    for i, (lbl, clr) in enumerate(items):
        lx = In(0.35) + i * In(4.10)
        rect(slide, lx, leg_y, In(0.16), In(0.16), clr)
        tb(slide, lbl, lx+In(0.20), leg_y-In(0.01),
           In(3.85), In(0.18), 7.5, False, NAVY, PP_ALIGN.LEFT)

    # ── Arrowheads — drawn LAST so they appear on top of all boxes ─────────────
    AH = 13   # arrowhead font size (pt)
    AW = In(0.24)
    ATH = In(0.26)

    # Intra-row ▶ at every destination box entry
    for (xs, ws, ry) in [(r1_x, r1_w, R1_Y), (r2_x, r2_w, R2_Y), (r3_x, r3_w, R3_Y)]:
        cy = ry + BH // 2
        for i in range(len(xs) - 1):
            x2 = xs[i + 1]
            tb(slide, "▶", I(x2) - In(0.20), I(cy) - In(0.13), AW, ATH, AH, True, NAVY)

    # L-bend row 1→2  ▼ at ARCH box top
    tb(slide, "▼", arch_cx - In(0.12), R2_Y - In(0.24), AW, ATH, AH, True, NAVY)
    # L-bend row 2→3  ▼ at DO box top
    tb(slide, "▼", do_cx   - In(0.12), R3_Y - In(0.24), AW, ATH, AH, True, NAVY)
    # SM fork         ▼ at SM box top
    tb(slide, "▼", hg4_cx  - In(0.12), SM_Y - In(0.24), AW, ATH, AH, True, NAVY)

    # ARCH FAIL  ▶ on horizontal segment (direction indicator, in gap)
    tb(slide, "▶", I(ba_cx)  - In(0.20), arch_fail_y - In(0.13), AW, ATH, AH, True, RED)
    # ARCH FAIL  ▲ arriving at BA box bottom
    tb(slide, "▲", ba_cx     - In(0.12), I(R1_Y + BH) - In(0.04), AW, ATH, AH, True, RED)

    # REQUEST CHANGES  ▼ arriving at DEV box top
    tb(slide, "▼", dev_cx    - In(0.12), arc_y - In(0.04), AW, ATH, AH, True, RED)

    # QA REJECT  ▶ on horizontal segment (direction indicator, in gap)
    tb(slide, "▶", I(dev_cx) - In(0.20), qa_arc - In(0.13), AW, ATH, AH, True, RED)
    # QA REJECT  ▲ arriving at DEV box bottom
    tb(slide, "▲", dev_cx    - In(0.12), I(R2_Y + BH) - In(0.04), AW, ATH, AH, True, RED)


# ── SLIDE 5 — Eval Loop Retry Logic ───────────────────────────────────────────
# 3 panels, each with stacked labelled boxes and ▼ text arrows.
# No connector XML shapes — avoids all float-EMU issues.

def build_slide5(slide):
    frame(slide, "BMAD Eval Loops — Retry Logic & Escalation", "05")
    rect(slide, In(0.25), In(1.02), In(12.85), In(5.78), BG)

    loops = [
        {
            "hdr":    "③  Arch Eval Loop\n/arch-eval-loop SCRUM-{n}   max 2 attempts",
            "impl":   "Write technical design\n\n• endpoint spec  • DB schema\n• Kafka topics  • Redis keys  • test plan",
            "eval":   "Validate 11 DoR items",
            "pass":   "✅  bmad-arch-approved\n→ pipeline continues to Dev",
            "retry":  "⟳  Attempt 1 — RETRY\nFeed gap list as mandatory constraints\n→ go back and rewrite design",
            "escl":   "❌  Attempt 2 — BLOCKED\nEscalate to BA Agent\nPost full gap evidence on JIRA",
        },
        {
            "hdr":    "④  Dev Eval Loop\n/dev-eval-loop SCRUM-{n}   max 3 attempts",
            "impl":   "Implement 8 layers\n\n• domain  • migration  • DTO  • mapper\n• repository  • service  • controller  • tests",
            "eval":   "12-category code review\n+ mvn test (0 failures)\n+ JaCoCo ≥ 80% coverage",
            "pass":   "✅  git push  ·  gh pr create\nJIRA status → In Review",
            "retry":  "⟳  Attempt < 3 — RETRY\nFeed file:line violation list as mandatory fixes\n→ go back and fix violations",
            "escl":   "❌  Attempt 3 — ESCALATED\nEscalate to Human\nPost full violation list on JIRA",
        },
        {
            "hdr":    "⑦  QA Eval Loop\n/qa-eval-loop SCRUM-{n}   max 3 attempts",
            "impl":   "Execute all AC tests\n\n• curl every acceptance criterion\n• + 4 mandatory edge cases:\n  404 · 401 · BigDecimal · ISO 8601",
            "eval":   "All ACs return expected\nHTTP status + response body?",
            "pass":   "✅  QA PASSED on JIRA\nJIRA status → In Review\n(awaits human approval to merge)",
            "retry":  "⟳  Attempt < 3 — RETRY\nFeed prior curl evidence as mandatory context\n→ go back and retest",
            "escl":   "❌  Attempt 3 — ESCALATED\nEscalate to Human\nPost full curl evidence on JIRA",
        },
    ]

    # Column geometry (3 cols, no division needed)
    CW    = In(3.95)   # panel width
    CGAP  = In(0.35)   # gap between panels
    SX    = In(0.30)   # start x
    PAD   = In(0.10)   # inner padding

    # 3 cols: SX, SX+CW+CGAP, SX+2*(CW+CGAP)
    # = 0.30, 4.60, 8.90  → last col right edge: 8.90+3.95=12.85 ✓

    # Row heights
    HDR_H  = In(0.58)
    IMPL_H = In(1.00)
    EVAL_H = In(0.72)
    PASS_H = In(0.62)
    RETY_H = In(0.72)
    ESCL_H = In(0.72)
    ARR    = In(0.22)   # arrow gap

    SY = In(1.12)   # panel top

    # Pre-compute Y positions for each box (same for all 3 columns)
    y_hdr  = SY
    y_impl = y_hdr  + HDR_H  + In(0.10)
    y_arr1 = y_impl + IMPL_H
    y_eval = y_arr1 + ARR
    y_arr2 = y_eval + EVAL_H
    y_pass = y_arr2 + ARR
    y_rety = y_pass + PASS_H + In(0.08)
    y_escl = y_rety + RETY_H + In(0.08)

    total_h = I(y_escl) + I(ESCL_H) - I(SY)

    ORANGE = RGBColor(0xE6, 0x77, 0x00)
    RED    = RGBColor(0xC7, 0x3E, 0x1D)
    DKBLUE = RGBColor(0x02, 0x3E, 0x8A)

    for ci, lp in enumerate(loops):
        px = SX + ci * (CW + CGAP)
        ix = px + PAD           # inner content x
        iw = CW - 2 * PAD      # inner content width
        col_cx = px + CW // 2  # column centre x

        # Panel background border
        s = slide.shapes.add_shape(5, I(px), I(SY), I(CW), I(total_h))
        s.fill.solid(); s.fill.fore_color.rgb = RGBColor(0xF0, 0xF8, 0xFF)
        s.line.color.rgb = C_LOOP; s.line.width = Pt(1.5)

        # Header
        rect(slide, px, y_hdr, CW, HDR_H, C_LOOP)
        tb(slide, lp["hdr"], ix, y_hdr + In(0.04), iw, HDR_H - In(0.08),
           8.5, True, WHITE, PP_ALIGN.LEFT)

        # Implement box
        rect(slide, ix, y_impl, iw, IMPL_H, C_LOOP,
             line=RGBColor(0x90, 0xE0, 0xEF), line_pt=0.5)
        tb(slide, lp["impl"], ix + In(0.06), y_impl + In(0.04),
           iw - In(0.12), IMPL_H - In(0.08), 7.5, False, WHITE, PP_ALIGN.LEFT)

        # ▼ arrow
        varrow(slide, col_cx, y_arr1, y_eval, C_LOOP)

        # Evaluate box
        rect(slide, ix, y_eval, iw, EVAL_H, DKBLUE,
             line=RGBColor(0x90, 0xE0, 0xEF), line_pt=0.5)
        tb(slide, lp["eval"], ix + In(0.06), y_eval + In(0.06),
           iw - In(0.12), EVAL_H - In(0.12), 8.5, True, WHITE)

        # ▼ arrow
        varrow(slide, col_cx, y_arr2, y_pass, C_TERM)

        # PASS box
        rect(slide, ix, y_pass, iw, PASS_H, C_TERM,
             line=RGBColor(0x95, 0xD5, 0xB2), line_pt=0.5, rounded=True)
        tb(slide, lp["pass"], ix + In(0.06), y_pass + In(0.04),
           iw - In(0.12), PASS_H - In(0.08), 7.5, False, WHITE, PP_ALIGN.LEFT)

        # RETRY box (with ⟳ left-edge indicator)
        rect(slide, ix, y_rety, iw, RETY_H, ORANGE,
             line=RGBColor(0xFF, 0xB7, 0x00), line_pt=0.5, rounded=True)
        tb(slide, lp["retry"], ix + In(0.06), y_rety + In(0.04),
           iw - In(0.12), RETY_H - In(0.08), 7.5, False, WHITE, PP_ALIGN.LEFT)

        # ESCALATE box
        rect(slide, ix, y_escl, iw, ESCL_H, RED,
             line=RGBColor(0xFF, 0x6B, 0x6B), line_pt=0.5, rounded=True)
        tb(slide, lp["escl"], ix + In(0.06), y_escl + In(0.04),
           iw - In(0.12), ESCL_H - In(0.08), 7.5, False, WHITE, PP_ALIGN.LEFT)


# (Slide 6 removed — content absorbed into Slide 4 footer strip)

def _never_called_slide6():
    frame(slide, "BMAD Human Governance — Who Owns What", "06")
    rect(slide, In(0.25), In(1.02), In(12.85), In(5.78), BG)

    PANEL_Y = In(1.12)
    PANEL_H = In(5.58)
    HDR_H   = In(0.48)
    ITEM_H  = In(0.52)
    ITEM_G  = In(0.05)
    PAD     = In(0.10)
    LPX     = In(0.32)
    LPW     = In(5.85)
    RPX     = In(6.87)
    RPW     = In(6.10)

    # Left panel — AI Agents
    rect(slide, LPX, PANEL_Y, LPW, PANEL_H,
         RGBColor(0xEB, 0xF3, 0xFD), line=C_AGENT, line_pt=1.2, rounded=True)
    rect(slide, LPX, PANEL_Y, LPW, HDR_H, C_AGENT, rounded=True)
    tb(slide, "🤖  AI Agents — What They Own",
       LPX + PAD, PANEL_Y, LPW - 2*PAD, HDR_H, 12, True, WHITE, PP_ALIGN.LEFT)

    ai_items = [
        ("① PM Agent  /pm-agent",               "Writes PRD · Creates Epic · Label: bmad-prd-approved",                  C_AGENT),
        ("② BA Agent  /ba-agent",                "Stories + ACs + DoR checklist · Label: bmad-dor-ready",                 C_AGENT),
        ("③ Arch Eval Loop ⟳  max 2 attempts",  "Technical design — auto-retries with gap list fed back each attempt",    C_LOOP),
        ("④ Dev Eval Loop ⟳  max 3 attempts",   "Code + tests — auto-retries with file:line violations fed back",         C_LOOP),
        ("⑤ Code Review  /code-review",          "12-category CLAUDE.md audit · Label: bmad-code-review-approved",        C_REVIEW),
        ("⑥ DevOps Agent  /devops-agent",        "CI pipeline monitor · Auto-fix compilation and test failures",          C_REVIEW),
        ("⑦ QA Eval Loop ⟳  max 3 attempts",    "AC verification with curl — auto-retries with curl evidence fed back",   C_LOOP),
        ("⑧ SM Agent  /sm-agent  (daily)",       "Sprint health · Velocity · DoD gate before closing tickets to Done",    C_AGENT),
        ("⑨ Doc Agent  /doc-agent  (post-merge)","Updates openapi.yaml + CLAUDE.md · Raises a separate docs PR",          C_AGENT),
    ]

    iy = PANEL_Y + HDR_H + In(0.08)
    for name, desc, col in ai_items:
        rect(slide, LPX + In(0.08), iy, LPW - In(0.16), ITEM_H,
             col, line=WHITE, line_pt=0.3, rounded=True)
        tb(slide, name,
           LPX + In(0.18), iy + In(0.04), LPW - In(0.34), In(0.22),
           8.5, True, WHITE, PP_ALIGN.LEFT)
        tb(slide, desc,
           LPX + In(0.18), iy + In(0.26), LPW - In(0.34), In(0.22),
           7.5, False, PALE, PP_ALIGN.LEFT)
        iy += ITEM_H + ITEM_G

    # Right panel — Humans
    GOLD_DRK = RGBColor(0xB7, 0x7D, 0x0A)
    rect(slide, RPX, PANEL_Y, RPW, PANEL_H,
         RGBColor(0xFF, 0xFB, 0xF0), line=GOLD_DRK, line_pt=1.2, rounded=True)
    rect(slide, RPX, PANEL_Y, RPW, HDR_H, GOLD_DRK, rounded=True)
    tb(slide, "👤  Humans — What They Own",
       RPX + PAD, PANEL_Y, RPW - 2*PAD, HDR_H, 12, True, WHITE, PP_ALIGN.LEFT)

    GATE_BG  = C_GATE
    OTHER_BG = RGBColor(0xDC, 0xE6, 0xF2)

    human_items = [
        ("🚧 Human Gate 1 — Review Epic + PRD",
         "After PM Agent. Approve or request changes to the product requirement doc.",
         GATE_BG, RGBColor(0x3D, 0x2B, 0x00), RGBColor(0x5A, 0x3E, 0x00)),
        ("🚧 Human Gate 2 — Review Stories + ACs",
         "After BA Agent. Confirm acceptance criteria are complete and testable.",
         GATE_BG, RGBColor(0x3D, 0x2B, 0x00), RGBColor(0x5A, 0x3E, 0x00)),
        ("🚧 Human Gate 3 — Review Technical Design",
         "After Arch Eval Loop. Sign off the design document before coding starts.",
         GATE_BG, RGBColor(0x3D, 0x2B, 0x00), RGBColor(0x5A, 0x3E, 0x00)),
        ("🚧 Human Gate 4 — Approve + Merge Pull Request",
         "After QA Eval Loop. Final PR review on GitHub then merge to develop.",
         GATE_BG, RGBColor(0x3D, 0x2B, 0x00), RGBColor(0x5A, 0x3E, 0x00)),
        ("Architecture decisions",
         "Technology choices, service boundaries, new external dependencies.",
         OTHER_BG, NAVY, GREY),
        ("JIRA → Done transition",
         "Type 'merged code' after merge — agent transitions the story to Done.",
         OTHER_BG, NAVY, GREY),
        ("Production deployment",
         "Out of scope for POC. Minikube is the deployment target.",
         OTHER_BG, NAVY, GREY),
    ]

    ITEM_H_R = In(0.68)
    ITEM_G_R = In(0.05)
    iy = PANEL_Y + HDR_H + In(0.08)
    for name, desc, bg, tc, sc in human_items:
        brd = RGBColor(0xCC, 0xAA, 0x40) if bg == GATE_BG else OTHER_BG
        rect(slide, RPX + In(0.08), iy, RPW - In(0.16), ITEM_H_R,
             bg, line=brd, line_pt=0.6, rounded=True)
        tb(slide, name,
           RPX + In(0.18), iy + In(0.04), RPW - In(0.34), In(0.24),
           8.5, True, tc, PP_ALIGN.LEFT)
        tb(slide, desc,
           RPX + In(0.18), iy + In(0.30), RPW - In(0.34), In(0.34),
           7.5, False, sc, PP_ALIGN.LEFT)
        iy += ITEM_H_R + ITEM_G_R


# ── SLIDES 8-10 — Agents in Action ───────────────────────────────────────────
# Each slide: 3 agent cards (header · context · terminal · gate).
# Terminal lines are colour-coded by prefix: $ blue · ✓ green · ⚠ yellow · ↺ orange

def agent_card(slide, px, py, pw, ph, hc, title, ctx, lines, gate):
    HDR_H  = In(0.54)
    CTX_H  = In(0.32)
    GATE_H = In(0.38)
    TERM_H = ph - HDR_H - CTX_H - GATE_H

    TERM_BG = RGBColor(0x0D, 0x1B, 0x2A)
    CTX_BG  = RGBColor(0xE4, 0xED, 0xF8)

    # Card shell
    rect(slide, px, py, pw, ph, RGBColor(0xF8, 0xFA, 0xFF), line=hc, line_pt=1.0)
    # Header
    rect(slide, px, py, pw, HDR_H, hc)
    tb(slide, title, px+In(0.10), py+In(0.08), pw-In(0.18), HDR_H-In(0.12),
       8.5, True, WHITE, PP_ALIGN.LEFT)
    # Context
    ctx_y = py + HDR_H
    rect(slide, px, ctx_y, pw, CTX_H, CTX_BG)
    tb(slide, ctx, px+In(0.10), ctx_y+In(0.05), pw-In(0.18), CTX_H-In(0.08),
       7, False, NAVY, PP_ALIGN.LEFT)
    # Terminal
    term_y = ctx_y + CTX_H
    PAD = In(0.06)
    rect(slide, px+PAD, term_y+PAD, pw-2*PAD, TERM_H-2*PAD, TERM_BG)
    LH = In(0.225)
    tx = px + PAD + In(0.08)
    tw = pw - 2*PAD - In(0.14)
    ty = term_y + PAD + In(0.08)
    for line in lines:
        if line == "":
            ty += In(0.07); continue
        if   line.startswith("$"):  c = RGBColor(0x7F, 0xD4, 0xFF)
        elif line.startswith("✓"):  c = RGBColor(0x50, 0xFF, 0x78)
        elif line.startswith("⚠"):  c = RGBColor(0xFF, 0xCC, 0x00)
        elif line.startswith("↺"):  c = RGBColor(0xFF, 0x8C, 0x00)
        elif line.startswith("❌"): c = RGBColor(0xFF, 0x6B, 0x6B)
        else:                       c = RGBColor(0xBB, 0xCC, 0xDD)
        tb(slide, line, tx, ty, tw, LH, 7.5, line.startswith("$"), c, PP_ALIGN.LEFT)
        ty += LH
    # Gate strip
    gate_y = py + ph - GATE_H
    rect(slide, px, gate_y, pw, GATE_H, hc)
    tb(slide, gate, px+In(0.10), gate_y+In(0.10), pw-In(0.18), GATE_H-In(0.14),
       7.5, True, WHITE, PP_ALIGN.LEFT)


def _action_slide(slide, title, num, phase_lbl, cards_data):
    frame(slide, title, num)
    rect(slide, In(0.25), In(1.02), In(12.85), In(5.78), BG)
    tb(slide, phase_lbl, In(9.80), In(1.08), In(3.10), In(0.26),
       8.5, True, NAVY, PP_ALIGN.RIGHT)

    CW, CH, CG, SX, SY = In(4.13), In(5.52), In(0.13), In(0.27), In(1.18)
    for i, (hc, t, ctx, lines, gate) in enumerate(cards_data):
        agent_card(slide, SX + i*(CW+CG), SY, CW, CH, hc, t, ctx, lines, gate)


def build_slide8(slide):
    _action_slide(slide,
        "Planning Phase — Agents in Action", "08",
        "PHASE 1  ·  PLAN",
        [
            (C_AGENT,
             "① PM Agent   /pm-agent",
             "Input: plain-English requirement · Output: PRD + Epic",
             [
                 "$ /pm-agent 'build a sell-side OMS'",
                 "",
                 "✓ Analysing requirement...",
                 "✓ PRD created → Confluence: BD-POC",
                 "✓ Scope: 9 microservices · US Equities",
                 "✓ Epic SCRUM-10 created in JIRA",
                 "✓ 12 candidate stories identified",
                 "✓ Backlog ordered by dependency",
                 "✓ Label: bmad-prd-approved",
             ],
             "🏷 bmad-prd-approved  on Epic SCRUM-10"),

            (C_AGENT,
             "② BA Agent   /ba-agent",
             "Input: epic:SCRUM-10 · Requires: bmad-prd-approved",
             [
                 "$ /ba-agent epic:SCRUM-10",
                 "",
                 "✓ Reads PRD + architecture debrief",
                 "✓ SCRUM-34: GET /clients/{id}/portfolio",
                 "✓ SCRUM-35: PATCH reserved-cash endpoint",
                 "✓ SCRUM-36: Kafka consumers — client-svc",
                 "✓ SCRUM-37: risk-service rule engine API",
                 "✓ DoR: 8/9 items checked (arch pending)",
                 "✓ Label: bmad-dor-ready on each story",
             ],
             "🏷 bmad-dor-ready  on SCRUM-34 / 35 / 36 / 37"),

            (C_LOOP,
             "③ Arch Eval Loop ⟳   /arch-eval-loop",
             "Input: SCRUM-36 · Requires: bmad-dor-ready · max 2 attempts",
             [
                 "$ /arch-eval-loop SCRUM-36",
                 "",
                 "Attempt 1/2 — writing technical design...",
                 "✓ Pattern: Kafka consumers, no REST",
                 "✓ Consumes: execution.completed",
                 "✓ Consumes: order.state.changed",
                 "✓ Consumer group: client-svc-exec-group",
                 "✓ Redis: client:portfolio:{id} evicted",
                 "✓ DoR: 11/11 items → PASS → HG3 ✓",
             ],
             "🏷 bmad-arch-approved  · Human Gate 3 unlocked"),
        ])


def build_slide9(slide):
    _action_slide(slide,
        "Build Phase — Agents in Action", "09",
        "PHASE 2  ·  BUILD",
        [
            (C_LOOP,
             "④ Dev Eval Loop ⟳   /dev-eval-loop",
             "Input: SCRUM-36 · Requires: both labels · max 3 attempts",
             [
                 "$ /dev-eval-loop SCRUM-36",
                 "",
                 "Attempt 1/3 — implementing 8 layers...",
                 "⚠ Cat 8: @Transactional missing on service",
                 "⚠ Cat 3: BigDecimal from double literal",
                 "↺ 2 violations → feeding back as fixes...",
                 "",
                 "Attempt 2/3 — applying mandatory fixes...",
                 "✓ 12-cat review PASS · mvn test 47/47",
                 "✓ JaCoCo 84% ≥ 80% · git push · PR #89",
             ],
             "git push  ·  PR #89 raised  ·  JIRA: In Review"),

            (C_REVIEW,
             "⑤ Code Review   /code-review",
             "Input: PR #89 on GitHub · Requires: bmad-arch-approved",
             [
                 "$ /code-review SCRUM-36",
                 "",
                 "Auditing PR #89 — 12 categories...",
                 "✓ Cat 1: package structure · naming ✓",
                 "✓ Cat 3: BigDecimal usage correct ✓",
                 "✓ Cat 8: @Transactional present ✓",
                 "✓ Cat 11: Kafka DLT pattern correct ✓",
                 "✓ Cat 12: GlobalExceptionHandler ✓",
                 "✓ All 12 categories PASS",
                 "✓ PR approved · label set on JIRA",
             ],
             "🏷 bmad-code-review-approved  · PR approved"),

            (C_REVIEW,
             "⑥ DevOps Agent   /devops-agent",
             "Input: pr:89 · Triggered on any CI pipeline failure",
             [
                 "$ /devops-agent pr:89",
                 "",
                 "Monitoring GitHub Actions pipeline...",
                 "✓ maven-build: PASS",
                 "✓ unit-tests: 47/47 PASS",
                 "✓ integration-tests: 12/12 PASS",
                 "✓ jacoco-coverage: 84% ≥ 80% PASS",
                 "✓ docker-build: client-service:latest ✓",
                 "✓ docker-push: Docker Hub ✓",
                 "✓ CI pipeline: ALL GREEN",
             ],
             "CI pipeline  ✓ GREEN  · PR unblocked for merge"),
        ])


def build_slide10(slide):
    _action_slide(slide,
        "Validate & Ship — Agents in Action", "10",
        "PHASE 3  ·  VALIDATE & SHIP",
        [
            (C_LOOP,
             "⑦ QA Eval Loop ⟳   /qa-eval-loop",
             "Input: SCRUM-36 · Requires: PR approved · max 3 attempts",
             [
                 "$ /qa-eval-loop SCRUM-36",
                 "",
                 "Attempt 1/3 — curl testing all ACs...",
                 "✓ AC1: execution.completed consumed ✓",
                 "✓ AC2: portfolio balance updated on fill ✓",
                 "✓ AC3: failed message routed to DLT ✓",
                 "✓ AC4: Redis key evicted on fill ✓",
                 "✓ Edge: 401 Unauthorized → 401 ✓",
                 "✓ Edge: BigDecimal precision ✓",
                 "✓ All ACs pass · JIRA: In Review ✓",
             ],
             "All ACs pass  · curl evidence on JIRA  · In Review"),

            (C_AGENT,
             "⑧ SM Agent (daily)   /sm-agent",
             "Input: sprint state · Trigger: daily or post-merge",
             [
                 "$ /sm-agent",
                 "",
                 "Sprint 4 health check...",
                 "✓ SCRUM-36: QA pass · PR merged · CI ✓",
                 "✓ Coverage 84% ≥ 80% · DoD: 8/8 items",
                 "✓ Velocity: 18 story pts delivered",
                 "✓ No blockers · 2 stories in progress",
                 "✓ DoD checklist comment posted to JIRA",
                 "✓ JIRA: In Review → Done",
             ],
             "DoD ✓  · JIRA: Done  · sprint velocity tracked"),

            (C_AGENT,
             "⑨ Doc Agent (post-merge)   /doc-agent",
             "Input: SCRUM-36 · Trigger: post PR merge · never direct commit",
             [
                 "$ /doc-agent SCRUM-36",
                 "",
                 "Reading merged code + story context...",
                 "✓ openapi.yaml: +2 Kafka consumer paths",
                 "✓ consumer-groups.md: 2 groups added",
                 "✓ CLAUDE.md: Redis cache table updated",
                 "✓ Confluence: BD-POC page refreshed",
                 "✓ Docs PR #92 raised → develop",
                 "   (never commits directly — PR only)",
             ],
             "Docs PR #92 raised  · never commits directly"),
        ])


# ── Slide management ───────────────────────────────────────────────────────────

def clear_slide(slide):
    sp = slide.shapes._spTree
    for c in list(sp)[2:]: sp.remove(c)


def delete_slide(prs, idx):
    xml_slides = prs.slides._sldIdLst
    rId = xml_slides[idx].attrib[
        '{http://schemas.openxmlformats.org/officeDocument/2006/relationships}id'
    ]
    prs.part.drop_rel(rId)
    del xml_slides[idx]


def insert_blank(prs, pos):
    prs.slides.add_slide(prs.slide_layouts[2])
    lst = prs.slides._sldIdLst
    moved = list(lst)[-1]
    lst.remove(moved); lst.insert(pos, moved)
    return prs.slides[pos]


def main():
    prs = Presentation(PPTX)
    n = len(prs.slides)
    print(f"Opened — {n} slides")

    if n == 14:
        # Delete old slide 6; slides 8-10 shift from indices 7,8,9 → 6,7,8 after deletion
        for idx in [3, 4, 5, 7, 8, 9]:
            clear_slide(prs.slides[idx])
        delete_slide(prs, 5)
        print(f"Cleared slides 4-5 + 8-10, deleted slide 6 → {len(prs.slides)} slides")
        build_slide4(prs.slides[3])
        build_slide5(prs.slides[4])
        build_slide8(prs.slides[6])
        build_slide9(prs.slides[7])
        build_slide10(prs.slides[8])
    elif n in (10, 13):
        for idx in [3, 4, 7, 8, 9]:
            clear_slide(prs.slides[idx])
        print(f"Cleared slides 4-5 and 8-10 ({n}-slide deck)")
        build_slide4(prs.slides[3])
        build_slide5(prs.slides[4])
        build_slide8(prs.slides[7])
        build_slide9(prs.slides[8])
        build_slide10(prs.slides[9])
    else:
        print(f"WARNING: unexpected slide count {n}")

    prs.save(PPTX)
    print(f"Saved — {len(prs.slides)} slides total")


if __name__ == "__main__":
    main()
