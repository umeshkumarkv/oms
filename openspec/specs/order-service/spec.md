# order-service -- Service Specification

## Purpose
order-service is the central order-management microservice in the broker-dealer OMS. It accepts order submissions from clients/traders, performs multi-level validation (syntactic + external: market hours, client KYC, symbol tradability, buying power, holdings sufficiency), persists orders to its own PostgreSQL schema, orchestrates asynchronous risk evaluation via Kafka, and forwards risk-approved orders to the execution layer. It also exposes dashboard aggregations and a system-wide health probe.

## Port
8082 (application.yml:43)

## Database Schema
Schema: `order_schema` (application.yml:21 `spring.flyway.schemas`, application.yml:18 `hibernate.default_schema`)
Flyway migrations location: `classpath:db/migration` (application.yml:22)

Table: `order_schema.orders`
Created in V1__schema.sql; altered by V3__fix_limitprice_precision.sql, V4__rename_gtc_to_gtd.sql, V5__add_last_price_and_order_value.sql, V6__add_short_sell_side.sql, V7__add_routing_destination_to_orders.sql.

Columns (post all migrations applied):

| Column | Type | Constraints / Notes |
|---|---|---|
| id | UUID | PK, NOT NULL, DEFAULT gen_random_uuid() (V1__schema.sql:5) |
| client_order_id | VARCHAR(100) | nullable (V1__schema.sql:6) |
| client_id | UUID | NOT NULL (V1__schema.sql:7) |
| symbol | VARCHAR(20) | NOT NULL (V1__schema.sql:8) |
| side | VARCHAR(10) | NOT NULL, CHECK IN ('BUY','SELL','SHORT_SELL') [V1 had BUY/SELL; V6 extended to include SHORT_SELL] |
| order_type | VARCHAR(10) | NOT NULL, CHECK IN ('MARKET','LIMIT') (V1__schema.sql:10) |
| time_in_force | VARCHAR(10) | NOT NULL, CHECK IN ('DAY','GTD') [V1 had DAY/GTC; V4 renamed GTC to GTD and replaced constraint] |
| quantity | INTEGER | NOT NULL, CHECK quantity > 0 (V1__schema.sql:12) |
| limit_price | NUMERIC(19,4) | nullable [V1 was NUMERIC(18,4); V3 altered to NUMERIC(19,4)] |
| last_price | NUMERIC(19,4) | nullable [added in V5__add_last_price_and_order_value.sql:4] |
| order_value | NUMERIC(19,4) | nullable [added in V5__add_last_price_and_order_value.sql:5] |
| status | VARCHAR(30) | NOT NULL, CHECK IN ('NEW','VALIDATED','RISK_CHECK_PASSED','RISK_REJECTED','ACCEPTED','SENT_TO_EXECUTION','PARTIALLY_FILLED','FILLED','CANCELLED','REJECTED','EXPIRED') (V1__schema.sql:14-17) |
| rejection_reason | VARCHAR(500) | nullable (V1__schema.sql:18) |
| expiry_date | DATE | nullable (V1__schema.sql:19) |
| expiry_applied | VARCHAR(10) | nullable, CHECK IN ('DEFAULT','TRADER') (V1__schema.sql:20) |
| created_at | TIMESTAMPTZ | NOT NULL, DEFAULT now() (V1__schema.sql:21) |
| updated_at | TIMESTAMPTZ | NOT NULL, DEFAULT now() (V1__schema.sql:22) |
| created_by | UUID | NOT NULL (V1__schema.sql:23) |
| routing_destination | VARCHAR(50) | nullable [added in V7__add_routing_destination_to_orders.sql:2] |

Indexes (V1__schema.sql:26-29): idx_orders_client_id (client_id), idx_orders_status (status), idx_orders_symbol (symbol), idx_orders_created_at (created_at).

## Kafka Produced

| Topic | Trigger | Payload fields | file:line |
|---|---|---|---|
| `order.state.changed` | Every order state transition: submission (NEW), risk result, execution result, scheduler expiry | orderId (UUID), clientId (UUID), userId (UUID), fromState (String, null on initial NEW), toState (String), triggeredBy (String: "USER:{userId}" / "SYSTEM:OMS" / "SYSTEM:RISK_ENGINE" / "SYSTEM:SCHEDULER" / "SYSTEM:RECOVERY"), timestamp (ISO 8601 UTC), metadata (Map<String,String>) | OrderEventProducer.java:16,22-26 |
| `risk.evaluation.requested` | On successful order submission after order persisted in NEW state | orderId, clientId, userId, symbol, side, orderType, quantity, limitPrice (String/null), timeInForce, orderValue (String), marginCallActive (Boolean), buyingPower (String), holdingsForSymbol (Integer), kycStatus (String), kycExpiryDate (String), tradingEnabled (Boolean), lastPrice (String), hasCrossingOpenOrder (Boolean), dailyOrderCount (Integer), maxDailyOrders (Integer), avgOrderQty (String), avgOrderValue (String) | OrderEventProducer.java:17,28-30 |
| `order.execution.requested` | On risk-passed state transition; triggered from OrderStateMachineService after ACCEPTED | orderId (UUID), clientId (UUID), symbol (String), side (String), orderType (String), quantity (Integer), limitPrice (BigDecimal), timeInForce (String), orderValue (BigDecimal), lastPrice (BigDecimal) | OrderEventProducer.java:18,34-36 |

