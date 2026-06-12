import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { AlertTriangle, Search } from 'lucide-react'
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

  // Toggle group
  toggleGroup:   { display: 'flex', borderRadius: 'var(--radius-md)', overflow: 'hidden', border: '1.5px solid var(--color-border)' } as React.CSSProperties,
  toggleBtn:     (active: boolean, activeColor?: string) => ({
    flex: 1, padding: '9px', fontSize: '13px', fontWeight: 600, cursor: 'pointer', border: 'none',
    background: active ? (activeColor ?? 'var(--color-primary)') : '#f9fafb',
    color:      active ? '#fff' : 'var(--color-text-secondary)',
    transition: 'background 0.15s, color 0.15s',
  } as React.CSSProperties),
  // Side toggle uses larger padding for prominence
  sideToggleBtn: (active: boolean, activeColor?: string) => ({
    flex: 1, padding: '11px', fontSize: '13px', fontWeight: 600, cursor: 'pointer', border: 'none',
    background: active ? (activeColor ?? 'var(--color-primary)') : 'var(--color-bg)',
    color:      active ? 'var(--color-surface)' : 'var(--color-text-secondary)',
    transition: 'background 0.15s, color 0.15s',
  } as React.CSSProperties),

  // Order Summary (SCRUM-186)
  summaryBox:         { background: 'var(--color-bg)', border: '1px solid var(--color-border)', borderRadius: 'var(--radius-md)', padding: '12px 14px', fontSize: '13px', display: 'flex', flexDirection: 'column', gap: '6px' } as React.CSSProperties,
  summaryPlaceholder: { color: 'var(--color-text-muted)', fontSize: '13px' } as React.CSSProperties,
  summaryValue:       { fontSize: '12px', color: 'var(--color-text-secondary)' } as React.CSSProperties,
  sideBadge: (side: OrderSide) => ({
    display: 'inline-block', padding: '1px 6px',
    borderRadius: 'var(--radius-sm)', fontSize: '11px', fontWeight: 700,
    color: 'var(--color-surface)',
    background: side === 'BUY' ? 'var(--color-buy)' : side === 'SELL' ? 'var(--color-sell)' : 'var(--color-warning)',
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
  const { bid: liveBid, ask: liveAsk, ltp: liveLtp, isStale } = useOrderEntryBidAsk(symbol)

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

  const orderSummaryText = useMemo<string | null>(() => {
    if (!allChecksPass || !symbol || !clientId) return null
    const priceStr = orderType === 'MARKET'
      ? 'Market'
      : `Limit $${parseFloat(limitPrice).toFixed(2)}`
    const qtyStr = qty.toLocaleString('en-US')
    return `${qtyStr} ${symbol} @ ${priceStr} · ${tif} · Route ${routingDestination}`
  }, [allChecksPass, symbol, clientId, qty, orderType, limitPrice, tif, routingDestination])

  const orderSummaryValue = useMemo<string | null>(() => {
    if (!orderSummaryText || orderValue <= 0) return null
    return `Est. value ${fmt(orderValue)} · Post-order cash ${fmt(postOrderCash)}`
  }, [orderSummaryText, orderValue, postOrderCash])

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

          {/* Row 1 — Client + Route To (2-column grid) */}
          <div
            data-testid="client-route-row"
            style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px', marginBottom: '14px' }}
          >
            <div>
              <label htmlFor="client-select" style={s.label}>Client</label>
              <select
                id="client-select"
                style={s.select}
                value={clientId}
                onChange={(e) => setClientId(e.target.value)}
                disabled={isExpired}
              >
                {clients.length === 0
                  ? <option value="">Loading clients…</option>
                  : clients.map((c) => <option key={c.clientId} value={c.clientId}>{c.name}</option>)
                }
              </select>
            </div>
            <div>
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
          </div>

          {/* Row 2 — Symbol typeahead (full-width, search icon) */}
          <div style={{ ...s.fieldGroup, position: 'relative' }}>
            <label style={s.label}>Symbol</label>
            <div style={{ position: 'relative' }}>
              <span style={{
                position: 'absolute', left: '12px', top: '50%', transform: 'translateY(-50%)',
                color: 'var(--color-text-muted)', pointerEvents: 'none', display: 'flex', alignItems: 'center',
              }}>
                <Search size={15} />
              </span>
              <input
                style={fieldErrors.symbol || symbolNotFound
                  ? { ...s.inputError, paddingLeft: '36px' }
                  : { ...s.input,      paddingLeft: '36px' }}
                placeholder="Search ticker — e.g. AAPL, MSFT, NVDA"
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
            </div>
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

          {/* Row 3 — Side selector (full-width, prominent 11px padding) */}
          <div style={s.fieldGroup}>
            <label style={s.label}>Side</label>
            <div style={s.toggleGroup} role="group" aria-label="Side">
              <button style={s.sideToggleBtn(side === 'BUY',        'var(--color-buy)')}     onClick={() => setSide('BUY')}        disabled={isExpired}>BUY</button>
              <button style={s.sideToggleBtn(side === 'SELL',       'var(--color-sell)')}    onClick={() => setSide('SELL')}       disabled={isExpired}>SELL</button>
              <button style={s.sideToggleBtn(side === 'SHORT_SELL', 'var(--color-warning)')} onClick={() => setSide('SHORT_SELL')} disabled={isExpired}>SHORT SELL</button>
            </div>
          </div>

          {/* Row 4 — Order Type + Time in Force (2-column grid) */}
          <div
            data-testid="ordertype-tif-row"
            style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px', marginBottom: '14px' }}
          >
            <div>
              <label style={s.label}>Order Type</label>
              <div style={s.toggleGroup}>
                <button style={s.toggleBtn(orderType === 'MARKET')} onClick={() => setOrderType('MARKET')} disabled={isExpired}>MARKET</button>
                <button style={s.toggleBtn(orderType === 'LIMIT')}  onClick={() => setOrderType('LIMIT')}  disabled={isExpired}>LIMIT</button>
              </div>
            </div>
            <div>
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
          </div>

          {/* Row 5 — Quantity + Limit Price (2-column grid; Limit Price absent when MARKET) */}
          <div
            data-testid="qty-limit-row"
            style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px', marginBottom: '14px' }}
          >
            <div>
              <label style={s.label}>Quantity (Shares)</label>
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
            {orderType === 'LIMIT' && (
              <div>
                <label style={s.label}>Limit Price ($)</label>
                <input
                  aria-label="Limit Price"
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

          {/* Row 6 — GTD Date Picker (inline, conditional on tif === 'GTD') */}
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

          {/* Order Summary (SCRUM-186) */}
          <div style={s.fieldGroup}>
            <label style={s.label}>Order Summary</label>
            <div data-testid="order-summary-box" style={s.summaryBox}>
              {!orderSummaryText ? (
                <span style={s.summaryPlaceholder}>Complete the order details to preview your summary.</span>
              ) : (
                <>
                  <div>
                    <span style={s.sideBadge(side)}>{side.replace('_', ' ')}</span>
                    {' '}{orderSummaryText}
                  </div>
                  {orderSummaryValue && <div style={s.summaryValue}>{orderSummaryValue}</div>}
                </>
              )}
            </div>
          </div>

          {/* Submit button */}
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
            ) : isExpired ? 'Order Window Expired'
              : !allChecksPass || symbolNotFound ? 'Complete required fields'
              : 'Review & Submit Order'}
          </button>
        </div>

        {/* ── Right: ORDER PREVIEW + MARKET QUOTE (SCRUM-187) ── */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>

          {/* ORDER PREVIEW */}
          <div style={s.card}>
            <h2 style={s.cardTitle}>Order Preview</h2>
            <div style={s.row}>
              <span style={s.rowLabel}>Estimated Value</span>
              <span style={s.rowValue(true, 'var(--color-primary)')}>
                {orderValue > 0 ? fmt(orderValue) : '—'}
              </span>
            </div>
            <div style={s.row}>
              <span style={s.rowLabel}>Cash Balance</span>
              <span style={s.rowValue()}>{portfolio ? fmt(portfolio.cashBalance) : '—'}</span>
            </div>
            <div style={s.row}>
              <span style={s.rowLabel}>Reserved Cash</span>
              <span style={s.rowValue()}>{portfolio ? fmt(portfolio.reservedCash) : '—'}</span>
            </div>
            <div style={s.row}>
              <span style={s.rowLabel}>Buying Power</span>
              <span style={s.rowValue(true, 'var(--color-success)')}>
                {portfolio ? fmt(portfolio.buyingPower) : '—'}
              </span>
            </div>
            <div style={s.divider} />
            <div style={s.row}>
              <span style={s.rowLabel}>Post-order Cash</span>
              <span style={s.rowValue(true, postOrderCash < 0 ? 'var(--color-error)' : 'var(--color-text-primary)')}>
                {portfolio ? fmt(postOrderCash) : '—'}
              </span>
            </div>
            <div style={s.row}>
              <span style={s.rowLabel}>Buying Power Used</span>
              <span style={s.rowValue()}>{portfolio ? `${bpUsedPct.toFixed(1)}%` : '—'}</span>
            </div>
            <div style={{ background: 'var(--color-border)', height: '6px', borderRadius: 'var(--radius-pill)', overflow: 'hidden', marginTop: '4px' }}>
              <div
                className="bp-progress-bar"
                style={{
                  height: '100%',
                  width: `${portfolio ? Math.min(bpUsedPct, 100) : 0}%`,
                  background: bpUsedPct > 80 ? 'var(--color-warning)' : 'var(--color-success)',
                  transition: 'width 0.3s ease',
                }}
              />
            </div>
            {side === 'SELL' && !portfolio && clientId && (
              <div style={{ ...s.errorText, marginTop: '4px' }}>
                Portfolio data unavailable — holding check skipped
              </div>
            )}
          </div>

          {/* MARKET QUOTE */}
          <div style={s.card}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px', paddingBottom: '10px', borderBottom: '1px solid var(--color-border-light)' }}>
              <span style={{ fontSize: '11px', fontWeight: 700, color: 'var(--color-text-secondary)', textTransform: 'uppercase' as const, letterSpacing: '0.06em' }}>Market Quote</span>
              <span style={{ fontSize: '13px', fontWeight: 700, color: 'var(--color-text-primary)' }}>{symbol || '—'}</span>
            </div>
            {!symbol && !quoteLoading && (
              <div style={{ color: 'var(--color-text-muted)', fontSize: '13px' }}>Enter a symbol to see the current quote.</div>
            )}
            {quoteLoading && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                <div className="skeleton" style={{ height: '28px', width: '80px' }} />
                <div className="skeleton" style={{ height: '36px', width: '120px' }} />
              </div>
            )}
            {quote && !quoteLoading && (
              <>
                <div style={{ marginBottom: '8px' }}>
                  <div style={s.ltpPrice}>
                    ${liveLtp ?? quote.lastPrice}
                    {isStale && symbol && (
                      <span style={s.staleBadge} aria-label="Price data is stale"><AlertTriangle size={10} strokeWidth={2.5} />Stale</span>
                    )}
                    {quote.halted && <span style={s.haltedBadge}>HALTED</span>}
                  </div>
                  {(quote.change || quote.changePct) && (
                    <div style={{
                      fontSize: '13px',
                      color: parseFloat(quote.change ?? '0') >= 0 ? 'var(--color-success)' : 'var(--color-error)',
                      fontWeight: 600,
                      marginBottom: '10px',
                    }}>
                      {parseFloat(quote.change ?? '0') >= 0 ? '+' : ''}{quote.change}
                      {quote.changePct && ` (${parseFloat(quote.changePct) >= 0 ? '+' : ''}${quote.changePct}%)`}
                    </div>
                  )}
                </div>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px', marginBottom: '10px' }}>
                  <div style={s.miItem}>
                    <div style={s.miLabel}>OPEN</div>
                    <div style={s.miValue}>{quote.open ?? '—'}</div>
                  </div>
                  <div style={s.miItem}>
                    <div style={s.miLabel}>PREV CLOSE</div>
                    <div style={s.miValue}>{quote.previousClose ?? '—'}</div>
                  </div>
                  <div style={s.miItem}>
                    <div style={s.miLabel}>
                      BID{isStale && symbol && (
                        <span style={s.staleBadge} aria-label="Price data is stale"><AlertTriangle size={10} strokeWidth={2.5} />Stale</span>
                      )}
                    </div>
                    <div style={{ ...s.miValue, color: 'var(--color-success)' }}>${liveBid ?? quote.bidPrice}</div>
                  </div>
                  <div style={s.miItem}>
                    <div style={s.miLabel}>
                      ASK{isStale && symbol && (
                        <span style={s.staleBadge} aria-label="Price data is stale"><AlertTriangle size={10} strokeWidth={2.5} />Stale</span>
                      )}
                    </div>
                    <div style={{ ...s.miValue, color: 'var(--color-error)' }}>${liveAsk ?? quote.askPrice}</div>
                  </div>
                  <div style={s.miItem}>
                    <div style={s.miLabel}>DAY HIGH</div>
                    <div style={s.miValue}>{quote.dayHigh ? `$${fmtDec2(quote.dayHigh)}` : '—'}</div>
                  </div>
                  <div style={s.miItem}>
                    <div style={s.miLabel}>DAY LOW</div>
                    <div style={s.miValue}>{quote.dayLow ? `$${fmtDec2(quote.dayLow)}` : '—'}</div>
                  </div>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', paddingTop: '8px', borderTop: '1px solid var(--color-border-light)' }}>
                  <span style={{ fontSize: '12px', color: 'var(--color-text-muted)' }}>Last Traded Price (LTP)</span>
                  <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--color-text-primary)', fontVariantNumeric: 'tabular-nums' }}>
                    {fmtDec2(liveLtp ?? quote.lastPrice)}
                  </span>
                </div>
              </>
            )}
          </div>

        </div>
      </div>
    </div>
  )
}

export default OrderEntryPage
