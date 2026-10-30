<!-- plumbline:template claude-reference v1 -->
# frontend — Reference

<!-- plumbline:begin reference -->
Last updated: 4586741 (2026-10-30, /pl-onboard, claude-reference v1)

## API

All calls go through the axios instance in `src/api/apiClient.ts` (baseURL `/api/v1`) except login, which uses a separate unauthenticated axios instance in `src/api/authApi.ts`.

### authApi.ts
- `POST /auth/login` — `login(username, password)` — authenticates user, returns token + role

### orderApi.ts
- `GET /orders` — `getOrders(params?)` — paginated order list (filters: status, clientId, timeInForce, createdAfter, page, pageSize)
- `GET /orders/{id}` — `getOrder(id)` — single order detail
- `GET /executions/{orderId}` — `getExecutions(orderId)` — fill records for an order
- `POST /orders` — `submitOrder(request)` — submit new order

### marketApi.ts
- `GET /market/hours` — `getMarketHours()` — open/close times and timezone
- `GET /market/instruments` — `getInstruments()` — full instrument list
- `GET /market/instrument/{symbol}` — `getInstrument(symbol)` — single instrument quote
- `GET /market/holidays` — `getHolidays()` — list of market holidays
- `GET /market/config` — `getMarketConfig()` — polling interval, data source
- `GET /market/ticks/history` — `getTickHistory(symbol)` — intraday tick history
- `GET /market/movers` — `getMarketMovers()` — top gainers/losers
- `GET /market/indices` — `getMarketIndices()` — index values (e.g. SPX)
- `GET /market/search` — `searchInstruments(q, limit)` — instrument typeahead (v1)
- `GET /market/instruments/search` — `searchInstrumentsV2(query)` — instrument typeahead (v2, used in order entry)

### riskApi.ts
- `GET /risk/rules` — `getRiskRules()` — list all risk rules
- `PATCH /risk/rules/{ruleCode}` — `patchRiskRule(ruleCode, body)` — submit enable/disable or threshold change (creates a pending-change for checker approval)
- `GET /risk/rules/audit` — `getRiskRuleAudit()` — rule change audit log
- `GET /risk/rules/pending-changes` — `getPendingChanges(status?)` — pending maker/checker items
- `PATCH /risk/rules/pending-changes/{changeId}/approve` — `approveChange(changeId)` — checker approves a change
- `PATCH /risk/rules/pending-changes/{changeId}/reject` — `rejectChange(changeId, rejectionReason)` — checker rejects a change

### auditApi.ts
- `GET /audit/trail` — `getAuditTrail(params?)` — paginated audit records (filters: clientId, page, size)
- `GET /audit/orders/{orderId}` — `getOrderAudit(orderId)` — audit records for a specific order

### clientApi.ts
- `GET /clients/{clientId}` — `getClient(clientId)` — single client profile
- `GET /clients/{clientId}/portfolio` — `getPortfolio(clientId)` — holdings + cash balance
- `GET /clients` — `getClients()` — all clients (used to populate order-entry dropdown)
- `GET /clients/active` — `getActiveClients(limit?)` — top active clients by turnover

### watchlistApi.ts
- `GET /watchlists` — `getWatchlist()` — user's watchlist entries
- `POST /watchlists/{symbol}` — `addToWatchlist(symbol)` — add symbol to watchlist
- `DELETE /watchlists/{symbol}` — `removeFromWatchlist(symbol)` — remove symbol from watchlist

### Calls with no matching endpoint in this frontend
- None found; all calls above correspond to documented backend routes.

---

## Data

Key TypeScript interfaces used across pages and API modules. No browser-side database.

### src/types/order.types.ts
- `OrderSide`: `'BUY' | 'SELL' | 'SHORT_SELL'`
- `OrderType`: `'MARKET' | 'LIMIT'`
- `TimeInForce`: `'DAY' | 'GTD'`
- `OrderStatus`: `'NEW' | 'VALIDATED' | 'RISK_CHECK_PASSED' | 'RISK_REJECTED' | 'ACCEPTED' | 'SENT_TO_EXECUTION' | 'PARTIALLY_FILLED' | 'FILLED' | 'CANCELLED' | 'REJECTED' | 'EXPIRED'`
- `OrderSummary`: id, clientOrderId, clientId, symbol, side, orderType, timeInForce, quantity, limitPrice?, status, expiryDate?, createdAt, filledQuantity?, avgFillPrice?, routingDestination?, rejectionReason?
- `SubmitOrderRequest`: clientOrderId, clientId, symbol, side, orderType, tif, quantity, limitPrice?, gtdDate?, routingDestination?
- `SubmitOrderResponse`: orderId, status, expiryDate?, expiryApplied?
- `Execution`: executionId, orderId, symbol, side, executedQty, executedPrice, fillType, fillValue, remainingQuantity, executionVenue, executedAt