## Kafka Consumed

| Topic | Consumer group | Action | file:line |
|---|---|---|---|
| `risk.evaluation.result` | `order-service-risk.evaluation.result-group` | result=PASSED → transitionOnRiskPassed(orderId); result=REJECTED → transitionOnRiskRejected(orderId, rejectionReason, violatedRuleCode) | RiskResultConsumer.java:22-24 |
| `execution.completed` | `order-service-execution.completed-group` | fillType=FULL → FILLED; fillType=PARTIAL → PARTIALLY_FILLED; fillType=REJECTED → REJECTED | RiskResultConsumer.java:40-42 |

## Redis Keys Owned

| Key pattern | TTL | Purpose | file:line |
|---|---|---|---|
| `dashboard:risk-alerts:today` | 15 seconds (Duration.ofSeconds(15)) | Cached risk-alert counts: clientsNearLimit, cashRejectCount, largeOrdersAwaitingReview, pendingVenueAck | DashboardService.java:33-34 |
| `dashboard:summary:today` | 15 seconds (Duration.ofSeconds(15)) | Cached trading summary: turnoverToday, ordersToday, fillRate, buySideNotional, sellSideNotional | DashboardService.java:35-36 |
| `dashboard:status-counts:today` | 15 seconds (Duration.ofSeconds(15)) | Cached per-status order count map for today (all OrderStatus values) | OrderService.java:84-85 |
| `order:state:{orderId}` | No explicit TTL set on write; deleted on terminal state or expiry | Single-order object cache for getOrderById; evicted by OrderStateMachineService and MarketCloseScheduler on state completion | OrderService.java:83,404; OrderStateMachineService.java:187; MarketCloseScheduler.java:114 |

## REST Endpoints Exposed

| Method | Path | Auth/Role | Handler method | file:line |
|---|---|---|---|---|
| POST | /api/v1/orders | No @PreAuthorize; X-User-Id header optional, parsed as UUID if present | submitOrder | OrderController.java:30-44 |
| GET | /api/v1/orders | No @PreAuthorize; supports filters: status, clientId, side, timeInForce, createdAfter, page, pageSize | getOrders | OrderController.java:46-57 |
| GET | /api/v1/orders/status-counts | No @PreAuthorize | getStatusCounts | OrderController.java:59-62 |
| GET | /api/v1/orders/{id} | No @PreAuthorize; service-layer role check: TRADER role may only access their own client's orders | getOrderById | OrderController.java:64-78 |
| GET | /api/v1/dashboard/risk-alerts | No @PreAuthorize | getRiskAlerts | DashboardController.java:23-25 |
| GET | /api/v1/dashboard/summary | No @PreAuthorize | getTradingSummary | DashboardController.java:27-29 |
| GET | /api/v1/dashboard/health | No @PreAuthorize | getSystemHealth | DashboardController.java:31-33 |

## REST Endpoints Called

| Target service | Method | URL / path pattern | Circuit breaker? | file:line |
|---|---|---|---|---|
| client-service | GET | `{CLIENT_SERVICE_URL}/api/v1/clients/{clientId}/portfolio` | Yes: `client-service` CB (sliding-window=10, failure-rate=50%, wait=10s, half-open=3 calls; ignores InsufficientBuyingPowerException) | ClientServiceClient.java:29-31 |
| marketdata-service | GET | `{MARKETDATA_SERVICE_URL}/api/v1/market/instrument/{symbol}` | Yes: `marketdata-service` CB (sliding-window=10, failure-rate=50%, wait=10s) | MarketDataServiceClient.java:29-31 |
| marketdata-service | GET | `{MARKETDATA_SERVICE_URL}/api/v1/market/hours` | Yes: `marketdata-service` CB | MarketDataServiceClient.java:46-48 |
| marketdata-service | GET | `{MARKETDATA_SERVICE_URL}/api/v1/market/holidays` | Yes: `marketdata-service` CB | MarketDataServiceClient.java:59-61 |
| execution-service | GET | `{EXECUTION_SERVICE_URL}/api/v1/executions/{orderId}` | Yes: `execution-service` CB (fallback returns empty list rather than throwing) | ExecutionServiceClient.java:26-28 |
| all services (health) | GET | `{SERVICE_URL}/actuator/health` | No; uses a dedicated `healthRestTemplate` with 500 ms timeout configured in HealthCheckConfig | HealthAggregatorService.java:75-76 |

