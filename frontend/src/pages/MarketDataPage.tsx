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
  pct > 0 ? '#10b981' : pct < 0 ? '#ef4444' : '#64748b'

const pctBg = (pct: number): string =>
  pct > 0 ? 'rgba(16,185,129,0.08)' : pct < 0 ? 'rgba(239,68,68,0.08)' : 'transparent'

const arrow = (pct: number): string =>
  pct > 0 ? '▲' : pct < 0 ? '▼' : '—'

// ── Sparkline ──────────────────────────────────────────────────────────────

const Sparkline: React.FC<{ prices: number[]; positive: boolean }> = ({ prices, positive }) => {
  if (prices.length < 2) return <div style={{ height: 48 }} />
  const W = 200, H = 48
  const min = Math.min(...prices)
  const max = Math.max(...prices)
  const range = max - min || 1
  const pts = prices
    .map((p, i) => {
      const x = (i / (prices.length - 1)) * W
      const y = H - ((p - min) / range) * (H - 6) - 3
      return `${x},${y}`
    })
    .join(' ')
  const color = positive ? '#10b981' : '#ef4444'
  const fillPts = `0,${H} ${pts} ${W},${H}`
  return (
    <svg width="100%" height={H} viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none" style={{ display: 'block' }}>
      <defs>
        <linearGradient id="sparkGrad" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor={color} stopOpacity="0.18" />
          <stop offset="100%" stopColor={color} stopOpacity="0" />
        </linearGradient>
      </defs>
      <polygon points={fillPts} fill="url(#sparkGrad)" />
      <polyline points={pts} fill="none" stroke={color} strokeWidth={2} strokeLinejoin="round" strokeLinecap="round" />
    </svg>
  )
}

// ── Injected global styles ─────────────────────────────────────────────────

const GlobalStyle = () => (
  <style>{`
    @import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap');

    .mkt-row { cursor: pointer; transition: background 0.12s; }
    .mkt-row:hover { background: #f8fafc !important; }
    .mkt-row.selected { background: #f0f7ff !important; }

    .mkt-order-btn {
      width: 100%; padding: 11px;
      background: #2563eb; color: #fff;
      border: none; border-radius: 8px;
      font-size: 13px; font-weight: 600;
      cursor: pointer; font-family: inherit;
      transition: background 0.15s, transform 0.1s, box-shadow 0.15s;
      box-shadow: 0 1px 3px rgba(37,99,235,0.25);
      letter-spacing: 0.01em;
    }
    .mkt-order-btn:hover:not(:disabled) {
      background: #1d4ed8;
      transform: translateY(-1px);
      box-shadow: 0 4px 12px rgba(37,99,235,0.35);
    }
    .mkt-order-btn:disabled {
      background: #e2e8f0; color: #94a3b8;
      cursor: not-allowed; box-shadow: none;
    }

    .mkt-pill-flash {
      animation: mkt-flash 0.4s ease;
    }
    @keyframes mkt-flash {
      0%   { opacity: 1; }
      40%  { opacity: 0.4; }
      100% { opacity: 1; }
    }

    @keyframes mkt-skeleton {
      0%, 100% { opacity: 0.5; }
      50%       { opacity: 1; }
    }
  `}</style>
)

// ── Styles ─────────────────────────────────────────────────────────────────

const FF = "'Inter', system-ui, sans-serif"