### src/types/client.types.ts
- `Client`: clientId, name, status (`ACTIVE | BLOCKED`), tradingEnabled, kycStatus (`VERIFIED | PENDING | EXPIRED`)
- `Portfolio`: cashBalance (string), holdings (`Holding[]`)
- `Holding`: symbol, quantity, avgPrice, currentPrice, marketValue (all strings)
- `ActiveClient`: clientId, clientName, turnoverToday, availableCash, cashLimitWarning

### src/types/market.types.ts
- `Instrument`: symbol, bidPrice, askPrice, lastPrice, open?, tradable, halted, minQuantity, maxQuantity, dataTimestamp; plus SCRUM-82 fields: change?, changePct?, volume?, dayHigh?, dayLow?, previousClose?, marketStatus?
- `MarketHours`: marketOpen, openTime, closeTime, timezone, nextOpenDate?
- `MarketConfig`: pollingIntervalMs, marketOpen, marketHours (start/end/timezone), dataSource (`WEBSOCKET | POLLING`)
- `TickHistoryResponse`: symbol, from, to, ticks (`TickEntry[]`)
- `TickEntry`: time (ISO LocalDateTime), price, bid, ask, volume
- `MarketMovers`: gainers and losers (`MoverEntry[]` — symbol, percentChange)
- `IndexEntry`: name, currentValue, percentChange
- `InstrumentSearchResult`: symbol, companyName, exchange, lastPrice, tradable, cusip, secNumber

### src/types/risk.types.ts
- `RiskRule`: ruleCode, ruleName, ruleCategory (`FINANCIAL_LIMIT | FAT_FINGER | PRE_TRADE | CLIENT | STATEFUL | REDUNDANT`), description, enabled, thresholdValue, thresholdUnit, action (`REJECT | WARN`), priority
- `PendingChange`: id, ruleCode, changeType (`ENABLE | DISABLE | UPDATE_THRESHOLD`), proposedEnabled, proposedThreshold, currentEnabled, currentThreshold, makerUserId, makerUsername, status (`PENDING | APPROVED | REJECTED`), makerNote, rejectionReason, checkerUserId, submittedAt, reviewedAt

### src/types/audit.types.ts
- `AuditRecord`: id, entityType, entityId, previousState?, newState, actorId?, actorType (`USER | SYSTEM`), eventTimestamp, metadata?

### src/types/notification.types.ts
- `NotificationMessage`: id (client-generated UUID), type (`ORDER_STATE_CHANGED | EXECUTION_COMPLETED | RISK_RESULT`), orderId, clientId, title, message, timestamp (ISO 8601 UTC)

### Local state / in-memory only
- `AuthUser` (in `src/context/AuthContext.tsx`): token, userId, username, role, displayName — persisted to `localStorage` key `oms-auth-user`.
- `TickData` (in `src/hooks/useMarketSocket.ts`): symbol, price, bid, ask, timestamp, previousClose, dayHigh, dayLow, volume, change, changePct — held in React state; lost on unmount.

---

## Integrations

### Backend services called (REST via axios → nginx → api-gateway)
| Service | Base path | Purpose |
|---|---|---|
| auth-service | `/api/v1/auth/` | Login only; no token needed |
| order-service | `/api/v1/orders`, `/api/v1/executions/` | Order CRUD and fill queries |
| marketdata-service | `/api/v1/market/`, `/api/v1/watchlists` | Instrument data, tick history, movers, indices, watchlists |
| risk-service | `/api/v1/risk/` | Rule management and pending-change workflow |
| audit-service | `/api/v1/audit/` | Audit trail queries |
| client-service | `/api/v1/clients/` | Client profiles, portfolios, active client list |
| notification-service | `/api/v1/notifications/stream` | SSE push (order events, executions, risk results) |

### WebSocket — market data ticks
- URL pattern: `ws[s]://<host>/ws/v1` — nginx proxies this directly to `marketdata-service:8084` (bypasses api-gateway).
- Authentication: first frame `{ type: 'AUTH', token: 'Bearer <jwt>' }`. Connection proceeds only after `AUTH_OK` response.
- Messages consumed: `MARKET_DATA_TICK`, `MARKET_OPEN`, `MARKET_CLOSE`, `PING` (replied with `PONG`).
- Subscription protocol: `{ action: 'subscribe', symbol }` and `{ action: 'unsubscribe', symbol }`.
- Reconnect: automatic after 3 seconds on close.
- Singleton: `marketDataWs` in `src/services/websocketService.ts`; all hooks share it.

