import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { AlertTriangle, CheckCircle, XCircle, ChevronDown, ChevronUp } from 'lucide-react'
import DatePicker from 'react-datepicker'
import 'react-datepicker/dist/react-datepicker.css'
import { useIdleTimer } from '../hooks/useIdleTimer'
import { useOrderEntryBidAsk } from '../hooks/useOrderEntryBidAsk'
import { getHolidays, getInstrument, getInstruments } from '../api/marketApi'
import { getClients, getPortfolio } from '../api/clientApi'
import { submitOrder } from '../api/orderApi'
import { Holiday, Instrument } from '../types/market.types'
import { Client, Portfolio } from '../types/client.types'
import { OrderSide, OrderType, TimeInForce } from '../types/order.types'
import { DEFAULT_VENUE, SOR_VENUES } from '../config/sorVenues'

// ── Helpers ───────────────────────────────────────────────────────────────────
const fmt = (v: string | number): string => {
  const n = typeof v === 'string' ? parseFloat(v) : v
  if (isNaN(n)) return '—'
  return n.toLocaleString('en-US', { style: 'currency', currency: 'USD' })
}

const fmtDec2 = (v: string | number): string => {
  const n = typeof v === 'string' ? parseFloat(v) : v
  if (isNaN(n)) return '—'
  return n.toFixed(2)
}

const fmtPnl = (value: number, pct: number): string =>
  `${fmt(value)} (${pct >= 0 ? '+' : ''}${pct.toFixed(2)}%)`

const tomorrowStr = (): string => {
  const d = new Date()
  d.setDate(d.getDate() + 1)
  return d.toISOString().split('T')[0]
}

