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
import { formatPriceToTwoDecimal } from '../utils/priceUtils'
import { IDLE_TIMER_SECONDS, SYMBOL_DEBOUNCE_MS, TOAST_SUCCESS_NAV_MS } from '../utils/constants'
import ToastStack from '../components/ToastStack'
import { TOAST_TYPE } from '../components/Toast'
import { useToast } from '../hooks/useToast'

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
  grid:       { display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '20px' } as React.CSSProperties,
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
interface FieldErrors { client?: string; route?: string; quantity?: string; limitPrice?: string; gtdDate?: string; symbol?: string }
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
  const [routingDestination, setRoutingDestination] = useState('')

  const [instruments,  setInstruments]  = useState<Instrument[]>([])
  const [quote,        setQuote]        = useState<Instrument | null>(null)
  const [portfolio,    setPortfolio]    = useState<Portfolio | null>(null)
  const [quoteLoading, setQuoteLoading] = useState(false)
  const [holidays,     setHolidays]     = useState<Holiday[]>([])

  const [submitting,   setSubmitting]   = useState(false)
  const [submitError,  setSubmitError]  = useState<SubmitError | null>(null)
  const { toasts, addToast } = useToast()
  const [fieldErrors,  setFieldErrors]  = useState<FieldErrors>({})
  const [showModal,    setShowModal]    = useState(false)
  const [symbolNotFound, setSymbolNotFound] = useState(false)
  const [portfolioError, setPortfolioError] = useState(false)
  const [clientsError,   setClientsError]   = useState(false)
  const [quoteError,     setQuoteError]     = useState(false)

  const symbolDebounce    = useRef<ReturnType<typeof setTimeout> | null>(null)
  const clientSelectRef   = useRef<HTMLSelectElement>(null)
  const routeSelectRef    = useRef<HTMLSelectElement>(null)
  const focusOnRestartRef = useRef(false)

  // Toggle button refs for roving tabIndex
  const buyRef        = useRef<HTMLButtonElement>(null)
  const sellRef       = useRef<HTMLButtonElement>(null)
  const shortSellRef  = useRef<HTMLButtonElement>(null)
  const marketRef     = useRef<HTMLButtonElement>(null)
  const limitRef      = useRef<HTMLButtonElement>(null)
  const dayRef        = useRef<HTMLButtonElement>(null)
  const gtdRef        = useRef<HTMLButtonElement>(null)

  // ── Real-time Bid/Ask (SCRUM-108) ────────────────────────────────────────────
  const { bid: liveBid, ask: liveAsk, ltp: liveLtp, isStale } = useOrderEntryBidAsk(symbol)

  // ── Idle timer ────────────────────────────────────────────────────────────────
  const { secondsRemaining, isExpired, resetTimer } = useIdleTimer(IDLE_TIMER_SECONDS)

  const handleInteraction = useCallback(() => {
    resetTimer()
  }, [resetTimer])

  const rove = useCallback((
    e: React.KeyboardEvent,
    refs: React.RefObject<HTMLButtonElement>[],
    currentIdx: number,
    onSelect: (idx: number) => void,
  ) => {
    if (e.key !== 'ArrowLeft' && e.key !== 'ArrowRight') return
    e.preventDefault()
    const n = refs.length
    const next = e.key === 'ArrowRight' ? (currentIdx + 1) % n : (currentIdx - 1 + n) % n
    onSelect(next)
    refs[next].current?.focus()
  }, [])

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
    setQuoteError(false)
    setSide('BUY')
    setOrderType('LIMIT')
    setRoutingDestination('')
  }, [isExpired])

  // Focus client dropdown when the user restarts after an expiry
  useEffect(() => {
    if (!isExpired && focusOnRestartRef.current) {
      focusOnRestartRef.current = false
      clientSelectRef.current?.focus()
    }
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
    getInstruments().then(setInstruments).catch(() => {
      addToast('Failed to load instrument list — symbol autocomplete unavailable', TOAST_TYPE.WARNING)
    })
    getHolidays().then(setHolidays).catch(() => {
      addToast('Failed to load market holidays — holiday dates may not be excluded from the date picker', TOAST_TYPE.WARNING)
    })
    getClients().then((list) => {
      setClients(list)
      setClientsError(false)
      if (list.length > 0) clientSelectRef.current?.focus()
    }).catch(() => {
      setClientsError(true)
      addToast('Failed to load client list — please refresh and try again', TOAST_TYPE.ERROR)
    })
  }, [])

  useEffect(() => {
    setPortfolio(null)
    setPortfolioError(false)
    if (clientId) getPortfolio(clientId).then(setPortfolio).catch(() => { setPortfolio(null); setPortfolioError(true) })
  }, [clientId])

  useEffect(() => {
    if (symbolDebounce.current) clearTimeout(symbolDebounce.current)
    if (!symbol) { setQuote(null); setSymbolNotFound(false); setQuoteError(false); return }
    symbolDebounce.current = setTimeout(async () => {
      setQuoteLoading(true)
      setSymbolNotFound(false)
      setQuoteError(false)
      try {
        const q = await getInstrument(symbol)
        setQuote(q)
        if (q && !limitPrice) {
          setLimitPrice(formatPriceToTwoDecimal(side === 'BUY' ? q.askPrice : q.bidPrice))
        }
      } catch (err: unknown) {
        const axiosErr = err as { response?: { data?: { error?: { code?: string } } } }
        const code = axiosErr?.response?.data?.error?.code
        if (code === 'SYMBOL_NOT_FOUND') {
          setSymbolNotFound(true)
          setQuote(null)
        } else {
          setQuote(null)
          setQuoteError(true)
          addToast(`Failed to fetch quote for ${symbol} — market data temporarily unavailable`, TOAST_TYPE.WARNING)
        }
      }
      setQuoteLoading(false)
    }, SYMBOL_DEBOUNCE_MS)
  }, [symbol]) // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (quote) setLimitPrice(formatPriceToTwoDecimal(side === 'BUY' ? quote.askPrice : quote.bidPrice))
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

  const fractionalQuantityError: string | null =
    quantity !== '' && !isNaN(parseFloat(quantity)) && parseFloat(quantity) % 1 !== 0
      ? 'Quantity must be a whole number — fractional shares are not supported'
      : null

  const limitPriceDecimalError: string | null =
    orderType === 'LIMIT' && limitPrice.includes('.') && limitPrice.split('.')[1].length > 2
      ? 'Limit price cannot have more than 2 digits after the decimal'
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
    client:       clientId.length > 0,
    route:        SOR_VENUES.length === 0 || routingDestination.length > 0,
    symbol:       symbol.length > 0 && symbol.length <= 20 && /^[A-Z0-9]+$/.test(symbol),
    quantity:     !isNaN(qty) && qty > 0 && fractionalQuantityError === null,
    limitPrice:   orderType === 'MARKET' || (!isNaN(parseFloat(limitPrice)) && parseFloat(limitPrice) > 0 && limitPriceDecimalError === null),
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
    const effectiveRoute = SOR_VENUES.length === 0 ? DEFAULT_VENUE : routingDestination
    return `${qtyStr} ${symbol} @ ${priceStr} · ${tif} · Route ${effectiveRoute}`
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
      addToast(`Order ${res.orderId} submitted successfully`, TOAST_TYPE.SUCCESS, TOAST_SUCCESS_NAV_MS)
      setTimeout(() => navigate('/orders'), TOAST_SUCCESS_NAV_MS)
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
      <ToastStack toasts={toasts} />

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
          className="order-form-card"
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
              <button style={s.restartBtn} onClick={() => { focusOnRestartRef.current = true; resetTimer() }}>Start New Order</button>
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
            <div className="field-group">
              <label htmlFor="client-select" style={s.label}>Client</label>
              <select
                id="client-select"
                ref={clientSelectRef}
                style={fieldErrors.client ? { ...s.select, border: '1.5px solid var(--color-error)' } : s.select}
                value={clientId}
                onChange={(e) => { setClientId(e.target.value); if (e.target.value) setFieldErrors((f) => ({ ...f, client: undefined })) }}
                onBlur={() => { if (!clientSelectRef.current?.value) setFieldErrors((f) => ({ ...f, client: 'required' })) }}
                onKeyDown={(e) => { if (e.key === 'ArrowDown' || e.key === 'ArrowUp') { e.preventDefault(); clientSelectRef.current?.showPicker() } }}
                disabled={isExpired}
              >
                {clientsError
                  ? <option value="API_ERROR">Failed to load clients</option>
                  : clients.length === 0
                    ? <option value="">Loading clients…</option>
                    : <><option value="">Select client…</option>{clients.map((c) => <option key={c.clientId} value={c.clientId}>{c.name}</option>)}</>
                }
              </select>
            </div>
            <div className="field-group">
              <label style={s.label}>Route To</label>
              <select
                style={fieldErrors.route ? { ...s.select, border: '1.5px solid var(--color-error)' } : s.select}
                value={SOR_VENUES.length === 0 ? DEFAULT_VENUE : routingDestination}
                onChange={(e) => { setRoutingDestination(e.target.value); if (e.target.value) setFieldErrors((f) => ({ ...f, route: undefined })) }}
                onBlur={() => { if (SOR_VENUES.length > 0 && !routeSelectRef.current?.value) setFieldErrors((f) => ({ ...f, route: 'required' })) }}
                ref={routeSelectRef}
                onKeyDown={(e) => { if (e.key === 'ArrowDown' || e.key === 'ArrowUp') { e.preventDefault(); routeSelectRef.current?.showPicker() } }}
                disabled={isExpired || SOR_VENUES.length === 0}
                title={SOR_VENUES.length === 0 ? 'No venues configured' : undefined}
                aria-label="Route To"
              >
                {SOR_VENUES.length === 0 ? (
                  <option value={DEFAULT_VENUE}>{DEFAULT_VENUE}</option>
                ) : (
                  <><option value="">Select route…</option>{SOR_VENUES.map((venue) => <option key={venue} value={venue}>{venue}</option>)}</>
                )}
              </select>
            </div>
          </div>

          {/* Row 2 — Symbol typeahead (full-width, search icon) */}
          <div className="field-group" style={{ ...s.fieldGroup, position: 'relative' }}>
            <label style={s.label}>Symbol</label>
            <div style={{ position: 'relative' }}>
              <span style={{
                position: 'absolute', left: '12px', top: '50%', transform: 'translateY(-50%)',
                color: 'var(--color-text-muted)', pointerEvents: 'none', display: 'flex', alignItems: 'center',
              }}>
                <Search size={15} />
              </span>
              <input
                aria-label="Symbol"
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
                onKeyDown={(e) => { if (e.key === 'Escape') setSymbolInput(symbol) }}
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
          <div className="field-group" style={s.fieldGroup}>
            <label style={s.label}>Side</label>
            <div
              style={s.toggleGroup}
              role="group"
              aria-label="Side"
              onKeyDown={(e) => rove(e, [buyRef, sellRef, shortSellRef], ['BUY', 'SELL', 'SHORT_SELL'].indexOf(side), (i) => setSide((['BUY', 'SELL', 'SHORT_SELL'] as OrderSide[])[i]))}
            >
              <button ref={buyRef}       tabIndex={side === 'BUY'        ? 0 : -1} style={s.sideToggleBtn(side === 'BUY',        'var(--color-buy)')}     onClick={() => setSide('BUY')}        disabled={isExpired} aria-pressed={side === 'BUY'}>BUY</button>
              <button ref={sellRef}      tabIndex={side === 'SELL'       ? 0 : -1} style={s.sideToggleBtn(side === 'SELL',       'var(--color-sell)')}    onClick={() => setSide('SELL')}       disabled={isExpired} aria-pressed={side === 'SELL'}>SELL</button>
              <button ref={shortSellRef} tabIndex={side === 'SHORT_SELL' ? 0 : -1} style={s.sideToggleBtn(side === 'SHORT_SELL', 'var(--color-warning)')} onClick={() => setSide('SHORT_SELL')} disabled={isExpired} aria-pressed={side === 'SHORT_SELL'}>SHORT SELL</button>
            </div>
          </div>

          {/* Row 4 — Order Type + Time in Force (2-column grid) */}
          <div
            data-testid="ordertype-tif-row"
            style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px', marginBottom: '14px' }}
          >
            <div className="field-group">
              <label style={s.label}>Order Type</label>
              <div
                style={s.toggleGroup}
                role="group"
                aria-label="Order Type"
                onKeyDown={(e) => rove(e, [marketRef, limitRef], ['MARKET', 'LIMIT'].indexOf(orderType), (i) => setOrderType((['MARKET', 'LIMIT'] as OrderType[])[i]))}
              >
                <button ref={marketRef} tabIndex={orderType === 'MARKET' ? 0 : -1} style={s.toggleBtn(orderType === 'MARKET')} onClick={() => setOrderType('MARKET')} disabled={isExpired} aria-pressed={orderType === 'MARKET'}>MARKET</button>
                <button ref={limitRef}  tabIndex={orderType === 'LIMIT'  ? 0 : -1} style={s.toggleBtn(orderType === 'LIMIT')}  onClick={() => setOrderType('LIMIT')}  disabled={isExpired} aria-pressed={orderType === 'LIMIT'}>LIMIT</button>
              </div>
            </div>
            <div className="field-group">
              <label style={s.label}>Time in Force</label>
              <div
                style={s.toggleGroup}
                role="group"
                aria-label="Time in Force"
                onKeyDown={(e) => rove(e, [dayRef, gtdRef], ['DAY', 'GTD'].indexOf(tif), (i) => {
                  const next = (['DAY', 'GTD'] as TimeInForce[])[i]
                  setTif(next)
                  if (next === 'DAY') setGtdDate(null)
                })}
              >
                <button
                  ref={dayRef}
                  tabIndex={tif === 'DAY' ? 0 : -1}
                  style={s.toggleBtn(tif === 'DAY')}
                  onClick={() => { setTif('DAY'); setGtdDate(null) }}
                  disabled={isExpired}
                  aria-pressed={tif === 'DAY'}
                >DAY</button>
                <button
                  ref={gtdRef}
                  tabIndex={tif === 'GTD' ? 0 : -1}
                  style={s.toggleBtn(tif === 'GTD')}
                  onClick={() => setTif('GTD')}
                  disabled={isExpired}
                  aria-pressed={tif === 'GTD'}
                >GTD</button>
              </div>
            </div>
          </div>

          {/* Row 5 — Quantity + Limit Price (2-column grid; Limit Price absent when MARKET) */}
          <div
            data-testid="qty-limit-row"
            style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px', marginBottom: '14px' }}
          >
            <div className="field-group">
              <label style={s.label}>Quantity (Shares)</label>
              <input
                aria-label="Quantity (Shares)"
                style={fieldErrors.quantity || sellHoldingsError || fractionalQuantityError ? s.inputError : s.input}
                type="number" min="1" step="1"
                placeholder="Number of shares"
                value={quantity}
                onChange={(e) => setQuantity(e.target.value)}
                disabled={isExpired}
              />
              {(fieldErrors.quantity || fractionalQuantityError || sellHoldingsError) && (
                <div style={s.errorText}>{fieldErrors.quantity ?? fractionalQuantityError ?? sellHoldingsError}</div>
              )}
            </div>
            {orderType === 'LIMIT' && (
              <div className="field-group">
                <label style={s.label}>Limit Price ($)</label>
                <input
                  aria-label="Limit Price"
                  style={fieldErrors.limitPrice || limitPriceDecimalError ? s.inputError : s.input}
                  type="number" min="0.01" step="0.01"
                  placeholder="0.00"
                  value={limitPrice}
                  onChange={(e) => setLimitPrice(e.target.value)}
                  disabled={isExpired}
                />
                {(fieldErrors.limitPrice || limitPriceDecimalError) && (
                  <div style={s.errorText}>{fieldErrors.limitPrice ?? limitPriceDecimalError}</div>
                )}
              </div>
            )}
          </div>

          {/* Row 6 — GTD Date Picker (inline, conditional on tif === 'GTD') */}
          {tif === 'GTD' && (
            <div className="field-group" style={s.fieldGroup}>
              <label htmlFor="gtd-date-input" style={s.label}>GTD Date</label>
              <div style={fieldErrors.gtdDate ? s.datepickerWrapperError : s.datepickerWrapper}>
                <DatePicker
                  id="gtd-date-input"
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
            aria-label="Submit Order"
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
            {portfolioError && !portfolio && clientId && (
              <div style={{ ...s.errorText, marginTop: '4px' }}>
                Unable to load portfolio data — please refresh
              </div>
            )}
            {side === 'SELL' && !portfolio && !portfolioError && clientId && (
              <div style={{ ...s.errorText, marginTop: '4px' }}>
                Portfolio data unavailable — holding check skipped
              </div>
            )}
          </div>

          {/* MARKET QUOTE */}
          <div style={{ ...s.card, flex: 1 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px', paddingBottom: '10px', borderBottom: '1px solid var(--color-border-light)' }}>
              <span style={{ fontSize: '11px', fontWeight: 700, color: 'var(--color-text-secondary)', textTransform: 'uppercase' as const, letterSpacing: '0.06em' }}>Market Quote</span>
              <span style={{ fontSize: '13px', fontWeight: 700, color: 'var(--color-text-primary)' }}>{symbol || '—'}</span>
            </div>
            {!symbol && !quoteLoading && (
              <div style={{ color: 'var(--color-text-muted)', fontSize: '13px' }}>Enter a symbol to see the current quote.</div>
            )}
            {quoteError && !quoteLoading && symbol && (
              <div style={{ color: 'var(--color-text-muted)', fontSize: '13px' }}>Unable to fetch quote — market data temporarily unavailable</div>
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
