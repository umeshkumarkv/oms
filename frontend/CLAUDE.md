<!-- plumbline:template claude-unit v1 -->
# Frontend

<!-- plumbline:begin unit -->
Last updated: 4369a80 (2026-10-28, /pl-onboard, claude-unit v1)

## Purpose

React 18 / TypeScript single-page application for the Broker-Dealer OMS. Provides role-gated
screens for login, order entry, order blotter, portfolio, market data, audit trail, and admin
(risk-rule management). Communicates with the backend through a Vite dev-proxy (`/api/v1`) or
nginx reverse-proxy in Docker; receives real-time price ticks over a WebSocket and trade
notifications over a Server-Sent Events stream.

## Build & run

```
npm install          # first time or after dependency changes
npm run dev          # Vite dev server — http://localhost:5173 (proxies /api → :9090, /ws/v1 → :8084)
npm run build        # tsc + vite build → dist/
npm run preview      # serve dist/ locally
npm test             # run unit tests (vitest, no watch)
npm run e2e          # Playwright end-to-end tests
```

Docker (production): nginx serves built assets on port **3000** (host) → 80 (container).
No hard-coded absolute paths that affect startup.

**Test recipe:** `npm test -- --watch=false` · Node v26.8.1 · **4 failed / 302 passed (306 total), 11 of 26 test files had failures** · 2026-10-28

## Architecture & structure

**Stack:** React 18.2, TypeScript 5.3, Vite 5.1, Vitest 1.3, React Router DOM 6.22, Axios 1.6,
`@microsoft/fetch-event-source` 2.0, Playwright 1.60. Node engine: v26.8.1.

**Layer pattern:**

```
pages/          — one file per route; orchestrate data fetch and render
components/     — shared UI pieces (NavBar, Sidebar, ProtectedRoute, modals, toasts)
api/            — thin wrappers around Axios or fetch (one file per domain)
  apiClient.ts  — shared Axios instance; injects Bearer token via interceptor
  http.ts       — re-exports apiClient (all domain api files import from here)
  authApi.ts    — unauthenticated plain axios for login
services/       — websocketService.ts: MarketDataWebSocketService singleton
context/        — AuthContext (auth state + token sync), NotificationsContext (SSE)
hooks/          — useIdleTimer, useMarketSocket, useNotifications, useOrderEntryBidAsk, useToast
types/          — TypeScript interfaces per domain (no runtime logic)
utils/          — constants, dateUtils, marketHours, priceUtils
config/         — sorVenues.ts (SOR venue list and default)
```

Routing is declared in `src/App.tsx` using nested `<ProtectedRoute>` wrappers. Sidebar
collapsed state is persisted to `localStorage` under key `oms-sidebar-collapsed`.

## Security

- **Authentication:** JWT Bearer tokens issued by the backend `POST /api/v1/auth/login`.
  Token stored in `localStorage` under key `oms-auth-user` (full user object as JSON).
  `[code: src/context/AuthContext.tsx]`
- **Token injection:** Axios request interceptor in `src/api/apiClient.ts` attaches
  `Authorization: Bearer <token>` to every request. Login uses a separate plain Axios instance
  with no token. `[code: src/api/apiClient.ts, src/api/authApi.ts]`
- **401 handling:** Axios response interceptor fires `auth:session-expired` window event;
  AuthContext listener clears token and redirects to `/`. `[code: src/api/apiClient.ts]`
- **Route guards:** `ProtectedRoute` redirects unauthenticated users to `/`; role-restricted
  routes redirect unauthorised roles to `/dashboard` or `/admin`.
  `[code: src/components/ProtectedRoute.tsx, src/App.tsx]`
- **Roles:** `TRADER`, `OPERATIONS`, `VIEWER`, `ADMIN` — enforced client-side and expected to
  match backend JWT claim `role`. `[code: src/App.tsx]`
- **WebSocket auth:** Token sent as `{ type: "AUTH", token: "Bearer <token>" }` on socket open;
  subscriptions queued until `AUTH_OK` received. `[code: src/services/websocketService.ts]`
- **SSE auth:** `Authorization: Bearer <token>` header on SSE connection to
  `GET /api/v1/notifications/stream`. `[code: src/hooks/useNotifications.ts]`

## Rules

- Every authenticated page must be wrapped in `<ProtectedRoute>`; unauthenticated access
  redirects to `/`. `[code: src/App.tsx]`
