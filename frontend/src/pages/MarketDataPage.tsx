import React, { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { getInstrument, getInstruments, getMarketHours } from '../api/marketApi'
import { Instrument, MarketHours } from '../types/market.types'
import { marketDataWs, PriceTick } from '../services/websocketService'
import { getCurrentUser } from '../api/authApi'

const POLL_MS = 1_000
const SPARKLINE_MAX = 20

// ── Types ──────────────────────────────────────────────────────────────────

interface LiveInstrument extends Instrument {
  change: number
  changePct: number
  priceHistory: number[]
}

// ── Helpers ────────────────────────────────────────────────────────────────

const fmt = (v: string | number, dec = 2): string =>
  Number(v).toFixed(dec)

const spread = (bid: string, ask: string): string =>
  fmt(Number(ask) - Number(bid), 4)

const pctColor = (pct: number): string =>
  pct > 0 ? '#059669' : pct < 0 ? '#dc2626' : '#6b7280'

const arrow = (pct: number): string =>
  pct > 0 ? '▲' : pct < 0 ? '▼' : '—'

// ── Sparkline ──────────────────────────────────────────────────────────────

const Sparkline: React.FC<{ prices: number[]; positive: boolean }> = ({ prices, positive }) => {
  if (prices.length < 2) return <div style={{ height: 40 }} />
  const W = 120, H = 40
  const min = Math.min(...prices)
  const max = Math.max(...prices)
  const range = max - min || 1
  const pts = prices
    .map((p, i) => {
      const x = (i / (prices.length - 1)) * W
      const y = H - ((p - min) / range) * (H - 4) - 2
      return `${x},${y}`
    })
    .join(' ')
  const color = positive ? '#059669' : '#dc2626'
  return (
    <svg width={W} height={H} style={{ display: 'block' }}>
      <polyline points={pts} fill="none" stroke={color} strokeWidth={1.5} />
    </svg>
  )
}

// ── Styles ─────────────────────────────────────────────────────────────────

const s = {
  page:         { fontFamily: 'system-ui, sans-serif', padding: '24px' } as React.CSSProperties,
  header:       { display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '20px' } as React.CSSProperties,
  title:        { margin: 0, fontSize: '20px', fontWeight: 700, color: '#1a2236' } as React.CSSProperties,
  headerRight:  { display: 'flex', alignItems: 'center', gap: '12px' } as React.CSSProperties,

  pill: (open: boolean) => ({
    padding: '3px 10px', borderRadius: '12px', fontSize: '11px', fontWeight: 700,
    background: open ? 'rgba(16,185,129,0.15)' : 'rgba(156,163,175,0.15)',
    color:      open ? '#059669' : '#6b7280',
    border:    `1px solid ${open ? '#10b981' : '#9ca3af'}`,
  } as React.CSSProperties),

  wsPill: (live: boolean) => ({
    padding: '3px 10px', borderRadius: '12px', fontSize: '11px', fontWeight: 700,
    background: live ? 'rgba(99,102,241,0.12)' : 'rgba(245,158,11,0.12)',
    color:      live ? '#6366f1' : '#d97706',
    border:    `1px solid ${live ? '#6366f1' : '#f59e0b'}`,
  } as React.CSSProperties),

  note:         { fontSize: '11px', color: '#9ca3af' } as React.CSSProperties,
  warnBanner:   { padding: '8px 16px', background: '#fef9c3', color: '#92400e', borderRadius: '6px', marginBottom: '16px', fontSize: '12px' } as React.CSSProperties,

  // Two-panel layout
  grid:         { display: 'grid', gridTemplateColumns: '1fr 420px', gap: '16px', alignItems: 'start' } as React.CSSProperties,

  // Watchlist
  section:      { background: '#fff', borderRadius: '10px', boxShadow: '0 1px 3px rgba(0,0,0,0.08)', overflow: 'hidden' } as React.CSSProperties,
  sHead:        { padding: '14px 20px', borderBottom: '1px solid #f0f0f0', display: 'flex', alignItems: 'center', justifyContent: 'space-between' } as React.CSSProperties,
  sTitle:       { margin: 0, fontSize: '14px', fontWeight: 600, color: '#1a2236' } as React.CSSProperties,

  // Table
  table:        { width: '100%', borderCollapse: 'collapse' as const, fontSize: '13px' } as React.CSSProperties,
  th:           { padding: '8px 12px', textAlign: 'left' as const, fontSize: '11px', fontWeight: 600, color: '#9ca3af', textTransform: 'uppercase' as const, letterSpacing: '0.04em', borderBottom: '1px solid #f0f0f0', whiteSpace: 'nowrap' as const } as React.CSSProperties,
  thRight:      { padding: '8px 12px', textAlign: 'right' as const, fontSize: '11px', fontWeight: 600, color: '#9ca3af', textTransform: 'uppercase' as const, letterSpacing: '0.04em', borderBottom: '1px solid #f0f0f0', whiteSpace: 'nowrap' as const } as React.CSSProperties,
  td:           { padding: '10px 12px', borderBottom: '1px solid #f9f9f9', color: '#374151', whiteSpace: 'nowrap' as const } as React.CSSProperties,
  tdRight:      { padding: '10px 12px', borderBottom: '1px solid #f9f9f9', color: '#374151', textAlign: 'right' as const, whiteSpace: 'nowrap' as const } as React.CSSProperties,

  symbolCell:   { fontWeight: 700, color: '#1a2236', cursor: 'pointer' } as React.CSSProperties,

  haltPill:     { display: 'inline-block', padding: '1px 6px', borderRadius: '4px', fontSize: '10px', fontWeight: 700, background: '#fee2e2', color: '#b91c1c' } as React.CSSProperties,
  activePill:   { display: 'inline-block', padding: '1px 6px', borderRadius: '4px', fontSize: '10px', fontWeight: 700, background: '#d1fae5', color: '#065f46' } as React.CSSProperties,

  selectedRow:  { background: '#f5f3ff' } as React.CSSProperties,

  // Quote card
  card:         { background: '#fff', borderRadius: '10px', boxShadow: '0 1px 3px rgba(0,0,0,0.08)', padding: '20px' } as React.CSSProperties,
  noSelect:     { padding: '48px 20px', textAlign: 'center' as const, color: '#9ca3af', fontSize: '13px' } as React.CSSProperties,

  qSymbol:      { fontSize: '22px', fontWeight: 700, color: '#1a2236', marginBottom: '2px' } as React.CSSProperties,
  qPrice:       { fontSize: '36px', fontWeight: 700, color: '#1a2236', lineHeight: 1.1 } as React.CSSProperties,
  qChange:      (pct: number) => ({ fontSize: '14px', fontWeight: 600, color: pctColor(pct), marginTop: '4px' } as React.CSSProperties),

  details:      { display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px 16px', marginTop: '16px', marginBottom: '16px' } as React.CSSProperties,
  detLabel:     { fontSize: '11px', color: '#9ca3af', fontWeight: 500, textTransform: 'uppercase' as const, letterSpacing: '0.04em' } as React.CSSProperties,
  detValue:     { fontSize: '13px', fontWeight: 600, color: '#374151', marginTop: '2px' } as React.CSSProperties,

  sparkRow:     { marginBottom: '16px' } as React.CSSProperties,
  sparkLabel:   { fontSize: '11px', color: '#9ca3af', fontWeight: 500, marginBottom: '4px' } as React.CSSProperties,

  orderBtn:     { width: '100%', padding: '10px', background: '#6366f1', color: '#fff', border: 'none', borderRadius: '8px', fontSize: '13px', fontWeight: 600, cursor: 'pointer' } as React.CSSProperties,
  disabledBtn:  { width: '100%', padding: '10px', background: '#e5e7eb', color: '#9ca3af', border: 'none', borderRadius: '8px', fontSize: '13px', fontWeight: 600, cursor: 'not-allowed' } as React.CSSProperties,

  skeleton:     { background: '#f3f4f6', borderRadius: '4px', height: '14px', animation: 'pulse 1.5s ease-in-out infinite' } as React.CSSProperties,
}

// ── Component ──────────────────────────────────────────────────────────────

const MarketDataPage: React.FC = () => {
  const navigate = useNavigate()

  const [instruments, setInstruments]     = useState<LiveInstrument[]>([])
  const [marketHours, setMarketHours]     = useState<MarketHours | null>(null)
  const [selected, setSelected]           = useState<string | null>(null)
  const [selectedDetail, setSelectedDetail] = useState<LiveInstrument | null>(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [loading, setLoading]             = useState(true)
  const [wsLive, setWsLive]               = useState(false)

  const instrumentsRef = useRef<LiveInstrument[]>([])
  instrumentsRef.current = instruments

  // ── Helpers ──────────────────────────────────────────────────────────────

  const buildLive = (inst: Instrument): LiveInstrument => {
    const last      = Number(inst.lastPrice)
    const prevClose = inst.previousClose ? Number(inst.previousClose) : last
    const change    = last - prevClose
    const changePct = prevClose !== 0 ? (change / prevClose) * 100 : 0
    return { ...inst, change, changePct, priceHistory: [last] }
  }

  const applyTick = useCallback((tick: PriceTick) => {
    const updateLive = (inst: LiveInstrument): LiveInstrument => {
      const last      = Number(tick.lastPrice)
      const prevClose = tick.previousClose ? Number(tick.previousClose)
                      : inst.previousClose  ? Number(inst.previousClose) : last
      const change    = last - prevClose
      const changePct = prevClose !== 0 ? (change / prevClose) * 100 : 0
      const priceHistory = [...inst.priceHistory, last].slice(-SPARKLINE_MAX)
      return { ...inst, bidPrice: tick.bidPrice, askPrice: tick.askPrice, lastPrice: tick.lastPrice,
               previousClose: tick.previousClose ?? inst.previousClose,
               dataTimestamp: tick.dataTimestamp, change, changePct, priceHistory }
    }

    setInstruments(prev => prev.map(inst => inst.symbol === tick.symbol ? updateLive(inst) : inst))
    setSelectedDetail(prev => prev?.symbol === tick.symbol ? updateLive(prev) : prev)
  }, [])

  // ── Initial load ─────────────────────────────────────────────────────────

  const loadData = useCallback(async (showSpinner = true) => {
    if (showSpinner) setLoading(true)
    const [insts, hours] = await Promise.all([getInstruments(), getMarketHours()])
    const live: LiveInstrument[] = (insts as Instrument[]).map(buildLive)
    setInstruments(prev => live.map(next => {
      const existing  = prev.find(p => p.symbol === next.symbol)
      const last      = Number(next.lastPrice)
      const prevClose = next.previousClose ? Number(next.previousClose) : last
      const change    = last - prevClose
      const changePct = prevClose !== 0 ? (change / prevClose) * 100 : 0
      const priceHistory = existing ? [...existing.priceHistory, last].slice(-SPARKLINE_MAX) : [last]
      return { ...next, change, changePct, priceHistory }
    }))
    setSelectedDetail(prev => {
      if (!prev) return prev
      const updated   = live.find(i => i.symbol === prev.symbol)
      if (!updated) return prev
      const last      = Number(updated.lastPrice)
      const prevClose = updated.previousClose ? Number(updated.previousClose) : last
      const change    = last - prevClose
      const changePct = prevClose !== 0 ? (change / prevClose) * 100 : 0
      const priceHistory = [...prev.priceHistory, last].slice(-SPARKLINE_MAX)
      return { ...prev, bidPrice: updated.bidPrice, askPrice: updated.askPrice,
               lastPrice: updated.lastPrice, previousClose: updated.previousClose,
               dataTimestamp: updated.dataTimestamp, change, changePct, priceHistory }
    })
    setMarketHours(hours)
    if (showSpinner) setLoading(false)
  }, [])

  // ── Symbol selection — calls /market/{symbol} for full detail ────────────

  const handleSelectSymbol = useCallback(async (symbol: string) => {
    setSelected(symbol)
    setDetailLoading(true)
    const inst = await getInstrument(symbol)
    if (inst) {
      const last      = Number(inst.lastPrice)
      const prevClose = inst.previousClose ? Number(inst.previousClose) : last
      const change    = last - prevClose
      const changePct = prevClose !== 0 ? (change / prevClose) * 100 : 0
      setSelectedDetail({ ...inst, change, changePct, priceHistory: [last] })
    }
    setDetailLoading(false)
  }, [])

  // ── WebSocket ─────────────────────────────────────────────────────────────

  useEffect(() => {
    marketDataWs.connect()

    const offTick   = marketDataWs.onTick(applyTick)
    const offConn   = marketDataWs.onConnectionChange(setWsLive)
    const offStatus = marketDataWs.onMarketStatus(ev => {
      setMarketHours(prev => prev
        ? { ...prev, marketOpen: ev.type === 'MARKET_OPEN' }
        : prev
      )
    })

    return () => {
      offTick()
      offConn()
      offStatus()
    }
  }, [applyTick])

  // ── Polling fallback (10s when WS not connected) ──────────────────────────

  useEffect(() => {
    loadData(true)
  }, [loadData])

  useEffect(() => {
    if (wsLive) return
    const id = setInterval(() => loadData(false), POLL_MS)
    return () => clearInterval(id)
  }, [wsLive, loadData])

  // ── Derived ───────────────────────────────────────────────────────────────

  const open = marketHours?.marketOpen ?? false

  // ── Render ────────────────────────────────────────────────────────────────

  return (
    <div style={s.page}>

      {/* Header */}
      <div style={s.header}>
        <h1 style={s.title}>Market Data</h1>
        <div style={s.headerRight}>
          {marketHours && (
            <span style={s.pill(open)}>{open ? 'MARKET OPEN' : 'MARKET CLOSED'}</span>
          )}
          <span style={s.wsPill(wsLive)}>{wsLive ? '● Live' : '○ Polling 1s'}</span>
          {marketHours && (
            <span style={s.note}>{marketHours.openTime} – {marketHours.closeTime} {marketHours.timezone}</span>
          )}
        </div>
      </div>

      {/* WS reconnecting banner */}
      {!wsLive && (
        <div style={s.warnBanner}>
          Connecting to live feed — polling every 1s.
          {marketHours?.nextOpenDate && !open && (
            <span> Next open: <strong>{marketHours.nextOpenDate}</strong>.</span>
          )}
        </div>
      )}

      {/* Two-panel grid */}
      <div style={s.grid}>

        {/* Left — Watchlist */}
        <div style={s.section}>
          <div style={s.sHead}>
            <h2 style={s.sTitle}>Watchlist</h2>
            <span style={s.note}>{instruments.length} instruments</span>
          </div>
          <table style={s.table}>
            <thead>
              <tr>
                <th style={s.th}>Symbol</th>
                <th style={s.thRight}>Last</th>
                <th style={s.thRight}>Chg %</th>
                <th style={s.thRight}>Bid</th>
                <th style={s.thRight}>Ask</th>
                <th style={s.thRight}>Spread</th>
                <th style={s.th}>Status</th>
              </tr>
            </thead>
            <tbody>
              {loading
                ? Array.from({ length: 6 }).map((_, i) => (
                    <tr key={i}>
                      {Array.from({ length: 7 }).map((_, j) => (
                        <td key={j} style={s.td}><div style={s.skeleton} /></td>
                      ))}
                    </tr>
                  ))
                : instruments.map(inst => {
                    const isSelected = selected === inst.symbol
                    return (
                      <tr
                        key={inst.symbol}
                        style={isSelected ? s.selectedRow : undefined}
                        onClick={() => handleSelectSymbol(inst.symbol)}
                      >
                        <td style={s.td}>
                          <span style={s.symbolCell}>{inst.symbol}</span>
                        </td>
                        <td style={s.tdRight}>{fmt(inst.lastPrice)}</td>
                        <td style={{ ...s.tdRight, color: pctColor(inst.changePct), fontWeight: 600 }}>
                          {arrow(inst.changePct)} {Math.abs(inst.changePct).toFixed(2)}%
                        </td>
                        <td style={s.tdRight}>{fmt(inst.bidPrice)}</td>
                        <td style={s.tdRight}>{fmt(inst.askPrice)}</td>
                        <td style={s.tdRight}>{spread(inst.bidPrice, inst.askPrice)}</td>
                        <td style={s.td}>
                          {inst.halted
                            ? <span style={s.haltPill}>HALTED</span>
                            : inst.tradable
                              ? <span style={s.activePill}>ACTIVE</span>
                              : <span style={s.haltPill}>CLOSED</span>
                          }
                        </td>
                      </tr>
                    )
                  })
              }
            </tbody>
          </table>
        </div>

        {/* Right — Quote Card */}
        <div style={s.card}>
          {!selected ? (
            <div style={s.noSelect}>
              <div style={{ fontSize: '28px', marginBottom: '8px' }}>📈</div>
              Select a symbol from the watchlist to view its quote
            </div>
          ) : detailLoading ? (
            <div style={s.noSelect}>
              <div style={{ fontSize: '13px', color: '#6b7280' }}>Loading {selected}…</div>
            </div>
          ) : !selectedDetail ? (
            <div style={s.noSelect}>
              <div style={{ fontSize: '13px', color: '#dc2626' }}>Failed to load {selected}</div>
            </div>
          ) : (
            <>
              <div style={s.qSymbol}>{selectedDetail.symbol}</div>
              <div style={s.qPrice}>${fmt(selectedDetail.lastPrice)}</div>
              <div style={s.qChange(selectedDetail.changePct)}>
                {arrow(selectedDetail.changePct)}&nbsp;
                {selectedDetail.change >= 0 ? '+' : ''}{fmt(selectedDetail.change)}&nbsp;
                ({selectedDetail.changePct >= 0 ? '+' : ''}{selectedDetail.changePct.toFixed(2)}%)
              </div>

              <div style={s.details}>
                <div>
                  <div style={s.detLabel}>Bid</div>
                  <div style={s.detValue}>${fmt(selectedDetail.bidPrice)}</div>
                </div>
                <div>
                  <div style={s.detLabel}>Ask</div>
                  <div style={s.detValue}>${fmt(selectedDetail.askPrice)}</div>
                </div>
                <div>
                  <div style={s.detLabel}>Spread</div>
                  <div style={s.detValue}>{spread(selectedDetail.bidPrice, selectedDetail.askPrice)}</div>
                </div>
                <div>
                  <div style={s.detLabel}>Prev Close</div>
                  <div style={s.detValue}>{selectedDetail.previousClose ? `$${fmt(selectedDetail.previousClose)}` : '—'}</div>
                </div>
                <div>
                  <div style={s.detLabel}>Day High</div>
                  <div style={s.detValue}>{selectedDetail.dayHigh ? `$${fmt(selectedDetail.dayHigh)}` : '—'}</div>
                </div>
                <div>
                  <div style={s.detLabel}>Day Low</div>
                  <div style={s.detValue}>{selectedDetail.dayLow ? `$${fmt(selectedDetail.dayLow)}` : '—'}</div>
                </div>
                <div>
                  <div style={s.detLabel}>Min Qty</div>
                  <div style={s.detValue}>{selectedDetail.minQuantity?.toLocaleString() ?? '—'}</div>
                </div>
                <div>
                  <div style={s.detLabel}>Max Qty</div>
                  <div style={s.detValue}>{selectedDetail.maxQuantity?.toLocaleString() ?? '—'}</div>
                </div>
                <div>
                  <div style={s.detLabel}>Status</div>
                  <div style={s.detValue}>
                    {selectedDetail.halted ? 'HALTED' : selectedDetail.tradable ? 'ACTIVE' : 'CLOSED'}
                  </div>
                </div>
                <div style={{ gridColumn: '1 / -1' }}>
                  <div style={s.detLabel}>Last Updated</div>
                  <div style={s.detValue}>
                    {new Date(selectedDetail.dataTimestamp).toLocaleTimeString('en-US', {
                      timeZone: 'America/New_York', hour: '2-digit', minute: '2-digit', second: '2-digit',
                    })} ET
                  </div>
                </div>
              </div>

              <div style={s.sparkRow}>
                <div style={s.sparkLabel}>Price history (last {selectedDetail.priceHistory.length} ticks)</div>
                <Sparkline prices={selectedDetail.priceHistory} positive={selectedDetail.changePct >= 0} />
              </div>

              {(getCurrentUser() === null || getCurrentUser()?.role === 'TRADER') ? (
                <button
                  style={s.orderBtn}
                  onClick={() => navigate(`/orders/new?symbol=${selectedDetail.symbol}`)}
                  disabled={selectedDetail.halted || !selectedDetail.tradable}
                >
                  New Order — {selectedDetail.symbol}
                </button>
              ) : (
                <button style={s.disabledBtn} disabled>
                  Order entry: TRADER role required
                </button>
              )}
            </>
          )}
        </div>

      </div>
    </div>
  )
}

export default MarketDataPage
