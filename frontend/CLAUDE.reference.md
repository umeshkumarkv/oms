<!-- plumbline:template claude-reference v1 -->
# Frontend — reference

<!-- plumbline:begin reference -->
Last updated: 4369a80 (2026-10-28, /pl-onboard, claude-reference v1)

## API

### React Router routes (`src/App.tsx`)

| Path | Component | Role restriction |
|---|---|---|
| `/` | `LoginPage` | None (public) |
| `/dashboard` | `DashboardPage` | Any authenticated |
| `/orders` | `OrderBlotterPage` | TRADER, OPERATIONS, VIEWER |
| `/portfolio` | `PortfolioPage` | TRADER, OPERATIONS, VIEWER |
| `/audit` | `AuditTrailPage` | TRADER, OPERATIONS, VIEWER |
| `/market` | `MarketDataPage` | TRADER, OPERATIONS, VIEWER |
| `/orders/new` | `OrderEntryPage` | TRADER only |
| `/admin` | `AdminPanelPage` | Any authenticated (ADMIN redirected here by ProtectedRoute) |

### Backend API calls made by the frontend

All REST calls use base URL `/api/v1` (proxied to `:9090` in dev, nginx upstream in Docker).

#### authApi.ts — `POST /api/v1/auth/login`
| Method | Path | File | Purpose |
|---|---|---|---|
| POST | `/auth/login` | `authApi.ts` | Authenticate user; returns JWT + role |

#### orderApi.ts
| Method | Path | File | Purpose |
|---|---|---|---|
| GET | `/orders` | `orderApi.ts` | Paginated order list with optional filters |
| GET | `/orders/:id` | `orderApi.ts` | Single order detail |
| GET | `/executions/:orderId` | `orderApi.ts` | Executions for an order |
| POST | `/orders` | `orderApi.ts` | Submit new order |

#### auditApi.ts
| Method | Path | File | Purpose |
|---|---|---|---|
| GET | `/audit/trail` | `auditApi.ts` | Paginated audit trail with optional clientId |
| GET | `/audit/orders/:orderId` | `auditApi.ts` | Audit records for a specific order |

#### clientApi.ts
| Method | Path | File | Purpose |
|---|---|---|---|
| GET | `/clients` | `clientApi.ts` | All clients list |
| GET | `/clients/:clientId` | `clientApi.ts` | Single client detail |
| GET | `/clients/:clientId/portfolio` | `clientApi.ts` | Portfolio (cash + holdings) for a client |
| GET | `/clients/active?limit=N` | `clientApi.ts` | Active clients ranked by today's turnover |

#### marketApi.ts
| Method | Path | File | Purpose |
|---|---|---|---|
| GET | `/market/hours` | `marketApi.ts` | Current market hours status |
| GET | `/market/instruments` | `marketApi.ts` | All tradable instruments |
| GET | `/market/instrument/:symbol` | `marketApi.ts` | Single instrument detail |
| GET | `/market/holidays` | `marketApi.ts` | Market holiday calendar |
| GET | `/market/config` | `marketApi.ts` | Market configuration |
| GET | `/market/ticks/history?symbol=X` | `marketApi.ts` | Tick history for a symbol |
| GET | `/market/movers` | `marketApi.ts` | Top market movers |
| GET | `/market/indices` | `marketApi.ts` | Market index entries |
| GET | `/market/search?q=X&limit=N` | `marketApi.ts` | Instrument search (v1) |
| GET | `/market/instruments/search?query=X` | `marketApi.ts` | Instrument search (v2) — unverified: no backend endpoint confirmed |

#### riskApi.ts
| Method | Path | File | Purpose |
|---|---|---|---|
| GET | `/risk/rules` | `riskApi.ts` | All risk rules (stub fallback active — SCRUM-47) |
| PATCH | `/risk/rules/:ruleCode` | `riskApi.ts` | Toggle enabled or update threshold (maker step) |
| GET | `/risk/rules/audit` | `riskApi.ts` | Risk rule change audit log (stub fallback — SCRUM-47) |
| GET | `/risk/rules/pending-changes[?status=X]` | `riskApi.ts` | Pending checker-approval changes |
| PATCH | `/risk/rules/pending-changes/:changeId/approve` | `riskApi.ts` | Approve a pending change |
| PATCH | `/risk/rules/pending-changes/:changeId/reject` | `riskApi.ts` | Reject a pending change with reason |

#### watchlistApi.ts
| Method | Path | File | Purpose |
|---|---|---|---|
| GET | `/watchlists` | `watchlistApi.ts` | User's watchlist entries |
| POST | `/watchlists/:symbol` | `watchlistApi.ts` | Add symbol to watchlist |
| DELETE | `/watchlists/:symbol` | `watchlistApi.ts` | Remove symbol from watchlist |

#### SSE stream (useNotifications.ts)
| Type | Path | File | Purpose |
|---|---|---|---|
| SSE | `/api/v1/notifications/stream` | `useNotifications.ts` | Real-time order/risk notifications pushed to user |

#### WebSocket (websocketService.ts)
| Type | Path | File | Purpose |
|---|---|---|---|
| WS | `ws[s]://<host>/ws/v1` | `websocketService.ts` | Real-time price ticks and market open/close events |

### Calls with no confirmed matching backend endpoint

| Call | File | Note |
|---|---|---|
| GET `/market/instruments/search` | `marketApi.ts` | `searchInstrumentsV2` — endpoint not confirmed in reviewed backend docs |
| POST `/api/v1/auth/users` | `AdminPanelPage.tsx` | Create User — UI button exists but call is not wired (SCRUM-16) |