- `ADMIN` role is redirected away from `/orders`, `/portfolio`, `/audit`, `/market`; all other
  authenticated roles are redirected away from `/admin`. `[code: src/App.tsx, src/pages/AdminPanelPage.tsx]`
- `/orders/new` (order entry) is restricted to `TRADER` role only. `[code: src/App.tsx]`
- All API calls (except login) must use the shared `http` / `apiClient` Axios instance so the
  Bearer token interceptor fires. `[code: src/api/apiClient.ts, src/api/http.ts]`
- Risk-rule changes (toggle or threshold) require a typed reason of at least 10 characters before
  submission. `[code: src/pages/AdminPanelPage.tsx]`
- The idle timer on order entry is 60 seconds (`IDLE_TIMER_SECONDS`). `[code: src/utils/constants.ts]`
- Toast auto-dismiss: 5 000 ms for error/warning, 2 500 ms for success-then-navigate.
  `[code: src/utils/constants.ts]`
- Symbol lookup on order entry is debounced 400 ms. `[code: src/utils/constants.ts]`
- SOR venues are `['NYSE', 'NASDAQ', 'ARCA', 'BATS', 'DARK_POOL']`; default is `NYSE`.
  `[code: src/config/sorVenues.ts]`
- Notification toasts are capped at 5 concurrent items (oldest evicted). `[code: src/hooks/useNotifications.ts]`
- WebSocket reconnects automatically after 3 000 ms on close. `[code: src/services/websocketService.ts]`

## Must never

- Never store credentials or raw tokens outside `localStorage` key `oms-auth-user`; never log
  token values.
- Never bypass the `ProtectedRoute` wrapper when adding a new authenticated route.
- Never call the backend directly from a component — all HTTP calls must go through an `api/`
  module so the interceptor chain is preserved.
- Never use a separate Axios instance for authenticated calls; only `authApi.ts` (login) is
  permitted to bypass the shared client.
- Never remove the 401 interceptor or the `auth:session-expired` event handler — doing so breaks
  automatic session expiry.
- Never add role logic to components outside `ProtectedRoute` and the pages that already enforce
  it (`AdminPanelPage`), to avoid role-check drift.

## Decisions & trade-offs

- **Module-level token store in `apiClient.ts`** (not `localStorage` lookup on each request):
  Axios interceptors cannot use React hooks, so `setApiToken` / `setCurrentUser` are imperative
  setters called by `AuthContext`. Source: comment in `src/api/apiClient.ts`.
- **WebSocket subscriptions queued before `AUTH_OK`:** Avoids a race condition where a component
  subscribes before authentication completes. Source: JSDoc in `src/services/websocketService.ts`.
- **`@microsoft/fetch-event-source` for SSE** instead of native `EventSource`: provides
  automatic reconnect and supports custom `Authorization` headers, which `EventSource` does not.
  Source: `package.json` dependency choice; comment in `src/hooks/useNotifications.ts`.
- **User management UI stubbed out (SCRUM-16):** Create User button exists but shows an error
  toast; `POST /api/v1/auth/users` not wired. Source: TODO comment in `src/pages/AdminPanelPage.tsx`.
- **Risk-rule API calls fall back to stubs (SCRUM-47):** `getRiskRules` and `getRiskRuleAudit`
  have stub fallbacks because the real endpoints were not available at export time. Source: TODO
  comments in `src/pages/AdminPanelPage.tsx`.

## Reference

- **API** — 8 React Router routes; 30 REST backend API calls across 7 api modules + 1 SSE stream + 1 WebSocket endpoint
- **Data** — 7 TypeScript domain type entries: 6 files in src/types/ + inline types in websocketService.ts (no local DB)
- **Integrations** — WebSocket `ws[s]://<host>/ws/v1`, SSE `GET /api/v1/notifications/stream`
- **Configuration** — 2 Vite proxy targets, 4 runtime constants, 2 SOR config values
- **Tech debt** — 4 TODO comments in 1 file (AdminPanelPage.tsx); 11 of 26 test files failing

Open CLAUDE.reference.md only for the section your task needs.

— Plumbline 2.8.7 · /pl-onboard · claude-unit v1 · commit 4369a80 · judge pending · AI-drafted, posted after SME review
<!-- plumbline:end unit -->
