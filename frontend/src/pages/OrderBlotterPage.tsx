import React, { useCallback, useEffect, useRef, useState } from 'react'
import { getExecutions, GetOrdersParams, getOrder, getOrders } from '../api/orderApi'
import { useNotificationsContext } from '../context/NotificationsContext'
import { getOrderAudit } from '../api/auditApi'
import { getClients } from '../api/clientApi'
import { AuditRecord } from '../types/audit.types'
import { Client } from '../types/client.types'
import { Execution, OrderSide, OrderStatus, OrderSummary } from '../types/order.types'

const ALL_STATUSES: OrderStatus[] = [
  'NEW', 'VALIDATED', 'RISK_CHECK_PASSED', 'RISK_REJECTED',
  'ACCEPTED', 'SENT_TO_EXECUTION', 'PARTIALLY_FILLED',
  'FILLED', 'CANCELLED', 'REJECTED', 'EXPIRED',
]

const TERMINAL_STATES = new Set<OrderStatus>(['RISK_REJECTED', 'FILLED', 'CANCELLED', 'REJECTED', 'EXPIRED'])

const isBlotterVisible = (order: OrderSummary, todayStartMs: number): boolean =>
  new Date(order.createdAt).getTime() >= todayStartMs || !TERMINAL_STATES.has(order.status)

interface StatusConfig { bg: string; color: string; label: string; strikethrough?: boolean }

const STATUS_CONFIG: Record<OrderStatus, StatusConfig> = {
  NEW:               { bg: '#f3f4f6', color: '#6b7280',  label: 'NEW'          },
  VALIDATED:         { bg: '#f3f4f6', color: '#6b7280',  label: 'VALIDATED'    },
  RISK_CHECK_PASSED: { bg: '#dbeafe', color: '#1d4ed8',  label: 'RISK PASSED'  },
  ACCEPTED:          { bg: '#dbeafe', color: '#1d4ed8',  label: 'ACCEPTED'     },
  SENT_TO_EXECUTION: { bg: '#fef3c7', color: '#d97706',  label: 'SENT TO EXEC' },
  PARTIALLY_FILLED:  { bg: '#fed7aa', color: '#ea580c',  label: 'PARTIAL FILL' },
  FILLED:            { bg: '#d1fae5', color: '#059669',  label: 'FILLED'       },
  REJECTED:          { bg: '#fee2e2', color: '#dc2626',  label: 'REJECTED'     },
  RISK_REJECTED:     { bg: '#fee2e2', color: '#dc2626',  label: 'RISK REJECTED'},
  EXPIRED:           { bg: '#f3f4f6', color: '#9ca3af',  label: 'EXPIRED',     strikethrough: true },
  CANCELLED:         { bg: '#f3f4f6', color: '#9ca3af',  label: 'CANCELLED',   strikethrough: true },
}

const stateTimelineDotColor = (state: string): string => {
  if (['FILLED'].includes(state))                      return 'var(--color-success)'
  if (['REJECTED', 'RISK_REJECTED'].includes(state))   return 'var(--color-error)'
  if (['EXPIRED', 'CANCELLED'].includes(state))        return 'var(--color-text-muted)'
  if (['PARTIALLY_FILLED'].includes(state))            return '#ea580c'
  if (['SENT_TO_EXECUTION'].includes(state))           return 'var(--color-warning)'
  return 'var(--color-primary)'
}