const toYYYYMMDD = (d: Date): string => {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

// ── Styles ────────────────────────────────────────────────────────────────────
const s = {
  page:       { fontFamily: "'Inter', system-ui, sans-serif", padding: '24px', minHeight: 'calc(100vh - 52px)', background: 'var(--color-bg)' } as React.CSSProperties,
  header:     { marginBottom: '20px' } as React.CSSProperties,
  title:      { margin: 0, fontSize: '20px', fontWeight: 700, color: 'var(--color-text-primary)', letterSpacing: '-0.01em' } as React.CSSProperties,
  grid:       { display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '20px', alignItems: 'start' } as React.CSSProperties,
  card:       { background: 'var(--color-surface)', borderRadius: 'var(--radius-lg)', boxShadow: 'var(--shadow-sm)', padding: '24px', border: '1px solid var(--color-border-light)' } as React.CSSProperties,
  cardTitle:  { margin: '0 0 16px', fontSize: '11px', fontWeight: 700, color: 'var(--color-text-secondary)', paddingBottom: '10px', borderBottom: '1px solid var(--color-border-light)', textTransform: 'uppercase' as const, letterSpacing: '0.06em' } as React.CSSProperties,
  fieldGroup: { marginBottom: '14px' } as React.CSSProperties,
  label:      { display: 'block', fontSize: '11px', fontWeight: 600, color: 'var(--color-text-secondary)', marginBottom: '6px', textTransform: 'uppercase' as const, letterSpacing: '0.05em' },
  input:      { width: '100%', padding: '9px 12px', borderRadius: 'var(--radius-md)', border: '1.5px solid var(--color-border)', fontSize: '14px', boxSizing: 'border-box' as const, outline: 'none', transition: 'border-color 0.15s', color: 'var(--color-text-primary)', background: '#fff' },
  inputError: { width: '100%', padding: '9px 12px', borderRadius: 'var(--radius-md)', border: '1.5px solid var(--color-error)', fontSize: '14px', boxSizing: 'border-box' as const, outline: 'none', color: 'var(--color-text-primary)', background: '#fff' },
  select:     { width: '100%', padding: '9px 12px', borderRadius: 'var(--radius-md)', border: '1.5px solid var(--color-border)', fontSize: '14px', background: '#fff', boxSizing: 'border-box' as const, color: 'var(--color-text-primary)' },
  errorText:  { fontSize: '12px', color: 'var(--color-error)', marginTop: '4px' } as React.CSSProperties,

  // Grouped fields visual section
  fieldBox:   { background: 'var(--color-bg)', borderRadius: 'var(--radius-md)', padding: '14px', border: '1px solid var(--color-border-light)', marginBottom: '14px' } as React.CSSProperties,

  // Advanced collapsible toggle
  advancedToggle: { display: 'flex', alignItems: 'center', gap: '5px', background: 'none', border: 'none', cursor: 'pointer', fontSize: '11px', fontWeight: 700, color: 'var(--color-text-secondary)', textTransform: 'uppercase' as const, letterSpacing: '0.05em', padding: '4px 0', marginBottom: '8px' } as React.CSSProperties,

  // Toggle group
  toggleGroup: { display: 'flex', borderRadius: 'var(--radius-md)', overflow: 'hidden', border: '1.5px solid var(--color-border)' } as React.CSSProperties,
  toggleBtn:   (active: boolean, activeColor?: string) => ({
    flex: 1, padding: '9px', fontSize: '13px', fontWeight: 600, cursor: 'pointer', border: 'none',
    background: active ? (activeColor ?? 'var(--color-primary)') : '#f9fafb',
    color:      active ? '#fff' : 'var(--color-text-secondary)',
    transition: 'background 0.15s, color 0.15s',
  } as React.CSSProperties),

  // Shared row layout
  divider:  { borderTop: '1px solid var(--color-border-light)', margin: '12px 0' } as React.CSSProperties,
  row:      { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' } as React.CSSProperties,
  rowLabel: { fontSize: '13px', color: 'var(--color-text-secondary)', flex: 1 } as React.CSSProperties,
  rowValue: (bold?: boolean, color?: string) => ({ fontSize: '13px', fontWeight: bold ? 700 : 400, color: color ?? 'var(--color-text-primary)', fontVariantNumeric: 'tabular-nums', textAlign: 'right' as const } as React.CSSProperties),

  // Market Intelligence panel
  ltpLabel:  { fontSize: '10px', color: 'var(--color-text-muted)', fontWeight: 700, textTransform: 'uppercase' as const, letterSpacing: '0.06em', marginBottom: '2px' } as React.CSSProperties,
  ltpPrice:  { fontSize: '28px', fontWeight: 700, color: 'var(--color-text-primary)', marginBottom: '2px', fontVariantNumeric: 'tabular-nums' } as React.CSSProperties,
  ltpMeta:   { fontSize: '12px', color: 'var(--color-text-secondary)', marginBottom: '12px', fontVariantNumeric: 'tabular-nums' } as React.CSSProperties,
  miGrid:    { display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '6px' } as React.CSSProperties,
  miItem:    { background: 'var(--color-bg)', borderRadius: 'var(--radius-md)', padding: '8px 10px' } as React.CSSProperties,
  miLabel:   { fontSize: '10px', color: 'var(--color-text-muted)', fontWeight: 600, textTransform: 'uppercase' as const, letterSpacing: '0.04em', marginBottom: '2px' } as React.CSSProperties,
  miValue:   { fontSize: '13px', fontWeight: 600, color: 'var(--color-text-primary)', fontVariantNumeric: 'tabular-nums' } as React.CSSProperties,
  haltedBadge: { display: 'inline-flex', alignItems: 'center', padding: '2px 8px', background: 'var(--color-error-bg)', color: 'var(--color-error)', borderRadius: 'var(--radius-sm)', fontSize: '11px', fontWeight: 700, marginLeft: '8px' } as React.CSSProperties,
  staleBadge:  { display: 'inline-flex', alignItems: 'center', gap: '3px', padding: '1px 6px', background: 'var(--color-warning-bg)', color: 'var(--color-warning)', borderRadius: 'var(--radius-sm)', fontSize: '10px', fontWeight: 700, marginLeft: '6px' } as React.CSSProperties,

  // Validation check
  checkRow:  { display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '6px', fontSize: '12px' } as React.CSSProperties,
  checkText: (pass: boolean) => ({ color: pass ? 'var(--color-text-primary)' : 'var(--color-text-muted)' } as React.CSSProperties),

  // Alert badge
  alertBadge: (type: 'success' | 'warning' | 'danger') => ({
    display: 'flex', alignItems: 'center', gap: '6px',
    padding: '6px 10px', borderRadius: 'var(--radius-sm)',
    fontSize: '12px', fontWeight: 600, marginBottom: '6px',
    background: type === 'success' ? 'var(--color-success-bg)'
      : type === 'warning' ? 'var(--color-warning-bg)'
      : 'var(--color-error-bg)',
    color: type === 'success' ? 'var(--color-success)'
      : type === 'warning' ? 'var(--color-warning)'
      : 'var(--color-error)',
    border: `1px solid ${type === 'success' ? 'var(--color-success-border)'
      : type === 'warning' ? 'var(--color-warning-border)'
      : 'var(--color-error-border)'}`,
  } as React.CSSProperties),

  // Toast
  toast: { position: 'fixed' as const, top: '20px', right: '20px', background: 'var(--color-nav)', color: '#fff', padding: '14px 20px', borderRadius: 'var(--radius-lg)', boxShadow: 'var(--shadow-lg)', zIndex: 9999, fontSize: '14px', fontWeight: 500 },

  // Error banner / modal
  errorBanner:  { padding: '12px 16px', background: 'var(--color-error-bg)', border: '1px solid var(--color-error-border)', borderRadius: 'var(--radius-md)', color: '#b91c1c', fontSize: '13px', marginBottom: '14px' } as React.CSSProperties,
  modalOverlay: { position: 'fixed' as const, inset: 0, background: 'rgba(0,0,0,0.4)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 9998 },
  modal:        { background: '#fff', borderRadius: 'var(--radius-xl)', padding: '28px', maxWidth: '420px', width: '90%', boxShadow: 'var(--shadow-lg)' } as React.CSSProperties,
  modalTitle:   { margin: '0 0 12px', fontSize: '17px', fontWeight: 700, color: '#b91c1c' } as React.CSSProperties,
  modalBody:    { fontSize: '13px', color: 'var(--color-text-primary)', marginBottom: '20px', lineHeight: 1.6 } as React.CSSProperties,
  modalClose:   { padding: '8px 20px', background: 'var(--color-primary)', color: '#fff', border: 'none', borderRadius: 'var(--radius-md)', cursor: 'pointer', fontWeight: 600 } as React.CSSProperties,

  // GTD date picker wrapper
  datepickerWrapper:      { border: '1.5px solid var(--color-border)', borderRadius: 'var(--radius-md)', overflow: 'hidden' } as React.CSSProperties,
  datepickerWrapperError: { border: '1.5px solid var(--color-error)', borderRadius: 'var(--radius-md)', overflow: 'hidden' } as React.CSSProperties,

  // Idle timer banners
  warningBanner: { padding: '12px 16px', background: 'var(--color-warning-bg)', border: '1px solid var(--color-warning-border)', borderRadius: 'var(--radius-md)', color: 'var(--color-warning)', fontSize: '13px', fontWeight: 600, marginBottom: '14px' } as React.CSSProperties,
  expiredBanner: { padding: '16px', background: 'var(--color-error-bg)', border: '1px solid var(--color-error-border)', borderRadius: 'var(--radius-md)', marginBottom: '14px' } as React.CSSProperties,
  expiredTitle:  { margin: '0 0 6px', fontSize: '14px', fontWeight: 700, color: '#b91c1c' } as React.CSSProperties,
  expiredMsg:    { margin: '0 0 12px', fontSize: '13px', color: '#b91c1c' } as React.CSSProperties,
  restartBtn:    { padding: '8px 18px', background: 'var(--color-primary)', color: '#fff', border: 'none', borderRadius: 'var(--radius-md)', cursor: 'pointer', fontWeight: 600, fontSize: '13px' } as React.CSSProperties,
}

// ── Types ─────────────────────────────────────────────────────────────────────
interface FieldErrors { quantity?: string; limitPrice?: string; gtdDate?: string; symbol?: string }
interface ErrorDetail { field: string; code: string; message: string }
interface AlertItem   { type: 'success' | 'warning' | 'danger'; message: string }

interface SubmitError {
  type: 'MARKET_CLOSED' | 'RISK_REJECTED' | 'INVALID_GTD_DATE' | 'VALIDATION_ERROR' | 'GENERIC'
  message: string
  ruleCode?: string
}

// ── Component ─────────────────────────────────────────────────────────────────
const OrderEntryPage: React.FC = () => {
  const navigate      = useNavigate()
  const [params]      = useSearchParams()

  const [clients,     setClients]     = useState<Client[]>([])
  const [clientId,    setClientId]    = useState('')
  const [symbol,      setSymbol]      = useState(params.get('symbol') ?? '')
  const [symbolInput, setSymbolInput] = useState(params.get('symbol') ?? '')
  const [side,        setSide]        = useState<OrderSide>('BUY')
  const [orderType,   setOrderType]   = useState<OrderType>('LIMIT')
  const [quantity,    setQuantity]    = useState('')
  const [limitPrice,  setLimitPrice]  = useState('')
  const [tif,                setTif]                = useState<TimeInForce>('DAY')
  const [gtdDate,            setGtdDate]            = useState<Date | null>(null)
  const [routingDestination, setRoutingDestination] = useState(DEFAULT_VENUE)
  const [advancedOpen,       setAdvancedOpen]       = useState(false)

  const [instruments,  setInstruments]  = useState<Instrument[]>([])
  const [quote,        setQuote]        = useState<Instrument | null>(null)
  const [portfolio,    setPortfolio]    = useState<Portfolio | null>(null)
  const [quoteLoading, setQuoteLoading] = useState(false)
  const [holidays,     setHolidays]     = useState<Holiday[]>([])

  const [submitting,   setSubmitting]   = useState(false)
  const [submitError,  setSubmitError]  = useState<SubmitError | null>(null)
  const [showToast,    setShowToast]    = useState(false)
  const [toastMsg,     setToastMsg]     = useState('')
  const [fieldErrors,  setFieldErrors]  = useState<FieldErrors>({})
  const [showModal,    setShowModal]    = useState(false)
  const [symbolNotFound, setSymbolNotFound] = useState(false)

  const symbolDebounce = useRef<ReturnType<typeof setTimeout> | null>(null)

  // ── Real-time Bid/Ask (SCRUM-108) ────────────────────────────────────────────
  const { bid: liveBid, ask: liveAsk, isStale } = useOrderEntryBidAsk(symbol)

  // ── Idle timer ────────────────────────────────────────────────────────────────
  const { secondsRemaining, isExpired, resetTimer } = useIdleTimer(60)

  const handleInteraction = useCallback(() => {
    resetTimer()
  }, [resetTimer])

  // Reset form fields when window expires
  useEffect(() => {
    if (!isExpired) return
    setSymbol('')
    setSymbolInput('')
    setQuantity('')
    setLimitPrice('')
    setTif('DAY')
    setGtdDate(null)
    setFieldErrors({})
    setSubmitError(null)
    setQuote(null)
    setSymbolNotFound(false)
    setSide('BUY')
    setOrderType('LIMIT')
    setRoutingDestination(DEFAULT_VENUE)
  }, [isExpired])

  // Auto-expand Advanced section when GTD is selected
  useEffect(() => {
    if (tif === 'GTD') setAdvancedOpen(true)
  }, [tif])

  // Computed values for the GTD date picker
  const tomorrow: Date = useMemo(() => {
    const d = new Date()
    d.setDate(d.getDate() + 1)
    d.setHours(0, 0, 0, 0)
    return d
  }, [])

  const holidayDates: Date[] = useMemo(
    () => holidays.map((h) => new Date(h.holidayDate + 'T12:00:00')),
    [holidays]
  )

  const gtdDateStr: string = useMemo(
    () => (gtdDate ? toYYYYMMDD(gtdDate) : ''),
    [gtdDate]
  )

  const gtdDisplayStr: string | null = useMemo(
    () => gtdDate
      ? gtdDate.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
      : null,
    [gtdDate]
  )

  useEffect(() => {
    getInstruments().then(setInstruments)
    getHolidays().then(setHolidays).catch(() => {})
    getClients().then((list) => {
      setClients(list)
      if (list.length > 0) setClientId(list[0].clientId)
    }).catch(() => {})
  }, [])

  useEffect(() => {
    setPortfolio(null)
    if (clientId) getPortfolio(clientId).then(setPortfolio).catch(() => setPortfolio(null))
  }, [clientId])

  useEffect(() => {
    if (symbolDebounce.current) clearTimeout(symbolDebounce.current)
    if (!symbol) { setQuote(null); setSymbolNotFound(false); return }
    symbolDebounce.current = setTimeout(async () => {
      setQuoteLoading(true)
      setSymbolNotFound(false)
      try {
        const q = await getInstrument(symbol)
        setQuote(q)
        if (q && !limitPrice) {
          setLimitPrice(side === 'BUY' ? q.askPrice : q.bidPrice)
        }
      } catch (err: unknown) {
        const axiosErr = err as { response?: { data?: { error?: { code?: string } } } }
        const code = axiosErr?.response?.data?.error?.code
        if (code === 'SYMBOL_NOT_FOUND') {
          setSymbolNotFound(true)
          setQuote(null)
        }
      }
      setQuoteLoading(false)
    }, 400)
  }, [symbol]) // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (quote) setLimitPrice(side === 'BUY' ? quote.askPrice : quote.bidPrice)
  }, [side, quote])

  const suggestions = symbolInput.length >= 1
    ? instruments.filter((i) => i.symbol.startsWith(symbolInput.toUpperCase())).slice(0, 6)
    : []

  const qty        = parseInt(quantity, 10)
  const price      = orderType === 'LIMIT' ? parseFloat(limitPrice) : parseFloat(quote?.lastPrice ?? '0')
  const orderValue = !isNaN(qty) && !isNaN(price) ? qty * price : 0
  const buyingPower   = portfolio ? parseFloat(portfolio.buyingPower) : 0
  const cashBalance   = portfolio ? parseFloat(portfolio.cashBalance) : 0
  const postOrderCash = side === 'BUY' ? cashBalance - orderValue : cashBalance + orderValue
  const bpUsedPct  = buyingPower > 0 ? Math.min((orderValue / buyingPower) * 100, 100) : 0
  const holding    = portfolio?.holdings.find((h) => h.symbol === symbol)
  const heldQty    = holding ? holding.quantity : 0

  // P&L for current holding of selected symbol
  const pnl = holding && quote
    ? (parseFloat(quote.lastPrice) - parseFloat(holding.avgPrice)) * holding.quantity
    : null
  const pnlPct = holding && quote
    ? ((parseFloat(quote.lastPrice) - parseFloat(holding.avgPrice)) / parseFloat(holding.avgPrice)) * 100
    : null

  // Spread from live or REST quote
  const spreadStr = quote
    ? fmtDec2(parseFloat(liveAsk ?? quote.askPrice) - parseFloat(liveBid ?? quote.bidPrice))
    : null

  const sellHoldingsError: string | null =
    side === 'SELL' && portfolio !== null && !isNaN(qty) && qty > 0 && qty > heldQty
      ? `Sell quantity cannot exceed available holding of ${heldQty} units`
      : null

  const filterWeekdays = (date: Date): boolean => date.getDay() !== 0 && date.getDay() !== 6

  const isWeekendOrHoliday = (dateStr: string): boolean => {
    if (!dateStr) return false
    const d = new Date(dateStr + 'T12:00:00')
    if (d.getDay() === 0 || d.getDay() === 6) return true
    return holidays.some((h) => h.holidayDate === dateStr)
  }

  const checks = {
    symbol:       symbol.length > 0 && symbol.length <= 20 && /^[A-Z0-9]+$/.test(symbol),
    quantity:     !isNaN(qty) && qty > 0,
    limitPrice:   orderType === 'MARKET' || (!isNaN(parseFloat(limitPrice)) && parseFloat(limitPrice) > 0),
    gtdDate:      tif === 'DAY' || (gtdDate !== null && gtdDateStr >= tomorrowStr() && !isWeekendOrHoliday(gtdDateStr)),
    notHalted:    !quote || !quote.halted,
    sellHoldings: side !== 'SELL' || portfolio === null || isNaN(qty) || heldQty >= qty,
  }

  const allChecksPass = Object.values(checks).every(Boolean)

  // Contextual alerts
  const alerts: AlertItem[] = useMemo(() => {
    const result: AlertItem[] = []
    if (quote?.halted) {
      result.push({ type: 'danger', message: 'Symbol is halted — orders will not execute' })
    }
    if (side === 'BUY' && orderValue > 0) {
      if (buyingPower >= orderValue) {
        result.push({ type: 'success', message: 'Margin sufficiency confirmed' })
      } else {
        result.push({ type: 'danger', message: 'Insufficient buying power for this order' })
      }
    }
    if (bpUsedPct > 80 && side === 'BUY' && orderValue > 0) {
      result.push({ type: 'warning', message: `High buying power utilization: ${bpUsedPct.toFixed(1)}% used` })
    }
    if (quote?.dayHigh && quote?.lastPrice) {
      const last = parseFloat(quote.lastPrice)
      const high = parseFloat(quote.dayHigh)
      if (high > 0 && last >= high * 0.95) {
        result.push({ type: 'warning', message: 'Price close to day high — upper circuit risk' })
      }
    }
    return result
  }, [quote, side, orderValue, buyingPower, bpUsedPct])

  const handleSubmit = async () => {
    setFieldErrors({})
    setSubmitError(null)
    if (!allChecksPass) return

    setSubmitting(true)
    try {
      const res = await submitOrder({
        clientOrderId: `UI-${Date.now()}`,
        clientId,
        symbol,
        side,
        orderType,
        quantity: qty,
        limitPrice: orderType === 'LIMIT' ? limitPrice : undefined,
        tif,
        gtdDate: tif === 'GTD' && gtdDateStr ? gtdDateStr : undefined,
        routingDestination: SOR_VENUES.length === 0 ? DEFAULT_VENUE : routingDestination,
      })
      setToastMsg(`Order ${res.orderId} submitted successfully`)
      setShowToast(true)
      setTimeout(() => { setShowToast(false); navigate('/orders') }, 2500)
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { error?: { code?: string; message?: string; details?: ErrorDetail[] } } } }
      const errBody  = axiosErr?.response?.data?.error
      const code     = errBody?.code ?? ''
      const message  = errBody?.message ?? 'An error occurred. Please try again.'
      const details  = errBody?.details ?? []

      if (code === 'MARKET_CLOSED') {
        setSubmitError({ type: 'MARKET_CLOSED', message })
      } else if (code.includes('RISK')) {
        setSubmitError({ type: 'RISK_REJECTED', message, ruleCode: code })
        setShowModal(true)
      } else if (code === 'INVALID_GTD_DATE') {
        setSubmitError({ type: 'INVALID_GTD_DATE', message })
        setFieldErrors((f) => ({ ...f, gtdDate: message }))
      } else {
        const FIELD_MAP: Record<string, keyof FieldErrors> = {
          quantity:   'quantity',
          limitPrice: 'limitPrice',
          symbol:     'symbol',
          expiryDate: 'gtdDate',
        }
        const mapped: FieldErrors = {}
        for (const d of details) {
          const key = FIELD_MAP[d.field]
          if (key) mapped[key] = d.message
        }
        if (Object.keys(mapped).length > 0) {
          setFieldErrors(mapped)
        } else {
          setSubmitError({ type: 'VALIDATION_ERROR', message })
        }
      }
    } finally {
      setSubmitting(false)
    }
  }

  // Side-aware accent color
  const sideAccent = side === 'BUY' ? 'var(--color-buy)' : side === 'SHORT_SELL' ? 'var(--color-warning)' : 'var(--color-sell)'
  const submitBg   = submitting || !allChecksPass || symbolNotFound
    ? '#9ca3af'
    : side === 'BUY'
      ? 'linear-gradient(135deg, #2563eb, #1d4ed8)'
      : side === 'SHORT_SELL'
        ? 'linear-gradient(135deg, #f59e0b, #d97706)'
        : 'linear-gradient(135deg, #db2777, #be185d)'

  // ── Render ───────────────────────────────────────────────────────────────────
  return (
    <div style={s.page}>

      {/* Toast */}
      {showToast && <div style={s.toast}>{toastMsg}</div>}

      {/* Risk Rejected Modal */}
      {showModal && submitError && (
        <div style={s.modalOverlay} onClick={() => setShowModal(false)}>
          <div style={s.modal} onClick={(e) => e.stopPropagation()}>
            <h3 style={s.modalTitle}>Order Risk Rejected</h3>
            <div style={s.modalBody}>
              <strong>Rule:</strong> {submitError.ruleCode}<br /><br />
              {submitError.message}
            </div>
            <button style={s.modalClose} onClick={() => setShowModal(false)}>Close</button>
          </div>
        </div>
      )}

      {/* Header */}
      <div style={s.header}>
        <h1 style={s.title}>New Order</h1>
      </div>

      <div style={s.grid}>

        {/* ── Left: Order Entry Form ── */}
        <div
          style={s.card}
          onKeyDown={handleInteraction}
          onClick={handleInteraction}
          onChange={handleInteraction}
        >
          <h2 style={{ ...s.cardTitle, fontSize: '15px', fontWeight: 600, textTransform: 'none', letterSpacing: 'normal' }}>Order Details</h2>

          {/* Idle timer — countdown warning */}
          {secondsRemaining <= 15 && !isExpired && (
            <div style={s.warningBanner} role="alert" aria-live="polite">
              ⚠ Order window will expire in {secondsRemaining} second{secondsRemaining !== 1 ? 's' : ''}
            </div>
          )}

          {/* Idle timer — expired overlay */}
          {isExpired && (
            <div style={s.expiredBanner} role="alert">
              <p style={s.expiredTitle}>Order Window Expired</p>
              <p style={s.expiredMsg}>This order window has expired. Unsaved data has been cleared.</p>
              <button style={s.restartBtn} onClick={() => resetTimer()}>Start New Order</button>
            </div>
          )}

          {submitError && submitError.type === 'MARKET_CLOSED' && (
            <div style={s.errorBanner}>Market is currently closed. {submitError.message}</div>
          )}
          {submitError && submitError.type === 'VALIDATION_ERROR' && (
            <div style={s.errorBanner}>{submitError.message}</div>
          )}

          {/* Client */}
          <div style={s.fieldGroup}>
            <label style={s.label}>Client</label>
            <select style={s.select} value={clientId} onChange={(e) => setClientId(e.target.value)} disabled={isExpired}>
              {clients.length === 0
                ? <option value="">Loading clients…</option>
                : clients.map((c) => <option key={c.clientId} value={c.clientId}>{c.name}</option>)
              }
            </select>
          </div>

          {/* Symbol typeahead */}
          <div style={{ ...s.fieldGroup, position: 'relative' }}>
            <label style={s.label}>Symbol</label>
            <input
              style={fieldErrors.symbol || symbolNotFound ? s.inputError : s.input}
              placeholder="e.g. AAPL"
              value={symbolInput}
              onChange={(e) => {
                const v = e.target.value.toUpperCase()
                setSymbolInput(v)
                setSymbol(v)
                setSymbolNotFound(false)
              }}
              autoComplete="off"
              disabled={isExpired}
            />
            {suggestions.length > 0 && symbolInput && symbolInput !== symbol && (
              <div style={{
                position: 'absolute', top: '100%', left: 0, right: 0,
                background: '#fff', border: '1.5px solid var(--color-border)',
                borderRadius: 'var(--radius-md)', boxShadow: 'var(--shadow-md)',
                zIndex: 100,
              }}>
                {suggestions.map((i) => (
                  <div
                    key={i.symbol}
                    style={{ padding: '9px 12px', cursor: 'pointer', fontSize: '13px', display: 'flex', justifyContent: 'space-between' }}
                    onMouseDown={() => { setSymbol(i.symbol); setSymbolInput(i.symbol) }}
                  >
                    <span style={{ fontWeight: 600 }}>{i.symbol}</span>
                    <span style={{ color: 'var(--color-text-secondary)' }}>
                      {i.halted ? '⛔ HALTED' : `$${i.lastPrice}`}
                    </span>
                  </div>
                ))}
              </div>
            )}
            {quoteLoading && <div style={{ fontSize: '11px', color: 'var(--color-text-muted)', marginTop: '4px' }}>Fetching quote…</div>}
            {symbolNotFound && <div style={s.errorText}>Symbol not found. Please search and select a valid instrument.</div>}
            {fieldErrors.symbol && <div style={s.errorText}>{fieldErrors.symbol}</div>}
          </div>

          {/* Side selector */}
          <div style={s.fieldGroup}>
            <label style={s.label}>Side</label>
            <div style={s.toggleGroup}>
              <button style={s.toggleBtn(side === 'BUY',        'var(--color-buy)')}     onClick={() => setSide('BUY')}        disabled={isExpired}>BUY</button>
              <button style={s.toggleBtn(side === 'SELL',       'var(--color-sell)')}    onClick={() => setSide('SELL')}       disabled={isExpired}>SELL</button>
              <button style={s.toggleBtn(side === 'SHORT_SELL', 'var(--color-warning)')} onClick={() => setSide('SHORT_SELL')} disabled={isExpired}>SHORT SELL</button>
            </div>
          </div>

          {/* Grouped fields: Order Type + Quantity + Limit Price */}
          <div style={s.fieldBox}>

            {/* Order Type */}
            <div style={{ ...s.fieldGroup, marginBottom: '12px' }}>
              <label style={s.label}>Order Type</label>
              <div style={s.toggleGroup}>
                <button style={s.toggleBtn(orderType === 'MARKET')} onClick={() => setOrderType('MARKET')} disabled={isExpired}>MARKET</button>
                <button style={s.toggleBtn(orderType === 'LIMIT')}  onClick={() => setOrderType('LIMIT')}  disabled={isExpired}>LIMIT</button>
              </div>
            </div>

            {/* Quantity */}
            <div style={{ ...s.fieldGroup, marginBottom: orderType === 'LIMIT' ? '12px' : 0 }}>
              <label style={s.label}>Quantity</label>
              <input
                style={fieldErrors.quantity || sellHoldingsError ? s.inputError : s.input}
                type="number" min="1" step="1"
                placeholder="Number of shares"
                value={quantity}
                onChange={(e) => setQuantity(e.target.value)}
                disabled={isExpired}
              />
              {(fieldErrors.quantity || sellHoldingsError) && (
                <div style={s.errorText}>{fieldErrors.quantity ?? sellHoldingsError}</div>
              )}
            </div>

            {/* Limit Price — visible only when LIMIT */}
            {orderType === 'LIMIT' && (
              <div style={{ ...s.fieldGroup, marginBottom: 0 }}>
                <label style={s.label}>Limit Price ($)</label>
                <input
                  style={fieldErrors.limitPrice ? s.inputError : s.input}
                  type="number" min="0.01" step="0.01"
                  placeholder="0.00"
                  value={limitPrice}
                  onChange={(e) => setLimitPrice(e.target.value)}
                  disabled={isExpired}
                />
                {fieldErrors.limitPrice && <div style={s.errorText}>{fieldErrors.limitPrice}</div>}
              </div>
            )}
          </div>

          {/* Time in Force */}
          <div style={s.fieldGroup}>
            <label style={s.label}>Time in Force</label>
            <div style={s.toggleGroup}>
              <button
                style={s.toggleBtn(tif === 'DAY')}
                onClick={() => { setTif('DAY'); setGtdDate(null) }}
                disabled={isExpired}
              >DAY</button>
              <button
                style={s.toggleBtn(tif === 'GTD')}
                onClick={() => setTif('GTD')}
                disabled={isExpired}
              >GTD</button>
            </div>
          </div>

          {/* Advanced section — collapsed by default; auto-expands when GTD selected */}
          <button
            data-testid="advanced-toggle"
            style={s.advancedToggle}
            onClick={() => setAdvancedOpen(v => !v)}
          >
            {advancedOpen ? <ChevronUp size={13} /> : <ChevronDown size={13} />}
            Advanced
          </button>

          {advancedOpen && (
            <>
              {/* GTD Date Picker */}
              {tif === 'GTD' && (
                <div style={s.fieldGroup}>
                  <label style={s.label}>GTD Date</label>
                  <div style={fieldErrors.gtdDate ? s.datepickerWrapperError : s.datepickerWrapper}>
                    <DatePicker
                      selected={gtdDate}
                      onChange={(date: Date | null) => {
                        setGtdDate(date)
                        setFieldErrors((f) => ({ ...f, gtdDate: undefined }))
                      }}
                      filterDate={filterWeekdays}
                      excludeDates={holidayDates}
                      minDate={tomorrow}
                      dateFormat="MMM dd, yyyy"
                      placeholderText="Select GTD date"
                      className="gtd-datepicker-input"
                    />
                  </div>
                  {fieldErrors.gtdDate && <div style={s.errorText}>{fieldErrors.gtdDate}</div>}
                </div>
              )}

              {/* Route To */}
              <div style={s.fieldGroup}>
                <label style={s.label}>Route To</label>
                <select
                  style={s.select}
                  value={SOR_VENUES.length === 0 ? DEFAULT_VENUE : routingDestination}
                  onChange={(e) => setRoutingDestination(e.target.value)}
                  disabled={isExpired || SOR_VENUES.length === 0}
                  title={SOR_VENUES.length === 0 ? 'No venues configured' : undefined}
                  aria-label="Route To"
                >
                  {SOR_VENUES.length === 0 ? (
                    <option value={DEFAULT_VENUE}>{DEFAULT_VENUE}</option>
                  ) : (
                    SOR_VENUES.map((venue) => (
                      <option key={venue} value={venue}>{venue}</option>
                    ))
                  )}
                </select>
              </div>
            </>
          )}

          {/* PLACE ORDER button */}
          <button
            style={{
              width: '100%', padding: '13px',
              borderRadius: 'var(--radius-md)', border: 'none',
              background: submitBg, color: '#fff',
              fontSize: '15px', fontWeight: 700, letterSpacing: '0.03em',
              cursor: submitting || !allChecksPass || isExpired || symbolNotFound ? 'not-allowed' : 'pointer',
              marginTop: '8px', transition: 'opacity 0.2s',
            }}
            onClick={handleSubmit}
            disabled={submitting || !allChecksPass || isExpired || symbolNotFound}
          >
            {submitting ? (
              <>
                <span className="btn-spinner" aria-label="Loading" />
                Placing…
              </>
            ) : 'PLACE ORDER'}
          </button>
        </div>

        {/* ── Right: Market Context Panels ── */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>

          {/* Market Intelligence */}
          <div style={s.card}>
            <h2 style={s.cardTitle}>Market Intelligence</h2>
            {!quote && !quoteLoading && (
              <div style={{ color: 'var(--color-text-muted)', fontSize: '13px' }}>Enter a symbol to see market data.</div>
            )}
            {quoteLoading && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                <div className="skeleton" style={{ height: '28px', width: '80px' }} />
                <div className="skeleton" style={{ height: '36px', width: '120px' }} />
              </div>
            )}
            {quote && !quoteLoading && (
              <>
                <div style={{ marginBottom: '4px' }}>
                  <div style={s.ltpLabel}>LTP</div>
                  <div style={s.ltpPrice}>
                    ${quote.lastPrice}
                    {quote.halted && <span style={s.haltedBadge}>HALTED</span>}
                  </div>
                  <div style={s.ltpMeta}>
                    {quote.symbol}
                    {(quote.change || quote.changePct) && (
                      <span style={{
                        marginLeft: '8px',
                        color: parseFloat(quote.change ?? '0') >= 0 ? 'var(--color-success)' : 'var(--color-error)',
                        fontWeight: 600,
                      }}>
                        {parseFloat(quote.change ?? '0') >= 0 ? '+' : ''}{quote.change}
                        {quote.changePct && ` (${parseFloat(quote.changePct) >= 0 ? '+' : ''}${quote.changePct}%)`}
                      </span>
                    )}
                  </div>
                </div>
                <div style={s.miGrid}>
                  {[
                    {
                      id: 'bid',
                      label: (
                        <span style={{ display: 'inline-flex', alignItems: 'center' }}>
                          Bid
                          {isStale && symbol && (
                            <span style={s.staleBadge} aria-label="Price data is stale"><AlertTriangle size={10} strokeWidth={2.5} />Stale</span>
                          )}
                        </span>
                      ),
                      value: `$${liveBid ?? quote.bidPrice}`,
                    },
                    { id: 'ask',    label: 'Ask',    value: `$${liveAsk ?? quote.askPrice}` },
                    { id: 'spread', label: 'Spread', value: spreadStr ?? '—' },
                    { id: 'vol',    label: 'Volume', value: quote.volume != null ? quote.volume.toLocaleString() : '—' },
                    { id: 'high',   label: 'High',   value: quote.dayHigh ? `$${fmtDec2(quote.dayHigh)}` : '—' },
                    { id: 'low',    label: 'Low',    value: quote.dayLow  ? `$${fmtDec2(quote.dayLow)}`  : '—' },
                  ].map(({ id, label, value }) => (
                    <div key={id} style={s.miItem}>
                      <div style={s.miLabel}>{label}</div>
                      <div style={s.miValue}>{value}</div>
                    </div>
                  ))}
                </div>
              </>
            )}
          </div>

          {/* Position */}
          {holding && quote && (
            <div style={s.card}>
              <h2 style={s.cardTitle}>Position</h2>
              <div style={s.row}>
                <span style={s.rowLabel}>Holdings</span>
                <span style={s.rowValue()}>{heldQty.toLocaleString()} {symbol}</span>
              </div>
              <div style={s.row}>
                <span style={s.rowLabel}>Avg Price</span>
                <span style={s.rowValue()}>${fmtDec2(holding.avgPrice)}</span>
              </div>
              <div style={s.row}>
                <span style={s.rowLabel}>Market Value</span>
                <span style={s.rowValue()}>{fmt(holding.marketValue)}</span>
              </div>
              {pnl !== null && pnlPct !== null && (
                <div style={s.row}>
                  <span style={s.rowLabel}>P&L</span>
                  <span style={s.rowValue(true, pnl >= 0 ? 'var(--color-success)' : 'var(--color-error)')}>
                    {fmtPnl(pnl, pnlPct)}
                  </span>
                </div>
              )}
            </div>
          )}

          {/* Margin & Costs */}
          <div style={{ ...s.card, borderTop: `3px solid ${sideAccent}`, paddingTop: '21px' }}>
            <h2 style={s.cardTitle}>Margin &amp; Costs</h2>

            <div style={s.row}>
              <span style={{ ...s.rowLabel, color: sideAccent, fontWeight: 600 }}>Order Value</span>
              <span style={{ ...s.rowValue(true), color: sideAccent }}>{orderValue > 0 ? fmt(orderValue) : '—'}</span>
            </div>
            <div style={s.row}>
              <span style={s.rowLabel}>Estimated Charges</span>
              <span style={s.rowValue()}>$0.00</span>
            </div>
            <div style={s.divider} />
            <div style={s.row}>
              <span style={s.rowLabel}>Buying Power</span>
              <span style={s.rowValue(true, 'var(--color-success)')}>{portfolio ? fmt(portfolio.buyingPower) : '—'}</span>
            </div>
            <div style={s.row}>
              <span style={s.rowLabel}>Cash Balance</span>
              <span style={s.rowValue()}>{portfolio ? fmt(portfolio.cashBalance) : '—'}</span>
            </div>
            <div style={s.row}>
              <span style={s.rowLabel}>Reserved Cash</span>
              <span style={s.rowValue()}>{portfolio ? fmt(portfolio.reservedCash) : '—'}</span>
            </div>
            {tif === 'GTD' && gtdDisplayStr && (
              <div style={s.row}>
                <span style={s.rowLabel}>Expires</span>
                <span style={s.rowValue()}>{gtdDisplayStr} at 4:00 PM ET</span>
              </div>
            )}
            {orderValue > 0 && (
              <>
                <div style={s.divider} />
                <div style={s.row}>
                  <span style={s.rowLabel}>Post-order Cash</span>
                  <span style={s.rowValue(true, postOrderCash < 0 ? 'var(--color-error)' : 'var(--color-text-primary)')}>{fmt(postOrderCash)}</span>
                </div>
                {side === 'BUY' && (
                  <div style={s.row}>
                    <span style={s.rowLabel}>Buying Power Used</span>
                    <span style={s.rowValue(false, bpUsedPct > 80 ? 'var(--color-warning)' : 'var(--color-text-secondary)')}>{bpUsedPct.toFixed(1)}%</span>
                  </div>
                )}
                {side === 'SELL' && (
                  <div style={s.row}>
                    <span style={s.rowLabel}>Shares Held</span>
                    <span style={s.rowValue()}>{heldQty.toLocaleString()} {symbol}</span>
                  </div>
                )}
              </>
            )}
            {side === 'SELL' && !portfolio && clientId && (
              <div style={{ ...s.errorText, marginTop: '4px' }}>
                Portfolio data unavailable — holding check skipped
              </div>
            )}
          </div>

          {/* Alerts */}
          {alerts.length > 0 && (
            <div style={s.card}>
              <h2 style={s.cardTitle}>Alerts</h2>
              <div style={alerts.length > 3 ? { maxHeight: '120px', overflowY: 'auto' } : undefined}>
                {alerts.map((alert, i) => (
                  <div key={i} style={s.alertBadge(alert.type)}>
                    {alert.type === 'success' && <CheckCircle size={13} strokeWidth={2.5} />}
                    {alert.type === 'warning' && <AlertTriangle size={13} strokeWidth={2.5} />}
                    {alert.type === 'danger'  && <XCircle      size={13} strokeWidth={2.5} />}
                    {alert.message}
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Validation */}
          <div style={s.card}>
            <h2 style={s.cardTitle}>Validation</h2>
            {[
              { label: 'Symbol entered',                                                                                                                      pass: checks.symbol       },
              { label: 'Quantity > 0',                                                                                                                        pass: checks.quantity     },
              { label: orderType === 'LIMIT' ? 'Limit price > 0' : 'Market order — no price needed',                                                         pass: checks.limitPrice   },
              { label: tif === 'GTD' ? 'GTD date is a future trading day' : 'DAY order — no expiry needed',                                                  pass: checks.gtdDate      },
              { label: 'Symbol not halted',                                                                                                                   pass: checks.notHalted    },
              { label: side !== 'SELL' ? 'Holdings check — N/A for BUY / SHORT_SELL' : `Sufficient holdings (holding: ${heldQty.toLocaleString()} ${symbol || '—'})`, pass: checks.sellHoldings },
            ].map(({ label, pass }) => (
              <div key={label} style={s.checkRow}>
                {pass
                  ? <CheckCircle size={14} strokeWidth={2} style={{ color: 'var(--color-success)', flexShrink: 0 }} />
                  : <XCircle    size={14} strokeWidth={2} style={{ color: 'var(--color-error)',   flexShrink: 0 }} />
                }
                <span style={s.checkText(pass)}>{label}</span>
              </div>
            ))}
          </div>

        </div>
      </div>
    </div>
  )
}

export default OrderEntryPage
