import React, { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { getOrders } from '../api/orderApi'
import { getMarketHours } from '../api/marketApi'
import { OrderSummary } from '../types/order.types'
import { MarketHours } from '../types/market.types'

// ── Constants ────────────────────────────────────────────────────────────────

const REFRESH_MS = 30_000

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

// ── Styles ────────────────────────────────────────────────────────────────────

const s = {
  page:          { fontFamily: 'system-ui, sans-serif', padding: '24px' } as React.CSSProperties,
  header:        { display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '20px' } as React.CSSProperties,
  title:         { margin: 0, fontSize: '20px', fontWeight: 700, color: '#1a2236' } as React.CSSProperties,
  headerRight:   { display: 'flex', alignItems: 'center', gap: '12px' } as React.CSSProperties,
  refreshNote:   { fontSize: '11px', color: '#9ca3af' } as React.CSSProperties,
  mockBanner:    { padding: '8px 16px', background: '#fef9c3', color: '#92400e', borderRadius: '6px', marginBottom: '16px', fontSize: '12px' } as React.CSSProperties,

  // Market status pill
  pill: (open: boolean) => ({
    padding: '3px 10px', borderRadius: '12px', fontSize: '11px', fontWeight: 700,
    background: open ? 'rgba(16,185,129,0.15)' : 'rgba(156,163,175,0.15)',
    color:      open ? '#059669'                : '#6b7280',
    border:    `1px solid ${open ? '#10b981' : '#9ca3af'}`,
  } as React.CSSProperties),

  // Metric cards
  cards:     { display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '16px', marginBottom: '24px' } as React.CSSProperties,
  card:      (accent: string) => ({
    background: '#fff', borderRadius: '10px', padding: '20px',
    boxShadow: '0 1px 3px rgba(0,0,0,0.08)', borderLeft: `4px solid ${accent}`,
  } as React.CSSProperties),
  cardLabel: { fontSize: '12px', color: '#6b7280', fontWeight: 500, textTransform: 'uppercase' as const, letterSpacing: '0.05em', marginBottom: '6px' },
  cardValue: (color?: string) => ({ fontSize: '28px', fontWeight: 700, color: color ?? '#1a2236' } as React.CSSProperties),

  // Skeleton
  skeleton:  { background: '#f3f4f6', borderRadius: '6px', height: '28px', width: '60px', animation: 'pulse 1.5s ease-in-out infinite' } as React.CSSProperties,

  // Two-col grid
  grid2: { display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px', marginBottom: '24px' } as React.CSSProperties,

  // Section card
  section:       { background: '#fff', borderRadius: '10px', boxShadow: '0 1px 3px rgba(0,0,0,0.08)', overflow: 'hidden' } as React.CSSProperties,
  sectionHeader: { padding: '14px 20px', borderBottom: '1px solid #f0f0f0', display: 'flex', alignItems: 'center', justifyContent: 'space-between' } as React.CSSProperties,
  sectionTitle:  { margin: 0, fontSize: '14px', fontWeight: 600, color: '#1a2236' } as React.CSSProperties,
  sectionBody:   { padding: '0' } as React.CSSProperties,
  emptyMsg:      { padding: '32px', textAlign: 'center' as const, color: '#9ca3af', fontSize: '13px' } as React.CSSProperties,

  // Activity row
  actRow:      { display: 'flex', alignItems: 'flex-start', gap: '10px', padding: '12px 20px', borderBottom: '1px solid #f9f9f9' } as React.CSSProperties,
  actDot:      (color: string) => ({ width: '8px', height: '8px', borderRadius: '50%', background: color, marginTop: '5px', flexShrink: 0 } as React.CSSProperties),
  actText:     { flex: 1, fontSize: '13px', color: '#374151' } as React.CSSProperties,
  actTime:     { fontSize: '11px', color: '#9ca3af', whiteSpace: 'nowrap' as const } as React.CSSProperties,

  // GTD row
  gtdRow:     { display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '12px 20px', borderBottom: '1px solid #f9f9f9', fontSize: '13px' } as React.CSSProperties,
  sidePill:   (side: string) => ({
    display: 'inline-block', padding: '2px 7px', borderRadius: '4px', fontSize: '11px', fontWeight: 700,
    background: side === 'BUY' ? '#dbeafe' : '#fce7f3',
    color:      side === 'BUY' ? '#1d4ed8' : '#be185d',
  } as React.CSSProperties),
  expiryWarn: { fontSize: '11px', color: '#d97706', fontWeight: 600 } as React.CSSProperties,

  // Quick actions
  actions:    { display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '12px', marginBottom: '24px' } as React.CSSProperties,
  actionBtn:  { padding: '14px', borderRadius: '10px', border: '1px solid #e5e7eb', background: '#fff', cursor: 'pointer', textAlign: 'center' as const, boxShadow: '0 1px 2px rgba(0,0,0,0.05)', transition: 'box-shadow 0.15s' },
  actionIcon: { fontSize: '20px', marginBottom: '6px' } as React.CSSProperties,
  actionLabel:{ fontSize: '13px', fontWeight: 600, color: '#374151' } as React.CSSProperties,
}

// ── Types ─────────────────────────────────────────────────────────────────────

interface Metrics {
  openCount:   number
  filledCount: number
  expiredCount: number
  gtcCount:    number
  gtcExpiringSoon: OrderSummary[]
}

interface ActivityEvent {
  id: string
  color: string
  message: string
  timestamp: string
}

// ── Component ─────────────────────────────────────────────────────────────────

const DashboardPage: React.FC = () => {
  const navigate = useNavigate()

  const [loading, setLoading]           = useState(true)
  const [usingMock, setUsingMock]       = useState(false)
  const [metrics, setMetrics]           = useState<Metrics | null>(null)
  const [marketHours, setMarketHours]   = useState<MarketHours | null>(null)
  const [lastRefreshed, setLastRefreshed] = useState<Date>(new Date())
  // WebSocket activity events — populated by future SCRUM-25 integration
  const [activityEvents] = useState<ActivityEvent[]>([])

  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null)

  const loadDataWithMockDetection = useCallback(async (showLoading = true) => {
    if (showLoading) setLoading(true)
    let backendLive = true
    try {
      // Quick probe: if this throws it means backend is offline
      await fetch('/api/v1/market/hours', { method: 'HEAD', signal: AbortSignal.timeout(2000) })
    } catch {
      backendLive = false
    }

    const [openPage, filledPage, expiredPage, gtcPage, hours] = await Promise.all([
      getOrders({ status: 'OPEN' }),
      getOrders({ status: 'FILLED' }),
      getOrders({ status: 'EXPIRED' }),
      getOrders({ tif: 'GTC', status: 'OPEN' }),
      getMarketHours(),
    ])

    const gtcExpiringSoon = (gtcPage.orders ?? []).filter(isExpiringSoon)

    setMetrics({
      openCount:       openPage.totalCount,
      filledCount:     filledPage.totalCount,
      expiredCount:    expiredPage.totalCount,
      gtcCount:        gtcPage.totalCount,
      gtcExpiringSoon,
    })
    setMarketHours(hours)
    setUsingMock(!backendLive)
    setLoading(false)
    setLastRefreshed(new Date())
  }, [])

  useEffect(() => {
    loadDataWithMockDetection(true)
    intervalRef.current = setInterval(() => loadDataWithMockDetection(false), REFRESH_MS)
    return () => { if (intervalRef.current) clearInterval(intervalRef.current) }
  }, [loadDataWithMockDetection])

  const open = marketHours?.marketOpen ?? false

  // ── Render ──────────────────────────────────────────────────────────────────

  return (
    <div style={s.page}>

      {/* Header */}
      <div style={s.header}>
        <h1 style={s.title}>Dashboard</h1>
        <div style={s.headerRight}>
          {marketHours && (
            <span style={s.pill(open)}>{open ? 'MARKET OPEN' : 'MARKET CLOSED'}</span>
          )}
          <span style={s.refreshNote}>
            Auto-refresh 30s · Last: {lastRefreshed.toLocaleTimeString()}
          </span>
        </div>
      </div>

      {/* Mock data banner */}
      {usingMock && (
        <div style={s.mockBanner}>
          Demo mode — showing seed data. Start docker-compose to load live data.
        </div>
      )}

      {/* ── Metric Cards ── */}
      <div style={s.cards}>
        {/* Open Orders */}
        <div style={s.card('#6366f1')}>
          <div style={s.cardLabel}>Open Orders</div>
          {loading
            ? <div style={s.skeleton} />
            : <div style={s.cardValue()}>{metrics?.openCount ?? 0}</div>
          }
        </div>

        {/* Filled Today */}
        <div style={s.card('#10b981')}>
          <div style={s.cardLabel}>Filled Today</div>
          {loading
            ? <div style={s.skeleton} />
            : <div style={s.cardValue('#059669')}>{metrics?.filledCount ?? 0}</div>
          }
        </div>

        {/* Expired Today */}
        <div style={s.card('#f59e0b')}>
          <div style={s.cardLabel}>Expired Today</div>
          {loading
            ? <div style={s.skeleton} />
            : <div style={s.cardValue('#d97706')}>{metrics?.expiredCount ?? 0}</div>
          }
        </div>

        {/* GTC Active */}
        <div style={s.card('#3b82f6')}>
          <div style={s.cardLabel}>GTC Active</div>
          {loading
            ? <div style={s.skeleton} />
            : <div style={s.cardValue('#1d4ed8')}>{metrics?.gtcCount ?? 0}</div>
          }
        </div>
      </div>

      {/* Quick Actions */}
      <div style={s.actions}>
        {[
          { icon: '📝', label: 'New Order',   path: '/orders/new' },
          { icon: '📋', label: 'Blotter',     path: '/orders'     },
          { icon: '💼', label: 'Portfolio',   path: '/portfolio'  },
          { icon: '📈', label: 'Market Data', path: '/market'     },
        ].map(({ icon, label, path }) => (
          <button
            key={path}
            style={s.actionBtn}
            onClick={() => navigate(path)}
            onMouseEnter={(e) => { (e.currentTarget as HTMLButtonElement).style.boxShadow = '0 4px 12px rgba(0,0,0,0.1)' }}
            onMouseLeave={(e) => { (e.currentTarget as HTMLButtonElement).style.boxShadow = '0 1px 2px rgba(0,0,0,0.05)' }}
          >
            <div style={s.actionIcon}>{icon}</div>
            <div style={s.actionLabel}>{label}</div>
          </button>
        ))}
      </div>

      {/* Two-column: Activity + GTD Expiring */}
      <div style={s.grid2}>

        {/* Recent Activity (WebSocket) */}
        <div style={s.section}>
          <div style={s.sectionHeader}>
            <h2 style={s.sectionTitle}>Recent Activity</h2>
            <span style={s.refreshNote}>Live events</span>
          </div>
          <div style={s.sectionBody}>
            {activityEvents.length === 0 ? (
              <div style={s.emptyMsg}>
                <div style={{ fontSize: '24px', marginBottom: '8px' }}>📡</div>
                Waiting for live events…
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

        {/* GTC Expiring Soon */}
        <div style={s.section}>
          <div style={s.sectionHeader}>
            <h2 style={s.sectionTitle}>GTC Expiring Soon</h2>
            <span style={s.refreshNote}>Within 2 days</span>
          </div>
          <div style={s.sectionBody}>
            {loading ? (
              <div style={s.emptyMsg}>Loading…</div>
            ) : !metrics || metrics.gtcExpiringSoon.length === 0 ? (
              <div style={s.emptyMsg}>No GTC orders expiring within 2 days</div>
            ) : (
              metrics.gtcExpiringSoon.map((order) => (
                <div key={order.id} style={s.gtdRow}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <span style={{ fontWeight: 600, color: '#1a2236', fontSize: '13px' }}>{order.symbol}</span>
                    <span style={s.sidePill(order.side)}>{order.side}</span>
                    <span style={{ color: '#6b7280', fontSize: '12px' }}>{order.quantity.toLocaleString()} shares</span>
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
  )
}

export default DashboardPage
