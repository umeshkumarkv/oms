import React, { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Briefcase, ClipboardList, FilePlus, Radio, TrendingUp } from 'lucide-react'
import { getOrders } from '../api/orderApi'
import { getMarketMovers, getMarketIndices } from '../api/marketApi'
import { getActiveClients } from '../api/clientApi'
import { OrderSummary } from '../types/order.types'
import { MarketMovers, IndexEntry } from '../types/market.types'
import { ActiveClient } from '../types/client.types'
import ActiveClientsPanel from '../components/ActiveClientsPanel'

// ── Constants ────────────────────────────────────────────────────────────────

const REFRESH_MS          = 30_000
const MOVERS_REFRESH_MS   = 30_000
const INDICES_REFRESH_MS  = 60_000
const CLIENTS_REFRESH_MS  = 30_000

// ── Helpers ───────────────────────────────────────────────────────────────────

const isExpiringSoon = (order: OrderSummary): boolean => {
  if (!order.expiryDate) return false
  const diff = new Date(order.expiryDate).getTime() - Date.now()
  return diff > 0 && diff <= 2 * 24 * 60 * 60 * 1000
}

const relativeTime = (iso: string): string => {
  const diff = Date.now() - new Date(iso).getTime()
  const s = Math.floor(diff / 1000)
  if (s < 60)  return `${s}s ago`
  const m = Math.floor(s / 60)
  if (m < 60)  return `${m}m ago`
  const h = Math.floor(m / 60)
  return `${h}h ago`
}

const formatDate = (iso: string): string => {
  try {
    return new Date(iso).toLocaleDateString('en-US', {
      month: 'short', day: 'numeric', year: 'numeric',
      timeZone: 'America/New_York',
    })
  } catch { return iso }
}

const formatPct = (pct: string): string => {
  const n = parseFloat(pct)
  return (n >= 0 ? '+' : '') + pct + '%'
}

const pctColor = (pct: string): string =>
  parseFloat(pct) >= 0 ? 'var(--color-success)' : 'var(--color-error)'

// ── Styles ────────────────────────────────────────────────────────────────────

