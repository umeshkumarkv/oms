import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Plus, Search, X } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { getInstruments, getInstrument, getMarketConfig, getTickHistory } from '../api/marketApi'
import { getWatchlist, addToWatchlist, removeFromWatchlist } from '../api/watchlistApi'
import { Instrument, MarketConfig, TickEntry } from '../types/market.types'
import { marketDataWs } from '../services/websocketService'
import { getCurrentUser } from '../api/authApi'
import { IntradayChart, IntradayPoint } from '../components/StockCard'
import { useMarketSocket, TickData } from '../hooks/useMarketSocket'
import { isMarketOpen } from '../utils/marketHours'

// ── Helpers ────────────────────────────────────────────────────────────────

const fmt = (v: string | number, dec = 2): string =>
  Number(v).toFixed(dec)

const spread = (bid: number, ask: number): string =>
  (ask - bid).toFixed(4)

const pctColor = (pct: number): string =>
  pct > 0 ? 'var(--color-success-vivid)' : pct < 0 ? 'var(--color-error-vivid)' : 'var(--color-text-secondary)'

const pctBg = (pct: number): string =>
  pct > 0 ? 'var(--color-success-bg)' : pct < 0 ? 'var(--color-error-bg)' : 'transparent'

const arrow = (pct: number): string =>
  pct > 0 ? '▲' : pct < 0 ? '▼' : '—'


const fmtET = (iso: string) =>
  new Intl.DateTimeFormat('en-US', {
    timeZone: 'America/New_York',
    hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false,
  }).format(new Date(iso)) + ' ET'

/** Deterministic, vivid colour per ticker — no logo files required.
 *  Hashes the symbol string into a fixed palette so the same symbol
 *  always renders with the same colour across the app. */
const SYMBOL_PALETTE = [
  '#0b1220', '#0a6cbd', '#1877f2', '#4338ca', '#7c3aed',
  '#c026d3', '#db2777', '#dc2626', '#ea580c', '#d97706',
  '#65a30d', '#0a8a4a', '#0891b2', '#0e7490', '#475569',
]
const getSymbolColor = (symbol: string): string => {
  let h = 0
  for (let i = 0; i < symbol.length; i++) h = (h * 31 + symbol.charCodeAt(i)) >>> 0
  return SYMBOL_PALETTE[h % SYMBOL_PALETTE.length]
}

/** Build one IntradayPoint per minute from tick history (last price of each minute).
 *  tick.time is already "HH:mm" in ET, as formatted by TickService. */
function candlesFromTicks(ticks: TickEntry[]): IntradayPoint[] {
  if (ticks.length < 2) return []
  const minuteMap = new Map<string, number>()
  for (const tick of ticks) {
    minuteMap.set(tick.time.substring(0, 5), parseFloat(tick.price)) // last price wins per minute
  }
  return Array.from(minuteMap.entries()).map(([time, price]) => ({ time, price }))
}

// ── Styles ─────────────────────────────────────────────────────────────────

const GlobalStyle = () => (
  <style>{`
    .mkt-row { cursor: pointer; transition: background 0.12s; }
    .mkt-row:hover { background: var(--color-bg) !important; }
    .mkt-row.selected { background: var(--color-row-selected) !important; }
    .wl-rm:hover { color: var(--color-error-vivid) !important; background: var(--color-error-bg) !important; }
    .wl-drop-item:hover { background: var(--color-bg); }

    .mkt-table thead th:first-child {
      position: sticky;
      left: 0;
      z-index: 3;
      box-shadow: 2px 0 6px -2px rgba(15,23,42,0.10);
    }
    .mkt-table .mkt-row td:first-child {
      position: sticky;
      left: 0;
      z-index: 1;
      background: var(--color-surface);
      box-shadow: 2px 0 6px -2px rgba(15,23,42,0.08);
    }
    .mkt-table .mkt-row:hover td:first-child { background: var(--color-bg); }
    .mkt-table .mkt-row.selected td:first-child { background: var(--color-row-selected); }

    .mkt-order-btn {
      width: 100%; padding: 11px;
      background: var(--color-buy); color: var(--color-surface);
      border: none; border-radius: 8px;
      font-size: 13px; font-weight: 600;
      cursor: pointer; font-family: inherit;
      transition: background 0.15s, transform 0.1s, box-shadow 0.15s;
      box-shadow: 0 1px 3px rgba(37,99,235,0.25);
      letter-spacing: 0.01em;
    }
    .mkt-order-btn:hover:not(:disabled) {
      background: var(--color-buy);
      transform: translateY(-1px);
      box-shadow: 0 4px 12px rgba(37,99,235,0.35);
    }
    .mkt-order-btn:disabled {
      background: var(--color-border); color: var(--color-text-muted);
      cursor: not-allowed; box-shadow: none;
    }

    @keyframes mkt-skeleton {
      0%, 100% { opacity: 0.5; }
      50%       { opacity: 1; }
    }

    @keyframes mkt-shimmer {
      0%   { background-position: -400px 0; }
      100% { background-position:  400px 0; }
    }

    .mkt-chart-skeleton {
      background: linear-gradient(90deg, var(--color-border-light) 25%, var(--color-border) 50%, var(--color-border-light) 75%);
      background-size: 800px 100%;
      animation: mkt-shimmer 1.4s ease-in-out infinite;
      border-radius: 6px;
      height: 130px;
    }

    /* ── Filter segmented control ── */
    .mkt-seg { display: inline-flex; padding: 3px; background: var(--color-border-light); border-radius: 8px; gap: 1px; }
    .mkt-seg button {
      background: transparent; border: none; color: var(--color-text-secondary);
      font-size: 11.5px; font-weight: 600; padding: 5px 11px; border-radius: 6px;
      cursor: pointer; font-family: inherit; letter-spacing: 0.01em;
      transition: background 0.12s, color 0.12s, box-shadow 0.12s;
      display: inline-flex; align-items: center; gap: 5px;
    }
    .mkt-seg button:hover { color: var(--color-text-primary); }
    .mkt-seg button.active {
      background: var(--color-surface); color: var(--color-text-primary);
      box-shadow: 0 1px 2px rgba(15,23,42,0.06);
    }
    .mkt-seg .seg-count {
      font-size: 10px; font-weight: 700; color: var(--color-text-muted);
      background: var(--color-surface); border-radius: 999px; padding: 0 5px; min-width: 16px; text-align: center;
    }
    .mkt-seg button.active .seg-count { color: var(--color-buy); background: var(--color-selection-bg); }

    /* ── Search shortcut key ── */
    .mkt-kbd {
      position: absolute; right: 8px; top: 50%; transform: translateY(-50%);
      font-size: 10px; color: var(--color-text-secondary); background: var(--color-surface);
      border: 1px solid var(--color-border); border-radius: 4px; padding: 1px 5px;
      font-family: 'Menlo','Consolas',monospace; font-weight: 600;
      pointer-events: none;
    }

    /* ── Symbol avatar ── */
    .mkt-sym-wrap { display: inline-flex; align-items: center; gap: 9px; min-width: 0; }
    .mkt-sym-avatar {
      width: 26px; height: 26px; border-radius: 6px;
      display: inline-flex; align-items: center; justify-content: center;
      font-size: 10px; font-weight: 700; color: var(--color-surface);
      letter-spacing: -0.01em; flex-shrink: 0;
      font-family: 'Inter', system-ui, sans-serif;
    }
  `}</style>
)