### SSE — notifications
- URL: `GET /api/v1/notifications/stream` (proxied by api-gateway `SseProxyFilter` to notification-service:8088).
- Library: `@microsoft/fetch-event-source` — required because native `EventSource` cannot set custom headers.
- Auth: `Authorization: Bearer <token>` header.
- Keepalive frames (`:keepalive`) are silently discarded.
- Max 5 toasts in memory at once; oldest dropped when queue overflows.

---

## Configuration

### Development proxy (vite.config.ts)
| Path prefix | Target |
|---|---|
| `/api` | `http://localhost:9090` (api-gateway) |
| `/ws/v1` | `ws://localhost:8084` (marketdata-service, WebSocket) |

### nginx.conf (production / Docker)
| Path | Upstream | Notes |
|---|---|---|
| `/api/` | `http://api-gateway:9090` | All REST calls; Docker internal DNS resolver (`127.0.0.11`) used for lazy resolution |
| `/ws/` | `http://marketdata-service:8084` | WebSocket upgrade; proxy_read_timeout 3600s |
| `/` (fallback) | `dist/index.html` | SPA fallback for client-side routing |

### Environment variables
No `VITE_*` environment variables are declared or read anywhere in the source. The api-gateway host is hardcoded as `api-gateway:9090` in `nginx.conf`; to change it in Kubernetes, override via ConfigMap (noted in `nginx.conf` comment).

### Static configuration (source-controlled)
- `src/config/sorVenues.ts`: `SOR_VENUES = ['NYSE', 'NASDAQ', 'ARCA', 'BATS', 'DARK_POOL']`, `DEFAULT_VENUE = 'NYSE'`
- `src/utils/constants.ts`: `TOAST_DISMISS_MS = 5000`, `TOAST_SUCCESS_NAV_MS = 2500`, `SYMBOL_DEBOUNCE_MS = 400`, `IDLE_TIMER_SECONDS = 60`

---

## Tech debt

**TODO / FIXME count:** 4 items, all in `src/pages/AdminPanelPage.tsx`.

| Location | Comment |
|---|---|
| `AdminPanelPage.tsx` line 284 | `TODO: SCRUM-47 — replace stub fallback in getRiskRules with real GET /api/v1/risk/rules` |
| `AdminPanelPage.tsx` line 292 | `TODO: SCRUM-47 — replace stub fallback in getRiskRuleAudit with real GET /api/v1/risk/rules/audit` |
| `AdminPanelPage.tsx` line 507 | `TODO: SCRUM-47 — replace stub fallback in getRiskRuleAudit with real GET /api/v1/risk/rules/audit` |
| `AdminPanelPage.tsx` line 553 | `TODO: SCRUM-16 — wire Create User to real POST /api/v1/auth/users` |

**Unimplemented features visible in UI:**
- "User Management" card in AdminPanelPage renders a placeholder message "User management API not available in this iteration"; the Create User button shows an error toast.
- No `VITE_*` environment variable mechanism; nginx upstream host is hardcoded, requiring a ConfigMap override for Kubernetes (noted in `nginx.conf` comment but not yet wired).

**Structural observations:**
- No global state library (Redux, Zustand); state is scoped to pages and passed via React context for auth and notifications only. Adding cross-page shared state will require lifting state or adding a library.
- Inline styles dominate (no CSS modules or Tailwind); adding a design-system token layer would require touching every component.
- The `/admin` route has no `allowedRoles` in `ProtectedRoute`; role enforcement relies entirely on the imperative check in `AdminPanelPage`. A non-ADMIN user navigating directly to `/admin` sees a brief flash before being redirected.
- `searchInstruments` (v1 endpoint `/market/search`) and `searchInstrumentsV2` (`/market/instruments/search`) both exist in `marketApi.ts`; only v2 is used in `OrderEntryPage`. The v1 function appears unused.

---

## Open questions

1. **No `VITE_*` env vars:** The api-gateway URL and marketdata-service WS URL are hardcoded in `nginx.conf` and `vite.config.ts`. Is there a plan to make these configurable at image build time or via runtime injection for non-Docker environments (e.g., standalone `npm run dev` against a different gateway host)?

2. **`/admin` route role guard gap:** `ProtectedRoute` on `/admin` has no `allowedRoles` restriction; a non-ADMIN authenticated user who navigates to `/admin` directly will briefly see the page before the in-page `useEffect` redirects them. Should `allowedRoles={['ADMIN']}` be added to the route declaration?

3. **`searchInstruments` (v1) vs `searchInstrumentsV2`:** Both functions exist in `marketApi.ts` but only v2 is used in `OrderEntryPage`. Is v1 (`GET /market/search`) still needed, or can it be removed?
<!-- plumbline:end reference -->