const s = {
  page:        { fontFamily: FF, padding: '24px 28px', background: '#f1f5f9', minHeight: '100vh' } as React.CSSProperties,

  header:      { display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '20px' } as React.CSSProperties,
  titleWrap:   { display: 'flex', alignItems: 'center', gap: '10px' } as React.CSSProperties,
  title:       { margin: 0, fontSize: '18px', fontWeight: 700, color: '#0f172a', letterSpacing: '-0.01em' } as React.CSSProperties,
  headerRight: { display: 'flex', alignItems: 'center', gap: '10px' } as React.CSSProperties,

  pill: (open: boolean) => ({
    display: 'inline-flex', alignItems: 'center', gap: '5px',
    padding: '4px 10px', borderRadius: '20px', fontSize: '11px', fontWeight: 700,
    background: open ? '#dcfce7' : '#f1f5f9',
    color:      open ? '#15803d' : '#64748b',
    border:    `1px solid ${open ? '#86efac' : '#e2e8f0'}`,
    letterSpacing: '0.03em',
  } as React.CSSProperties),

  wsPill: (live: boolean) => ({
    display: 'inline-flex', alignItems: 'center', gap: '5px',
    padding: '4px 10px', borderRadius: '20px', fontSize: '11px', fontWeight: 600,
    background: live ? '#eff6ff' : '#fffbeb',
    color:      live ? '#2563eb' : '#b45309',
    border:    `1px solid ${live ? '#bfdbfe' : '#fde68a'}`,
  } as React.CSSProperties),

  pillDot: (color: string) => ({
    width: '6px', height: '6px', borderRadius: '50%', background: color, display: 'inline-block',
  } as React.CSSProperties),

  note:        { fontSize: '12px', color: '#94a3b8', fontWeight: 500 } as React.CSSProperties,
  warnBanner:  {
    display: 'flex', alignItems: 'center', gap: '8px',
    padding: '10px 16px', background: '#fffbeb',
    color: '#92400e', borderRadius: '8px', marginBottom: '16px',
    fontSize: '12px', fontWeight: 500,
    border: '1px solid #fde68a',
  } as React.CSSProperties,

  // ── Two-panel layout ──────────────────────────────────────────────────────
  grid:        { display: 'grid', gridTemplateColumns: '1fr 400px', gap: '16px', alignItems: 'start' } as React.CSSProperties,

  // ── Watchlist panel ───────────────────────────────────────────────────────
  section:     {
    background: '#fff', borderRadius: '12px',
    boxShadow: '0 1px 3px rgba(15,23,42,0.06), 0 1px 2px rgba(15,23,42,0.04)',
    overflow: 'hidden',
  } as React.CSSProperties,

  sHead:       {
    padding: '14px 20px', borderBottom: '1px solid #f1f5f9',
    display: 'flex', alignItems: 'center', justifyContent: 'space-between',
    background: '#fff',
  } as React.CSSProperties,

  sTitleRow:   { display: 'flex', alignItems: 'center', gap: '8px' } as React.CSSProperties,
  sTitle:      { margin: 0, fontSize: '13px', fontWeight: 700, color: '#0f172a', letterSpacing: '-0.01em' } as React.CSSProperties,
  sCount:      {
    fontSize: '11px', fontWeight: 600, color: '#64748b',
    background: '#f1f5f9', borderRadius: '20px', padding: '1px 8px',
  } as React.CSSProperties,

  // ── Table ─────────────────────────────────────────────────────────────────
  table:       { width: '100%', borderCollapse: 'collapse' as const, fontSize: '12.5px' } as React.CSSProperties,

  th:          {
    padding: '9px 14px', textAlign: 'left' as const,
    fontSize: '10.5px', fontWeight: 700, color: '#94a3b8',
    textTransform: 'uppercase' as const, letterSpacing: '0.06em',
    borderBottom: '1px solid #f1f5f9', background: '#fafafa',
    whiteSpace: 'nowrap' as const,
  } as React.CSSProperties,

  thRight:     {
    padding: '9px 14px', textAlign: 'right' as const,
    fontSize: '10.5px', fontWeight: 700, color: '#94a3b8',
    textTransform: 'uppercase' as const, letterSpacing: '0.06em',
    borderBottom: '1px solid #f1f5f9', background: '#fafafa',
    whiteSpace: 'nowrap' as const,
  } as React.CSSProperties,

  td:          { padding: '10px 14px', borderBottom: '1px solid #f8fafc', color: '#334155', whiteSpace: 'nowrap' as const } as React.CSSProperties,
  tdRight:     { padding: '10px 14px', borderBottom: '1px solid #f8fafc', color: '#334155', textAlign: 'right' as const, whiteSpace: 'nowrap' as const } as React.CSSProperties,
  tdMono:      { padding: '10px 14px', borderBottom: '1px solid #f8fafc', color: '#334155', textAlign: 'right' as const, whiteSpace: 'nowrap' as const, fontVariantNumeric: 'tabular-nums' as const, fontFamily: "'Menlo', 'Consolas', monospace" } as React.CSSProperties,

  symbolCell:  { fontWeight: 700, color: '#0f172a', fontSize: '13px', letterSpacing: '-0.01em' } as React.CSSProperties,
  nameCell:    { fontSize: '11px', color: '#94a3b8', marginTop: '1px' } as React.CSSProperties,

  haltPill:    { display: 'inline-flex', alignItems: 'center', padding: '2px 7px', borderRadius: '4px', fontSize: '10px', fontWeight: 700, background: '#fee2e2', color: '#b91c1c', letterSpacing: '0.03em' } as React.CSSProperties,
  activePill:  { display: 'inline-flex', alignItems: 'center', gap: '4px', padding: '2px 7px', borderRadius: '4px', fontSize: '10px', fontWeight: 700, background: '#dcfce7', color: '#15803d', letterSpacing: '0.03em' } as React.CSSProperties,
  closedPill:  { display: 'inline-flex', alignItems: 'center', padding: '2px 7px', borderRadius: '4px', fontSize: '10px', fontWeight: 700, background: '#f1f5f9', color: '#64748b', letterSpacing: '0.03em' } as React.CSSProperties,

  skeleton:    { background: 'linear-gradient(90deg,#f1f5f9 25%,#e2e8f0 50%,#f1f5f9 75%)', backgroundSize: '200% 100%', borderRadius: '4px', height: '13px', animation: 'mkt-skeleton 1.4s ease-in-out infinite' } as React.CSSProperties,

  // ── Quote card ────────────────────────────────────────────────────────────
  card:        {
    background: '#fff', borderRadius: '12px',
    boxShadow: '0 1px 3px rgba(15,23,42,0.06), 0 1px 2px rgba(15,23,42,0.04)',
    overflow: 'hidden',
  } as React.CSSProperties,

  cardHeader:  { padding: '16px 20px 0', borderBottom: '1px solid #f1f5f9' } as React.CSSProperties,

  noSelect:    { padding: '56px 24px', textAlign: 'center' as const } as React.CSSProperties,
  noSelectIcon:{ fontSize: '32px', marginBottom: '10px', opacity: 0.4 } as React.CSSProperties,
  noSelectTxt: { fontSize: '13px', color: '#94a3b8', fontWeight: 500 } as React.CSSProperties,

  qHeaderTop:  { display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', paddingBottom: '14px' } as React.CSSProperties,
  qSymbol:     { fontSize: '20px', fontWeight: 800, color: '#0f172a', letterSpacing: '-0.02em' } as React.CSSProperties,
  qName:       { fontSize: '11px', color: '#94a3b8', fontWeight: 500, marginTop: '2px' } as React.CSSProperties,

  qPriceRow:   { display: 'flex', alignItems: 'baseline', gap: '10px', marginTop: '4px' } as React.CSSProperties,
  qPrice:      { fontSize: '32px', fontWeight: 800, color: '#0f172a', letterSpacing: '-0.03em', fontVariantNumeric: 'tabular-nums' as const } as React.CSSProperties,
  qChange:     (pct: number) => ({
    display: 'inline-flex', alignItems: 'center', gap: '4px',
    fontSize: '12px', fontWeight: 600, color: pctColor(pct),
    background: pctBg(pct),
    padding: '3px 8px', borderRadius: '6px',
  } as React.CSSProperties),

  cardBody:    { padding: '16px 20px' } as React.CSSProperties,

  // Bid/Ask bar
  bidAskRow:   { display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px', marginBottom: '14px' } as React.CSSProperties,
  bidBox:      { background: '#f0fdf4', borderRadius: '8px', padding: '10px 12px', border: '1px solid #bbf7d0' } as React.CSSProperties,
  askBox:      { background: '#fff5f5', borderRadius: '8px', padding: '10px 12px', border: '1px solid #fecaca' } as React.CSSProperties,
  baLabel:     { fontSize: '10px', fontWeight: 700, letterSpacing: '0.06em', textTransform: 'uppercase' as const, color: '#94a3b8', marginBottom: '2px' } as React.CSSProperties,
  baValue:     (color: string) => ({ fontSize: '18px', fontWeight: 700, color, fontVariantNumeric: 'tabular-nums' as const, letterSpacing: '-0.01em' } as React.CSSProperties),

  spreadRow:   { display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '14px', padding: '8px 12px', background: '#fafafa', borderRadius: '8px' } as React.CSSProperties,
  spreadLabel: { fontSize: '11px', color: '#94a3b8', fontWeight: 600 } as React.CSSProperties,
  spreadVal:   { fontSize: '13px', fontWeight: 700, color: '#334155', fontVariantNumeric: 'tabular-nums' as const } as React.CSSProperties,

  divider:     { height: '1px', background: '#f1f5f9', margin: '14px 0' } as React.CSSProperties,

  details:     { display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px 16px', marginBottom: '14px' } as React.CSSProperties,
  detLabel:    { fontSize: '10px', color: '#94a3b8', fontWeight: 700, textTransform: 'uppercase' as const, letterSpacing: '0.06em', marginBottom: '2px' } as React.CSSProperties,
  detValue:    { fontSize: '13px', fontWeight: 600, color: '#0f172a' } as React.CSSProperties,

  sparkWrap:   { marginBottom: '16px' } as React.CSSProperties,
  sparkLabel:  { fontSize: '10px', color: '#94a3b8', fontWeight: 700, textTransform: 'uppercase' as const, letterSpacing: '0.06em', marginBottom: '6px' } as React.CSSProperties,
  sparkBox:    { borderRadius: '8px', overflow: 'hidden', background: '#fafafa', padding: '6px 0 0' } as React.CSSProperties,

  statusBadge: (halted: boolean, tradable: boolean) => ({
    display: 'inline-flex', alignItems: 'center', gap: '5px',
    padding: '3px 8px', borderRadius: '6px', fontSize: '11px', fontWeight: 700,
    background: halted ? '#fee2e2' : tradable ? '#dcfce7' : '#f1f5f9',
    color:      halted ? '#b91c1c' : tradable ? '#15803d' : '#64748b',
  } as React.CSSProperties),

  tsRow:       { gridColumn: '1 / -1', display: 'flex', alignItems: 'center', justifyContent: 'space-between' } as React.CSSProperties,
  tsLabel:     { fontSize: '10px', color: '#94a3b8', fontWeight: 700, textTransform: 'uppercase' as const, letterSpacing: '0.06em' } as React.CSSProperties,
  tsValue:     { fontSize: '12px', fontWeight: 600, color: '#0f172a' } as React.CSSProperties,
}

// ── Component ──────────────────────────────────────────────────────────────

const MarketDataPage: React.FC = () => {
  const navigate = useNavigate()

  const [instruments, setInstruments]       = useState<LiveInstrument[]>([])
  const [marketHours, setMarketHours]       = useState<MarketHours | null>(null)
  const [selected, setSelected]             = useState<string | null>(null)
  const [selectedDetail, setSelectedDetail] = useState<LiveInstrument | null>(null)
  const [detailLoading, setDetailLoading]   = useState(false)
  const [loading, setLoading]               = useState(true)
  const [wsLive, setWsLive]                 = useState(false)

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
      return {
        ...inst,
        bidPrice: tick.bidPrice, askPrice: tick.askPrice, lastPrice: tick.lastPrice,
        previousClose: tick.previousClose ?? inst.previousClose,
        dataTimestamp: tick.dataTimestamp, change, changePct, priceHistory,
      }
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
      const last          = Number(updated.lastPrice)
      const resolvedPrev  = updated.previousClose ?? prev.previousClose
      const prevClose     = resolvedPrev ? Number(resolvedPrev) : last
      const change        = last - prevClose
      const changePct = prevClose !== 0 ? (change / prevClose) * 100 : 0
      const priceHistory = [...prev.priceHistory, last].slice(-SPARKLINE_MAX)
      return {
        ...prev,
        bidPrice:      updated.bidPrice,
        askPrice:      updated.askPrice,
        lastPrice:     updated.lastPrice,
        previousClose: updated.previousClose ?? prev.previousClose,
        dataTimestamp: updated.dataTimestamp ?? prev.dataTimestamp,
        change, changePct, priceHistory,
      }
    })
    setMarketHours(hours)
    if (showSpinner) setLoading(false)
  }, [])

  // ── Symbol selection ─────────────────────────────────────────────────────

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
    return () => { offTick(); offConn(); offStatus() }
  }, [applyTick])

  useEffect(() => { loadData(true) }, [loadData])

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
      <GlobalStyle />

      {/* ── Header ── */}
      <div style={s.header}>
        <div style={s.titleWrap}>
          <h1 style={s.title}>Market Data</h1>
        </div>
        <div style={s.headerRight}>
          {marketHours && (
            <span style={s.pill(open)}>
              <span style={s.pillDot(open ? '#15803d' : '#94a3b8')} />
              {open ? 'MARKET OPEN' : 'MARKET CLOSED'}
            </span>
          )}
          <span style={s.wsPill(wsLive)}>
            <span style={s.pillDot(wsLive ? '#2563eb' : '#b45309')} />
            {wsLive ? 'Live' : 'Polling 1s'}
          </span>
          {marketHours && (
            <span style={s.note}>{marketHours.openTime} – {marketHours.closeTime} {marketHours.timezone}</span>
          )}
        </div>
      </div>

      {/* ── Warning banner ── */}
      {!wsLive && (
        <div style={s.warnBanner}>
          <span>⚠️</span>
          <span>
            Connecting to live feed — polling every 1s.
            {marketHours?.nextOpenDate && !open && (
              <span> Next open: <strong>{marketHours.nextOpenDate}</strong>.</span>
            )}
          </span>
        </div>
      )}

      {/* ── Two-panel grid ── */}
      <div style={s.grid}>

        {/* ────────────────────────── LEFT: Watchlist ────────────────────────── */}
        <div style={s.section}>
          <div style={s.sHead}>
            <div style={s.sTitleRow}>
              <h2 style={s.sTitle}>Watchlist</h2>
              {!loading && <span style={s.sCount}>{instruments.length}</span>}
            </div>
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
                ? Array.from({ length: 8 }).map((_, i) => (
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
                        className={`mkt-row${isSelected ? ' selected' : ''}`}
                        onClick={() => handleSelectSymbol(inst.symbol)}
                      >
                        {/* Symbol */}
                        <td style={s.td}>
                          <div style={s.symbolCell}>{inst.symbol}</div>
                        </td>

                        {/* Last */}
                        <td style={s.tdMono}>
                          <strong>{fmt(inst.lastPrice)}</strong>
                        </td>

                        {/* Chg % */}
                        <td style={{ ...s.tdRight }}>
                          <span style={{
                            display: 'inline-flex', alignItems: 'center', gap: '3px',
                            fontSize: '12px', fontWeight: 600,
                            color: pctColor(inst.changePct),
                            background: pctBg(inst.changePct),
                            padding: '2px 6px', borderRadius: '4px',
                            fontVariantNumeric: 'tabular-nums',
                          }}>
                            {arrow(inst.changePct)} {Math.abs(inst.changePct).toFixed(2)}%
                          </span>
                        </td>

                        {/* Bid */}
                        <td style={s.tdMono}>{fmt(inst.bidPrice)}</td>

                        {/* Ask */}
                        <td style={s.tdMono}>{fmt(inst.askPrice)}</td>

                        {/* Spread */}
                        <td style={{ ...s.tdMono, color: '#64748b', fontSize: '11.5px' }}>
                          {spread(inst.bidPrice, inst.askPrice)}
                        </td>

                        {/* Status */}
                        <td style={s.td}>
                          {inst.halted
                            ? <span style={s.haltPill}>HALTED</span>
                            : inst.tradable
                              ? <span style={s.activePill}><span style={{ width: 5, height: 5, borderRadius: '50%', background: '#15803d', display: 'inline-block' }} />ACTIVE</span>
                              : <span style={s.closedPill}>CLOSED</span>
                          }
                        </td>
                      </tr>
                    )
                  })
              }
            </tbody>
          </table>
        </div>

        {/* ────────────────────────── RIGHT: Quote Card ────────────────────────── */}
        <div style={s.card}>
          {!selected ? (
            <div style={s.noSelect}>
              <div style={s.noSelectIcon}>📈</div>
              <div style={s.noSelectTxt}>Select a symbol from the watchlist<br />to view its live quote</div>
            </div>

          ) : detailLoading ? (
            <div style={s.noSelect}>
              <div style={{ ...s.noSelectTxt, fontSize: '12px' }}>Loading {selected}…</div>
            </div>

          ) : !selectedDetail ? (
            <div style={s.noSelect}>
              <div style={{ fontSize: '13px', color: '#ef4444', fontWeight: 600 }}>Failed to load {selected}</div>
            </div>

          ) : (() => {
            const d = selectedDetail
            const halted   = d.halted ?? false
            const tradable = d.tradable ?? false
            const canOrder = (getCurrentUser() === null || getCurrentUser()?.role === 'TRADER') && !halted && tradable

            return (
              <>
                {/* ── Card header ── */}
                <div style={s.cardHeader}>
                  <div style={s.qHeaderTop}>
                    <div>
                      <div style={s.qSymbol}>{d.symbol}</div>
                    </div>
                    <span style={s.statusBadge(halted, tradable)}>
                      {halted ? 'HALTED' : tradable ? 'ACTIVE' : 'CLOSED'}
                    </span>
                  </div>

                  <div style={s.qPriceRow}>
                    <div style={s.qPrice}>${fmt(d.lastPrice)}</div>
                    <span style={s.qChange(d.changePct)}>
                      {arrow(d.changePct)}&nbsp;
                      {d.change >= 0 ? '+' : ''}{fmt(d.change)}&nbsp;
                      ({d.changePct >= 0 ? '+' : ''}{d.changePct.toFixed(2)}%)
                    </span>
                  </div>

                  {/* Sparkline in header */}
                  <div style={{ margin: '10px 0 0', height: 48 }}>
                    <Sparkline prices={d.priceHistory} positive={d.changePct >= 0} />
                  </div>
                </div>

                {/* ── Card body ── */}
                <div style={s.cardBody}>

                  {/* Bid / Ask */}
                  <div style={s.bidAskRow}>
                    <div style={s.bidBox}>
                      <div style={s.baLabel}>Bid</div>
                      <div style={s.baValue('#15803d')}>${fmt(d.bidPrice)}</div>
                    </div>
                    <div style={s.askBox}>
                      <div style={s.baLabel}>Ask</div>
                      <div style={s.baValue('#b91c1c')}>${fmt(d.askPrice)}</div>
                    </div>
                  </div>

                  {/* Spread row */}
                  <div style={s.spreadRow}>
                    <span style={s.spreadLabel}>Spread</span>
                    <span style={s.spreadVal}>{spread(d.bidPrice, d.askPrice)}</span>
                    <span style={s.spreadLabel}>Prev Close</span>
                    <span style={s.spreadVal}>{d.previousClose ? `$${fmt(d.previousClose)}` : '—'}</span>
                  </div>

                  {/* Details grid */}
                  <div style={s.details}>
                    <div>
                      <div style={s.detLabel}>Day High</div>
                      <div style={s.detValue}>{d.dayHigh ? `$${fmt(d.dayHigh)}` : '—'}</div>
                    </div>
                    <div>
                      <div style={s.detLabel}>Day Low</div>
                      <div style={s.detValue}>{d.dayLow ? `$${fmt(d.dayLow)}` : '—'}</div>
                    </div>
                    <div>
                      <div style={s.detLabel}>Min Qty</div>
                      <div style={s.detValue}>{d.minQuantity?.toLocaleString() ?? '—'}</div>
                    </div>
                    <div>
                      <div style={s.detLabel}>Max Qty</div>
                      <div style={s.detValue}>{d.maxQuantity?.toLocaleString() ?? '—'}</div>
                    </div>

                    {/* Last updated — full width */}
                    <div style={s.tsRow}>
                      <span style={s.tsLabel}>Last Updated</span>
                      <span style={s.tsValue}>
                        {new Date(d.dataTimestamp).toLocaleTimeString('en-US', {
                          timeZone: 'America/New_York', hour: '2-digit',
                          minute: '2-digit', second: '2-digit',
                        })} ET
                      </span>
                    </div>
                  </div>

                  <div style={s.divider} />

                  {/* Order button */}
                  {(getCurrentUser() === null || getCurrentUser()?.role === 'TRADER') ? (
                    <button
                      className="mkt-order-btn"
                      onClick={() => navigate(`/orders/new?symbol=${d.symbol}`)}
                      disabled={!canOrder}
                    >
                      {halted ? 'Trading Halted' : !tradable ? 'Market Closed' : `New Order — ${d.symbol}`}
                    </button>
                  ) : (
                    <button className="mkt-order-btn" disabled>
                      Order entry: TRADER role required
                    </button>
                  )}
                </div>
              </>
            )
          })()}
        </div>
        {/* ── end right panel ── */}

      </div>
    </div>
  )
}

export default MarketDataPage