const FF = "'Inter', system-ui, sans-serif"

const s = {
  page:        { fontFamily: FF, padding: '24px 28px', background: 'var(--color-border-light)', minHeight: '100vh' } as React.CSSProperties,
  header:      { display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '20px' } as React.CSSProperties,
  titleWrap:   { display: 'flex', alignItems: 'center', gap: '10px' } as React.CSSProperties,
  title:       { margin: 0, fontSize: '18px', fontWeight: 700, color: 'var(--color-text-primary)', letterSpacing: '-0.01em' } as React.CSSProperties,
  headerRight: { display: 'flex', alignItems: 'center', gap: '10px' } as React.CSSProperties,

  pill: (open: boolean) => ({
    display: 'inline-flex', alignItems: 'center', gap: '5px',
    padding: '4px 10px', borderRadius: '20px', fontSize: '11px', fontWeight: 700,
    background: open ? 'var(--color-success-bg)' : 'var(--color-border-light)',
    color:      open ? 'var(--color-success-text)' : 'var(--color-text-secondary)',
    border:    `1px solid ${open ? 'var(--color-success-border)' : 'var(--color-border)'}`,
    letterSpacing: '0.03em',
  } as React.CSSProperties),

  wsPill: (live: boolean) => ({
    display: 'inline-flex', alignItems: 'center', gap: '5px',
    padding: '4px 10px', borderRadius: '20px', fontSize: '11px', fontWeight: 600,
    background: live ? 'var(--color-selection-bg)' : 'var(--color-warning-bg-soft)',
    color:      live ? 'var(--color-buy)' : 'var(--color-warning-dark)',
    border:    `1px solid ${live ? 'var(--color-selection-border)' : 'var(--color-warning-border-light)'}`,
  } as React.CSSProperties),

  pillDot: (color: string) => ({
    width: '6px', height: '6px', borderRadius: '50%', background: color, display: 'inline-block',
  } as React.CSSProperties),

  warnBanner:  {
    display: 'flex', alignItems: 'center', gap: '8px',
    padding: '10px 16px', background: 'var(--color-warning-bg-soft)',
    color: 'var(--color-warning-text)', borderRadius: '8px', marginBottom: '16px',
    fontSize: '12px', fontWeight: 500, border: '1px solid var(--color-warning-border-light)',
  } as React.CSSProperties,
  closedBanner: {
    display: 'flex', alignItems: 'center', gap: '8px',
    padding: '8px 14px', background: 'var(--color-bg)',
    color: 'var(--color-text-secondary)', borderRadius: '6px', marginBottom: '12px',
    fontSize: '12px', fontWeight: 500, border: '1px solid var(--color-border)',
  } as React.CSSProperties,

  grid:        { display: 'grid', gridTemplateColumns: '1fr 400px', gap: '16px', alignItems: 'start' } as React.CSSProperties,

  section:     { background: 'var(--color-surface)', borderRadius: '12px', boxShadow: '0 1px 3px rgba(15,23,42,0.06)', overflowX: 'auto', overflowY: 'hidden' } as React.CSSProperties,
  sHead:       { padding: '14px 20px', borderBottom: '1px solid var(--color-border-light)', display: 'flex', alignItems: 'center', justifyContent: 'space-between', position: 'sticky', left: 0 } as React.CSSProperties,
  sTitleRow:   { display: 'flex', alignItems: 'center', gap: '8px' } as React.CSSProperties,
  sTitle:      { margin: 0, fontSize: '13px', fontWeight: 700, color: 'var(--color-text-primary)', letterSpacing: '-0.01em' } as React.CSSProperties,
  sCount:      { fontSize: '11px', fontWeight: 600, color: 'var(--color-text-secondary)', background: 'var(--color-border-light)', borderRadius: '20px', padding: '1px 8px' } as React.CSSProperties,

  table:    { width: '100%', borderCollapse: 'collapse' as const, fontSize: '12.5px' } as React.CSSProperties,
  th:       { padding: '9px 14px', textAlign: 'left' as const, fontSize: '10.5px', fontWeight: 700, color: 'var(--color-text-muted)', textTransform: 'uppercase' as const, letterSpacing: '0.06em', borderBottom: '1px solid var(--color-border-light)', background: 'var(--color-surface-muted)', whiteSpace: 'nowrap' as const } as React.CSSProperties,
  thRight:  { padding: '9px 14px', textAlign: 'right' as const, fontSize: '10.5px', fontWeight: 700, color: 'var(--color-text-muted)', textTransform: 'uppercase' as const, letterSpacing: '0.06em', borderBottom: '1px solid var(--color-border-light)', background: 'var(--color-surface-muted)', whiteSpace: 'nowrap' as const } as React.CSSProperties,
  td:       { padding: '10px 14px', borderBottom: '1px solid var(--color-bg)', color: 'var(--color-text-body)', whiteSpace: 'nowrap' as const } as React.CSSProperties,
  tdRight:  { padding: '10px 14px', borderBottom: '1px solid var(--color-bg)', color: 'var(--color-text-body)', textAlign: 'right' as const, whiteSpace: 'nowrap' as const } as React.CSSProperties,
  tdMono:   { padding: '10px 14px', borderBottom: '1px solid var(--color-bg)', color: 'var(--color-text-body)', textAlign: 'right' as const, whiteSpace: 'nowrap' as const, fontVariantNumeric: 'tabular-nums' as const, fontFamily: "'Menlo','Consolas',monospace" } as React.CSSProperties,
  symbolCell:  { fontWeight: 700, color: 'var(--color-text-primary)', fontSize: '13px', letterSpacing: '-0.01em' } as React.CSSProperties,
  haltPill:    { display: 'inline-flex', alignItems: 'center', padding: '2px 7px', borderRadius: '4px', fontSize: '10px', fontWeight: 700, background: 'var(--color-error-bg)', color: 'var(--color-error-text)', letterSpacing: '0.03em' } as React.CSSProperties,
  activePill:  { display: 'inline-flex', alignItems: 'center', gap: '4px', padding: '2px 7px', borderRadius: '4px', fontSize: '10px', fontWeight: 700, background: 'var(--color-success-bg)', color: 'var(--color-success-text)', letterSpacing: '0.03em' } as React.CSSProperties,
  closedPill:  { display: 'inline-flex', alignItems: 'center', padding: '2px 7px', borderRadius: '4px', fontSize: '10px', fontWeight: 700, background: 'var(--color-border-light)', color: 'var(--color-text-secondary)', letterSpacing: '0.03em' } as React.CSSProperties,
  skeleton:    { background: 'linear-gradient(90deg, var(--color-border-light) 25%, var(--color-border) 50%, var(--color-border-light) 75%)', backgroundSize: '200% 100%', borderRadius: '4px', height: '13px', animation: 'mkt-skeleton 1.4s ease-in-out infinite' } as React.CSSProperties,

  card:        { background: 'var(--color-surface)', borderRadius: '12px', boxShadow: '0 1px 3px rgba(15,23,42,0.06)', overflow: 'hidden' } as React.CSSProperties,
  cardHeader:  { padding: '16px 20px 0', borderBottom: '1px solid var(--color-border-light)' } as React.CSSProperties,
  noSelect:    { padding: '56px 24px', textAlign: 'center' as const } as React.CSSProperties,
  noSelectIcon:{ fontSize: '32px', marginBottom: '10px', opacity: 0.4 } as React.CSSProperties,
  noSelectTxt: { fontSize: '13px', color: 'var(--color-text-muted)', fontWeight: 500 } as React.CSSProperties,
  qHeaderTop:  { display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', paddingBottom: '14px' } as React.CSSProperties,
  qSymbol:     { fontSize: '20px', fontWeight: 800, color: 'var(--color-text-primary)', letterSpacing: '-0.02em' } as React.CSSProperties,
  qPriceRow:   { display: 'flex', alignItems: 'baseline', gap: '10px', marginTop: '4px' } as React.CSSProperties,
  qPrice:      { fontSize: '32px', fontWeight: 800, color: 'var(--color-text-primary)', letterSpacing: '-0.03em', fontVariantNumeric: 'tabular-nums' as const } as React.CSSProperties,
  qChange:     (pct: number) => ({
    display: 'inline-flex', alignItems: 'center', gap: '4px',
    fontSize: '12px', fontWeight: 600, color: pctColor(pct),
    background: pctBg(pct), padding: '3px 8px', borderRadius: '6px',
  } as React.CSSProperties),
  cardBody:    { padding: '16px 20px' } as React.CSSProperties,
  bidAskRow:   { display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px', marginBottom: '14px' } as React.CSSProperties,
  bidBox:      { background: 'var(--color-bid-bg)', borderRadius: '8px', padding: '10px 12px', border: '1px solid var(--color-bid-border)' } as React.CSSProperties,
  askBox:      { background: 'var(--color-ask-bg)', borderRadius: '8px', padding: '10px 12px', border: '1px solid var(--color-ask-border)' } as React.CSSProperties,
  baLabel:     { fontSize: '10px', fontWeight: 700, letterSpacing: '0.06em', textTransform: 'uppercase' as const, color: 'var(--color-text-muted)', marginBottom: '2px' } as React.CSSProperties,
  baValue:     (color: string) => ({ fontSize: '18px', fontWeight: 700, color, fontVariantNumeric: 'tabular-nums' as const, letterSpacing: '-0.01em' } as React.CSSProperties),
  spreadRow:   { display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '14px', padding: '8px 12px', background: 'var(--color-surface-muted)', borderRadius: '8px' } as React.CSSProperties,
  spreadLabel: { fontSize: '11px', color: 'var(--color-text-muted)', fontWeight: 600 } as React.CSSProperties,
  spreadVal:   { fontSize: '13px', fontWeight: 700, color: 'var(--color-text-body)', fontVariantNumeric: 'tabular-nums' as const } as React.CSSProperties,
  divider:     { height: '1px', background: 'var(--color-border-light)', margin: '14px 0' } as React.CSSProperties,
  tsRow:       { display: 'flex', alignItems: 'center', justifyContent: 'space-between' } as React.CSSProperties,
  tsLabel:     { fontSize: '10px', color: 'var(--color-text-muted)', fontWeight: 700, textTransform: 'uppercase' as const, letterSpacing: '0.06em' } as React.CSSProperties,
  tsValue:     { fontSize: '12px', fontWeight: 600, color: 'var(--color-text-primary)' } as React.CSSProperties,
  statusBadge: (halted: boolean, tradable: boolean) => ({
    display: 'inline-flex', alignItems: 'center', gap: '5px',
    padding: '3px 8px', borderRadius: '6px', fontSize: '11px', fontWeight: 700,
    background: halted ? 'var(--color-error-bg)' : tradable ? 'var(--color-success-bg)' : 'var(--color-border-light)',
    color:      halted ? 'var(--color-error-text)' : tradable ? 'var(--color-success-text)' : 'var(--color-text-secondary)',
  } as React.CSSProperties),

  wlSearchWrap:  { position: 'relative' as const, flex: 1 } as React.CSSProperties,
  wlInput:       { width: '100%', boxSizing: 'border-box' as const, paddingLeft: '28px', paddingRight: '46px', paddingTop: '6px', paddingBottom: '6px', border: '1px solid var(--color-border)', borderRadius: '6px', fontSize: '12px', outline: 'none', background: 'var(--color-surface-muted)', color: 'var(--color-text-primary)', fontFamily: FF } as React.CSSProperties,
  wlInputFull:   { width: '100%', boxSizing: 'border-box' as const, paddingLeft: '28px', paddingRight: '46px', paddingTop: '6px', paddingBottom: '6px', border: '1px solid var(--color-border)', borderRadius: '6px', fontSize: '12px', outline: 'none', background: 'var(--color-border-light)', color: 'var(--color-text-muted)', fontFamily: FF, cursor: 'not-allowed' as const } as React.CSSProperties,
  wlResultsPanel: { borderTop: '1px solid var(--color-border-light)', borderBottom: '2px solid var(--color-border)', maxHeight: '200px', overflowY: 'auto' as const, background: 'var(--color-surface)' } as React.CSSProperties,
  wlDropItem:    { padding: '8px 12px', fontSize: '12px', cursor: 'pointer', color: 'var(--color-text-primary)', fontWeight: 600, borderBottom: '1px solid var(--color-bg)', display: 'flex', alignItems: 'center', justifyContent: 'space-between' } as React.CSSProperties,
  wlErrorBanner: { margin: '8px 0 0', padding: '7px 12px', background: 'var(--color-error-bg)', color: 'var(--color-error-text)', borderRadius: '6px', fontSize: '12px', fontWeight: 500, display: 'flex', alignItems: 'center', justifyContent: 'space-between' } as React.CSSProperties,
  wlFullBanner:  { margin: '8px 0 0', padding: '7px 12px', background: 'var(--color-warning-bg)', color: 'var(--color-warning-text)', borderRadius: '6px', fontSize: '12px', fontWeight: 500 } as React.CSSProperties,
  wlEmpty:       { padding: '48px 24px', textAlign: 'center' as const, color: 'var(--color-text-muted)', fontSize: '13px', fontWeight: 500 } as React.CSSProperties,
  removeBtn:     { background: 'none', border: 'none', cursor: 'pointer', padding: '4px', color: 'var(--color-text-muted)', display: 'inline-flex', alignItems: 'center', borderRadius: '4px', transition: 'color 0.1s, background 0.1s', lineHeight: 0 } as React.CSSProperties,
}

// ── WatchlistPanel ─────────────────────────────────────────────────────────

type WatchlistFilter = 'all' | 'gainers' | 'losers' | 'halted'

interface WatchlistPanelProps {
  instruments:    Instrument[]
  allInstruments: Instrument[]
  loading:        boolean
  selected:       string | null
  ticks:          Record<string, TickData>
  marketOpen:     boolean
  onSelect:       (symbol: string) => void
  wlSymbols:      Set<string>
  wlSymbolsSize:  number
  onAdd:          (symbol: string) => Promise<void>
  onRemove:       (symbol: string) => Promise<void>
  wlApiMissing?:  boolean
}

const WatchlistPanel: React.FC<WatchlistPanelProps> = ({
  instruments, allInstruments, loading, selected, ticks, marketOpen, onSelect,
  wlSymbols, wlSymbolsSize, onAdd, onRemove, wlApiMissing = false,
}) => {
  const [query,         setQuery]         = useState('')
  const [showDrop,      setShowDrop]      = useState(false)
  const [localError,    setLocalError]    = useState<string | null>(null)
  const [adding,        setAdding]        = useState(false)
  const [removingSet,   setRemovingSet]   = useState<Set<string>>(new Set())
  const [filter,        setFilter]        = useState<WatchlistFilter>('all')
  const searchRef                          = useRef<HTMLInputElement>(null)

  const isFull = wlSymbols.size >= 50

  // ⌘K / Ctrl-K focuses the search box
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault()
        searchRef.current?.focus()
      }
    }
    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [])

  // Compute per-symbol changePct using the same logic as the row render,
  // so filter counts/results stay in sync with what the user sees.
  const changePctFor = useCallback((inst: Instrument): number => {
    const tick     = ticks[inst.symbol]
    const livePx   = tick?.price        ?? Number(inst.lastPrice)
    const livePrev = tick?.previousClose ?? Number(inst.previousClose ?? inst.lastPrice)
    const change   = tick?.change       ?? (livePx - livePrev)
    return tick?.changePct ?? (livePrev !== 0 ? (change / livePrev) * 100 : 0)
  }, [ticks])

  // Filter counts (always reflect the full watchlist regardless of active filter)
  const counts = useMemo(() => {
    let g = 0, l = 0, h = 0
    for (const inst of instruments) {
      if (inst.halted) { h++; continue }
      const p = changePctFor(inst)
      if (p > 0) g++
      else if (p < 0) l++
    }
    return { all: instruments.length, gainers: g, losers: l, halted: h }
  }, [instruments, changePctFor])

  // Apply the active filter on top of the watchlist instruments
  const filteredInstruments = useMemo(() => {
    if (filter === 'all') return instruments
    return instruments.filter(inst => {
      if (filter === 'halted') return !!inst.halted
      if (inst.halted) return false
      const p = changePctFor(inst)
      if (filter === 'gainers') return p > 0
      if (filter === 'losers')  return p < 0
      return true
    })
  }, [instruments, filter, changePctFor])

  const dropOptions = useMemo(() =>
    allInstruments
      .filter(i => !wlSymbols.has(i.symbol) && i.symbol.toLowerCase().includes(query.toLowerCase()))
      .slice(0, 10),
    [allInstruments, wlSymbols, query]
  )

  // Auto-clear error after 4 s
  useEffect(() => {
    if (!localError) return
    const t = setTimeout(() => setLocalError(null), 4000)
    return () => clearTimeout(t)
  }, [localError])

  const handleAdd = async (symbol: string) => {
    setQuery('')
    setShowDrop(false)
    setAdding(true)
    try {
      await onAdd(symbol)
      setLocalError(null)
    } catch (err: any) {
      const code = err?.response?.data?.error?.code
      if (code === 'SYMBOL_ALREADY_IN_WATCHLIST') setLocalError('Symbol already in watchlist')
      else if (code === 'SYMBOL_NOT_FOUND')        setLocalError('Symbol not found')
      else if (code === 'WATCHLIST_LIMIT_EXCEEDED') setLocalError('Watchlist full (50/50)')
      else                                          setLocalError('Failed to add symbol')
    } finally {
      setAdding(false)
    }
  }

  const handleRemove = async (e: React.MouseEvent, symbol: string) => {
    e.stopPropagation()
    setRemovingSet(prev => new Set([...prev, symbol]))
    try {
      await onRemove(symbol)
    } catch {
      setLocalError(`Failed to remove ${symbol}`)
    } finally {
      setRemovingSet(prev => { const next = new Set(prev); next.delete(symbol); return next })
    }
  }

  return (
    <div style={s.section}>
      <div style={{ ...s.sHead, flexDirection: 'column', alignItems: 'stretch', gap: 0 }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12, flexWrap: 'wrap' }}>
          <div style={s.sTitleRow}>
            <h2 style={s.sTitle}>Watchlist</h2>
            {!loading && (
              <span style={s.sCount}>
                {isFull ? '50/50' : `${wlSymbolsSize}`}
              </span>
            )}
          </div>

          {/* ── Filter tabs ─────────────────────────────────────────────── */}
          {!loading && instruments.length > 0 && (
            <div className="mkt-seg" role="tablist" aria-label="Watchlist filter">
              <button
                className={filter === 'all' ? 'active' : ''}
                onClick={() => setFilter('all')}
                role="tab"
                aria-selected={filter === 'all'}
              >
                All <span className="seg-count">{counts.all}</span>
              </button>
              <button
                className={filter === 'gainers' ? 'active' : ''}
                onClick={() => setFilter('gainers')}
                role="tab"
                aria-selected={filter === 'gainers'}
              >
                <span style={{ color: filter === 'gainers' ? 'var(--color-success)' : undefined }}>▲</span>
                Gainers <span className="seg-count">{counts.gainers}</span>
              </button>
              <button
                className={filter === 'losers' ? 'active' : ''}
                onClick={() => setFilter('losers')}
                role="tab"
                aria-selected={filter === 'losers'}
              >
                <span style={{ color: filter === 'losers' ? 'var(--color-error)' : undefined }}>▼</span>
                Losers <span className="seg-count">{counts.losers}</span>
              </button>
              <button
                className={filter === 'halted' ? 'active' : ''}
                onClick={() => setFilter('halted')}
                role="tab"
                aria-selected={filter === 'halted'}
              >
                Halted <span className="seg-count">{counts.halted}</span>
              </button>
            </div>
          )}
        </div>

        {/* Search / Add row — hidden when watchlist API is not available */}
        {!wlApiMissing && <div style={{ marginTop: '10px' }}>
          <div style={s.wlSearchWrap}>
            <Search
              size={13} strokeWidth={2}
              style={{ position: 'absolute', left: 9, top: '50%', transform: 'translateY(-50%)', color: 'var(--color-text-muted)', pointerEvents: 'none' }}
            />
            <input
              ref={searchRef}
              style={isFull ? s.wlInputFull : s.wlInput}
              placeholder={isFull ? 'Watchlist full (50/50)' : adding ? 'Adding…' : 'Search symbol to add…'}
              disabled={isFull || adding}
              value={query}
              onChange={e => { setQuery(e.target.value); setShowDrop(true) }}
              onFocus={() => { if (query) setShowDrop(true) }}
              onBlur={() => setTimeout(() => setShowDrop(false), 150)}
            />
            {!isFull && !adding && <span className="mkt-kbd">⌘K</span>}
          </div>
        </div>}

        {!wlApiMissing && isFull && <div style={s.wlFullBanner}>Watchlist full (50/50) — remove a symbol to add another</div>}
        {localError && (
          <div style={s.wlErrorBanner}>
            <span>{localError}</span>
            <button style={s.removeBtn} onClick={() => setLocalError(null)} aria-label="Dismiss error">
              <X size={12} strokeWidth={2.5} />
            </button>
          </div>
        )}
      </div>

      {/* In-flow search results — pushes the table down while searching */}
      {!wlApiMissing && showDrop && !isFull && dropOptions.length > 0 && (
        <div style={s.wlResultsPanel}>
          {dropOptions.map(opt => (
            <div
              key={opt.symbol}
              className="wl-drop-item"
              style={s.wlDropItem}
              onMouseDown={() => handleAdd(opt.symbol)}
            >
              <span style={{ display: 'inline-flex', alignItems: 'center', gap: 9 }}>
                <span className="mkt-sym-avatar" style={{ background: getSymbolColor(opt.symbol), width: 22, height: 22, fontSize: 9 }}>
                  {opt.symbol.slice(0, 2)}
                </span>
                {opt.symbol}
              </span>
              <span style={{ display: 'inline-flex', alignItems: 'center', gap: '4px', color: 'var(--color-text-muted)', fontSize: '11px', fontWeight: 500 }}>
                {opt.halted ? <span style={{ color: 'var(--color-error)', fontWeight: 700 }}>HALTED</span> : <span style={{ color: 'var(--color-success)' }}>ACTIVE</span>}
                <Plus size={12} strokeWidth={2.5} style={{ color: 'var(--color-primary)' }} />
              </span>
            </div>
          ))}
        </div>
      )}

      <table style={s.table} className="mkt-table">
        <thead>
          <tr>
            <th style={s.th}>Symbol</th>
            <th style={s.thRight}>Live Price</th>
            <th style={s.thRight}>Prev Close</th>
            <th style={s.thRight}>Change</th>
            <th style={s.thRight}>Chg %</th>
            <th style={s.thRight}>Bid</th>
            <th style={s.thRight}>Ask</th>
            <th style={s.thRight}>Volume</th>
            <th style={s.thRight}>Spread</th>
            <th style={s.thRight}>Day High</th>
            <th style={s.thRight}>Day Low</th>
            <th style={s.th}>Status</th>
            <th style={s.thRight}>Last Updated</th>
            <th style={s.th}></th>
          </tr>
        </thead>
        <tbody>
          {loading
            ? Array.from({ length: 8 }).map((_, i) => (
                <tr key={i}>
                  {Array.from({ length: 14 }).map((_, j) => (
                    <td key={j} style={s.td}><div style={s.skeleton} /></td>
                  ))}
                </tr>
              ))
            : filteredInstruments.length === 0
              ? (
                  <tr>
                    <td colSpan={14} style={s.wlEmpty}>
                      {instruments.length === 0
                        ? 'Search above to add instruments to your watchlist'
                        : filter === 'gainers' ? 'No gainers right now'
                        : filter === 'losers'  ? 'No losers right now'
                        : filter === 'halted'  ? 'No halted symbols'
                        : 'No symbols match the current filter'}
                    </td>
                  </tr>
                )
              : filteredInstruments.map(inst => {
                  const tick        = ticks[inst.symbol]
                  const livePx      = tick?.price         ?? Number(inst.lastPrice)
                  const liveBid     = tick?.bid            ?? Number(inst.bidPrice)
                  const liveAsk     = tick?.ask            ?? Number(inst.askPrice)
                  const liveTs      = tick?.timestamp      ?? inst.dataTimestamp
                  const livePrev    = tick?.previousClose  ?? Number(inst.previousClose ?? inst.lastPrice)
                  const liveDayHigh = tick?.dayHigh        ?? (inst.dayHigh ? Number(inst.dayHigh) : null)
                  const liveDayLow  = tick?.dayLow         ?? (inst.dayLow  ? Number(inst.dayLow)  : null)
                  const liveVolume  = tick?.volume         ?? inst.volume   ?? null
                  const change      = tick?.change         ?? (livePx - livePrev)
                  const changePct   = tick?.changePct      ?? (livePrev !== 0 ? (change / livePrev) * 100 : 0)
                  const removing    = removingSet.has(inst.symbol)

                  return (
                    <tr
                      key={inst.symbol}
                      className={`mkt-row${selected === inst.symbol ? ' selected' : ''}`}
                      onClick={() => onSelect(inst.symbol)}
                      style={{ opacity: removing ? 0.4 : 1, transition: 'opacity 0.2s' }}
                    >
                      <td style={s.td}>
                        <span className="mkt-sym-wrap">
                          <span className="mkt-sym-avatar" style={{ background: getSymbolColor(inst.symbol) }}>
                            {inst.symbol.slice(0, 2)}
                          </span>
                          <span style={s.symbolCell}>{inst.symbol}</span>
                        </span>
                      </td>

                      <td style={s.tdMono}><strong>{fmt(livePx)}</strong></td>

                      <td style={s.tdMono}>{livePrev ? `$${fmt(livePrev)}` : '—'}</td>

                      <td style={{ ...s.tdRight, fontVariantNumeric: 'tabular-nums', fontFamily: "'Menlo','Consolas',monospace" }}>
                        <span style={{ fontWeight: 600, color: pctColor(change) }}>
                          {change >= 0 ? '+' : ''}{fmt(change)}
                        </span>
                      </td>

                      <td style={s.tdRight}>
                        <span style={{
                          display: 'inline-flex', alignItems: 'center', gap: '3px',
                          fontSize: '12px', fontWeight: 600, color: pctColor(changePct),
                          background: pctBg(changePct), padding: '2px 6px', borderRadius: '4px',
                          fontVariantNumeric: 'tabular-nums',
                        }}>
                          {arrow(changePct)} {Math.abs(changePct).toFixed(2)}%
                        </span>
                      </td>

                      <td style={s.tdMono}>{fmt(liveBid)}</td>
                      <td style={s.tdMono}>{fmt(liveAsk)}</td>

                      <td style={{ ...s.tdMono, color: 'var(--color-text-primary)' }}>
                        {liveVolume != null ? liveVolume.toLocaleString() : '—'}
                      </td>

                      <td style={{ ...s.tdMono, color: 'var(--color-text-secondary)', fontSize: '11.5px' }}>
                        {spread(liveBid, liveAsk)}
                      </td>

                      <td style={{ ...s.tdMono, color: 'var(--color-success)' }}>
                        {liveDayHigh != null ? `$${fmt(liveDayHigh)}` : '—'}
                      </td>
                      <td style={{ ...s.tdMono, color: 'var(--color-error)' }}>
                        {liveDayLow != null ? `$${fmt(liveDayLow)}` : '—'}
                      </td>

                      <td style={s.td}>
                        {inst.halted
                          ? <span style={s.haltPill}>HALTED</span>
                          : inst.tradable
                            ? <span style={s.activePill}><span style={{ width: 5, height: 5, borderRadius: '50%', background: 'var(--color-success)', display: 'inline-block' }} />ACTIVE</span>
                            : <span style={s.closedPill}>CLOSED</span>
                        }
                      </td>

                      <td style={{ ...s.tdMono, color: marketOpen ? 'var(--color-text-primary)' : 'var(--color-text-muted)' }}>
                        {fmtET(liveTs)}
                      </td>

                      <td style={{ ...s.td, textAlign: 'center', padding: '10px 10px' }}>
                        {!wlApiMissing && (
                          <button
                            className="wl-rm"
                            style={s.removeBtn}
                            onClick={e => handleRemove(e, inst.symbol)}
                            disabled={removing}
                            aria-label={`Remove ${inst.symbol}`}
                          >
                            <X size={13} strokeWidth={2.5} />
                          </button>
                        )}
                      </td>
                    </tr>
                  )
                })
          }
        </tbody>
      </table>
    </div>
  )
}