## Entities

### Order
File: `services/order-service/src/main/java/com/brokerdealer/orderservice/domain/Order.java`
Table: `order_schema.orders`

| Field | Java type | Column name | Constraint annotations | file:line |
|---|---|---|---|---|
| id | UUID | id | @Id, @GeneratedValue(strategy=GenerationType.UUID), @Column(updatable=false, nullable=false) | Order.java:19-22 |
| clientOrderId | String | client_order_id | @Column(name="client_order_id", length=100) nullable | Order.java:24-25 |
| clientId | UUID | client_id | @Column(name="client_id", nullable=false) | Order.java:27-28 |
| symbol | String | symbol | @Column(name="symbol", nullable=false, length=20) | Order.java:30-31 |
| side | OrderSide | side | @Enumerated(EnumType.STRING), @Column(name="side", nullable=false, length=10) | Order.java:33-35 |
| orderType | OrderType | order_type | @Enumerated(EnumType.STRING), @Column(name="order_type", nullable=false, length=10) | Order.java:37-39 |
| timeInForce | TimeInForce | time_in_force | @Enumerated(EnumType.STRING), @Column(name="time_in_force", nullable=false, length=10) | Order.java:41-43 |
| quantity | Integer | quantity | @Column(name="quantity", nullable=false) | Order.java:45-46 |
| limitPrice | BigDecimal | limit_price | @Column(name="limit_price", precision=19, scale=4) nullable | Order.java:48-49 |
| lastPrice | BigDecimal | last_price | @Column(name="last_price", precision=19, scale=4) nullable | Order.java:51-52 |
| orderValue | BigDecimal | order_value | @Column(name="order_value", precision=19, scale=4) nullable | Order.java:54-55 |
| status | OrderStatus | status | @Enumerated(EnumType.STRING), @Column(name="status", nullable=false, length=30) | Order.java:57-59 |
| rejectionReason | String | rejection_reason | @Column(name="rejection_reason", length=500) nullable | Order.java:61-62 |
| expiryDate | LocalDate | expiry_date | @Column(name="expiry_date") nullable | Order.java:64-65 |
| expiryApplied | ExpiryApplied | expiry_applied | @Enumerated(EnumType.STRING), @Column(name="expiry_applied", length=10) nullable | Order.java:67-69 |
| createdAt | Instant | created_at | @Column(name="created_at", nullable=false, updatable=false) | Order.java:71-72 |
| updatedAt | Instant | updated_at | @Column(name="updated_at", nullable=false) | Order.java:74-75 |
| createdBy | UUID | created_by | @Column(name="created_by", nullable=false) | Order.java:77-78 |
| routingDestination | String | routing_destination | @Column(name="routing_destination", length=50) nullable | Order.java:80-81 |

Lifecycle callbacks: @PrePersist sets createdAt (if null) and updatedAt to Instant.now(); @PreUpdate sets updatedAt to Instant.now() (Order.java:83-92).

Enum constants:
- OrderSide: BUY, SELL, SHORT_SELL (OrderSide.java:4-6)
- OrderType: MARKET, LIMIT (OrderType.java:4-5)
- TimeInForce: DAY, GTD (TimeInForce.java:4-5)
- OrderStatus: NEW, VALIDATED, RISK_CHECK_PASSED, RISK_REJECTED, ACCEPTED, SENT_TO_EXECUTION, PARTIALLY_FILLED, FILLED, CANCELLED, REJECTED, EXPIRED (OrderStatus.java:4-14)
- ExpiryApplied: DEFAULT, TRADER (ExpiryApplied.java:4-5)
- ServiceHealthStatus: UP, DEGRADED, DOWN (ServiceHealthStatus.java:4-6)

## What This Service Must NEVER Do

1. [CONVENTION -- INFERRED, CONFIRM WITH TEAM] Write directly to another service's database schema. No cross-schema SQL or JPA entity mappings from other services were found in scan. All inter-service data access uses REST calls via typed client classes.

2. [CONVENTION -- INFERRED, CONFIRM WITH TEAM] Synchronously call risk-service or audit-service for order validation. No RestTemplate or FeignClient reference to risk-service or audit-service exists in src/main (grep Step 3 returned no such files). Risk evaluation is entirely async via the Kafka topic `risk.evaluation.requested`.

