# CLAUDE.md — Broker Dealer OMS Frontend
# Owner: Umesh Kumar KV
# Last Updated: April 2026
# Status: Active — Sprint 1

---

## CRITICAL — READ BEFORE ANY FRONTEND CHANGE

This file is the single source of truth for frontend design and architecture.
Never change colors, layout structure, or component patterns without updating
this file first.

---

## 1. Tech Stack — Locked

| Layer      | Technology                   | Version  |
|------------|------------------------------|----------|
| Framework  | React + TypeScript           | 18.x / 5.x |
| Build      | Vite                         | 5.x      |
| Routing    | React Router DOM             | 6.x      |
| HTTP       | Axios                        | 1.x      |
| Icons      | lucide-react                 | 1.x      |
| Font       | @fontsource/inter            | 5.x      |
| Date Picker | react-datepicker             | 4.x      |
| Testing    | Vitest + React Testing Library | 1.x    |

---

## 2. Design System — Never Deviate

### 2a. CSS Custom Properties — `src/index.css`

All colors, radius, and shadow values must come from these tokens.
Never hardcode hex values in component files.

```css
/* Brand */
--color-primary:        #6366f1   /* indigo — buttons, active states, accents */
--color-primary-dark:   #4f46e5   /* hover state for primary */
--color-primary-light:  #e0e7ff   /* light tint for backgrounds */

/* Surface */
--color-surface:        #ffffff
--color-bg:             #f8fafc   /* page background — slate-50 */
--color-nav:            #0f172a   /* navbar AND sidebar — must be identical */

/* Trading semantics */
--color-buy:            #1d4ed8
--color-buy-bg:         #dbeafe
--color-sell:           #be185d
--color-sell-bg:        #fce7f3
--color-success:        #059669
--color-success-bg:     #d1fae5
--color-warning:        #d97706
--color-warning-bg:     #fef3c7
--color-error:          #dc2626
--color-error-bg:       #fee2e2

/* Text */
--color-text-primary:   #0f172a
--color-text-secondary: #64748b
--color-text-muted:     #94a3b8

/* Border */
--color-border:         #e2e8f0
--color-border-light:   #f1f5f9

/* Radius */
--radius-sm:   4px
--radius-md:   8px
--radius-lg:   12px
--radius-xl:   16px
--radius-pill: 999px

/* Shadow */
--shadow-sm:  0 1px 3px rgba(0, 0, 0, 0.06)
--shadow-md:  0 4px 12px rgba(0, 0, 0, 0.08)
--shadow-lg:  0 8px 32px rgba(0, 0, 0, 0.12)
```

### 2b. Typography

- Font: Inter (via @fontsource/inter) — weights 400, 500, 600, 700
- Body: 14px / Inter / color-text-primary
- Page titles: 20px / weight 700 / letter-spacing -0.01em
- Section titles: 14px / weight 600
- Labels / overlines: 10–11px / weight 700 / uppercase / letter-spacing 0.04–0.08em
- Table headers: 11px / weight 700 / uppercase / color-text-secondary
- Code / IDs: monospace / 12px

---

## 3. Layout Structure — Fixed

```
┌─────────────────────────────────────────────────────────┐
│  NavBar (52px, sticky, var(--color-nav), zIndex 100)    │
├──────────────┬──────────────────────────────────────────┤
│  Sidebar     │  <main> (flex: 1, minWidth: 0)           │
│  220px / 56px│  Page content renders here               │
│  sticky      │  padding: 20–24px                        │
│  top: 52px   │  background: var(--color-bg)             │
│  h: 100vh-52 │                                          │
└──────────────┴──────────────────────────────────────────┘
```

### NavBar rules
- Height: 52px — do not change
- Background: `var(--color-nav)` — same as sidebar
- Left section: burger toggle (32×32, radius-md) + indigo logo mark + "Broker OMS"
- Right section: market status pill → bell → initials avatar → role badge → sign out
- No navigation links in the navbar — navigation lives exclusively in the sidebar
- No "New Order" button in the navbar — use the sidebar Trading > New Order link

### Sidebar rules
- Expanded: 220px — do not change
- Collapsed: 56px — do not change
- Background: `var(--color-nav)` — must match navbar exactly (no seam)
- Right border: `1px solid rgba(255,255,255,0.08)`
- Top padding: 6px below navbar
- localStorage key: `oms-sidebar-collapsed`
- Transition: `width 0.22s cubic-bezier(0.4, 0, 0.2, 1)`

---

## 4. Navigation Structure — Order Is Fixed

Do not reorder or rename groups/items without updating this file.