## Data

The frontend holds no persistent database. All state is in-memory (React state) or in `localStorage` for session persistence.

### localStorage keys

| Key | Content | File |
|---|---|---|
| `oms-auth-user` | Full `AuthUser` JSON object (`token`, `userId`, `username`, `role`, `displayName`) | `src/context/AuthContext.tsx` |
| `oms-sidebar-collapsed` | `"true"` or `"false"` | `src/App.tsx` |

### TypeScript domain types (no DB schema)

| File | Key types |
|---|---|
| `types/order.types.ts` | `OrderSummary`, `SubmitOrderRequest`, `SubmitOrderResponse`, `Execution`; enums: `OrderSide`, `OrderType`, `TimeInForce`, `OrderStatus`, `FillType` |
| `types/client.types.ts` | `Client` (`clientId`, `name`, `status: ClientStatus`, `tradingEnabled`, `kycStatus`), `Portfolio`, `Holding`, `ActiveClient`, `FillActivity` |
| `types/market.types.ts` | `Instrument`, `InstrumentSearchResult`, `MarketHours`, `MarketConfig`, `Holiday`, `MarketMovers`, `IndexEntry`, `TickHistoryResponse` |
| `types/risk.types.ts` | `RiskRule`, `PatchRiskRuleRequest`, `RiskRuleAuditRecord`, `PendingChange`; enums: `RuleAction`, `RuleCategory`, `PendingChangeStatus`, `PendingChangeType` |
| `types/audit.types.ts` | `AuditRecord` |
| `types/notification.types.ts` | `NotificationMessage` |
| `services/websocketService.ts` | `PriceTick`, `MarketStatusEvent` (inline interfaces) |

## Integrations

| Target | Protocol | URL / path | File | Notes |
|---|---|---|---|---|
| API Gateway / backend | REST over HTTP | `/api/v1/*` | All `api/` modules | Proxied to `:9090` in dev; nginx upstream in Docker |
| Market data service | WebSocket | `ws[s]://<host>/ws/v1` | `services/websocketService.ts` | Proxied to `:8084` in dev; auth via first WS message |
| Notification service | SSE | `/api/v1/notifications/stream` | `hooks/useNotifications.ts` | Bearer token in header; auto-reconnect via fetch-event-source |

## Configuration

### Vite dev proxy (`vite.config.ts`)

| Pattern | Target | Note |
|---|---|---|
| `/api` | `http://localhost:9090` | All REST API calls |
| `/ws/v1` | `ws://localhost:8084` | WebSocket upgrade |

### Runtime constants (`src/utils/constants.ts`)

| Key | Value | Used in |
|---|---|---|
| `TOAST_DISMISS_MS` | `5000` | OrderEntryPage error/warning toast auto-dismiss |
| `TOAST_SUCCESS_NAV_MS` | `2500` | OrderEntryPage success toast before navigate |
| `SYMBOL_DEBOUNCE_MS` | `400` | OrderEntryPage symbol lookup debounce |
| `IDLE_TIMER_SECONDS` | `60` | OrderEntryPage idle timeout |

### SOR venues (`src/config/sorVenues.ts`)

| Key | Value |
|---|---|
| `SOR_VENUES` | `['NYSE', 'NASDAQ', 'ARCA', 'BATS', 'DARK_POOL']` |
| `DEFAULT_VENUE` | `'NYSE'` |

No environment variables are read at runtime by the SPA itself. Secrets (JWT) arrive as API
responses and are stored in `localStorage`; no `.env` values are baked into the build beyond
the proxy targets above.

## Tech debt

**TODO/FIXME count: 4 items in 1 file**

| File | Count | Items |
|---|---|---|
| `src/pages/AdminPanelPage.tsx` | 4 | SCRUM-47 (×3): stub fallback for `GET /risk/rules` and `GET /risk/rules/audit`; SCRUM-16 (×1): Create User button not wired to `POST /api/v1/auth/users` |

**Test failures (at commit 4369a80):** 4 failing tests across 11 of 26 test files. No test
coverage for `src/pages/PortfolioPage.tsx`, `src/pages/AuditTrailPage.tsx`, or
`src/services/websocketService.ts` unit-level.

**`searchInstrumentsV2`** (`GET /market/instruments/search`) in `marketApi.ts` is an undocumented
v2 search variant — no backend endpoint has been confirmed for it.

## Open questions

1. Which backend service (market-data service or API gateway) owns `GET /market/instruments/search`
   (`searchInstrumentsV2`)? Confirmation needed before the call is used in production.
2. What are the 4 failing unit tests? They need to be identified and either fixed or marked
   as known failures with a ticket.
3. Is `POST /api/v1/auth/users` (SCRUM-16) planned for a future iteration, or has the scope
   changed?
4. The `ADMIN` role route guard in `AdminPanelPage.tsx` has a comment "only enforce when a JWT
   is present (auth-service stub issues no token yet)" — is the auth stub still in use in any
   environment?
5. No environment-variable injection (e.g., `VITE_API_BASE_URL`) is used; the proxy targets are
   hard-coded in `vite.config.ts`. Will those need to be parameterised for multi-environment
   deployments?

— Plumbline 2.8.7 · /pl-onboard · claude-reference v1 · commit 4369a80 · judge pending · AI-drafted, posted after SME review
<!-- plumbline:end reference -->
