import React, { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { getExecutions, GetOrdersParams, getOrders } from '../api/orderApi'
import { getOrderAudit } from '../api/auditApi'
import { AuditRecord } from '../types/audit.types'
import { Execution, OrderStatus, OrderSummary } from '../types/order.types'

// ── Seed clients (POC — no GET /clients list yet) ────────────────────────────
const SEED_CLIENTS = [
  { id: '',                                        name: 'All Clients' },
  { id: 'a1000000-0000-0000-0000-000000000001', name: 'John Smith' },
  { id: 'a2000000-0000-0000-0000-000000000002', name: 'Sarah Johnson' },
  { id: 'a3000000-0000-0000-0000-000000000003', name: 'Mike Davis' },
  { id: 'a4000000-0000-0000-0000-000000000004', name: 'Emily Chen' },
  { id: 'a5000000-0000-0000-0000-000000000005', name: 'Robert Wilson' },
]

const CLIENT_NAMES: Record<string, string> = {
  'a1000000-0000-0000-0000-000000000001': 'John Smith',
  'a2000000-0000-0000-0000-000000000002': 'Sarah Johnson',
  'a3000000-0000-0000-0000-000000000003': 'Mike Davis',
  'a4000000-0000-0000-0000-000000000004': 'Emily Chen',
  'a5000000-0000-0000-0000-000000000005': 'Robert Wilson',
}

// Mock current prices for indicative P&L
// TODO: SCRUM-25 — replace with live prices from WebSocket market data context
const MOCK_PRICES: Record<string, number> = {
  AAPL: 178.35, MSFT: 415.00, GOOGL: 172.25, TSLA: 245.70,
  NVDA: 870.00, META: 504.85, AMD: 155.25,   NFLX: 628.70,
}

// TODO: SCRUM-16 — replace with real user from auth context
const CURRENT_USER = { name: 'trader.john', role: 'TRADER' }

const ALL_STATUSES: OrderStatus[] = [
  'NEW', 'VALIDATED', 'RISK_CHECK_PASSED', 'RISK_REJECTED',
  'ACCEPTED', 'SENT_TO_EXECUTION', 'PARTIALLY_FILLED',
  'FILLED', 'CANCELLED', 'REJECTED', 'EXPIRED',
]

interface StatusConfig { bg: string; color: string; label: string; strikethrough?: boolean }

const STATUS_CONFIG: Record<OrderStatus, StatusConfig> = {
  NEW:               { bg: '#f3f4f6', color: '#6b7280', label: 'NEW' },
  VALIDATED:         { bg: '#f3f4f6', color: '#6b7280', label: 'VALIDATED' },
  RISK_CHECK_PASSED: { bg: '#dbeafe', color: '#1d4ed8', label: 'RISK PASSED' },
  ACCEPTED:          { bg: '#dbeafe', color: '#1d4ed8', label: 'ACCEPTED' },
  SENT_TO_EXECUTION: { bg: '#fef3c7', color: '#d97706', label: 'SENT TO EXEC' },
  PARTIALLY_FILLED:  { bg: '#fed7aa', color: '#ea580c', label: 'PARTIAL FILL' },
  FILLED:            { bg: '#d1fae5', color: '#059669', label: 'FILLED' },
  REJECTED:          { bg: '#fee2e2', color: '#dc2626', label: 'REJECTED' },
  RISK_REJECTED:     { bg: '#fee2e2', color: '#dc2626', label: 'RISK REJECTED' },
  EXPIRED:           { bg: '#f3f4f6', color: '#9ca3af', label: 'EXPIRED',   strikethrough: true },
  CANCELLED:         { bg: '#f3f4f6', color: '#9ca3af', label: 'CANCELLED', strikethrough: true },
}

// ── Styles ────────────────────────────────────────────────────────────────────
const s = {
  page:     { fontFamily: 'system-ui, sans-serif', padding: '20px', minHeight: 'calc(100vh - 52px)', background: '#f5f6fa' } as React.CSSProperties,
  header:   { display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '16px' } as React.CSSProperties,
  title:    { margin: 0, fontSize: '20px', fontWeight: 700, color: '#1a2236' } as React.CSSProperties,
  subtitle: { fontSize: '12px', color: '#9ca3af', marginTop: '3px' } as React.CSSProperties,

  toolbar:  {
    background: '#fff', borderRadius: '10px',
    boxShadow: '0 1px 3px rgba(0,0,0,0.08)',
    padding: '12px 16px', marginBottom: '12px',
    display: 'flex', flexWrap: 'wrap' as const, gap: '8px', alignItems: 'center',
  } as React.CSSProperties,

  input:  { padding: '7px 10px', borderRadius: '6px', border: '1px solid #d1d5db', fontSize: '13px', outline: 'none', background: '#fff' } as React.CSSProperties,
  select: { padding: '7px 10px', borderRadius: '6px', border: '1px solid #d1d5db', fontSize: '13px', background: '#fff', outline: 'none', cursor: 'pointer' } as React.CSSProperties,

  toggleGrp: { display: 'flex', borderRadius: '6px', border: '1px solid #d1d5db', overflow: 'hidden' } as React.CSSProperties,
  toggleBtn: (active: boolean, color?: string) => ({
    padding: '6px 11px', fontSize: '12px', fontWeight: 600, cursor: 'pointer',
    border: 'none', background: active ? (color ?? '#6366f1') : '#f9fafb',
    color: active ? '#fff' : '#6b7280', transition: 'background 0.15s',
  } as React.CSSProperties),

  tableCard:  { background: '#fff', borderRadius: '10px', boxShadow: '0 1px 3px rgba(0,0,0,0.08)', overflowX: 'auto' as const } as React.CSSProperties,
  table:      { width: '100%', borderCollapse: 'collapse' as const, fontSize: '13px', minWidth: '1200px' } as React.CSSProperties,
  th:         {
    padding: '10px 12px', textAlign: 'left' as const, fontSize: '11px',
    fontWeight: 700, color: '#6b7280', borderBottom: '1px solid #f0f0f0',
    background: '#fafafa', whiteSpace: 'nowrap' as const,
    textTransform: 'uppercase' as const, letterSpacing: '0.03em',
  } as React.CSSProperties,
  td:         { padding: '10px 12px', borderBottom: '1px solid #f8f8f8', verticalAlign: 'middle' as const, whiteSpace: 'nowrap' as const } as React.CSSProperties,
  tdR:        { padding: '10px 12px', borderBottom: '1px solid #f8f8f8', verticalAlign: 'middle' as const, whiteSpace: 'nowrap' as const, textAlign: 'right' as const } as React.CSSProperties,

  pagination: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '12px', padding: '0 2px' } as React.CSSProperties,
  pgInfo:     { fontSize: '12px', color: '#6b7280' } as React.CSSProperties,
  pgControls: { display: 'flex', gap: '4px', alignItems: 'center' } as React.CSSProperties,
  pgBtn:      (disabled: boolean) => ({
    padding: '5px 11px', borderRadius: '6px', border: '1px solid #d1d5db',
    background: disabled ? '#f9fafb' : '#fff', color: disabled ? '#9ca3af' : '#374151',
    fontSize: '12px', cursor: disabled ? 'not-allowed' : 'pointer', fontWeight: 500,
  } as React.CSSProperties),

  btn: (variant: 'primary' | 'secondary' | 'ghost') => {
    const map = {
      primary:   { background: '#6366f1', color: '#fff',     border: 'none' },
      secondary: { background: '#fff',    color: '#374151',  border: '1px solid #d1d5db' },
      ghost:     { background: 'transparent', color: '#6b7280', border: 'none' },
    }
    return {
      padding: '7px 14px', borderRadius: '6px', fontSize: '13px',
      fontWeight: 600, cursor: 'pointer', transition: 'opacity 0.15s', ...map[variant],
    } as React.CSSProperties
  },

  wsPill: (connected: boolean) => ({
    display: 'inline-flex', alignItems: 'center', gap: '5px',
    padding: '3px 10px', borderRadius: '10px', fontSize: '11px', fontWeight: 600,
    background: connected ? 'rgba(16,185,129,0.1)' : 'rgba(156,163,175,0.1)',
    color: connected ? '#059669' : '#9ca3af',
    border: `1px solid ${connected ? '#10b981' : '#d1d5db'}`,
  } as React.CSSProperties),

  empty:  { padding: '40px', textAlign: 'center' as const, color: '#9ca3af', fontSize: '13px' } as React.CSSProperties,

  // Drawer
  overlay: (open: boolean) => ({
    position: 'fixed' as const, inset: 0, background: 'rgba(0,0,0,0.25)',
    zIndex: 199, display: open ? 'block' : 'none',
  }),
  drawer: (open: boolean) => ({
    position: 'fixed' as const, top: '52px', right: 0,
    width: '500px', height: 'calc(100vh - 52px)',
    background: '#fff', boxShadow: '-4px 0 24px rgba(0,0,0,0.15)',
    zIndex: 200, display: 'flex', flexDirection: 'column' as const,
    transform: open ? 'translateX(0)' : 'translateX(100%)',
    transition: 'transform 0.3s ease', overflowY: 'hidden' as const,
  }),
  dHeader: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '16px 20px', borderBottom: '1px solid #f0f0f0', flexShrink: 0 } as React.CSSProperties,
  dBody:   { overflowY: 'auto' as const, flex: 1, padding: '20px' } as React.CSSProperties,
  dSection:{ marginBottom: '22px' } as React.CSSProperties,
  dSTitle: { fontSize: '11px', fontWeight: 700, color: '#6b7280', textTransform: 'uppercase' as const, letterSpacing: '0.04em', marginBottom: '10px', paddingBottom: '6px', borderBottom: '1px solid #f0f0f0' } as React.CSSProperties,
  kvGrid:  { display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px' } as React.CSSProperties,
  kvItem:  { background: '#f9fafb', borderRadius: '6px', padding: '8px 10px' } as React.CSSProperties,
  kvLabel: { fontSize: '10px', color: '#9ca3af', fontWeight: 600, textTransform: 'uppercase' as const } as React.CSSProperties,
  kvValue: { fontSize: '13px', fontWeight: 600, color: '#1a2236', marginTop: '2px' } as React.CSSProperties,
}