```
Main
  └── Dashboard          /dashboard        LayoutDashboard

Trading
  ├── New Order          /orders/new       FilePlus
  └── Order Blotter      /orders           ClipboardList  (end match)

Market & Portfolio
  ├── Market Data        /market           LineChart
  └── Portfolio          /portfolio        Briefcase

Compliance
  └── Audit Trail        /audit            ScrollText

Admin  ← ADMIN role only
  └── Admin Panel        /admin            Settings
```

---

## 5. Sidebar Link States — Visual Rules

These are deliberate design decisions. Do not adjust opacity/color without reason.

| State    | Background                    | Text                       | Left border              |
|----------|-------------------------------|----------------------------|--------------------------|
| Default  | transparent                   | rgba(255,255,255,0.45)     | 2px solid transparent    |
| Hover    | rgba(255,255,255,0.06)        | rgba(255,255,255,0.85)     | rgba(255,255,255,0.15)   |
| Active   | rgba(99,102,241,0.15)         | #a5b4fc                    | var(--color-primary)     |

Active state uses a **2px left border in `--color-primary`** — this is the primary active indicator.
Do not replace it with a background-only approach.

Collapsed state:
- Left border hidden (border-left-color: transparent)
- Active: rgba(99,102,241,0.18) background only
- Icons centered via justify-content: center

Group headers: `rgba(255,255,255,0.28)` — readable on dark nav, not competing with links.

---

## 6. Icon Rules

Library: **lucide-react only** — no other icon sets, no emoji icons.

| Context            | Size | strokeWidth |
|--------------------|------|-------------|
| Sidebar nav links  | 18   | default     |
| Navbar actions     | 16   | 1.75        |
| Logo mark          | 15   | 2.5         |
| Small inline       | 14   | 2.5         |

Icon imports must be named imports for tree-shaking:
```tsx
// CORRECT
import { LayoutDashboard, FilePlus } from 'lucide-react'

// WRONG
import * as Icons from 'lucide-react'
```

---

## 7. CSS Class Conventions

Global utility classes defined in `src/index.css`:

| Class               | Purpose                                      |
|---------------------|----------------------------------------------|
| `.skeleton`         | Shimmer loading placeholder                  |
| `.pulse-dot`        | Pulsing market status indicator dot          |
| `.card-hover`       | Lift on hover (transform + shadow transition)|
| `.nav-link`         | Navbar link with active bottom-border        |
| `.nav-link.active`  | Active navbar link (bottom border indicator) |
| `.login-input`      | Login form input with focus ring via CSS     |
| `.login-left-panel` | Hidden on screens ≤699px                     |
| `.sidebar`          | Sidebar container with width transition      |
| `.sidebar.collapsed`| 56px collapsed state                         |
| `.sidebar-link`     | Nav link in sidebar                          |
| `.sidebar-link.active` | Active nav link with left border          |
| `.sidebar-label`    | Label text that fades on collapse            |
| `.sidebar-group-header` | Section label above nav groups           |

Do not add global classes without documenting them here.

---

## 8. Component Conventions

### File naming
- Pages: `{Name}Page.tsx` in `src/pages/`
- Components: `{Name}.tsx` in `src/components/`
- API modules: `{domain}Api.ts` in `src/api/`
- Types: `{domain}.types.ts` in `src/types/`

### Inline styles vs CSS classes
- **Structural layout** (flex, grid, position, dimensions): inline styles
- **Animated or stateful styles** (hover, active, transitions, collapsed): CSS classes in `index.css`
- **Design tokens** always via `var(--token-name)`, never hardcoded hex

### Patterns
- Use `React.CSSProperties` for typed inline style objects
- Collect related inline styles into a local `const s = {}` style map at the top of the file
- Do not import external CSS-in-JS libraries (styled-components, emotion, etc.)

---

## 9. State Management

- No Redux, Zustand, or other global state library — use React context only
- Auth state: `src/context/AuthContext.tsx`
- Sidebar collapsed state: lifted to `AppLayout` in `App.tsx`, persisted to localStorage
- All other state: local `useState` in the component that owns it

---

## 10. Testing

- Framework: Vitest + React Testing Library
- Test files: `src/test/{Component}.test.tsx`
- All aria-labels must be unique — avoid regex collisions with testing-library queries
  - Eye toggle: `aria-label="Reveal"` / `aria-label="Hide"` — NOT "Show password" (collides with label)
- Run before every push: `npm test` (must be 0 failures)
- Build check before every push: `npm run build` (must succeed with 0 errors)

---

## 11. Out of Scope for POC Frontend

- Dark mode toggle
- Internationalisation (i18n)
- Mobile layout (screen < 700px only hides login left panel)
- Drag-and-drop order management
- Real-time charting (TradingView or similar)
- Notification popover / notification centre
- User preference persistence beyond sidebar state
