import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { getInstruments, getMarketConfig, getTickHistory } from '../api/marketApi'
import { Instrument, MarketConfig, TickEntry } from '../types/market.types'
import { marketDataWs } from '../services/websocketService'
import { getCurrentUser } from '../api/authApi'
import { IntradayChart, generateIntraday, IntradayPoint } from '../components/StockCard'
import { useMarketSocket } from '../hooks/useMarketSocket'
import { isMarketOpen } from '../utils/marketHours'

// ── Helpers ────────────────────────────────────────────────────────────────

const fmt = (v: string | number, dec = 2): string =>
  Number(v).toFixed(dec)

const spread = (bid: number, ask: number): string =>
  (ask - bid).toFixed(4)

const pctColor = (pct: number): string =>
  pct > 0 ? '#10b981' : pct < 0 ? '#ef4444' : '#64748b'

const pctBg = (pct: number): string =>
  pct > 0 ? 'rgba(16,185,129,0.08)' : pct < 0 ? 'rgba(239,68,68,0.08)' : 'transparent'

const arrow = (pct: number): string =>
  pct > 0 ? '▲' : pct < 0 ? '▼' : '—'

const fmtET = (iso: string) =>
  new Intl.DateTimeFormat('en-US', {
    timeZone: 'America/New_York',
    hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false,
  }).format(new Date(iso)) + ' ET'

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

    @keyframes mkt-skeleton {
      0%, 100% { opacity: 0.5; }
      50%       { opacity: 1; }
    }

    @keyframes mkt-shimmer {
      0%   { background-position: -400px 0; }
      100% { background-position:  400px 0; }
    }

    .mkt-chart-skeleton {
      background: linear-gradient(90deg, #f1f5f9 25%, #e2e8f0 50%, #f1f5f9 75%);
      background-size: 800px 100%;
      animation: mkt-shimmer 1.4s ease-in-out infinite;
      border-radius: 6px;
      height: 130px;
    }
  `}</style>
)

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
    fontSize: '12px', fontWeight: 500, border: '1px solid #fde68a',
  } as React.CSSProperties,
  closedBanner: {
    display: 'flex', alignItems: 'center', gap: '8px',
    padding: '8px 14px', background: '#f8fafc',
    color: '#64748b', borderRadius: '6px', marginBottom: '12px',
    fontSize: '12px', fontWeight: 500, border: '1px solid #e2e8f0',
  } as React.CSSProperties,

  grid:        { display: 'grid', gridTemplateColumns: '1fr 400px', gap: '16px', alignItems: 'start' } as React.CSSProperties,

  section:     { background: '#fff', borderRadius: '12px', boxShadow: '0 1px 3px rgba(15,23,42,0.06)', overflow: 'hidden' } as React.CSSProperties,
  sHead:       { padding: '14px 20px', borderBottom: '1px solid #f1f5f9', display: 'flex', alignItems: 'center', justifyContent: 'space-between' } as React.CSSProperties,
  sTitleRow:   { display: 'flex', alignItems: 'center', gap: '8px' } as React.CSSProperties,
  sTitle:      { margin: 0, fontSize: '13px', fontWeight: 700, color: '#0f172a', letterSpacing: '-0.01em' } as React.CSSProperties,
  sCount:      { fontSize: '11px', fontWeight: 600, color: '#64748b', background: '#f1f5f9', borderRadius: '20px', padding: '1px 8px' } as React.CSSProperties,

  table:    { width: '100%', borderCollapse: 'collapse' as const, fontSize: '12.5px' } as React.CSSProperties,
  th:       { padding: '9px 14px', textAlign: 'left' as const, fontSize: '10.5px', fontWeight: 700, color: '#94a3b8', textTransform: 'uppercase' as const, letterSpacing: '0.06em', borderBottom: '1px solid #f1f5f9', background: '#fafafa', whiteSpace: 'nowrap' as const } as React.CSSProperties,
  thRight:  { padding: '9px 14px', textAlign: 'right' as const, fontSize: '10.5px', fontWeight: 700, color: '#94a3b8', textTransform: 'uppercase' as const, letterSpacing: '0.06em', borderBottom: '1px solid #f1f5f9', background: '#fafafa', whiteSpace: 'nowrap' as const } as React.CSSProperties,
  td:       { padding: '10px 14px', borderBottom: '1px solid #f8fafc', color: '#334155', whiteSpace: 'nowrap' as const } as React.CSSProperties,
  tdRight:  { padding: '10px 14px', borderBottom: '1px solid #f8fafc', color: '#334155', textAlign: 'right' as const, whiteSpace: 'nowrap' as const } as React.CSSProperties,
  tdMono:   { padding: '10px 14px', borderBottom: '1px solid #f8fafc', color: '#334155', textAlign: 'right' as const, whiteSpace: 'nowrap' as const, fontVariantNumeric: 'tabular-nums' as const, fontFamily: "'Menlo','Consolas',monospace" } as React.CSSProperties,
  symbolCell:  { fontWeight: 700, color: '#0f172a', fontSize: '13px', letterSpacing: '-0.01em' } as React.CSSProperties,
  haltPill:    { display: 'inline-flex', alignItems: 'center', padding: '2px 7px', borderRadius: '4px', fontSize: '10px', fontWeight: 700, background: '#fee2e2', color: '#b91c1c', letterSpacing: '0.03em' } as React.CSSProperties,
  activePill:  { display: 'inline-flex', alignItems: 'center', gap: '4px', padding: '2px 7px', borderRadius: '4px', fontSize: '10px', fontWeight: 700, background: '#dcfce7', color: '#15803d', letterSpacing: '0.03em' } as React.CSSProperties,
  closedPill:  { display: 'inline-flex', alignItems: 'center', padding: '2px 7px', borderRadius: '4px', fontSize: '10px', fontWeight: 700, background: '#f1f5f9', color: '#64748b', letterSpacing: '0.03em' } as React.CSSProperties,
  skeleton:    { background: 'linear-gradient(90deg,#f1f5f9 25%,#e2e8f0 50%,#f1f5f9 75%)', backgroundSize: '200% 100%', borderRadius: '4px', height: '13px', animation: 'mkt-skeleton 1.4s ease-in-out infinite' } as React.CSSProperties,

  card:        { background: '#fff', borderRadius: '12px', boxShadow: '0 1px 3px rgba(15,23,42,0.06)', overflow: 'hidden' } as React.CSSProperties,
  cardHeader:  { padding: '16px 20px 0', borderBottom: '1px solid #f1f5f9' } as React.CSSProperties,
  noSelect:    { padding: '56px 24px', textAlign: 'center' as const } as React.CSSProperties,
  noSelectIcon:{ fontSize: '32px', marginBottom: '10px', opacity: 0.4 } as React.CSSProperties,
  noSelectTxt: { fontSize: '13px', color: '#94a3b8', fontWeight: 500 } as React.CSSProperties,
  qHeaderTop:  { display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', paddingBottom: '14px' } as React.CSSProperties,
  qSymbol:     { fontSize: '20px', fontWeight: 800, color: '#0f172a', letterSpacing: '-0.02em' } as React.CSSProperties,
  qPriceRow:   { display: 'flex', alignItems: 'baseline', gap: '10px', marginTop: '4px' } as React.CSSProperties,
  qPrice:      { fontSize: '32px', fontWeight: 800, color: '#0f172a', letterSpacing: '-0.03em', fontVariantNumeric: 'tabular-nums' as const } as React.CSSProperties,
  qChange:     (pct: number) => ({
    display: 'inline-flex', alignItems: 'center', gap: '4px',
    fontSize: '12px', fontWeight: 600, color: pctColor(pct),
    background: pctBg(pct), padding: '3px 8px', borderRadius: '6px',
  } as React.CSSProperties),
  cardBody:    { padding: '16px 20px' } as React.CSSProperties,
  bidAskRow:   { display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px', marginBottom: '14px' } as React.CSSProperties,
  bidBox:      { background: '#f0fdf4', borderRadius: '8px', padding: '10px 12px', border: '1px solid #bbf7d0' } as React.CSSProperties,
  askBox:      { background: '#fff5f5', borderRadius: '8px', padding: '10px 12px', border: '1px solid #fecaca' } as React.CSSProperties,
  baLabel:     { fontSize: '10px', fontWeight: 700, letterSpacing: '0.06em', textTransform: 'uppercase' as const, color: '#94a3b8', marginBottom: '2px' } as React.CSSProperties,
  baValue:     (color: string) => ({ fontSize: '18px', fontWeight: 700, color, fontVariantNumeric: 'tabular-nums' as const, letterSpacing: '-0.01em' } as React.CSSProperties),
  spreadRow:   { display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '14px', padding: '8px 12px', background: '#fafafa', borderRadius: '8px' } as React.CSSProperties,
  spreadLabel: { fontSize: '11px', color: '#94a3b8', fontWeight: 600 } as React.CSSProperties,
  spreadVal:   { fontSize: '13px', fontWeight: 700, color: '#334155', fontVariantNumeric: 'tabular-nums' as const } as React.CSSProperties,
  divider:     { height: '1px', background: '#f1f5f9', margin: '14px 0' } as React.CSSProperties,
  tsRow:       { display: 'flex', alignItems: 'center', justifyContent: 'space-between' } as React.CSSProperties,
  tsLabel:     { fontSize: '10px', color: '#94a3b8', fontWeight: 700, textTransform: 'uppercase' as const, letterSpacing: '0.06em' } as React.CSSProperties,
  tsValue:     { fontSize: '12px', fontWeight: 600, color: '#0f172a' } as React.CSSProperties,
  statusBadge: (halted: boolean, tradable: boolean) => ({
    display: 'inline-flex', alignItems: 'center', gap: '5px',
    padding: '3px 8px', borderRadius: '6px', fontSize: '11px', fontWeight: 700,
    background: halted ? '#fee2e2' : tradable ? '#dcfce7' : '#f1f5f9',
    color:      halted ? '#b91c1c' : tradable ? '#15803d' : '#64748b',
  } as React.CSSProperties),
}

// ── WatchlistPanel ─────────────────────────────────────────────────────────

interface WatchlistPanelProps {
  instruments: Instrument[]
  loading:     boolean
  selected:    string | null
  ticks:       Record<string, { price: number; bid: number; ask: number; timestamp: string }>
  marketOpen:  boolean
  onSelect:    (symbol: string) => void
}

const WatchlistPanel: React.FC<WatchlistPanelProps> = ({
  instruments, loading, selected, ticks, marketOpen, onSelect,
}) => (
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
          <th style={s.thRight}>Change</th>
          <th style={s.thRight}>Chg %</th>
          <th style={s.thRight}>Bid</th>
          <th style={s.thRight}>Ask</th>
          <th style={s.thRight}>Spread</th>
          <th style={s.thRight}>Day High</th>
          <th style={s.thRight}>Day Low</th>
          <th style={s.th}>Status</th>
          <th style={s.thRight}>Last Updated</th>
        </tr>
      </thead>
      <tbody>
        {loading
          ? Array.from({ length: 8 }).map((_, i) => (
              <tr key={i}>
                {Array.from({ length: 11 }).map((_, j) => (
                  <td key={j} style={s.td}><div style={s.skeleton} /></td>
                ))}
              </tr>
            ))
          : instruments.map(inst => {
              // WS tick overrides REST data; REST data is always kept fresh by the unified poll
              const tick    = ticks[inst.symbol]
              const livePx  = tick?.price ?? Number(inst.lastPrice)
              const liveBid = tick?.bid   ?? Number(inst.bidPrice)
              const liveAsk = tick?.ask   ?? Number(inst.askPrice)
              const liveTs  = tick?.timestamp ?? inst.dataTimestamp
              const prev    = Number(inst.previousClose ?? inst.lastPrice)
              const change  = livePx - prev
              const changePct = prev !== 0 ? (change / prev) * 100 : 0

              return (
                <tr
                  key={inst.symbol}
                  className={`mkt-row${selected === inst.symbol ? ' selected' : ''}`}
                  onClick={() => onSelect(inst.symbol)}
                >
                  <td style={s.td}><div style={s.symbolCell}>{inst.symbol}</div></td>

                  <td style={s.tdMono}><strong>{fmt(livePx)}</strong></td>

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

                  <td style={{ ...s.tdMono, color: '#64748b', fontSize: '11.5px' }}>
                    {spread(liveBid, liveAsk)}
                  </td>

                  <td style={{ ...s.tdMono, color: '#10b981' }}>
                    {inst.dayHigh ? `$${fmt(inst.dayHigh)}` : '—'}
                  </td>
                  <td style={{ ...s.tdMono, color: '#ef4444' }}>
                    {inst.dayLow ? `$${fmt(inst.dayLow)}` : '—'}
                  </td>

                  <td style={s.td}>
                    {inst.halted
                      ? <span style={s.haltPill}>HALTED</span>
                      : inst.tradable
                        ? <span style={s.activePill}><span style={{ width: 5, height: 5, borderRadius: '50%', background: '#15803d', display: 'inline-block' }} />ACTIVE</span>
                        : <span style={s.closedPill}>CLOSED</span>
                    }
                  </td>

                  <td style={{ ...s.tdMono, color: marketOpen ? '#334155' : '#94a3b8' }}>
                    {fmtET(liveTs)}
                  </td>
                </tr>
              )
            })
        }
      </tbody>
    </table>
  </div>
)

// ── StockDetailPanel ───────────────────────────────────────────────────────

interface StockDetailPanelProps {
  selected:       string | null
  selectedBase:   Instrument | null   // kept fresh by the unified poll
  detailLoading:  boolean
  candles:        IntradayPoint[]     // from unified poll — in sync with watchlist
  candlesLoading: boolean
  ticks:          Record<string, { price: number; bid: number; ask: number; timestamp: string }>
  marketOpen:     boolean
  onNavigate:     (path: string) => void
}

const StockDetailPanel: React.FC<StockDetailPanelProps> = ({
  selected, selectedBase, detailLoading, candles, candlesLoading, ticks, marketOpen, onNavigate,
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
          <div style={{ fontSize: '13px', color: '#ef4444', fontWeight: 600 }}>Failed to load {selected}</div>
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
  const prevClose = Number(selectedBase.previousClose ?? selectedBase.lastPrice)
  const change    = livePx - prevClose
  const changePct = prevClose !== 0 ? (change / prevClose) * 100 : 0
  const halted    = selectedBase.halted   ?? false
  const tradable  = selectedBase.tradable ?? false
  const canOrder  = (getCurrentUser() === null || getCurrentUser()?.role === 'TRADER') && !halted && tradable

  const chartData = candles.length >= 2 ? candles : generateIntraday(livePx)

  return (
    <div style={s.card}>
      <div style={s.cardHeader}>
        <div style={s.qHeaderTop}>
          <div style={s.qSymbol}>{selectedBase.symbol}</div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            {!marketOpen && (
              <span style={{
                display: 'inline-flex', alignItems: 'center', gap: '4px',
                padding: '3px 8px', borderRadius: '6px', fontSize: '10px', fontWeight: 700,
                background: '#f1f5f9', color: '#64748b', letterSpacing: '0.04em',
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
          {candlesLoading && candles.length === 0
            ? <div className="mkt-chart-skeleton" />
            : <IntradayChart data={chartData} symbol={selectedBase.symbol} positive={changePct >= 0} />
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
            <div style={s.baValue('#15803d')}>${fmt(liveBid)}</div>
          </div>
          <div style={s.askBox}>
            <div style={s.baLabel}>Ask</div>
            <div style={s.baValue('#b91c1c')}>${fmt(liveAsk)}</div>
          </div>
        </div>

        <div style={s.spreadRow}>
          <span style={s.spreadLabel}>Spread</span>
          <span style={s.spreadVal}>{spread(liveBid, liveAsk)}</span>
          <span style={s.spreadLabel}>Prev Close</span>
          <span style={s.spreadVal}>{selectedBase.previousClose ? `$${fmt(selectedBase.previousClose)}` : '—'}</span>
        </div>

        <div style={s.tsRow}>
          <span style={s.tsLabel}>Last Updated</span>
          <span style={s.tsValue}>{fmtET(liveTs)}</span>
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
  const [selected,       setSelected]       = useState<string | null>(null)
  const [selectedBase,   setSelectedBase]   = useState<Instrument | null>(null)
  const [detailLoading,  setDetailLoading]  = useState(false)
  const [loading,        setLoading]        = useState(true)
  const [candles,        setCandles]        = useState<IntradayPoint[]>([])
  const [candlesLoading, setCandlesLoading] = useState(false)

  const watchlistSymbols = useMemo(() => instruments.map(i => i.symbol), [instruments])
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
  }, [])

  // ── Unified poll ──────────────────────────────────────────────────────────
  //
  // ONE timer — ONE Promise.all — watchlist and chart always come from the
  // same response batch so they stay in sync.
  // Restarts automatically when config.pollingIntervalMs changes (only once on first load).

  useEffect(() => {
    const intervalMs = config?.pollingIntervalMs ?? 5_000

    const poll = async () => {
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
        // Only reset candles from DB when WS is offline.
        // When WS is live it appends ticks directly — resetting here would wipe live data.
        if (history && history.ticks.length >= 2 && !wsLiveRef.current) {
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

  // ── Symbol selection ──────────────────────────────────────────────────────
  // Step 1: load tick history for the chart
  // Step 2: WS subscription is handled automatically by useMarketSocket

  const handleSelectSymbol = useCallback(async (symbol: string) => {
    setSelected(symbol)
    // Immediately show instrument data already in the watchlist (no extra REST round-trip)
    const existing = instruments.find(i => i.symbol === symbol) ?? null
    setSelectedBase(existing)
    setDetailLoading(!existing)
    setCandlesLoading(true)
    setCandles([])
    try {
      const history = await getTickHistory(symbol)
      const built = candlesFromTicks(history.ticks)
      setCandles(built)
    } finally {
      setDetailLoading(false)
      setCandlesLoading(false)
    }
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
              <span style={s.pillDot(open ? '#15803d' : '#94a3b8')} />
              {open ? 'MARKET OPEN' : 'MARKET CLOSED'}
            </span>
          )}
          <span style={s.wsPill(wsLive)}>
            <span style={s.pillDot(wsLive ? '#2563eb' : '#b45309')} />
            {wsLive ? 'Live' : `Polling ${(config?.pollingIntervalMs ?? 5000) / 1000}s`}
          </span>
          {config && (
            <span style={s.note}>
              {config.marketHours.start} – {config.marketHours.end} {config.marketHours.timezone}
            </span>
          )}
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
          instruments={instruments}
          loading={loading}
          selected={selected}
          ticks={ticks}
          marketOpen={open}
          onSelect={handleSelectSymbol}
        />

        <StockDetailPanel
          selected={selected}
          selectedBase={selectedBase}
          detailLoading={detailLoading}
          candles={candles}
          candlesLoading={candlesLoading}
          ticks={ticks}
          marketOpen={open}
          onNavigate={navigate}
        />
      </div>
    </div>
  )
}

export default MarketDataPage