// ── Helpers ───────────────────────────────────────────────────────────────────
const fmtUsd = (v: string | number): string => {
  const n = typeof v === 'string' ? parseFloat(v) : v
  if (isNaN(n)) return '—'
  return n.toLocaleString('en-US', { style: 'currency', currency: 'USD' })
}

const fmtDateTime = (iso: string): string =>
  new Date(iso).toLocaleString('en-US', { month: 'short', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false })

const relTime = (iso: string): string => {
  const mins = Math.floor((Date.now() - new Date(iso).getTime()) / 60000)
  if (mins < 1)  return 'just now'
  if (mins < 60) return `${mins}m ago`
  const hrs = Math.floor(mins / 60)
  if (hrs < 24)  return `${hrs}h ago`
  return `${Math.floor(hrs / 24)}d ago`
}

// ── Sub-components ────────────────────────────────────────────────────────────
const StatusBadge: React.FC<{ status: OrderStatus }> = ({ status }) => {
  const cfg = STATUS_CONFIG[status] ?? { bg: '#f3f4f6', color: '#6b7280', label: status }
  return (
    <span style={{
      display: 'inline-block', padding: '2px 7px', borderRadius: '4px',
      fontSize: '11px', fontWeight: 700, background: cfg.bg, color: cfg.color,
      textDecoration: cfg.strikethrough ? 'line-through' : 'none',
    }}>
      {cfg.label}
    </span>
  )
}