const s = {
  page:          { fontFamily: "'Inter', system-ui, sans-serif", padding: '24px', minHeight: 'calc(100vh - 52px)', background: 'var(--color-bg)' } as React.CSSProperties,
  header:        { display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '20px' } as React.CSSProperties,
  title:         { margin: 0, fontSize: '20px', fontWeight: 700, color: 'var(--color-text-primary)', letterSpacing: '-0.01em' } as React.CSSProperties,
  headerRight:   { display: 'flex', alignItems: 'center', gap: '12px' } as React.CSSProperties,
  refreshNote:   { fontSize: '11px', color: 'var(--color-text-muted)' } as React.CSSProperties,

  // Metric cards
  cards:     { display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '16px', marginBottom: '24px' } as React.CSSProperties,
  card:      (accent: string) => ({
    background: 'var(--color-surface)', borderRadius: 'var(--radius-lg)', padding: '20px',
    boxShadow: 'var(--shadow-sm)', borderLeft: `4px solid ${accent}`,
  } as React.CSSProperties),
  cardLabel: {
    fontSize: '11px', color: 'var(--color-text-secondary)', fontWeight: 600,
    textTransform: 'uppercase' as const, letterSpacing: '0.05em', marginBottom: '8px',
  },
  cardValue: (color?: string) => ({
    fontSize: '30px', fontWeight: 700, color: color ?? 'var(--color-text-primary)',
    fontVariantNumeric: 'tabular-nums',
  } as React.CSSProperties),
  cardFooter: { fontSize: '11px', color: 'var(--color-text-muted)', marginTop: '4px' } as React.CSSProperties,

  // Two-col grid
  grid2: { display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px', marginBottom: '24px' } as React.CSSProperties,

  // Section card
  section:       { background: 'var(--color-surface)', borderRadius: 'var(--radius-lg)', boxShadow: 'var(--shadow-sm)', overflow: 'hidden' } as React.CSSProperties,
  sectionHeader: { padding: '14px 20px', borderBottom: '1px solid var(--color-border-light)', display: 'flex', alignItems: 'center', justifyContent: 'space-between' } as React.CSSProperties,
  sectionTitle:  { margin: 0, fontSize: '14px', fontWeight: 600, color: 'var(--color-text-primary)' } as React.CSSProperties,
  sectionBody:   { padding: '0' } as React.CSSProperties,
  emptyMsg:      { padding: '36px 24px', textAlign: 'center' as const, color: 'var(--color-text-muted)', fontSize: '13px' } as React.CSSProperties,

  // Activity row
  actRow:  { display: 'flex', alignItems: 'flex-start', gap: '10px', padding: '12px 20px', borderBottom: '1px solid var(--color-border-light)' } as React.CSSProperties,
  actDot:  (color: string) => ({ width: '8px', height: '8px', borderRadius: '50%', background: color, marginTop: '5px', flexShrink: 0 } as React.CSSProperties),
  actText: { flex: 1, fontSize: '13px', color: 'var(--color-text-primary)' } as React.CSSProperties,
  actTime: { fontSize: '11px', color: 'var(--color-text-muted)', whiteSpace: 'nowrap' as const } as React.CSSProperties,

  // GTD row
  gtdRow:     { display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '12px 20px', borderBottom: '1px solid var(--color-border-light)', fontSize: '13px' } as React.CSSProperties,
  sidePill:   (side: string) => ({
    display: 'inline-block', padding: '2px 7px', borderRadius: 'var(--radius-sm)', fontSize: '11px', fontWeight: 700,
    background: side === 'BUY' ? 'var(--color-buy-bg)' : 'var(--color-sell-bg)',
    color:      side === 'BUY' ? 'var(--color-buy)'    : 'var(--color-sell)',
  } as React.CSSProperties),
  expiryWarn: { fontSize: '11px', color: 'var(--color-warning)', fontWeight: 600 } as React.CSSProperties,

  // Quick actions — 2×2 grid
  actions:     { display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px' } as React.CSSProperties,
  actionBtn:   {
    padding: '16px 10px', borderRadius: 'var(--radius-lg)', border: '1px solid var(--color-border)',
    background: 'var(--color-surface)', cursor: 'pointer', textAlign: 'center' as const,
    boxShadow: 'var(--shadow-sm)', transition: 'box-shadow 0.15s, border-color 0.15s',
    display: 'flex', flexDirection: 'column' as const, alignItems: 'center', gap: '8px',
  },
  actionIcon:  { color: 'var(--color-primary)' } as React.CSSProperties,
  actionLabel: { fontSize: '12px', fontWeight: 600, color: 'var(--color-text-primary)' } as React.CSSProperties,
  rightCol:    { display: 'flex', flexDirection: 'column' as const, gap: '16px' },

  // Market intelligence tiles
  moverColHead: { fontSize: '11px', fontWeight: 700, textTransform: 'uppercase' as const, letterSpacing: '0.05em', color: 'var(--color-text-secondary)', padding: '10px 20px', borderBottom: '1px solid var(--color-border-light)' } as React.CSSProperties,
  moverRow:     { display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '10px 20px', borderBottom: '1px solid var(--color-border-light)' } as React.CSSProperties,
  moverSymbol:  { fontSize: '13px', fontWeight: 600, color: 'var(--color-text-primary)' } as React.CSSProperties,
  indexRow:     { display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '12px 20px', borderBottom: '1px solid var(--color-border-light)' } as React.CSSProperties,
  indexName:    { fontSize: '13px', fontWeight: 600, color: 'var(--color-text-primary)' } as React.CSSProperties,
  indexVal:     { fontSize: '12px', color: 'var(--color-text-secondary)', fontVariantNumeric: 'tabular-nums' as const } as React.CSSProperties,
}

// ── Types ─────────────────────────────────────────────────────────────────────

interface Metrics {
  openCount:       number
  filledCount:     number
  expiredCount:    number
  gtdCount:        number
  gtdExpiringSoon: OrderSummary[]
}

interface ActivityEvent {
  id:        string
  color:     string
  message:   string
  timestamp: string
}

// ── Component ─────────────────────────────────────────────────────────────────

const DashboardPage: React.FC = () => {
  const navigate = useNavigate()

  const [loading,       setLoading]       = useState(true)
  const [metrics,       setMetrics]       = useState<Metrics | null>(null)
  const [lastRefreshed, setLastRefreshed] = useState<Date>(new Date())
  const [activityEvents] = useState<ActivityEvent[]>([])

  const intervalRef         = useRef<ReturnType<typeof setInterval> | null>(null)
  const moversIntervalRef   = useRef<ReturnType<typeof setInterval> | null>(null)
  const indicesIntervalRef  = useRef<ReturnType<typeof setInterval> | null>(null)
  const clientsIntervalRef  = useRef<ReturnType<typeof setInterval> | null>(null)

  const [movers,         setMovers]         = useState<MarketMovers | null>(null)
  const [moversError,    setMoversError]    = useState(false)
  const [indices,        setIndices]        = useState<IndexEntry[] | null>(null)
  const [indicesError,   setIndicesError]   = useState(false)
  const [activeClients,  setActiveClients]  = useState<ActiveClient[] | null>(null)
  const [activeClientsError, setActiveClientsError] = useState(false)

  const loadData = useCallback(async (showLoading = true) => {
    if (showLoading) setLoading(true)
    try {
      const todayStart = new Date(new Date().setUTCHours(0, 0, 0, 0)).toISOString()
      const [openPage, filledPage, expiredPage, gtdPage] = await Promise.all([
        getOrders({ status: 'OPEN' }),
        getOrders({ status: 'FILLED', createdAfter: todayStart }),
        getOrders({ status: 'EXPIRED', createdAfter: todayStart }),
        getOrders({ timeInForce: 'GTD', status: 'OPEN' }),
      ])
      const gtdExpiringSoon = (gtdPage.orders ?? []).filter(isExpiringSoon)
      setMetrics({
        openCount:       openPage.totalCount,
        filledCount:     filledPage.totalCount,
        expiredCount:    expiredPage.totalCount,
        gtdCount:        gtdPage.totalCount,
        gtdExpiringSoon,
      })
    } finally {
      setLoading(false)
      setLastRefreshed(new Date())
    }
  }, [])

  const loadMovers = useCallback(async () => {
    try {
      const data = await getMarketMovers()
      setMovers(data)
      setMoversError(false)
    } catch {
      setMoversError(true)
      setMovers(null)
    }
  }, [])

  const loadIndices = useCallback(async () => {
    try {
      const data = await getMarketIndices()
      setIndices(data)
      setIndicesError(false)
    } catch {
      setIndicesError(true)
      setIndices([])
    }
  }, [])

  const loadActiveClients = useCallback(async () => {
    try {
      const data = await getActiveClients(10)
      setActiveClients(data)
      setActiveClientsError(false)
    } catch {
      setActiveClientsError(true)
      setActiveClients([])
    }
  }, [])

  useEffect(() => {
    loadData(true)
    intervalRef.current = setInterval(() => loadData(false), REFRESH_MS)
    return () => { if (intervalRef.current) clearInterval(intervalRef.current) }
  }, [loadData])

  useEffect(() => {
    loadMovers()
    moversIntervalRef.current = setInterval(loadMovers, MOVERS_REFRESH_MS)
    return () => { if (moversIntervalRef.current) clearInterval(moversIntervalRef.current) }
  }, [loadMovers])

  useEffect(() => {
    loadIndices()
    indicesIntervalRef.current = setInterval(loadIndices, INDICES_REFRESH_MS)
    return () => { if (indicesIntervalRef.current) clearInterval(indicesIntervalRef.current) }
  }, [loadIndices])

  useEffect(() => {
    loadActiveClients()
    clientsIntervalRef.current = setInterval(loadActiveClients, CLIENTS_REFRESH_MS)
    return () => { if (clientsIntervalRef.current) clearInterval(clientsIntervalRef.current) }
  }, [loadActiveClients])

  // Quick actions config with Lucide icons
  const quickActions = [
    { icon: <FilePlus size={24} strokeWidth={1.5} />,     label: 'New Order',    path: '/orders/new' },
    { icon: <ClipboardList size={24} strokeWidth={1.5} />, label: 'View Blotter', path: '/orders'     },
    { icon: <Briefcase size={24} strokeWidth={1.5} />,    label: 'Portfolio',    path: '/portfolio'  },
    { icon: <TrendingUp size={24} strokeWidth={1.5} />,   label: 'Market Data',  path: '/market'     },
  ]

  // ── Render ──────────────────────────────────────────────────────────────────

  return (
    <div style={s.page}>

      {/* Header */}
      <div style={s.header}>
        <h1 style={s.title}>Dashboard</h1>
        <div style={s.headerRight}>
          <span style={s.refreshNote}>
            Auto-refresh 30s · Last: {lastRefreshed.toLocaleTimeString()}
          </span>
        </div>
      </div>

      {/* ── Metric Cards ── */}
      <div style={s.cards}>

        <div style={s.card('#6366f1')} className="card-hover">
          <div style={s.cardLabel}>Open Orders</div>
          {loading
            ? <div className="skeleton" style={{ height: '36px', width: '64px' }} />
            : <div style={s.cardValue()}>{metrics?.openCount ?? 0}</div>
          }
          <div style={s.cardFooter}>Active in market</div>
        </div>

        <div style={s.card('#10b981')} className="card-hover">
          <div style={s.cardLabel}>Filled Today</div>
          {loading
            ? <div className="skeleton" style={{ height: '36px', width: '64px' }} />
            : <div style={s.cardValue('var(--color-success)')}>{metrics?.filledCount ?? 0}</div>
          }
          <div style={s.cardFooter}>Executed</div>
        </div>

        <div style={s.card('#f59e0b')} className="card-hover">
          <div style={s.cardLabel}>Expired Today</div>
          {loading
            ? <div className="skeleton" style={{ height: '36px', width: '64px' }} />
            : <div style={s.cardValue('var(--color-warning)')}>{metrics?.expiredCount ?? 0}</div>
          }
          <div style={s.cardFooter}>Past expiry</div>
        </div>

        <div style={s.card('#3b82f6')} className="card-hover">
          <div style={s.cardLabel}>GTD Active</div>
          {loading
            ? <div className="skeleton" style={{ height: '36px', width: '64px' }} />
            : <div style={s.cardValue('var(--color-buy)')}>{metrics?.gtdCount ?? 0}</div>
          }
          <div style={s.cardFooter}>Good-till-date</div>
        </div>

      </div>

      {/* Two-column layout */}
      <div style={s.grid2}>

        {/* Left — Recent Activity */}
        <div style={s.section}>
          <div style={s.sectionHeader}>
            <h2 style={s.sectionTitle}>Recent Activity</h2>
            <span style={s.refreshNote}>Live events</span>
          </div>
          <div style={s.sectionBody}>
            {activityEvents.length === 0 ? (
              <div style={s.emptyMsg}>
                <Radio size={32} style={{ color: 'var(--color-text-muted)', marginBottom: '10px' }} />
                <div style={{ marginBottom: '6px' }}>Live feed connects when WebSocket is active</div>
                <div style={{
                  display: 'inline-block', fontSize: '10px', fontWeight: 600,
                  padding: '2px 8px', borderRadius: 'var(--radius-pill)',
                  background: 'var(--color-primary-light)', color: 'var(--color-primary)',
                }}>
                  Coming in SCRUM-25
                </div>
              </div>
            ) : (
              activityEvents.slice(0, 5).map((ev) => (
                <div key={ev.id} style={s.actRow}>
                  <div style={s.actDot(ev.color)} />
                  <div style={s.actText}>{ev.message}</div>
                  <div style={s.actTime}>{relativeTime(ev.timestamp)}</div>
                </div>
              ))
            )}
          </div>
        </div>

        {/* Right — Quick Actions + GTD Expiring Soon */}
        <div style={s.rightCol}>

          {/* Quick Actions */}
          <div style={s.section}>
            <div style={s.sectionHeader}>
              <h2 style={s.sectionTitle}>Quick Actions</h2>
            </div>
            <div style={{ padding: '16px' }}>
              <div style={s.actions}>
                {quickActions.map(({ icon, label, path }) => (
                  <button
                    key={path}
                    style={s.actionBtn}
                    onClick={() => navigate(path)}
                    onMouseEnter={e => {
                      const b = e.currentTarget as HTMLButtonElement
                      b.style.boxShadow = 'var(--shadow-md)'
                      b.style.borderColor = 'var(--color-primary)'
                    }}
                    onMouseLeave={e => {
                      const b = e.currentTarget as HTMLButtonElement
                      b.style.boxShadow = 'var(--shadow-sm)'
                      b.style.borderColor = 'var(--color-border)'
                    }}
                  >
                    <div style={s.actionIcon}>{icon}</div>
                    <div style={s.actionLabel}>{label}</div>
                  </button>
                ))}
              </div>
            </div>
          </div>

          {/* GTD Expiring Soon */}
          <div style={s.section}>
            <div style={s.sectionHeader}>
              <h2 style={s.sectionTitle}>GTD Expiring Soon</h2>
              <span style={s.refreshNote}>Within 2 days</span>
            </div>
            <div style={s.sectionBody}>
              {loading ? (
                <div style={{ padding: '16px 20px', display: 'flex', flexDirection: 'column', gap: '8px' }}>
                  <div className="skeleton" style={{ height: '20px', width: '100%' }} />
                  <div className="skeleton" style={{ height: '20px', width: '80%' }} />
                </div>
              ) : !metrics || metrics.gtdExpiringSoon.length === 0 ? (
                <div style={s.emptyMsg}>No GTD orders expiring within 2 days</div>
              ) : (
                metrics.gtdExpiringSoon.map((order) => (
                  <div key={order.id} style={s.gtdRow}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                      <span style={{ fontWeight: 600, color: 'var(--color-text-primary)', fontSize: '13px' }}>{order.symbol}</span>
                      <span style={s.sidePill(order.side)}>{order.side}</span>
                      <span style={{ color: 'var(--color-text-secondary)', fontSize: '12px' }}>{order.quantity.toLocaleString()} shares</span>
                    </div>
                    <div style={s.expiryWarn}>
                      Expires {order.expiryDate ? formatDate(order.expiryDate) : '—'}
                    </div>
                  </div>
                ))
              )}
            </div>
          </div>

        </div>
      </div>

      {/* ── Market Intelligence Tiles ── */}
      <div style={s.grid2}>

        {/* Gainers & Losers */}
        <div style={s.section}>
          <div style={s.sectionHeader}>
            <h2 style={s.sectionTitle}>Gainers &amp; Losers</h2>
            <span style={s.refreshNote}>30s refresh</span>
          </div>
          <div style={s.sectionBody}>
            {moversError ? (
              <div style={s.emptyMsg}>--</div>
            ) : movers === null ? (
              <div style={{ padding: '16px 20px', display: 'flex', flexDirection: 'column', gap: '8px' }}>
                <div className="skeleton" style={{ height: '16px', width: '100%' }} />
                <div className="skeleton" style={{ height: '16px', width: '75%' }} />
                <div className="skeleton" style={{ height: '16px', width: '85%' }} />
              </div>
            ) : (
              <div style={{ display: 'flex' }}>
                {/* Gainers column */}
                <div style={{ flex: 1, borderRight: '1px solid var(--color-border-light)' }}>
                  <div style={s.moverColHead}>Gainers</div>
                  {movers.gainers.length === 0
                    ? <div style={s.emptyMsg}>--</div>
                    : movers.gainers.map((m) => (
                      <div key={m.symbol} style={s.moverRow}>
                        <span style={s.moverSymbol}>{m.symbol}</span>
                        <span style={{ fontSize: '13px', fontWeight: 700, color: pctColor(m.percentChange), fontVariantNumeric: 'tabular-nums' }}>
                          {formatPct(m.percentChange)}
                        </span>
                      </div>
                    ))
                  }
                </div>
                {/* Losers column */}
                <div style={{ flex: 1 }}>
                  <div style={s.moverColHead}>Losers</div>
                  {movers.losers.length === 0
                    ? <div style={s.emptyMsg}>--</div>
                    : movers.losers.map((m) => (
                      <div key={m.symbol} style={s.moverRow}>
                        <span style={s.moverSymbol}>{m.symbol}</span>
                        <span style={{ fontSize: '13px', fontWeight: 700, color: pctColor(m.percentChange), fontVariantNumeric: 'tabular-nums' }}>
                          {formatPct(m.percentChange)}
                        </span>
                      </div>
                    ))
                  }
                </div>
              </div>
            )}
          </div>
        </div>

        {/* Index Snapshot */}
        <div style={s.section}>
          <div style={s.sectionHeader}>
            <h2 style={s.sectionTitle}>Index Snapshot</h2>
            <span style={s.refreshNote}>60s refresh</span>
          </div>
          <div style={s.sectionBody}>
            {indicesError ? (
              <div style={s.emptyMsg}>--</div>
            ) : indices === null ? (
              <div style={{ padding: '16px 20px', display: 'flex', flexDirection: 'column', gap: '8px' }}>
                <div className="skeleton" style={{ height: '16px', width: '100%' }} />
                <div className="skeleton" style={{ height: '16px', width: '80%' }} />
                <div className="skeleton" style={{ height: '16px', width: '90%' }} />
              </div>
            ) : indices.length === 0 ? (
              <div style={s.emptyMsg}>--</div>
            ) : (
              indices.map((idx) => (
                <div key={idx.name} style={s.indexRow}>
                  <span style={s.indexName}>{idx.name}</span>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                    <span style={s.indexVal}>{idx.currentValue}</span>
                    <span style={{ fontSize: '13px', fontWeight: 700, color: pctColor(idx.percentChange), fontVariantNumeric: 'tabular-nums' }}>
                      {formatPct(idx.percentChange)}
                    </span>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>

      </div>

      {/* ── Most Active Clients ── */}
      <div style={{ marginBottom: '24px' }}>
        <div style={s.section}>
          <div style={s.sectionHeader}>
            <h2 style={s.sectionTitle}>Most Active Clients</h2>
            <span style={s.refreshNote}>Top 10 by today's turnover · 30s refresh</span>
          </div>
          <ActiveClientsPanel clients={activeClients} error={activeClientsError} />
        </div>
      </div>

    </div>
  )
}

export default DashboardPage
