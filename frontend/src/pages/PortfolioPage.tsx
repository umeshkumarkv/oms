import React, { useEffect, useRef, useState } from 'react'
import { getClient, getClients, getPortfolio } from '../api/clientApi'
import { getAuditTrail } from '../api/auditApi'
import { Client, FillActivity, Holding, Portfolio } from '../types/client.types'
import { AuditRecord } from '../types/audit.types'

const POLL_INTERVAL_MS = 15_000

const formatCurrency = (value: string | number): string => {
  const num = typeof value === 'string' ? parseFloat(value) : value
  if (isNaN(num)) return '—'
  return num.toLocaleString('en-US', { style: 'currency', currency: 'USD' })
}

const formatTime = (iso: string): string => {
  try {
    return new Date(iso).toLocaleTimeString('en-US', {
      timeZone: 'America/New_York',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    })
  } catch {
    return iso
  }
}

const isToday = (iso: string): boolean => {
  try {
    const d = new Date(iso)
    const now = new Date()
    return d.getFullYear() === now.getFullYear() && d.getMonth() === now.getMonth() && d.getDate() === now.getDate()
  } catch {
    return false
  }
}

const parseFillActivities = (records: AuditRecord[]): FillActivity[] =>
  records
    .filter((r) => r.entityType === 'EXECUTION' && isToday(r.eventTimestamp))
    .map((r) => {
      let meta: Record<string, unknown> = {}
      try { meta = r.metadata ? JSON.parse(r.metadata) : {} } catch { /* ignore */ }
      return {
        auditId: r.id,
        orderId: (meta.orderId as string) ?? r.entityId,
        symbol: (meta.symbol as string) ?? '—',
        side: ((meta.side as string) ?? 'BUY') as FillActivity['side'],
        filledQty: Number(meta.executedQty ?? 0),
        fillPrice: String(meta.executedPrice ?? '0'),
        fillValue: String(meta.fillValue ?? '0'),
        fillType: ((meta.fillType as string) ?? 'FULL') as FillActivity['fillType'],
        timestamp: r.eventTimestamp,
      }
    })

// ── Styles ────────────────────────────────────────────────────────────────────

const styles = {
  page: { fontFamily: 'system-ui, sans-serif', padding: '24px' } as React.CSSProperties,
  header: { display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '20px' } as React.CSSProperties,
  headerLeft: { display: 'flex', alignItems: 'center', gap: '10px' } as React.CSSProperties,
  select: { padding: '8px 12px', borderRadius: '6px', border: '1px solid #d1d5db', fontSize: '14px', background: '#fff', cursor: 'pointer' } as React.CSSProperties,
  badge: (status: string) => ({
    padding: '2px 8px', borderRadius: '12px', fontSize: '11px', fontWeight: 600,
    background: status === 'ACTIVE' ? '#dcfce7' : '#fee2e2',
    color: status === 'ACTIVE' ? '#15803d' : '#b91c1c',
  } as React.CSSProperties),
  cards: { display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '16px', marginBottom: '24px' } as React.CSSProperties,
  card: (accent?: string) => ({
    background: '#fff', borderRadius: '10px', padding: '20px', boxShadow: '0 1px 3px rgba(0,0,0,0.08)',
    borderLeft: `4px solid ${accent ?? '#6366f1'}`,
  } as React.CSSProperties),
  cardLabel: { fontSize: '12px', color: '#6b7280', fontWeight: 500, textTransform: 'uppercase' as const, letterSpacing: '0.05em', marginBottom: '6px' },
  cardValue: (color?: string) => ({ fontSize: '20px', fontWeight: 700, color: color ?? '#1a2236' } as React.CSSProperties),
  section: { background: '#fff', borderRadius: '10px', boxShadow: '0 1px 3px rgba(0,0,0,0.08)', marginBottom: '24px', overflow: 'hidden' } as React.CSSProperties,
  sectionHeader: { padding: '14px 20px', borderBottom: '1px solid #f0f0f0', display: 'flex', alignItems: 'center', justifyContent: 'space-between' } as React.CSSProperties,
  sectionTitle: { fontSize: '15px', fontWeight: 600, color: '#1a2236', margin: 0 } as React.CSSProperties,
  table: { width: '100%', borderCollapse: 'collapse' as const, fontSize: '13px' },
  th: { padding: '10px 16px', textAlign: 'left' as const, color: '#6b7280', fontWeight: 600, fontSize: '11px', textTransform: 'uppercase' as const, background: '#f9fafb', borderBottom: '1px solid #f0f0f0' },
  td: { padding: '12px 16px', borderBottom: '1px solid #f9f9f9', color: '#374151' },
  pnlPositive: { color: '#16a34a', fontWeight: 600 } as React.CSSProperties,
  pnlNegative: { color: '#dc2626', fontWeight: 600 } as React.CSSProperties,
  sidePill: (side: string) => ({
    display: 'inline-block', padding: '2px 8px', borderRadius: '4px', fontSize: '11px', fontWeight: 700,
    background: side === 'BUY' ? '#dbeafe' : '#fce7f3',
    color: side === 'BUY' ? '#1d4ed8' : '#be185d',
  } as React.CSSProperties),
  emptyRow: { padding: '32px', textAlign: 'center' as const, color: '#9ca3af', fontSize: '13px' },
  loadingBox: { padding: '48px', textAlign: 'center' as const, color: '#9ca3af' } as React.CSSProperties,
  refreshNote: { fontSize: '11px', color: '#9ca3af' } as React.CSSProperties,
}