3. [CONVENTION -- INFERRED, CONFIRM WITH TEAM] Manage in-flight cash reservation state on the client-service side. Available cash is computed entirely within this service by subtracting the sum of open BUY order values (from order_schema.orders) from the settled cashBalance fetched from client-service (OrderService.java:234-240).

4. [ENFORCED] SHORT_SELL orders bypass the holdings-sufficiency check. The holdings validation block at OrderService.java:219 explicitly guards only `requestedSide == OrderSide.SELL`; SHORT_SELL is excluded by the conditional and proceeds without a holdings check.

5. [CONVENTION -- INFERRED, CONFIRM WITH TEAM] Persist GTC (Good Till Cancelled) orders. V4__rename_gtc_to_gtd.sql migrated the value to GTD and dropped the old constraint; the TimeInForce enum has only DAY and GTD (TimeInForce.java:4-5). No GTC constant exists in application code.

## Key Business Rules

1. GTD default expiry: 180 calendar days from submission date when no expiryDate is provided by the caller (`GTD_DEFAULT_EXPIRY_DAYS = 180`, OrderService.java:75).

2. GTD expiry date validation: must be a future date (strictly after today), must not fall on a Saturday or Sunday, and must not be a US market holiday as returned by marketdata-service `/api/v1/market/holidays` (OrderService.java:377-388).

3. Order type constraints: LIMIT orders require limitPrice > 0 (OrderService.java:356-368); MARKET orders must not include limitPrice (OrderService.java:370-373).

4. BUY buying-power check: availableCash = settled cashBalance (from client-service) minus sum of orderValue of all open BUY orders in order_schema.orders. Order rejected with InsufficientBuyingPowerException if availableCash < orderValue (OrderService.java:234-242).

5. SELL holdings check: availableToSell = settled holdings quantity for the symbol (from client-service) minus sum of open SELL quantity for the same symbol in order_schema.orders. Order rejected with InsufficientHoldingsException if availableToSell <= 0 or availableToSell < requested quantity (OrderService.java:219-228). SHORT_SELL is exempt from this check (see Must Never item 4).

6. Market hours gate: market must report marketOpen=true via marketdata-service `/api/v1/market/hours` before any order is accepted; throws MarketClosedException otherwise (OrderService.java:104-108).

7. Client eligibility gate: client status must be "ACTIVE", tradingEnabled must be true, and kycStatus must be "VERIFIED" with kycExpiryDate strictly after today; throws ClientInactiveException or ClientNotEligibleException otherwise (OrderService.java:116-129).

8. Symbol gate: symbol must be tradable (tradable=true) and not halted (halted=false) per marketdata-service `/api/v1/market/instrument/{symbol}`; throws SymbolNotFoundException or SymbolHaltedException otherwise (OrderService.java:132-139).

9. DAY order expiry: DAY orders in SENT_TO_EXECUTION or PARTIALLY_FILLED status are expired to EXPIRED at 16:00 ET Mon-Fri by MarketCloseScheduler (MarketCloseScheduler.java:56-68). The scheduler skips hardcoded 2026 US market holidays (MarketCloseScheduler.java:31-41).

10. GTD order expiry: GTD orders whose expiryDate equals today are expired to EXPIRED at 16:00 ET Mon-Fri (MarketCloseScheduler.java:70-72). A market-open recovery job at 09:30 ET Mon-Fri also expires any stale GTD orders with expiryDate in the past (MarketCloseScheduler.java:78-96).

11. Dashboard thresholds: clients-near-limit alert fires when total notional >= $800,000; large-order alert fires when active order notional >= $500,000 (application.yml:107-108; `clientsNearLimitThreshold` and `largeOrderNotionalThreshold`).

12. Order status lifecycle: NEW → (risk async) → RISK_CHECK_PASSED or RISK_REJECTED → ACCEPTED → SENT_TO_EXECUTION → PARTIALLY_FILLED | FILLED. Terminal states: RISK_REJECTED, CANCELLED, REJECTED, EXPIRED (OrderStatus.java:4-14). Idempotency guard in risk result consumer skips orders already in {FILLED, CANCELLED, REJECTED, EXPIRED, RISK_REJECTED, SENT_TO_EXECUTION} (OrderStateMachineService.java:33-36).

13. Default routing destination: if no routingDestination is specified in the submission request, it defaults to "NYSE" (OrderService.java:562).

14. Order list page size: capped at a maximum of 200 per request (`MAX_PAGE_SIZE = 200`, OrderService.java:82).
