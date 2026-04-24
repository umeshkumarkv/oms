import React, { useEffect, useRef, useState } from 'react'
import { getClient, getPortfolio } from '../api/clientApi'
import { getAuditTrail } from '../api/auditApi'
import { Client, FillActivity, Holding, Portfolio } from '../types/client.types'
import { AuditRecord } from '../types/audit.types'

// Seed clients — POC: hardcoded for simplicity (no GET /api/v1/clients list endpoint)
const SEED_CLIENTS = [
  { id: 'a1000000-0000-0000-0000-000000000001', name: 'John Smith' },
  { id: 'a2000000-0000-0000-0000-000000000002', name: 'Sarah Johnson' },
  { id: 'a3000000-0000-0000-0000-000000000003', name: 'Mike Davis' },
  { id: 'a4000000-0000-0000-0000-000000000004', name: 'Emily Chen' },
  { id: 'a5000000-0000-0000-0000-000000000005', name: 'Robert Wilson' },
]

// ── Mock data — used when backend is offline (demo / local dev without Docker) ──
const MOCK_PORTFOLIOS: Record<string, { client: Client; portfolio: Portfolio; fills: FillActivity[] }> = {
  'a1000000-0000-0000-0000-000000000001': {
    client: { clientId: 'a1000000-0000-0000-0000-000000000001', name: 'John Smith', status: 'ACTIVE', tradingEnabled: true, kycStatus: 'VERIFIED' },
    portfolio: {
      cashBalance: '500000.00',
      reservedCash: '17500.00',
      buyingPower: '482500.00',
      holdings: [
        { symbol: 'AAPL', quantity: 100, avgPrice: '150.00', currentPrice: '178.50', marketValue: '17850.00' },
        { symbol: 'MSFT', quantity: 50,  avgPrice: '400.00', currentPrice: '415.20', marketValue: '20760.00' },
      ],
    },
    fills: [
      { auditId: 'f1', orderId: 'b2c3d4e5-0000-0000-0000-000000000001', symbol: 'AAPL', side: 'BUY',  filledQty: 50,  fillPrice: '178.50', fillValue: '8925.00',  fillType: 'FULL',    timestamp: new Date(Date.now() - 3_600_000).toISOString() },
      { auditId: 'f2', orderId: 'b2c3d4e5-0000-0000-0000-000000000002', symbol: 'MSFT', side: 'BUY',  filledQty: 20,  fillPrice: '415.20', fillValue: '8304.00',  fillType: 'PARTIAL', timestamp: new Date(Date.now() - 1_800_000).toISOString() },
    ],
  },
  'a2000000-0000-0000-0000-000000000002': {
    client: { clientId: 'a2000000-0000-0000-0000-000000000002', name: 'Sarah Johnson', status: 'ACTIVE', tradingEnabled: true, kycStatus: 'VERIFIED' },
    portfolio: {
      cashBalance: '50000.00',
      reservedCash: '0.00',
      buyingPower: '50000.00',
      holdings: [
        { symbol: 'TSLA', quantity: 20, avgPrice: '170.00', currentPrice: '245.80', marketValue: '4916.00' },
      ],
    },
    fills: [
      { auditId: 'f3', orderId: 'b2c3d4e5-0000-0000-0000-000000000003', symbol: 'TSLA', side: 'SELL', filledQty: 5, fillPrice: '245.80', fillValue: '1229.00', fillType: 'FULL', timestamp: new Date(Date.now() - 900_000).toISOString() },
    ],
  },
  'a3000000-0000-0000-0000-000000000003': {
    client: { clientId: 'a3000000-0000-0000-0000-000000000003', name: 'Mike Davis', status: 'ACTIVE', tradingEnabled: false, kycStatus: 'VERIFIED' },
    portfolio: { cashBalance: '200000.00', reservedCash: '0.00', buyingPower: '200000.00', holdings: [] },
    fills: [],
  },
  'a4000000-0000-0000-0000-000000000004': {
    client: { clientId: 'a4000000-0000-0000-0000-000000000004', name: 'Emily Chen', status: 'BLOCKED', tradingEnabled: true, kycStatus: 'VERIFIED' },
    portfolio: {
      cashBalance: '100000.00',
      reservedCash: '1600.00',
      buyingPower: '98400.00',
      holdings: [
        { symbol: 'GOOGL', quantity: 10, avgPrice: '160.00', currentPrice: '172.30', marketValue: '1723.00' },
      ],
    },
    fills: [],
  },
  'a5000000-0000-0000-0000-000000000005': {
    client: { clientId: 'a5000000-0000-0000-0000-000000000005', name: 'Robert Wilson', status: 'ACTIVE', tradingEnabled: true, kycStatus: 'EXPIRED' },
    portfolio: { cashBalance: '75000.00', reservedCash: '0.00', buyingPower: '75000.00', holdings: [] },
    fills: [],
  },
}

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
  mockBanner: { padding: '8px 16px', background: '#fef9c3', color: '#92400e', borderRadius: '6px', marginBottom: '16px', fontSize: '12px' } as React.CSSProperties,
  refreshNote: { fontSize: '11px', color: '#9ca3af' } as React.CSSProperties,
}

// ── Component ─────────────────────────────────────────────────────────────────

const PortfolioPage: React.FC = () => {
  const [selectedClientId, setSelectedClientId] = useState<string>(SEED_CLIENTS[0].id)
  const [client, setClient] = useState<Client | null>(null)
  const [portfolio, setPortfolio] = useState<Portfolio | null>(null)
  const [fills, setFills] = useState<FillActivity[]>([])
  const [loading, setLoading] = useState(true)
  const [usingMock, setUsingMock] = useState(false)
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null)

  const loadData = async (clientId: string, showLoading = true) => {
    if (showLoading) setLoading(true)
    try {
      const [clientData, portfolioData, auditData] = await Promise.all([
        getClient(clientId),
        getPortfolio(clientId),
        getAuditTrail({ clientId, size: 100 }),
      ])
      setClient(clientData)
      setPortfolio(portfolioData)
      setFills(parseFillActivities(auditData?.records ?? []))
      setUsingMock(false)
    } catch {
      // Fall back to mock data so the UI is usable without the backend
      const mock = MOCK_PORTFOLIOS[clientId]
      if (mock) {
        setClient(mock.client)
        setPortfolio(mock.portfolio)
        setFills(mock.fills)
        setUsingMock(true)
      }
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
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
            {SEED_CLIENTS.map((c) => (
              <option key={c.id} value={c.id}>{c.name}</option>
            ))}
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

      {/* ── Mock data banner ── */}
      {usingMock && (
        <div style={styles.mockBanner}>
          Demo mode — showing seed data. Start docker-compose to load live data.
        </div>
      )}

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