<!-- plumbline:template claude-unit v2 -->
# frontend

<!-- plumbline:begin unit -->
Last updated: 39916bd (2026-10-30, /pl-onboard, claude-unit v2)

## Purpose

React 18 SPA — trader-facing UI for the Broker-Dealer OMS. Pages: login, order entry, order blotter, portfolio, market data (live WebSocket ticks), audit trail, admin panel (risk rules). Authenticated pages gated by `ProtectedRoute`; TRADER only for order entry; ADMIN redirected to `/admin`. Built with Vite + TypeScript, served by nginx on port 3000 in Docker, `/api/` proxied to api-gateway:9090.

## Build & run

Dev: `npm run dev` (Vite, proxies `/api` → 9090, `/ws/v1` → 8084) | Build: `npm run build` (`tsc && vite build → dist/`) | Docker: `docker build -t frontend .` (nginx:alpine, port 80; exposed as 3000 in docker-compose) | E2E: `npm run e2e` (Playwright).

**Test recipe:** `npm test` · node · **NOT RUN** — `node_modules` not present; `npm install` required first · 2026-10-30

## Architecture & structure

**Stack:** React 18.2, TypeScript 5.3, Vite 5.1, react-router-dom 6.22, axios 1.6, @microsoft/fetch-event-source 2.0, vitest 1.3, Playwright 1.60.

Packages: `src/api/` (axios wrappers), `src/context/` (AuthContext, NotificationContext), `src/hooks/` (WebSocket, SSE, idle-timer), `src/pages/` (route components), `src/components/`, `src/types/` (TS interfaces), `src/utils/`, `src/config/`.

**Auth flow:** `AuthContext` reads `localStorage` (`oms-auth-user`) on mount, restores `apiClient.ts` module-level token. Login writes full `AuthUser` (token, userId, username, role). 401 fires `auth:session-expired`; context clears state → redirects to `/`.

**Real-time:** `MarketDataWebSocketService` singleton → `ws[s]://<host>/ws/v1` (nginx bypasses gateway); sends `AUTH` frame, buffers subscriptions until `AUTH_OK`, auto-reconnects 3 s. SSE via `fetchEventSource` at `/api/v1/notifications/stream` (bearer in header).

**Routes:** `/` login; `/dashboard` all roles; `/orders`, `/portfolio`, `/audit`, `/market` non-ADMIN; `/orders/new` TRADER only; `/admin` (role enforced in-page, not on route).

## Security
- Identity: JWT stored in `localStorage` (`oms-auth-user`); axios interceptor sends `Authorization: Bearer <token>`. XSS risk: same-origin script can read the token (POC). [code: src/context/AuthContext.tsx, src/api/apiClient.ts]
- Verified at: src/components/ProtectedRoute.tsx (client-side route guard); backend services perform final JWT validation on every API call.
- Reachable without that check: docker-compose: yes, port 3000 published (docker-compose.yml); k8s: yes, NodePort (k8s/services/frontend-service.yaml) — static assets served without auth.
- Route guard: `ProtectedRoute` checks `AuthContext.user`; unauth → `/`; ADMIN → `/admin`; wrong role → `/dashboard`. [code: src/components/ProtectedRoute.tsx]
- Admin page: `/admin` has no `allowedRoles`; role enforced imperatively via `useEffect`. [code: src/pages/AdminPanelPage.tsx:243]
- WebSocket bypass: `AUTH` frame sent after WS open; nginx proxies `/ws/` direct to marketdata-service:8084, bypassing api-gateway. [code: src/services/websocketService.ts, nginx.conf]
- Nginx headers: `X-Frame-Options: SAMEORIGIN`, `X-Content-Type-Options: nosniff`, `X-XSS-Protection: 1; mode=block`.

## Rules

- Client dropdown shows only `status=ACTIVE`, `tradingEnabled=true`, `kycStatus=VERIFIED` clients. [code: src/pages/OrderEntryPage.tsx]
- Order form 60 s idle timer clears and disables submission on expiry. [code: src/hooks/useIdleTimer.ts]
- Quantity must be whole number; limit price ≤ 2 decimal places; SELL qty ≤ held qty (validated client-side). [code: src/pages/OrderEntryPage.tsx]
- GTD date must be a future weekday non-holiday. [code: src/pages/OrderEntryPage.tsx]
- Risk-rule change reason ≥ 10 characters. [code: src/pages/AdminPanelPage.tsx]
- SOR venues from `SOR_VENUES` list; falls back to `NYSE` if empty. [code: src/config/sorVenues.ts]
- `/orders/new` TRADER only (BRD 12.2 — ADMIN denied server-side; client reflects via `ProtectedRoute`). [decided BRD §12.2; code: src/App.tsx]

## Must never

- Do not store JWT in a URL parameter or query string. [code: src/api/apiClient.ts]
- Do not remove `ProtectedRoute` from authenticated routes without equivalent server-side enforcement. [code: src/components/ProtectedRoute.tsx]
- Do not create a second WebSocket for market data; reuse `marketDataWs` singleton via `useMarketSocket`. [code: src/services/websocketService.ts]
- Do not hardcode credentials or secrets in source code. [decided: CLAUDE.md — Rules for every change]
- Do not change `baseURL` in `apiClient.ts` to a hostname; keep `/api/v1` for nginx proxying. [code: src/api/apiClient.ts, nginx.conf]

## Decisions & trade-offs

- JWT in localStorage: Module-level `authToken` in `apiClient.ts`; standard SPA pattern without BFF. [code: src/api/apiClient.ts]
- WebSocket via nginx (not gateway): Spring Cloud Gateway MVC cannot proxy WebSocket; nginx `location /ws/` goes direct to marketdata-service. [code: nginx.conf]
- SSE via fetch-event-source: Native `EventSource` cannot send custom headers; library sends `Authorization: Bearer`. [code: src/hooks/useNotifications.ts]
- WS singleton: `marketDataWs` reused across hooks; server-side multiplexing. [code: src/services/websocketService.ts]
- SOR venues hardcoded: Five venues in `src/config/sorVenues.ts`; no API call. [code: src/config/sorVenues.ts]

## Reference

- API — 30 backend calls across 7 api/*.ts files
- Data — key interfaces across 6 types/*.ts files
- Integrations — 7 backend services (REST), 1 WebSocket endpoint, 1 SSE endpoint
- Configuration — vite.config.ts dev proxy; nginx.conf upstream targets; no VITE_* env vars
- Tech debt — 4 TODO comments (AdminPanelPage); 2 unimplemented features (user management, risk stub fallback)
- Open questions — 3 items

Open CLAUDE.reference.md only for the section your task needs.
<!-- plumbline:end unit -->