const SideBadge: React.FC<{ side: 'BUY' | 'SELL' }> = ({ side }) => (
  <span style={{
    display: 'inline-block', padding: '2px 7px', borderRadius: '4px',
    fontSize: '11px', fontWeight: 700,
    background: side === 'BUY' ? '#dbeafe' : '#fce7f3',
    color:      side === 'BUY' ? '#1d4ed8' : '#be185d',
  }}>
    {side}
  </span>
)

interface StatusDropdownProps {
  selected: OrderStatus[]
  onToggle: (s: OrderStatus) => void
  onClear: () => void
}

const StatusDropdown: React.FC<StatusDropdownProps> = ({ selected, onToggle, onClear }) => {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [])

  return (
    <div ref={ref} style={{ position: 'relative' }}>
      <button
        onClick={() => setOpen(o => !o)}
        style={{
          padding: '7px 10px', borderRadius: '6px', border: '1px solid #d1d5db',
          background: selected.length > 0 ? '#f0f4ff' : '#fff',
          fontSize: '13px', cursor: 'pointer',
          display: 'flex', alignItems: 'center', gap: '6px',
          color: selected.length > 0 ? '#6366f1' : '#374151', fontWeight: 500,
        }}
      >
        {selected.length === 0 ? 'All Statuses' : `Status (${selected.length})`}
        <span style={{ fontSize: '10px' }}>▼</span>
      </button>

      {open && (
        <div style={{
          position: 'absolute', top: 'calc(100% + 4px)', left: 0,
          background: '#fff', border: '1px solid #d1d5db', borderRadius: '8px',
          boxShadow: '0 4px 16px rgba(0,0,0,0.12)', zIndex: 300,
          minWidth: '210px', padding: '6px',
        }}>
          <div
            onClick={onClear}
            style={{ padding: '6px 8px', fontSize: '12px', color: '#6366f1', cursor: 'pointer', borderRadius: '4px', fontWeight: 600, marginBottom: '4px' }}
          >
            Clear all
          </div>
          {ALL_STATUSES.map(status => {
            const cfg = STATUS_CONFIG[status]
            const checked = selected.includes(status)
            return (
              <div
                key={status}
                onClick={() => onToggle(status)}
                style={{
                  display: 'flex', alignItems: 'center', gap: '8px',
                  padding: '5px 8px', borderRadius: '4px', cursor: 'pointer',
                  background: checked ? '#f0f4ff' : 'transparent', marginBottom: '1px',
                }}
              >
                <div style={{
                  width: '14px', height: '14px', borderRadius: '3px',
                  border: `1.5px solid ${checked ? '#6366f1' : '#d1d5db'}`,
                  background: checked ? '#6366f1' : '#fff',
                  display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0,
                }}>
                  {checked && <span style={{ color: '#fff', fontSize: '10px', lineHeight: 1 }}>✓</span>}
                </div>
                <span style={{
                  display: 'inline-block', padding: '1px 6px', borderRadius: '3px',
                  background: cfg.bg, color: cfg.color, fontSize: '11px', fontWeight: 700,
                }}>
                  {cfg.label}
                </span>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}

// ── Main Component ────────────────────────────────────────────────────────────
const OrderBlotterPage: React.FC = () => {
  const navigate = useNavigate()

  // Orders data
  const [orders,      setOrders]      = useState<OrderSummary[]>([])
  const [loading,     setLoading]     = useState(true)
  const [flashingIds, setFlashingIds] = useState<Set<string>>(new Set())

  // Filters
  const [searchText,     setSearchText]     = useState('')
  const [filterClientId, setFilterClientId] = useState('')
  const [filterStatuses, setFilterStatuses] = useState<OrderStatus[]>([])
  const [filterTif,      setFilterTif]      = useState<'ALL' | 'DAY' | 'GTC'>('ALL')
  const [filterSide,     setFilterSide]     = useState<'ALL' | 'BUY' | 'SELL'>('ALL')
  const [filterDateFrom, setFilterDateFrom] = useState('')
  const [filterDateTo,   setFilterDateTo]   = useState('')

  // Pagination
  const [page,     setPage]     = useState(1)
  const [pageSize, setPageSize] = useState(25)

  // Drawer
  const [drawerOpen,    setDrawerOpen]    = useState(false)
  const [selectedOrder, setSelectedOrder] = useState<OrderSummary | null>(null)
  const [drawerFills,   setDrawerFills]   = useState<Execution[]>([])
  const [drawerAudit,   setDrawerAudit]   = useState<AuditRecord[]>([])
  const [drawerLoading, setDrawerLoading] = useState(false)

  // WebSocket stub — always disconnected until SCRUM-25 is implemented
  // TODO: SCRUM-25 — connect to ws://localhost:8080/ws/v1 and subscribe to
  //                  ORDER_STATUS_CHANGED and FILL_RECEIVED events; set wsConnected=true
  const wsConnected = false

  // ── Flash animation helper ───────────────────────────────────────────────
  const flashRow = useCallback((orderId: string) => {
    setFlashingIds(prev => new Set([...prev, orderId]))
    setTimeout(() => setFlashingIds(prev => { const n = new Set(prev); n.delete(orderId); return n }), 300)
  }, [])

  // ── Load orders ──────────────────────────────────────────────────────────
  const loadOrders = useCallback(async () => {
    setLoading(true)
    const params: GetOrdersParams = {}
    if (filterClientId) params.clientId = filterClientId
    // TODO: SCRUM-44 — replace stub fallback in getOrders with real GET /api/v1/orders
    const result = await getOrders(params)
    setOrders(result.orders)
    setLoading(false)
  }, [filterClientId])

  useEffect(() => { loadOrders() }, [loadOrders])

  // Auto-refresh every 15s when WebSocket is disconnected (stub fallback)
  useEffect(() => {
    if (wsConnected) return
    const id = setInterval(loadOrders, 15_000)
    return () => clearInterval(id)
  }, [wsConnected, loadOrders])

  // Demo flash animation: simulate a WebSocket ORDER_STATUS_CHANGED event after 6s
  // TODO: SCRUM-25 — remove this demo timer once real WebSocket events arrive
  useEffect(() => {
    if (orders.length === 0) return
    const id = setTimeout(() => {
      const target = orders.find(o => o.status === 'SENT_TO_EXECUTION' || o.status === 'VALIDATED')
      if (target) flashRow(target.id)
    }, 6000)
    return () => clearTimeout(id)
  }, [orders, flashRow])

  // ── Open drawer ──────────────────────────────────────────────────────────
  const openDrawer = useCallback(async (order: OrderSummary) => {
    setSelectedOrder(order)
    setDrawerOpen(true)
    setDrawerFills([])
    setDrawerAudit([])
    setDrawerLoading(true)
    // TODO: SCRUM-48 — replace stub fallback in getExecutions with real GET /api/v1/executions/{orderId}
    // TODO: SCRUM-49 — replace stub fallback in getOrderAudit with real GET /api/v1/audit/orders/{id}
    const [fills, audit] = await Promise.all([
      getExecutions(order.id),
      getOrderAudit(order.id),
    ])
    setDrawerFills(fills)
    setDrawerAudit(audit)
    setDrawerLoading(false)
  }, [])

  const closeDrawer = () => { setDrawerOpen(false); setSelectedOrder(null) }

  // ── Client-side filtering ────────────────────────────────────────────────
  const filteredOrders = orders.filter(o => {
    if (searchText) {
      const q = searchText.toLowerCase()
      if (!o.id.toLowerCase().includes(q) &&
          !o.clientOrderId.toLowerCase().includes(q) &&
          !o.symbol.toLowerCase().includes(q)) return false
    }
    if (filterStatuses.length > 0 && !filterStatuses.includes(o.status)) return false
    if (filterTif  !== 'ALL' && o.timeInForce !== filterTif)  return false
    if (filterSide !== 'ALL' && o.side !== filterSide)        return false
    if (filterDateFrom && new Date(o.createdAt) < new Date(filterDateFrom + 'T00:00:00')) return false
    if (filterDateTo   && new Date(o.createdAt) > new Date(filterDateTo   + 'T23:59:59')) return false
    return true
  })

  const totalFiltered = filteredOrders.length
  const totalPages    = Math.max(1, Math.ceil(totalFiltered / pageSize))
  const pagedOrders   = filteredOrders.slice((page - 1) * pageSize, page * pageSize)

  // Reset to page 1 on filter change
  useEffect(() => { setPage(1) }, [searchText, filterStatuses, filterTif, filterSide, filterDateFrom, filterDateTo])

  // ── Indicative P&L ───────────────────────────────────────────────────────
  const calcPnl = (order: OrderSummary): number | null => {
    if (!order.filledQuantity || !order.avgFillPrice) return null
    const currentPrice = MOCK_PRICES[order.symbol]
    if (currentPrice == null) return null
    const avg = parseFloat(order.avgFillPrice)
    if (isNaN(avg)) return null
    const raw = order.filledQuantity * (currentPrice - avg)
    // For SELL: P&L is inverted (we sold at avg, current price is opportunity cost)
    return order.side === 'SELL' ? -raw : raw
  }

  // ── Export CSV ───────────────────────────────────────────────────────────
  const handleExportCsv = () => {
    const headers = ['Order ID', 'Client Ref', 'Client', 'Symbol', 'Side', 'Type', 'Qty', 'Limit Price', 'Status', 'Filled Qty', 'Avg Fill Price', 'TIF', 'Created At']
    const rows = filteredOrders.map(o => [
      o.id, o.clientOrderId, CLIENT_NAMES[o.clientId] ?? o.clientId,
      o.symbol, o.side, o.orderType, o.quantity,
      o.limitPrice ?? '', o.status,
      o.filledQuantity ?? '', o.avgFillPrice ?? '',
      o.timeInForce, new Date(o.createdAt).toISOString(),
    ])
    const csv = [headers, ...rows].map(r => r.map(v => `"${v}"`).join(',')).join('\n')
    const blob = new Blob([csv], { type: 'text/csv' })
    const url  = URL.createObjectURL(blob)
    const a    = document.createElement('a')
    a.href = url
    a.download = `order-blotter-${new Date().toISOString().slice(0, 10)}.csv`
    a.click()
    URL.revokeObjectURL(url)
  }

  const toggleStatus = (status: OrderStatus) =>
    setFilterStatuses(prev => prev.includes(status) ? prev.filter(s => s !== status) : [...prev, status])

  // ── Render ────────────────────────────────────────────────────────────────
  return (
    <div style={s.page}>

      {/* ── Header ── */}
      <div style={s.header}>
        <div>
          <h1 style={s.title}>Order Blotter</h1>
          <div style={s.subtitle}>
            {loading ? 'Loading…' : `${totalFiltered} order${totalFiltered !== 1 ? 's' : ''}`}
            {' · '}auto-refresh every 15s
          </div>
        </div>
        <div style={{ display: 'flex', gap: '10px', alignItems: 'center' }}>
          <span style={s.wsPill(wsConnected)}>
            <span style={{ width: '6px', height: '6px', borderRadius: '50%', background: wsConnected ? '#10b981' : '#9ca3af', display: 'inline-block' }} />
            {wsConnected ? 'Live' : 'Polling'}
          </span>
          {/* New Order — TRADER only (TODO: SCRUM-16 hide for VIEWER role) */}
          {CURRENT_USER.role === 'TRADER' && (
            <button style={s.btn('primary')} onClick={() => navigate('/orders/new')}>
              + New Order
            </button>
          )}
        </div>
      </div>

      {/* ── Filter Toolbar ── */}
      <div style={s.toolbar}>
        {/* Free-text search */}
        <input
          style={{ ...s.input, width: '190px' }}
          placeholder="Search ID, symbol, ref…"
          value={searchText}
          onChange={e => setSearchText(e.target.value)}
        />

        {/* Client dropdown */}
        <select style={s.select} value={filterClientId} onChange={e => setFilterClientId(e.target.value)}>
          {SEED_CLIENTS.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
        </select>

        {/* Status multi-select */}
        <StatusDropdown selected={filterStatuses} onToggle={toggleStatus} onClear={() => setFilterStatuses([])} />

        {/* TIF toggle */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
          <span style={{ fontSize: '11px', fontWeight: 600, color: '#6b7280' }}>TIF</span>
          <div style={s.toggleGrp}>
            {(['ALL', 'DAY', 'GTC'] as const).map(t => (
              <button key={t} style={s.toggleBtn(filterTif === t)} onClick={() => setFilterTif(t)}>{t}</button>
            ))}
          </div>
        </div>

        {/* Side toggle */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
          <span style={{ fontSize: '11px', fontWeight: 600, color: '#6b7280' }}>Side</span>
          <div style={s.toggleGrp}>
            {(['ALL', 'BUY', 'SELL'] as const).map(side => (
              <button key={side} style={s.toggleBtn(filterSide === side)} onClick={() => setFilterSide(side)}>{side}</button>
            ))}
          </div>
        </div>

        {/* Date range */}
        <input style={{ ...s.input, width: '130px' }} type="date" value={filterDateFrom} title="From date"
          onChange={e => setFilterDateFrom(e.target.value)} />
        <span style={{ fontSize: '12px', color: '#9ca3af' }}>–</span>
        <input style={{ ...s.input, width: '130px' }} type="date" value={filterDateTo} title="To date"
          onChange={e => setFilterDateTo(e.target.value)} />

        <div style={{ flex: 1 }} />

        {/* Export CSV */}
        <button style={s.btn('secondary')} onClick={handleExportCsv} title="Export current filtered view as CSV">
          ⬇ Export CSV
        </button>
      </div>

      {/* ── Table ── */}
      <div style={s.tableCard}>
        <table style={s.table}>
          <thead>
            <tr>
              {['Order ID', 'Client Ref', 'Client', 'Symbol', 'Side', 'Type', 'Qty', 'Limit', 'Status', 'Filled', 'Avg Fill', 'P&L', 'TIF', 'Created'].map(h => (
                <th key={h} style={s.th}>{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr><td colSpan={14} style={s.empty}>Loading orders…</td></tr>
            ) : pagedOrders.length === 0 ? (
              <tr><td colSpan={14} style={s.empty}>No orders match the current filters.</td></tr>
            ) : (
              pagedOrders.map(order => {
                const pnl        = calcPnl(order)
                const isFlashing = flashingIds.has(order.id)
                const isSelected = selectedOrder?.id === order.id
                return (
                  <tr
                    key={order.id}
                    onClick={() => openDrawer(order)}
                    style={{
                      cursor: 'pointer',
                      background: isFlashing ? '#fef9c3' : isSelected ? '#f0f7ff' : 'transparent',
                      transition: 'background 0.3s',
                    }}
                    onMouseEnter={e => { if (!isFlashing && !isSelected) (e.currentTarget as HTMLTableRowElement).style.background = '#f9fafb' }}
                    onMouseLeave={e => { if (!isFlashing && !isSelected) (e.currentTarget as HTMLTableRowElement).style.background = 'transparent' }}
                  >
                    <td style={s.td}>
                      <span style={{ fontFamily: 'monospace', fontSize: '12px', color: '#6366f1' }}>
                        …{order.id.slice(-8)}
                      </span>
                    </td>
                    <td style={s.td}>
                      <span style={{ fontSize: '12px', color: '#374151' }}>{order.clientOrderId}</span>
                    </td>
                    <td style={s.td}>
                      <span style={{ fontSize: '12px', color: '#6b7280' }}>{CLIENT_NAMES[order.clientId] ?? '—'}</span>
                    </td>
                    <td style={s.td}>
                      <span style={{ fontWeight: 700, color: '#1a2236' }}>{order.symbol}</span>
                    </td>
                    <td style={s.td}><SideBadge side={order.side} /></td>
                    <td style={s.td}>
                      <span style={{ fontSize: '12px', color: '#374151' }}>{order.orderType}</span>
                    </td>
                    <td style={s.tdR}>{order.quantity.toLocaleString()}</td>
                    <td style={s.tdR}>
                      {order.limitPrice
                        ? fmtUsd(order.limitPrice)
                        : <span style={{ color: '#9ca3af' }}>—</span>}
                    </td>
                    <td style={s.td}><StatusBadge status={order.status} /></td>
                    <td style={s.tdR}>
                      {order.filledQuantity != null
                        ? order.filledQuantity.toLocaleString()
                        : <span style={{ color: '#9ca3af' }}>—</span>}
                    </td>
                    <td style={s.tdR}>
                      {order.avgFillPrice
                        ? fmtUsd(order.avgFillPrice)
                        : <span style={{ color: '#9ca3af' }}>—</span>}
                    </td>
                    <td style={s.tdR}>
                      {pnl != null
                        ? <span style={{ fontWeight: 600, color: pnl >= 0 ? '#059669' : '#dc2626' }}>
                            {pnl >= 0 ? '+' : ''}{fmtUsd(pnl)}
                          </span>
                        : <span style={{ color: '#9ca3af' }}>—</span>}
                    </td>
                    <td style={s.td}>
                      <span style={{ fontSize: '11px', color: '#6b7280' }}>{order.timeInForce}</span>
                    </td>
                    <td style={s.td}>
                      <span style={{ fontSize: '11px', color: '#9ca3af' }} title={new Date(order.createdAt).toLocaleString()}>
                        {relTime(order.createdAt)}
                      </span>
                    </td>
                  </tr>
                )
              })
            )}
          </tbody>
        </table>
      </div>

      {/* ── Pagination ── */}
      <div style={s.pagination}>
        <span style={s.pgInfo}>
          {pagedOrders.length > 0
            ? `Showing ${(page - 1) * pageSize + 1}–${Math.min(page * pageSize, totalFiltered)} of ${totalFiltered}`
            : '0 orders'}
        </span>
        <div style={s.pgControls}>
          <span style={{ fontSize: '12px', color: '#6b7280', marginRight: '2px' }}>Rows:</span>
          <select
            style={{ ...s.select, padding: '4px 8px', fontSize: '12px' }}
            value={pageSize}
            onChange={e => { setPageSize(Number(e.target.value)); setPage(1) }}
          >
            {[10, 25, 50, 100].map(n => <option key={n} value={n}>{n}</option>)}
          </select>
          <button style={s.pgBtn(page <= 1)}        disabled={page <= 1}        onClick={() => setPage(1)}>«</button>
          <button style={s.pgBtn(page <= 1)}        disabled={page <= 1}        onClick={() => setPage(p => p - 1)}>‹</button>
          <span style={{ padding: '5px 10px', fontSize: '12px', color: '#374151', fontWeight: 600 }}>
            {page} / {totalPages}
          </span>
          <button style={s.pgBtn(page >= totalPages)} disabled={page >= totalPages} onClick={() => setPage(p => p + 1)}>›</button>
          <button style={s.pgBtn(page >= totalPages)} disabled={page >= totalPages} onClick={() => setPage(totalPages)}>»</button>
        </div>
      </div>

      {/* ── Drawer backdrop ── */}
      <div style={s.overlay(drawerOpen)} onClick={closeDrawer} />

      {/* ── Order Detail Drawer ── */}
      <div style={s.drawer(drawerOpen)}>
        {selectedOrder && (
          <>
            {/* Drawer header */}
            <div style={s.dHeader}>
              <div>
                <div style={{ fontWeight: 700, fontSize: '15px', color: '#1a2236' }}>
                  {selectedOrder.symbol} · <SideBadge side={selectedOrder.side} />
                  {' '}{selectedOrder.orderType}
                </div>
                <div style={{ fontSize: '11px', color: '#9ca3af', marginTop: '3px', fontFamily: 'monospace' }}>
                  {selectedOrder.id}
                </div>
              </div>
              <button style={s.btn('ghost')} onClick={closeDrawer}>✕</button>
            </div>

            <div style={s.dBody}>

              {/* ── Order Details ── */}
              <div style={s.dSection}>
                <div style={s.dSTitle}>Order Details</div>
                <div style={s.kvGrid}>
                  {([
                    { label: 'Status',      value: <StatusBadge status={selectedOrder.status} /> },
                    { label: 'Symbol',      value: selectedOrder.symbol },
                    { label: 'Side',        value: <SideBadge side={selectedOrder.side} /> },
                    { label: 'Type',        value: selectedOrder.orderType },
                    { label: 'Quantity',    value: selectedOrder.quantity.toLocaleString() },
                    { label: 'Limit Price', value: selectedOrder.limitPrice ? fmtUsd(selectedOrder.limitPrice) : 'Market' },
                    { label: 'TIF',         value: selectedOrder.timeInForce },
                    { label: 'Expiry',      value: selectedOrder.expiryDate ? new Date(selectedOrder.expiryDate).toLocaleDateString() : '—' },
                    { label: 'Filled Qty',  value: selectedOrder.filledQuantity?.toLocaleString() ?? '—' },
                    { label: 'Avg Fill',    value: selectedOrder.avgFillPrice ? fmtUsd(selectedOrder.avgFillPrice) : '—' },
                    { label: 'Client Ref',  value: selectedOrder.clientOrderId },
                    { label: 'Created',     value: fmtDateTime(selectedOrder.createdAt) },
                  ] as { label: string; value: React.ReactNode }[]).map(({ label, value }) => (
                    <div key={label} style={s.kvItem}>
                      <div style={s.kvLabel}>{label}</div>
                      <div style={s.kvValue}>{value}</div>
                    </div>
                  ))}
                </div>

                {/* Indicative P&L banner */}
                {(() => {
                  const pnl = calcPnl(selectedOrder)
                  if (pnl == null) return null
                  const positive = pnl >= 0
                  return (
                    <div style={{
                      marginTop: '10px', padding: '10px 14px', borderRadius: '6px',
                      background: positive ? '#d1fae5' : '#fee2e2',
                      display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                    }}>
                      <span style={{ fontSize: '12px', fontWeight: 600, color: positive ? '#059669' : '#dc2626' }}>
                        Indicative P&L
                      </span>
                      <span style={{ fontSize: '15px', fontWeight: 700, color: positive ? '#059669' : '#dc2626' }}>
                        {positive ? '+' : ''}{fmtUsd(pnl)}
                      </span>
                    </div>
                  )
                })()}
              </div>

              {drawerLoading ? (
                <div style={{ textAlign: 'center', color: '#9ca3af', fontSize: '13px', padding: '24px' }}>
                  Loading fills and audit trail…
                </div>
              ) : (
                <>
                  {/* ── Fills ── */}
                  <div style={s.dSection}>
                    <div style={s.dSTitle}>
                      Fills ({drawerFills.length})
                      {/* TODO: SCRUM-48 — replace stub with GET /api/v1/executions/{orderId} */}
                    </div>
                    {drawerFills.length === 0 ? (
                      <div style={{ fontSize: '12px', color: '#9ca3af' }}>No fills recorded.</div>
                    ) : (
                      <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '12px' }}>
                        <thead>
                          <tr>
                            {['Qty', 'Price', 'Value', 'Type', 'Venue', 'Time'].map(h => (
                              <th key={h} style={{ padding: '6px 8px', textAlign: 'left' as const, fontSize: '10px', fontWeight: 700, color: '#6b7280', borderBottom: '1px solid #f0f0f0', textTransform: 'uppercase' as const }}>
                                {h}
                              </th>
                            ))}
                          </tr>
                        </thead>
                        <tbody>
                          {drawerFills.map(f => (
                            <tr key={f.executionId}>
                              <td style={{ padding: '6px 8px', borderBottom: '1px solid #f8f8f8' }}>{f.executedQty.toLocaleString()}</td>
                              <td style={{ padding: '6px 8px', borderBottom: '1px solid #f8f8f8' }}>{fmtUsd(f.executedPrice)}</td>
                              <td style={{ padding: '6px 8px', borderBottom: '1px solid #f8f8f8' }}>{fmtUsd(f.fillValue)}</td>
                              <td style={{ padding: '6px 8px', borderBottom: '1px solid #f8f8f8' }}>
                                <span style={{
                                  padding: '1px 5px', borderRadius: '3px', fontSize: '10px', fontWeight: 700,
                                  background: f.fillType === 'FULL' ? '#d1fae5' : '#fed7aa',
                                  color:      f.fillType === 'FULL' ? '#059669' : '#ea580c',
                                }}>
                                  {f.fillType}
                                </span>
                              </td>
                              <td style={{ padding: '6px 8px', borderBottom: '1px solid #f8f8f8', color: '#9ca3af' }}>{f.executionVenue}</td>
                              <td style={{ padding: '6px 8px', borderBottom: '1px solid #f8f8f8', color: '#9ca3af', fontSize: '11px' }}>
                                {fmtDateTime(f.executedAt)}
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    )}
                  </div>

                  {/* ── State Transition Timeline ── */}
                  <div style={s.dSection}>
                    <div style={s.dSTitle}>
                      State Timeline ({drawerAudit.length})
                      {/* TODO: SCRUM-49 — replace stub with GET /api/v1/audit/orders/{id} */}
                    </div>
                    {drawerAudit.length === 0 ? (
                      <div style={{ fontSize: '12px', color: '#9ca3af' }}>No audit records found.</div>
                    ) : (
                      drawerAudit.map((rec, i) => (
                        <div key={rec.id} style={{ display: 'flex', gap: '12px' }}>
                          {/* Connector */}
                          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', flexShrink: 0 }}>
                            <div style={{ width: '10px', height: '10px', borderRadius: '50%', background: '#6366f1', marginTop: '2px' }} />
                            {i < drawerAudit.length - 1 && (
                              <div style={{ width: '1px', flex: 1, background: '#e5e7eb', minHeight: '18px' }} />
                            )}
                          </div>
                          {/* Content */}
                          <div style={{ flex: 1, paddingBottom: '14px' }}>
                            <div style={{ fontSize: '13px', fontWeight: 600, color: '#1a2236' }}>
                              {rec.previousState ? `${rec.previousState} → ${rec.newState}` : rec.newState}
                            </div>
                            <div style={{ fontSize: '11px', color: '#9ca3af', marginTop: '2px' }}>
                              {rec.actorType === 'USER'
                                ? `User: ${rec.actorId ?? 'unknown'}`
                                : `System: ${rec.actorId ?? 'OMS'}`}
                              {' · '}{fmtDateTime(rec.eventTimestamp)}
                            </div>
                            {rec.metadata && (
                              <div style={{
                                fontSize: '11px', color: '#6b7280', marginTop: '4px',
                                fontFamily: 'monospace', background: '#f9fafb',
                                padding: '4px 6px', borderRadius: '4px',
                              }}>
                                {rec.metadata}
                              </div>
                            )}
                          </div>
                        </div>
                      ))
                    )}
                  </div>
                </>
              )}

            </div>
          </>
        )}
      </div>

    </div>
  )
}

export default OrderBlotterPage