// ── StockDetailPanel ───────────────────────────────────────────────────────

interface StockDetailPanelProps {
  selected:       string | null
  selectedBase:   Instrument | null   // kept fresh by the unified poll
  detailLoading:  boolean
  candles:        IntradayPoint[]     // from unified poll — in sync with watchlist
  candlesLoading: boolean
  ticks:          Record<string, TickData>
  marketOpen:     boolean
  wsLive:         boolean
  onNavigate:     (path: string) => void
}

const StockDetailPanel: React.FC<StockDetailPanelProps> = ({
  selected, selectedBase, detailLoading, candles, candlesLoading, ticks, marketOpen, wsLive, onNavigate,
}) => {
  if (!selected) {
    return (
      <div style={s.card}>
        <div style={s.noSelect}>
          <div style={s.noSelectIcon}>📈</div>
          <div style={s.noSelectTxt}>Select a symbol from the watchlist<br />to view its live quote</div>
        </div>
      </div>
    )
  }

  if (detailLoading) {
    return (
      <div style={s.card}>
        <div style={s.noSelect}>
          <div style={{ ...s.noSelectTxt, fontSize: '12px' }}>Loading {selected}…</div>
        </div>
      </div>
    )
  }

  if (!selectedBase) {
    return (
      <div style={s.card}>
        <div style={s.noSelect}>
          <div style={{ fontSize: '13px', color: 'var(--color-error)', fontWeight: 600 }}>Failed to load {selected}</div>
        </div>
      </div>
    )
  }

  // WS tick overrides REST; REST (selectedBase) is kept fresh by the unified poll
  const tick      = ticks[selected]
  const livePx    = tick?.price     ?? Number(selectedBase.lastPrice)
  const liveBid   = tick?.bid       ?? Number(selectedBase.bidPrice)
  const liveAsk   = tick?.ask       ?? Number(selectedBase.askPrice)
  const liveTs    = tick?.timestamp ?? selectedBase.dataTimestamp
  const prevClose = tick?.previousClose ?? Number(selectedBase.previousClose ?? selectedBase.lastPrice)
  const change    = livePx - prevClose
  const changePct = prevClose !== 0 ? (change / prevClose) * 100 : 0
  const halted    = selectedBase.halted   ?? false
  const tradable  = selectedBase.tradable ?? false
  const canOrder  = (getCurrentUser() === null || getCurrentUser()?.role === 'TRADER') && !halted && tradable

  // Show only the last 60 minutes so the Y scale is tight around recent prices,
  // making live tick movements clearly visible (full-day scale = < 1px per tick).
  const recentCandles = candles.length > 60 ? candles.slice(-60) : candles
  const hasChartData  = recentCandles.length >= 2

  return (
    <div style={s.card}>
      <div style={s.cardHeader}>
        <div style={s.qHeaderTop}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <span
              className="mkt-sym-avatar"
              style={{ background: getSymbolColor(selectedBase.symbol), width: 34, height: 34, fontSize: 12, borderRadius: 8 }}
            >
              {selectedBase.symbol.slice(0, 2)}
            </span>
            <div style={s.qSymbol}>{selectedBase.symbol}</div>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            {!marketOpen && (
              <span style={{
                display: 'inline-flex', alignItems: 'center', gap: '4px',
                padding: '3px 8px', borderRadius: '6px', fontSize: '10px', fontWeight: 700,
                background: 'var(--color-border-light)', color: 'var(--color-text-secondary)', letterSpacing: '0.04em',
              }}>
                MARKET CLOSED
              </span>
            )}
            <span style={s.statusBadge(halted, tradable)}>
              {halted ? 'HALTED' : tradable ? 'ACTIVE' : 'CLOSED'}
            </span>
          </div>
        </div>

        <div style={s.qPriceRow}>
          <div style={s.qPrice}>${fmt(livePx)}</div>
          <span style={s.qChange(changePct)}>
            {arrow(changePct)}&nbsp;
            {change >= 0 ? '+' : ''}{fmt(change)}&nbsp;
            ({changePct >= 0 ? '+' : ''}{changePct.toFixed(2)}%)
          </span>
        </div>

        <div style={{ margin: '10px -4px 0' }}>
          {candlesLoading && !hasChartData
            ? <div className="mkt-chart-skeleton" />
            : hasChartData
              ? <IntradayChart data={recentCandles} symbol={selectedBase.symbol} positive={changePct >= 0} live={wsLive && hasChartData && marketOpen} />
              : (
                  <div style={{ height: '130px', display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'var(--color-text-muted)', fontSize: '12px', fontWeight: 500 }}>
                    No intraday data yet — simulation accumulates ticks while market is open
                  </div>
                )
          }
        </div>
      </div>

      <div style={s.cardBody}>
        {!marketOpen && (
          <div style={s.closedBanner}>
            <span>🕐</span>
            <span>Chart shows last known data. Polling paused — market is closed.</span>
          </div>
        )}

        <div style={s.bidAskRow}>
          <div style={s.bidBox}>
            <div style={s.baLabel}>Bid</div>
            <div style={s.baValue('var(--color-success)')}>${fmt(liveBid)}</div>
          </div>
          <div style={s.askBox}>
            <div style={s.baLabel}>Ask</div>
            <div style={s.baValue('var(--color-error)')}>${fmt(liveAsk)}</div>
          </div>
        </div>

        <div style={s.spreadRow}>
          <span style={s.spreadLabel}>Spread</span>
          <span style={s.spreadVal}>{spread(liveBid, liveAsk)}</span>
          <span style={s.spreadLabel}>Prev Close</span>
          <span style={s.spreadVal}>{prevClose ? `$${fmt(prevClose)}` : '—'}</span>
        </div>

        <div style={s.spreadRow}>
          <span style={s.spreadLabel}>Last Updated</span>
          <span style={s.spreadVal}>{fmtET(liveTs)}</span>
          <span style={s.spreadLabel}>Volume</span>
          <span style={s.spreadVal}>
            {(tick?.volume ?? selectedBase.volume) != null
              ? (tick?.volume ?? selectedBase.volume)!.toLocaleString()
              : '—'}
          </span>
        </div>

        <div style={s.divider} />

        {(getCurrentUser() === null || getCurrentUser()?.role === 'TRADER') ? (
          <button
            className="mkt-order-btn"
            onClick={() => onNavigate(`/orders/new?symbol=${selectedBase.symbol}`)}
            disabled={!canOrder}
          >
            {halted ? 'Trading Halted' : !tradable ? 'Market Closed' : `New Order — ${selectedBase.symbol}`}
          </button>
        ) : (
          <button className="mkt-order-btn" disabled>
            Order entry: TRADER role required
          </button>
        )}
      </div>
    </div>
  )
}