// ── Styles ────────────────────────────────────────────────────────────────────
const s = {
  page:     { fontFamily: "'Inter', system-ui, sans-serif", padding: '20px', minHeight: 'calc(100vh - 52px)', background: 'var(--color-bg)' } as React.CSSProperties,
  header:   { display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '16px' } as React.CSSProperties,
  title:    { margin: 0, fontSize: '20px', fontWeight: 700, color: 'var(--color-text-primary)', letterSpacing: '-0.01em' } as React.CSSProperties,
  subtitle: { fontSize: '12px', color: 'var(--color-text-muted)', marginTop: '3px' } as React.CSSProperties,

  toolbar: {
    background: 'var(--color-surface)', borderRadius: 'var(--radius-lg)',
    boxShadow: 'var(--shadow-sm)',
    padding: '12px 16px', marginBottom: '12px',
    display: 'flex', flexWrap: 'wrap' as const, gap: '8px', alignItems: 'center',
  } as React.CSSProperties,

  input:  { padding: '7px 10px', borderRadius: 'var(--radius-md)', border: '1.5px solid var(--color-border)', fontSize: '13px', outline: 'none', background: '#fff', color: 'var(--color-text-primary)' } as React.CSSProperties,
  select: { padding: '7px 10px', borderRadius: 'var(--radius-md)', border: '1.5px solid var(--color-border)', fontSize: '13px', background: '#fff', outline: 'none', cursor: 'pointer', color: 'var(--color-text-primary)' } as React.CSSProperties,

  toggleGrp: { display: 'flex', borderRadius: 'var(--radius-md)', border: '1.5px solid var(--color-border)', overflow: 'hidden' } as React.CSSProperties,
  toggleBtn: (active: boolean, color?: string) => ({
    padding: '6px 11px', fontSize: '12px', fontWeight: 600, cursor: 'pointer',
    border: 'none', background: active ? (color ?? 'var(--color-primary)') : '#f9fafb',
    color: active ? '#fff' : 'var(--color-text-secondary)', transition: 'background 0.15s',
  } as React.CSSProperties),

  tableCard: { background: 'var(--color-surface)', borderRadius: 'var(--radius-lg)', boxShadow: 'var(--shadow-sm)', overflowX: 'auto' as const } as React.CSSProperties,
  table:     { width: '100%', borderCollapse: 'collapse' as const, fontSize: '13px', minWidth: '1100px' } as React.CSSProperties,
  th:        {
    padding: '10px 12px', textAlign: 'left' as const, fontSize: '11px',
    fontWeight: 700, color: 'var(--color-text-secondary)',
    borderBottom: '1px solid var(--color-border-light)',
    background: '#fafafa', whiteSpace: 'nowrap' as const,
    textTransform: 'uppercase' as const, letterSpacing: '0.04em',
    position: 'sticky' as const, top: 0, zIndex: 10,
  } as React.CSSProperties,
  td:  { padding: '10px 12px', borderBottom: '1px solid var(--color-border-light)', verticalAlign: 'middle' as const, whiteSpace: 'nowrap' as const } as React.CSSProperties,
  tdR: { padding: '10px 12px', borderBottom: '1px solid var(--color-border-light)', verticalAlign: 'middle' as const, whiteSpace: 'nowrap' as const, textAlign: 'right' as const } as React.CSSProperties,

  pagination: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '12px', padding: '0 2px' } as React.CSSProperties,
  pgInfo:     { fontSize: '12px', color: 'var(--color-text-secondary)' } as React.CSSProperties,
  pgControls: { display: 'flex', gap: '4px', alignItems: 'center' } as React.CSSProperties,
  pgBtn:      (disabled: boolean) => ({
    padding: '5px 11px', borderRadius: 'var(--radius-md)', border: '1px solid var(--color-border)',
    background: disabled ? '#f9fafb' : '#fff', color: disabled ? 'var(--color-text-muted)' : 'var(--color-text-primary)',
    fontSize: '12px', cursor: disabled ? 'not-allowed' : 'pointer', fontWeight: 500,
  } as React.CSSProperties),

  btn: (variant: 'primary' | 'secondary' | 'ghost') => {
    const map = {
      primary:   { background: 'var(--color-primary)', color: '#fff',                  border: 'none' },
      secondary: { background: '#fff',                 color: 'var(--color-text-primary)', border: '1px solid var(--color-border)' },
      ghost:     { background: 'transparent',          color: 'var(--color-text-muted)',   border: 'none' },
    }
    return {
      padding: '7px 14px', borderRadius: 'var(--radius-md)', fontSize: '13px',
      fontWeight: 600, cursor: 'pointer', transition: 'opacity 0.15s', ...map[variant],
    } as React.CSSProperties
  },

  wsPill: (connected: boolean) => ({
    display: 'inline-flex', alignItems: 'center', gap: '5px',
    padding: '3px 10px', borderRadius: 'var(--radius-pill)', fontSize: '11px', fontWeight: 600,
    background: connected ? 'rgba(16,185,129,0.1)' : 'rgba(156,163,175,0.1)',
    color: connected ? 'var(--color-success)' : 'var(--color-text-muted)',
    border: `1px solid ${connected ? 'rgba(16,185,129,0.3)' : 'var(--color-border)'}`,
  } as React.CSSProperties),

  empty: { padding: '40px', textAlign: 'center' as const, color: 'var(--color-text-muted)', fontSize: '13px' } as React.CSSProperties,

  // Drawer
  overlay: (open: boolean) => ({
    position: 'fixed' as const, inset: 0, background: 'rgba(0,0,0,0.2)',
    zIndex: 199, display: open ? 'block' : 'none',
  }),
  drawer: (open: boolean) => ({
    position: 'fixed' as const, top: '52px', right: 0,
    width: '500px', height: 'calc(100vh - 52px)',
    background: '#fff', boxShadow: '-4px 0 32px rgba(0,0,0,0.12)',
    zIndex: 200, display: 'flex', flexDirection: 'column' as const,
    transform: open ? 'translateX(0)' : 'translateX(100%)',
    transition: 'transform 0.28s cubic-bezier(0.4,0,0.2,1)',
    overflowY: 'hidden' as const,
  }),
  dHeader:  { display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '16px 20px', borderBottom: '1px solid var(--color-border-light)', flexShrink: 0 } as React.CSSProperties,
  dBody:    { overflowY: 'auto' as const, flex: 1, padding: '20px' } as React.CSSProperties,
  dSection: { marginBottom: '22px' } as React.CSSProperties,
  dSTitle:  { fontSize: '11px', fontWeight: 700, color: 'var(--color-text-secondary)', textTransform: 'uppercase' as const, letterSpacing: '0.04em', marginBottom: '10px', paddingBottom: '6px', borderBottom: '1px solid var(--color-border-light)' } as React.CSSProperties,
  kvGrid:   { display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px' } as React.CSSProperties,
  kvItem:   { background: 'var(--color-bg)', borderRadius: 'var(--radius-md)', padding: '8px 10px' } as React.CSSProperties,
  kvLabel:  { fontSize: '10px', color: 'var(--color-text-muted)', fontWeight: 600, textTransform: 'uppercase' as const } as React.CSSProperties,
  kvValue:  { fontSize: '13px', fontWeight: 600, color: 'var(--color-text-primary)', marginTop: '2px' } as React.CSSProperties,
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
      display: 'inline-block', padding: '2px 7px', borderRadius: 'var(--radius-sm)',
      fontSize: '11px', fontWeight: 700, background: cfg.bg, color: cfg.color,
      textDecoration: cfg.strikethrough ? 'line-through' : 'none',
    }}>
      {cfg.label}
    </span>
  )
}