// ── Component ─────────────────────────────────────────────────────────────────

const PortfolioPage: React.FC = () => {
  const [clients, setClients] = useState<Client[]>([])
  const [selectedClientId, setSelectedClientId] = useState<string>('')
  const [client, setClient] = useState<Client | null>(null)
  const [portfolio, setPortfolio] = useState<Portfolio | null>(null)
  const [fills, setFills] = useState<FillActivity[]>([])
  const [loading, setLoading] = useState(false)
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null)
  // Tracks whether the selectedClientId effect should skip loadData because
  // the initial load was already triggered directly from the getClients callback.
  const skipNextLoadRef = useRef(false)

  const loadData = async (clientId: string, showLoading = true) => {
    if (!clientId) return
    if (showLoading) setLoading(true)
    try {
      const [clientData, portfolioData] = await Promise.all([
        getClient(clientId),
        getPortfolio(clientId),
      ])
      setClient(clientData)
      setPortfolio(portfolioData)
    } finally {
      setLoading(false)
    }
    // Audit trail is role-gated — load independently so a 403 never blocks portfolio
    try {
      const auditData = await getAuditTrail({ clientId, size: 100 })
      setFills(parseFillActivities(auditData?.records ?? []))
    } catch {
      setFills([])
    }
  }

  // Fetch client list and kick off portfolio load immediately — no extra render cycle.
  useEffect(() => {
    getClients().then((list) => {
      setClients(list)
      if (list.length > 0) {
        const firstId = list[0].clientId
        skipNextLoadRef.current = true  // selectedClientId effect will skip loadData
        setSelectedClientId(firstId)
        loadData(firstId)               // start loading immediately
      }
    }).catch(() => setLoading(false))
  }, [])

  useEffect(() => {
    if (!selectedClientId) return

    if (skipNextLoadRef.current) {
      // Initial load already triggered — just wire up the polling interval
      skipNextLoadRef.current = false
      intervalRef.current = setInterval(() => loadData(selectedClientId, false), POLL_INTERVAL_MS)
      return () => { if (intervalRef.current) clearInterval(intervalRef.current) }
    }

    // User changed client — reset stale data and reload
    setClient(null)
    setPortfolio(null)
    setFills([])
    loadData(selectedClientId)
    intervalRef.current = setInterval(() => loadData(selectedClientId, false), POLL_INTERVAL_MS)
    return () => { if (intervalRef.current) clearInterval(intervalRef.current) }
  }, [selectedClientId])

  // ── Derived metrics ──────────────────────────────────────────────────────
  const cashBalance   = portfolio ? parseFloat(portfolio.cashBalance)  : 0
  const reservedCash  = portfolio ? parseFloat(portfolio.reservedCash) : 0
  const buyingPower   = cashBalance - reservedCash
  const holdingsValue = portfolio?.holdings.reduce((sum, h) => sum + parseFloat(h.marketValue), 0) ?? 0
  const portfolioValue = cashBalance + holdingsValue

  const computeUnrealisedPnL = (h: Holding): number =>
    (parseFloat(h.currentPrice) - parseFloat(h.avgPrice)) * h.quantity

  return (
    <div style={styles.page}>

      {/* ── Header: client selector on LEFT, refresh note on RIGHT ── */}
      <div style={styles.header}>
        <div style={styles.headerLeft}>
          <label style={{ fontSize: '13px', color: '#6b7280', fontWeight: 500 }}>Client:</label>
          <select style={styles.select} value={selectedClientId} onChange={(e) => setSelectedClientId(e.target.value)}>
            {clients.length === 0
              ? <option value="">Loading clients…</option>
              : clients.map((c) => (
                  <option key={c.clientId} value={c.clientId}>{c.name}</option>
                ))
            }
          </select>
          {client && <span style={styles.badge(client.status)}>{client.status}</span>}
          {client && !client.tradingEnabled && (
            <span style={{ ...styles.badge('BLOCKED'), background: '#fef3c7', color: '#92400e' }}>Trading Disabled</span>
          )}
          {client && client.kycStatus !== 'VERIFIED' && (
            <span style={{ ...styles.badge('BLOCKED'), background: '#fef3c7', color: '#92400e' }}>KYC {client.kycStatus}</span>
          )}
        </div>
        <span style={styles.refreshNote}>Auto-refreshes every 15s</span>
      </div>

      {/* ── Loading ── */}
      {loading ? (
        <div style={styles.loadingBox}>Loading portfolio…</div>
      ) : (
        <>
          {/* ── Metric Cards ── */}
          <div style={styles.cards}>
            <div style={styles.card('#6366f1')}>
              <div style={styles.cardLabel}>Cash Balance</div>
              <div style={styles.cardValue()}>{formatCurrency(cashBalance)}</div>
            </div>
            <div style={styles.card('#f59e0b')}>
              <div style={styles.cardLabel}>Reserved Cash</div>
              <div style={styles.cardValue('#d97706')}>{formatCurrency(reservedCash)}</div>
            </div>
            <div style={styles.card('#10b981')}>
              <div style={styles.cardLabel}>Buying Power</div>
              <div style={styles.cardValue('#059669')}>{formatCurrency(buyingPower)}</div>
            </div>
            <div style={styles.card('#3b82f6')}>
              <div style={styles.cardLabel}>Portfolio Value</div>
              <div style={styles.cardValue('#1d4ed8')}>{formatCurrency(portfolioValue)}</div>
            </div>
          </div>

          {/* ── Holdings Table ── */}
          <div style={styles.section}>
            <div style={styles.sectionHeader}>
              <h2 style={styles.sectionTitle}>Holdings</h2>
              <span style={styles.refreshNote}>
                {portfolio?.holdings.length ?? 0} position{portfolio?.holdings.length !== 1 ? 's' : ''}
              </span>
            </div>
            <table style={styles.table}>
              <thead>
                <tr>
                  {['Symbol', 'Qty', 'Avg Cost', 'Current Price', 'Market Value', 'Unrealised P&L'].map((h) => (
                    <th key={h} style={styles.th}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {!portfolio || portfolio.holdings.length === 0 ? (
                  <tr><td colSpan={6} style={styles.emptyRow}>No holdings</td></tr>
                ) : (
                  portfolio.holdings.map((h) => {
                    const pnl = computeUnrealisedPnL(h)
                    return (
                      <tr key={h.symbol}>
                        <td style={{ ...styles.td, fontWeight: 600 }}>{h.symbol}</td>
                        <td style={styles.td}>{h.quantity.toLocaleString()}</td>
                        <td style={styles.td}>{formatCurrency(h.avgPrice)}</td>
                        <td style={styles.td}>{formatCurrency(h.currentPrice)}</td>
                        <td style={styles.td}>{formatCurrency(h.marketValue)}</td>
                        <td style={{ ...styles.td, ...(pnl >= 0 ? styles.pnlPositive : styles.pnlNegative) }}>
                          {pnl >= 0 ? '+' : ''}{formatCurrency(pnl)}
                        </td>
                      </tr>
                    )
                  })
                )}
              </tbody>
            </table>
          </div>

          {/* ── Today's Activity Table ── */}
          <div style={styles.section}>
            <div style={styles.sectionHeader}>
              <h2 style={styles.sectionTitle}>Today's Activity</h2>
              <span style={styles.refreshNote}>{fills.length} fill{fills.length !== 1 ? 's' : ''} today</span>
            </div>
            <table style={styles.table}>
              <thead>
                <tr>
                  {['Order ID', 'Symbol', 'Side', 'Fill Qty', 'Fill Price', 'Fill Value', 'Fill Type', 'Time (ET)'].map((h) => (
                    <th key={h} style={styles.th}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {fills.length === 0 ? (
                  <tr><td colSpan={8} style={styles.emptyRow}>No fill activity today</td></tr>
                ) : (
                  fills.map((f) => (
                    <tr key={f.auditId}>
                      <td style={{ ...styles.td, fontFamily: 'monospace', fontSize: '11px' }}>
                        {f.orderId.substring(0, 8)}…
                      </td>
                      <td style={{ ...styles.td, fontWeight: 600 }}>{f.symbol}</td>
                      <td style={styles.td}><span style={styles.sidePill(f.side)}>{f.side}</span></td>
                      <td style={styles.td}>{f.filledQty.toLocaleString()}</td>
                      <td style={styles.td}>{formatCurrency(f.fillPrice)}</td>
                      <td style={styles.td}>{formatCurrency(f.fillValue)}</td>
                      <td style={styles.td}>
                        <span style={{
                          padding: '2px 6px', borderRadius: '4px', fontSize: '11px',
                          background: f.fillType === 'FULL' ? '#dcfce7' : '#fef9c3',
                          color: f.fillType === 'FULL' ? '#15803d' : '#a16207',
                        }}>
                          {f.fillType}
                        </span>
                      </td>
                      <td style={{ ...styles.td, fontFamily: 'monospace', fontSize: '12px' }}>
                        {formatTime(f.timestamp)}
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </>
      )}
    </div>
  )
}

export default PortfolioPage