// ── MarketDataPage ─────────────────────────────────────────────────────────

const MarketDataPage: React.FC = () => {
  const navigate = useNavigate()

  const [config,         setConfig]         = useState<MarketConfig | null>(null)
  const [open,           setOpen]           = useState<boolean>(isMarketOpen()) // pre-config fallback
  const [instruments,    setInstruments]    = useState<Instrument[]>([])
  const [wlSymbols,      setWlSymbols]      = useState<Set<string>>(new Set())
  const [wlLoaded,       setWlLoaded]       = useState(false)
  const [wlApiMissing,   setWlApiMissing]   = useState(false)
  const [selected,       setSelected]       = useState<string | null>(null)
  const [selectedBase,   setSelectedBase]   = useState<Instrument | null>(null)
  const [detailLoading,  setDetailLoading]  = useState(false)
  const [loading,        setLoading]        = useState(true)
  const [candles,        setCandles]        = useState<IntradayPoint[]>([])
  const [candlesLoading, setCandlesLoading] = useState(false)

  // Show all instruments when watchlist API is unavailable (backend not rebuilt yet)
  const watchlistInstruments = useMemo(
    () => wlApiMissing ? instruments : instruments.filter(i => wlSymbols.has(i.symbol)),
    [instruments, wlSymbols, wlApiMissing]
  )

  const watchlistSymbols = useMemo(() => watchlistInstruments.map(i => i.symbol), [watchlistInstruments])
  const { ticks, wsLive } = useMarketSocket(watchlistSymbols)

  // Refs so polls always read the latest values without re-registering the interval
  const selectedRef = useRef(selected)
  const openRef     = useRef(open)
  const wsLiveRef   = useRef(wsLive)
  selectedRef.current = selected
  openRef.current     = open
  wsLiveRef.current   = wsLive

  // ── Initial load ──────────────────────────────────────────────────────────

  useEffect(() => {
    Promise.all([getMarketConfig(), getInstruments()]).then(([cfg, insts]) => {
      setConfig(cfg)
      setOpen(cfg.marketOpen)
      setInstruments(insts as Instrument[])
      setLoading(false)
    })
    // Load watchlist independently — non-blocking
    getWatchlist()
      .then(wl => {
        setWlSymbols(new Set(wl.map(e => e.symbol)))
        setWlLoaded(true)
      })
      .catch((err) => {
        // 404 / 503 = backend not rebuilt yet → fall back to showing all instruments
        const status = err?.response?.status
        if (!status || status === 404 || status === 503) {
          setWlApiMissing(true)
        }
        setWlLoaded(true)
      })
  }, [])

  // ── Unified poll ──────────────────────────────────────────────────────────
  //
  // ONE timer — ONE Promise.all — watchlist and chart always come from the
  // same response batch so they stay in sync.
  // Restarts automatically when config.pollingIntervalMs changes (only once on first load).

  useEffect(() => {
    const intervalMs = config?.pollingIntervalMs ?? 5_000

    const poll = async () => {
      if (wsLiveRef.current) return  // WS live — all price data comes from socket
      try {
        const sel = selectedRef.current
        const [insts, history] = await Promise.all([
          getInstruments(),
          sel ? getTickHistory(sel) : Promise.resolve(null),
        ])
        setInstruments(insts as Instrument[])
        if (sel) {
          const updated = (insts as Instrument[]).find(i => i.symbol === sel) ?? null
          setSelectedBase(updated)
        }
        if (history && history.ticks.length >= 2) {
          setCandles(candlesFromTicks(history.ticks))
        }
      } catch { /* silent — next tick retries */ }
    }

    const id = setInterval(() => {
      if (!openRef.current || document.visibilityState !== 'visible') return
      poll()
    }, intervalMs)

    const handleVisibility = () => {
      if (document.visibilityState === 'visible') poll()
    }
    document.addEventListener('visibilitychange', handleVisibility)

    return () => {
      clearInterval(id)
      document.removeEventListener('visibilitychange', handleVisibility)
    }
  }, [config?.pollingIntervalMs]) // restarts when polling interval arrives from API

  // Merge live WS ticks into the per-minute candles array.
  // Same minute → update the last point's price in place.
  // New minute  → append a new point.
  // This keeps the chart live (updates every second) while preserving
  // the full intraday session view (one point per minute).
  useEffect(() => {
    const off = marketDataWs.onTick(tick => {
      if (tick.symbol !== selectedRef.current) return
      // Convert UTC dataTimestamp → ET "HH:mm" to match the REST tick history format
      const time = new Intl.DateTimeFormat('en-US', {
        timeZone: 'America/New_York',
        hour: '2-digit', minute: '2-digit', hour12: false,
      }).format(new Date(tick.dataTimestamp))
      const price = Number(tick.lastPrice)
      setCandles(prev => {
        if (prev.length === 0) return [{ time, price }]
        const last = prev[prev.length - 1]
        if (last.time === time) {
          // Same minute — update last point with latest price
          return [...prev.slice(0, -1), { time, price }]
        }
        // New minute — append
        return [...prev, { time, price }]
      })
    })
    return off
  }, [])

  // Update market-open flag on WS market-status events
  useEffect(() => {
    const off = marketDataWs.onMarketStatus(ev => {
      setOpen(ev.type === 'MARKET_OPEN')
    })
    return off
  }, [])

  // ── Watchlist add / remove ────────────────────────────────────────────────

  const handleAddToWatchlist = useCallback(async (symbol: string): Promise<void> => {
    await addToWatchlist(symbol)
    setWlSymbols(prev => new Set([...prev, symbol]))
  }, [])

  const handleRemoveFromWatchlist = useCallback(async (symbol: string): Promise<void> => {
    await removeFromWatchlist(symbol)
    setWlSymbols(prev => { const next = new Set(prev); next.delete(symbol); return next })
  }, [])

  // ── Symbol selection ──────────────────────────────────────────────────────
  // Step 1: load tick history for the chart
  // Step 2: WS subscription is handled automatically by useMarketSocket

  const handleSelectSymbol = useCallback(async (symbol: string) => {
    setSelected(symbol)
    // Show cached data immediately while fresh data loads
    const existing = instruments.find(i => i.symbol === symbol) ?? null
    setSelectedBase(existing)
    setDetailLoading(!existing)
    setCandlesLoading(true)
    setCandles([])

    // Call GET /api/v1/market/{symbol} and GET /api/v1/market/ticks/history in parallel.
    // allSettled so a failure of one does not cancel the other.
    const [detailRes, historyRes] = await Promise.allSettled([
      getInstrument(symbol),
      getTickHistory(symbol),
    ])

    if (detailRes.status === 'fulfilled' && detailRes.value) {
      setSelectedBase(detailRes.value)
    }
    if (historyRes.status === 'fulfilled') {
      setCandles(candlesFromTicks(historyRes.value.ticks))
    }
    setDetailLoading(false)
    setCandlesLoading(false)
  }, [instruments])

  // ── Render ────────────────────────────────────────────────────────────────

  return (
    <div style={s.page}>
      <GlobalStyle />

      <div style={s.header}>
        <div style={s.titleWrap}>
          <h1 style={s.title}>Market Data</h1>
        </div>
        <div style={s.headerRight}>
          {config && (
            <span style={s.pill(open)}>
              <span style={s.pillDot(open ? 'var(--color-success)' : 'var(--color-text-muted)')} />
              {open ? 'MARKET OPEN' : 'MARKET CLOSED'}
            </span>
          )}
          <span style={s.wsPill(wsLive)}>
            <span style={s.pillDot(wsLive ? 'var(--color-primary)' : 'var(--color-warning)')} />
            {wsLive ? 'Live' : `Polling ${(config?.pollingIntervalMs ?? 5000) / 1000}s`}
          </span>
        </div>
      </div>

      {!wsLive && (
        <div style={s.warnBanner}>
          <span>⚠️</span>
          <span>
            WebSocket offline — polling every {(config?.pollingIntervalMs ?? 5000) / 1000}s.
            Watchlist and chart update together from the same snapshot.
          </span>
        </div>
      )}

      <div style={s.grid}>
        <WatchlistPanel
          instruments={watchlistInstruments}
          allInstruments={instruments}
          loading={loading || !wlLoaded}
          selected={selected}
          ticks={ticks}
          marketOpen={open}
          onSelect={handleSelectSymbol}
          wlSymbols={wlSymbols}
          wlSymbolsSize={wlApiMissing ? watchlistInstruments.length : wlSymbols.size}
          onAdd={handleAddToWatchlist}
          onRemove={handleRemoveFromWatchlist}
          wlApiMissing={wlApiMissing}
        />

        <StockDetailPanel
          selected={selected}
          selectedBase={selectedBase}
          detailLoading={detailLoading}
          candles={candles}
          candlesLoading={candlesLoading}
          ticks={ticks}
          marketOpen={open}
          wsLive={wsLive}
          onNavigate={navigate}
        />
      </div>
    </div>
  )
}

export default MarketDataPage