const SideBadge: React.FC<{ side: OrderSide }> = ({ side }) => (
  <span style={{
    display: 'inline-block', padding: '2px 7px', borderRadius: 'var(--radius-sm)',
    fontSize: '11px', fontWeight: 700,
    background: side === 'BUY' ? 'var(--color-buy-bg)' : side === 'SHORT_SELL' ? 'var(--color-warning-bg)' : 'var(--color-sell-bg)',
    color:      side === 'BUY' ? 'var(--color-buy)'    : side === 'SHORT_SELL' ? 'var(--color-warning)'    : 'var(--color-sell)',
  }}>
    {side === 'SHORT_SELL' ? 'Short Sell' : side}
  </span>
)

interface StatusDropdownProps {
  selected: OrderStatus[]
  onToggle: (s: OrderStatus) => void
  onClear:  () => void
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
          padding: '7px 10px', borderRadius: 'var(--radius-md)', border: '1.5px solid var(--color-border)',
          background: selected.length > 0 ? '#f0f4ff' : '#fff',
          fontSize: '13px', cursor: 'pointer',
          display: 'flex', alignItems: 'center', gap: '6px',
          color: selected.length > 0 ? 'var(--color-primary)' : 'var(--color-text-primary)', fontWeight: 500,
        }}
      >
        {selected.length === 0 ? 'All Statuses' : `Status (${selected.length})`}
        <span style={{ fontSize: '10px' }}>▼</span>
      </button>

      {open && (
        <div style={{
          position: 'absolute', top: 'calc(100% + 4px)', left: 0,
          background: '#fff', border: '1px solid var(--color-border)', borderRadius: 'var(--radius-lg)',
          boxShadow: 'var(--shadow-md)', zIndex: 300,
          minWidth: '210px', padding: '6px',
        }}>
          <div
            onClick={onClear}
            style={{ padding: '6px 8px', fontSize: '12px', color: 'var(--color-primary)', cursor: 'pointer', borderRadius: 'var(--radius-sm)', fontWeight: 600, marginBottom: '4px' }}
          >
            Clear all
          </div>
          {ALL_STATUSES.map(status => {
            const cfg     = STATUS_CONFIG[status]
            const checked = selected.includes(status)
            return (
              <div
                key={status}
                onClick={() => onToggle(status)}
                style={{
                  display: 'flex', alignItems: 'center', gap: '8px',
                  padding: '5px 8px', borderRadius: 'var(--radius-sm)', cursor: 'pointer',
                  background: checked ? '#f0f4ff' : 'transparent', marginBottom: '1px',
                }}
              >
                <div style={{
                  width: '14px', height: '14px', borderRadius: '3px',
                  border: `1.5px solid ${checked ? 'var(--color-primary)' : 'var(--color-border)'}`,
                  background: checked ? 'var(--color-primary)' : '#fff',
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
  const [orders,      setOrders]      = useState<OrderSummary[]>([])
  const [loading,     setLoading]     = useState(true)
  const [flashingIds, setFlashingIds] = useState<Set<string>>(new Set())

  const [clients, setClients] = useState<Client[]>([])
  const clientNameMap = new Map(clients.map(c => [c.clientId, c.name]))

  const [searchText,     setSearchText]     = useState('')
  const [filterClientId, setFilterClientId] = useState('')
  const [filterStatuses, setFilterStatuses] = useState<OrderStatus[]>([])
  const [filterTif,      setFilterTif]      = useState<'ALL' | 'DAY' | 'GTD'>('ALL')
  const [filterSide,     setFilterSide]     = useState<'ALL' | 'BUY' | 'SELL' | 'SHORT_SELL'>('ALL')

  const [page,     setPage]     = useState(1)
  const [pageSize, setPageSize] = useState(25)

  const [drawerOpen,    setDrawerOpen]    = useState(false)
  const [selectedOrder, setSelectedOrder] = useState<OrderSummary | null>(null)
  const [drawerFills,   setDrawerFills]   = useState<Execution[]>([])
  const [drawerAudit,   setDrawerAudit]   = useState<AuditRecord[]>([])
  const [drawerLoading, setDrawerLoading] = useState(false)

  const { notifications, sseConnected } = useNotificationsContext()

  const flashRow = useCallback((orderId: string) => {
    setFlashingIds(prev => new Set([...prev, orderId]))
    setTimeout(() => setFlashingIds(prev => { const n = new Set(prev); n.delete(orderId); return n }), 300)
  }, [])

  useEffect(() => {
    getClients().then(setClients).catch(() => {})
  }, [])

  const loadOrders = useCallback(async () => {
    setLoading(true)
    const params: GetOrdersParams = { pageSize: 500 }
    if (filterClientId) params.clientId = filterClientId
    try {
      const result = await getOrders(params)
      const todayStartMs = new Date(new Date().setUTCHours(0, 0, 0, 0)).getTime()
      setOrders(result.orders.filter(o => isBlotterVisible(o, todayStartMs)))
    } finally {
      setLoading(false)
    }
  }, [filterClientId])

  useEffect(() => { loadOrders() }, [loadOrders])

  // Polling fallback — only runs when SSE is not connected
  useEffect(() => {
    if (sseConnected) return
    const id = setInterval(loadOrders, 15_000)
    return () => clearInterval(id)
  }, [sseConnected, loadOrders])

  // SSE-driven updates — upsert a single order on state-change events
  const processedIds = useRef(new Set<string>())
  useEffect(() => {
    const last = notifications[notifications.length - 1]
    if (!last || processedIds.current.has(last.id)) return
    processedIds.current.add(last.id)

    if ((last.type === 'ORDER_STATE_CHANGED' || last.type === 'EXECUTION_COMPLETED') && last.orderId) {
      const targetId = last.orderId
      const todayStartMs = new Date(new Date().setUTCHours(0, 0, 0, 0)).getTime()
      getOrder(targetId)
        .then(updated => {
          setOrders(prev => {
            const idx = prev.findIndex(o => o.id === updated.id)
            const visible = isBlotterVisible(updated, todayStartMs)
            if (idx === -1) {
              return visible ? [updated, ...prev] : prev
            }
            // Prior-day order just reached terminal state — remove it from the blotter
            if (!visible) return prev.filter(o => o.id !== updated.id)
            const next = [...prev]
            next[idx] = updated
            return next
          })
          flashRow(targetId)
        })
        .catch(() => {})
    }
  }, [notifications, flashRow])

  const openDrawer = useCallback(async (order: OrderSummary) => {
    setSelectedOrder(order)
    setDrawerOpen(true)
    setDrawerFills([])
    setDrawerAudit([])
    setDrawerLoading(true)
    try {
      const [fills, audit] = await Promise.all([
        getExecutions(order.id),
        getOrderAudit(order.id),
      ])
      setDrawerFills(fills)
      setDrawerAudit(audit)
    } finally {
      setDrawerLoading(false)
    }
  }, [])

  const closeDrawer = () => { setDrawerOpen(false); setSelectedOrder(null) }

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
    return true
  })

  const totalFiltered = filteredOrders.length
  const totalPages    = Math.max(1, Math.ceil(totalFiltered / pageSize))
  const pagedOrders   = filteredOrders.slice((page - 1) * pageSize, page * pageSize)

  useEffect(() => { setPage(1) }, [searchText, filterStatuses, filterTif, filterSide])

  const handleExportCsv = () => {
    const headers = ['Order ID', 'Client Ref', 'Client', 'Symbol', 'Side', 'Type', 'Qty', 'Limit Price', 'Status', 'Filled Qty', 'Avg Fill Price', 'TIF', 'Route', 'Created At']
    const rows = filteredOrders.map(o => [
      o.id, o.clientOrderId, clientNameMap.get(o.clientId) ?? o.clientId,
      o.symbol, o.side, o.orderType, o.quantity,
      o.limitPrice ?? '', o.status,
      o.filledQuantity ?? '', o.avgFillPrice ?? '',
      o.timeInForce, o.routingDestination ?? '',
      new Date(o.createdAt).toISOString(),
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

  // Drawer side accent
  const drawerAccent = selectedOrder?.side === 'BUY' ? 'var(--color-buy)' : selectedOrder?.side === 'SHORT_SELL' ? 'var(--color-warning)' : 'var(--color-sell)'

  // ── Render ────────────────────────────────────────────────────────────────
  return (
    <div style={s.page}>

      {/* Header */}
      <div style={s.header}>
        <div>
          <h1 style={s.title}>Order Blotter</h1>
          <div style={s.subtitle}>
            {loading ? 'Loading…' : `${totalFiltered} order${totalFiltered !== 1 ? 's' : ''} today`}
            {' · '}{sseConnected ? 'live via SSE' : 'polling every 15s'}
          </div>
        </div>
        <span style={s.wsPill(sseConnected)}>
          <span style={{ width: '6px', height: '6px', borderRadius: '50%', background: sseConnected ? '#10b981' : '#9ca3af', display: 'inline-block' }} />
          {sseConnected ? 'Live' : 'Polling'}
        </span>
      </div>

      {/* Filter Toolbar */}
      <div style={s.toolbar}>
        <input
          style={{ ...s.input, width: '190px' }}
          placeholder="Search ID, symbol, ref…"
          value={searchText}
          onChange={e => setSearchText(e.target.value)}
        />

        <select style={s.select} value={filterClientId} onChange={e => setFilterClientId(e.target.value)}>
          <option value="">All Clients</option>
          {clients.map(c => <option key={c.clientId} value={c.clientId}>{c.name}</option>)}
        </select>

        <StatusDropdown selected={filterStatuses} onToggle={toggleStatus} onClear={() => setFilterStatuses([])} />

        <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
          <span style={{ fontSize: '11px', fontWeight: 600, color: 'var(--color-text-secondary)' }}>TIF</span>
          <div style={s.toggleGrp}>
            {(['ALL', 'DAY', 'GTD'] as const).map(t => (
              <button key={t} style={s.toggleBtn(filterTif === t)} onClick={() => setFilterTif(t)}>{t}</button>
            ))}
          </div>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
          <span style={{ fontSize: '11px', fontWeight: 600, color: 'var(--color-text-secondary)' }}>Side</span>
          <div style={s.toggleGrp}>
            {(['ALL', 'BUY', 'SELL', 'SHORT_SELL'] as const).map(side => (
              <button key={side} style={s.toggleBtn(filterSide === side)} onClick={() => setFilterSide(side)}>{side === 'SHORT_SELL' ? 'SHORT SELL' : side}</button>
            ))}
          </div>
        </div>

        <div style={{ flex: 1 }} />

        <button style={s.btn('secondary')} onClick={handleExportCsv} title="Export current filtered view as CSV">
          ⬇ Export CSV
        </button>
      </div>

      {/* Table */}
      <div style={s.tableCard}>
        <table style={s.table}>
          <thead>
            <tr>
              {['Order ID', 'Client Ref', 'Client', 'Symbol', 'Side', 'Type', 'Qty', 'Limit', 'Status', 'Filled', 'Avg Fill', 'TIF', 'Route', 'Created'].map(h => (
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
                    onMouseEnter={e => {
                      if (!isFlashing && !isSelected)
                        (e.currentTarget as HTMLTableRowElement).style.background = 'var(--color-bg)'
                    }}
                    onMouseLeave={e => {
                      if (!isFlashing && !isSelected)
                        (e.currentTarget as HTMLTableRowElement).style.background = 'transparent'
                    }}
                  >
                    <td style={s.td}>
                      <span style={{ fontFamily: 'monospace', fontSize: '12px', color: 'var(--color-primary)' }}>
                        …{order.id.slice(-8)}
                      </span>
                    </td>
                    <td style={s.td}>
                      <span style={{ fontSize: '12px', color: 'var(--color-text-primary)' }}>{order.clientOrderId}</span>
                    </td>
                    <td style={s.td}>
                      <span style={{ fontSize: '12px', color: 'var(--color-text-secondary)' }}>
                        {clientNameMap.get(order.clientId) ?? order.clientId.slice(-8)}
                      </span>
                    </td>
                    <td style={s.td}>
                      <span style={{ fontWeight: 700, color: 'var(--color-text-primary)' }}>{order.symbol}</span>
                    </td>
                    <td style={s.td}><SideBadge side={order.side} /></td>
                    <td style={s.td}>
                      <span style={{ fontSize: '12px', color: 'var(--color-text-secondary)' }}>{order.orderType}</span>
                    </td>
                    <td style={s.tdR}>{order.quantity.toLocaleString()}</td>
                    <td style={s.tdR}>
                      {order.limitPrice
                        ? fmtUsd(order.limitPrice)
                        : <span style={{ color: 'var(--color-text-muted)' }}>—</span>}
                    </td>
                    <td style={s.td}><StatusBadge status={order.status} /></td>
                    <td style={s.tdR}>
                      {order.filledQuantity != null
                        ? order.filledQuantity.toLocaleString()
                        : <span style={{ color: 'var(--color-text-muted)' }}>—</span>}
                    </td>
                    <td style={s.tdR}>
                      {order.avgFillPrice
                        ? fmtUsd(order.avgFillPrice)
                        : <span style={{ color: 'var(--color-text-muted)' }}>—</span>}
                    </td>
                    <td style={s.td}>
                      <span style={{ fontSize: '11px', color: 'var(--color-text-secondary)' }}>{order.timeInForce}</span>
                    </td>
                    <td style={s.td}>
                      <span style={{ fontSize: '11px', color: 'var(--color-text-secondary)', fontWeight: 600 }}>
                        {order.routingDestination ?? <span style={{ color: 'var(--color-text-muted)' }}>—</span>}
                      </span>
                    </td>
                    <td style={s.td}>
                      <span style={{ fontSize: '11px', color: 'var(--color-text-muted)' }} title={new Date(order.createdAt).toLocaleString()}>
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

      {/* Pagination */}
      <div style={s.pagination}>
        <span style={s.pgInfo}>
          {pagedOrders.length > 0
            ? `Showing ${(page - 1) * pageSize + 1}–${Math.min(page * pageSize, totalFiltered)} of ${totalFiltered}`
            : '0 orders'}
        </span>
        <div style={s.pgControls}>
          <span style={{ fontSize: '12px', color: 'var(--color-text-secondary)', marginRight: '2px' }}>Rows:</span>
          <select
            style={{ ...s.select, padding: '4px 8px', fontSize: '12px' }}
            value={pageSize}
            onChange={e => { setPageSize(Number(e.target.value)); setPage(1) }}
          >
            {[10, 25, 50, 100].map(n => <option key={n} value={n}>{n}</option>)}
          </select>
          <button style={s.pgBtn(page <= 1)}          disabled={page <= 1}          onClick={() => setPage(1)}>«</button>
          <button style={s.pgBtn(page <= 1)}          disabled={page <= 1}          onClick={() => setPage(p => p - 1)}>‹</button>
          <span style={{ padding: '5px 10px', fontSize: '12px', color: 'var(--color-text-primary)', fontWeight: 600 }}>
            {page} / {totalPages}
          </span>
          <button style={s.pgBtn(page >= totalPages)} disabled={page >= totalPages} onClick={() => setPage(p => p + 1)}>›</button>
          <button style={s.pgBtn(page >= totalPages)} disabled={page >= totalPages} onClick={() => setPage(totalPages)}>»</button>
        </div>
      </div>

      {/* Drawer backdrop */}
      <div style={s.overlay(drawerOpen)} onClick={closeDrawer} />

      {/* Order Detail Drawer */}
      <div style={{ ...s.drawer(drawerOpen), borderTop: drawerOpen && selectedOrder ? `3px solid ${drawerAccent}` : 'none' }}>
        {selectedOrder && (
          <>
            <div style={s.dHeader}>
              <div>
                <div style={{ fontWeight: 700, fontSize: '15px', color: 'var(--color-text-primary)' }}>
                  {selectedOrder.symbol} · <SideBadge side={selectedOrder.side} />
                  {' '}{selectedOrder.orderType}
                </div>
                <div style={{ fontSize: '11px', color: 'var(--color-text-muted)', marginTop: '3px', fontFamily: 'monospace' }}>
                  {selectedOrder.id}
                </div>
              </div>
              <button style={s.btn('ghost')} onClick={closeDrawer}>✕</button>
            </div>

            <div style={s.dBody}>

              {/* Order Details */}
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
              </div>

              {drawerLoading ? (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', padding: '8px 0' }}>
                  <div className="skeleton" style={{ height: '20px', width: '100%' }} />
                  <div className="skeleton" style={{ height: '20px', width: '75%' }} />
                  <div className="skeleton" style={{ height: '20px', width: '85%' }} />
                </div>
              ) : (
                <>
                  {/* Fills */}
                  <div style={s.dSection}>
                    <div style={s.dSTitle}>Fills ({drawerFills.length})</div>
                    {drawerFills.length === 0 ? (
                      <div style={{ fontSize: '12px', color: 'var(--color-text-muted)' }}>No fills recorded.</div>
                    ) : (
                      <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '12px' }}>
                        <thead>
                          <tr>
                            {['Qty', 'Price', 'Value', 'Type', 'Venue', 'Time'].map(h => (
                              <th key={h} style={{ padding: '6px 8px', textAlign: 'left' as const, fontSize: '10px', fontWeight: 700, color: 'var(--color-text-secondary)', borderBottom: '1px solid var(--color-border-light)', textTransform: 'uppercase' as const }}>
                                {h}
                              </th>
                            ))}
                          </tr>
                        </thead>
                        <tbody>
                          {drawerFills.map(f => (
                            <tr key={f.executionId}>
                              <td style={{ padding: '6px 8px', borderBottom: '1px solid var(--color-border-light)' }}>{f.executedQty.toLocaleString()}</td>
                              <td style={{ padding: '6px 8px', borderBottom: '1px solid var(--color-border-light)' }}>{fmtUsd(f.executedPrice)}</td>
                              <td style={{ padding: '6px 8px', borderBottom: '1px solid var(--color-border-light)' }}>{fmtUsd(f.fillValue)}</td>
                              <td style={{ padding: '6px 8px', borderBottom: '1px solid var(--color-border-light)' }}>
                                <span style={{
                                  padding: '1px 5px', borderRadius: 'var(--radius-sm)', fontSize: '10px', fontWeight: 700,
                                  background: f.fillType === 'FULL' ? 'var(--color-success-bg)' : '#fed7aa',
                                  color:      f.fillType === 'FULL' ? 'var(--color-success)'    : '#ea580c',
                                }}>
                                  {f.fillType}
                                </span>
                              </td>
                              <td style={{ padding: '6px 8px', borderBottom: '1px solid var(--color-border-light)', color: 'var(--color-text-muted)' }}>{f.executionVenue}</td>
                              <td style={{ padding: '6px 8px', borderBottom: '1px solid var(--color-border-light)', color: 'var(--color-text-muted)', fontSize: '11px' }}>
                                {fmtDateTime(f.executedAt)}
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    )}
                  </div>

                  {/* State Transition Timeline */}
                  <div style={s.dSection}>
                    <div style={s.dSTitle}>State Timeline ({drawerAudit.length})</div>
                    {drawerAudit.length === 0 ? (
                      <div style={{ fontSize: '12px', color: 'var(--color-text-muted)' }}>No audit records found.</div>
                    ) : (
                      drawerAudit.map((rec, i) => (
                        <div key={rec.id} style={{ display: 'flex', gap: '12px' }}>
                          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', flexShrink: 0 }}>
                            <div style={{
                              width: '10px', height: '10px', borderRadius: '50%',
                              background: stateTimelineDotColor(rec.newState),
                              marginTop: '2px',
                            }} />
                            {i < drawerAudit.length - 1 && (
                              <div style={{ width: '1px', flex: 1, background: 'var(--color-border)', minHeight: '18px' }} />
                            )}
                          </div>
                          <div style={{ flex: 1, paddingBottom: '14px' }}>
                            <div style={{ fontSize: '13px', fontWeight: 600, color: 'var(--color-text-primary)' }}>
                              {rec.previousState ? `${rec.previousState} → ${rec.newState}` : rec.newState}
                            </div>
                            <div style={{ fontSize: '11px', color: 'var(--color-text-muted)', marginTop: '2px' }}>
                              {rec.actorType === 'USER'
                                ? `User: ${rec.actorId ?? 'unknown'}`
                                : `System: ${rec.actorId ?? 'OMS'}`}
                              {' · '}{fmtDateTime(rec.eventTimestamp)}
                            </div>
                            {rec.metadata && (
                              <div style={{
                                fontSize: '11px', color: 'var(--color-text-secondary)', marginTop: '4px',
                                fontFamily: 'monospace', background: 'var(--color-bg)',
                                padding: '4px 6px', borderRadius: 'var(--radius-sm)',
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
