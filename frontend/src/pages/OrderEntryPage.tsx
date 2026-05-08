import React, { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { CheckCircle, XCircle } from 'lucide-react'
import DatePicker from 'react-datepicker'
import 'react-datepicker/dist/react-datepicker.css'
import { getHolidays, getInstrument, getInstruments } from '../api/marketApi'
import { getClients, getPortfolio } from '../api/clientApi'
import { submitOrder } from '../api/orderApi'
import { Holiday, Instrument } from '../types/market.types'
import { Client, Portfolio } from '../types/client.types'
import { OrderSide, OrderType, TimeInForce } from '../types/order.types'

// ── Helpers ───────────────────────────────────────────────────────────────────
const fmt = (v: string | number): string => {
  const n = typeof v === 'string' ? parseFloat(v) : v
  if (isNaN(n)) return '—'
  return n.toLocaleString('en-US', { style: 'currency', currency: 'USD' })
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
  page:        { fontFamily: "'Inter', system-ui, sans-serif", padding: '24px', minHeight: 'calc(100vh - 52px)', background: 'var(--color-bg)' } as React.CSSProperties,
  header:      { marginBottom: '20px' } as React.CSSProperties,
  title:       { margin: 0, fontSize: '20px', fontWeight: 700, color: 'var(--color-text-primary)', letterSpacing: '-0.01em' } as React.CSSProperties,
  grid:        { display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '20px', alignItems: 'start' } as React.CSSProperties,
  card:        { background: 'var(--color-surface)', borderRadius: 'var(--radius-lg)', boxShadow: 'var(--shadow-sm)', padding: '24px' } as React.CSSProperties,
  cardTitle:   { margin: '0 0 20px', fontSize: '15px', fontWeight: 600, color: 'var(--color-text-primary)', paddingBottom: '12px', borderBottom: '1px solid var(--color-border-light)' } as React.CSSProperties,
  fieldGroup:  { marginBottom: '16px' } as React.CSSProperties,
  label:       { display: 'block', fontSize: '11px', fontWeight: 600, color: 'var(--color-text-secondary)', marginBottom: '6px', textTransform: 'uppercase' as const, letterSpacing: '0.05em' },
  input:       { width: '100%', padding: '9px 12px', borderRadius: 'var(--radius-md)', border: '1.5px solid var(--color-border)', fontSize: '14px', boxSizing: 'border-box' as const, outline: 'none', transition: 'border-color 0.15s', color: 'var(--color-text-primary)', background: '#fff' },
  inputError:  { width: '100%', padding: '9px 12px', borderRadius: 'var(--radius-md)', border: '1.5px solid var(--color-error)', fontSize: '14px', boxSizing: 'border-box' as const, outline: 'none', color: 'var(--color-text-primary)', background: '#fff' },
  select:      { width: '100%', padding: '9px 12px', borderRadius: 'var(--radius-md)', border: '1.5px solid var(--color-border)', fontSize: '14px', background: '#fff', boxSizing: 'border-box' as const, color: 'var(--color-text-primary)' },
  errorText:   { fontSize: '12px', color: 'var(--color-error)', marginTop: '4px' } as React.CSSProperties,

  // Toggle group
  toggleGroup: { display: 'flex', borderRadius: 'var(--radius-md)', overflow: 'hidden', border: '1.5px solid var(--color-border)' } as React.CSSProperties,
  toggleBtn:   (active: boolean, color?: string) => ({
    flex: 1, padding: '9px', fontSize: '13px', fontWeight: 600, cursor: 'pointer', border: 'none',
    background: active ? (color ?? 'var(--color-primary)') : '#f9fafb',
    color:      active ? '#fff' : 'var(--color-text-secondary)',
    transition: 'background 0.15s',
  } as React.CSSProperties),

  // Right panel sections
  divider:    { borderTop: '1px solid var(--color-border-light)', margin: '16px 0' } as React.CSSProperties,
  row:        { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '10px' } as React.CSSProperties,
  rowLabel:   { fontSize: '13px', color: 'var(--color-text-secondary)' } as React.CSSProperties,
  rowValue:   (bold?: boolean, color?: string) => ({ fontSize: '13px', fontWeight: bold ? 700 : 400, color: color ?? 'var(--color-text-primary)', fontVariantNumeric: 'tabular-nums' } as React.CSSProperties),

  // Quote card
  quoteSymbol: { fontSize: '22px', fontWeight: 800, color: 'var(--color-text-primary)', marginBottom: '4px' } as React.CSSProperties,
  quotePrice:  { fontSize: '28px', fontWeight: 700, color: 'var(--color-primary)', marginBottom: '12px', fontVariantNumeric: 'tabular-nums' } as React.CSSProperties,
  quoteMeta:   { display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px', marginBottom: '12px' } as React.CSSProperties,
  quoteItem:   { background: 'var(--color-bg)', borderRadius: 'var(--radius-md)', padding: '8px 10px' } as React.CSSProperties,
  quoteItemLabel: { fontSize: '10px', color: 'var(--color-text-muted)', fontWeight: 600, textTransform: 'uppercase' as const } as React.CSSProperties,
  quoteItemVal:   { fontSize: '14px', fontWeight: 600, color: 'var(--color-text-primary)', marginTop: '2px', fontVariantNumeric: 'tabular-nums' } as React.CSSProperties,
  haltedBadge: { display: 'inline-block', padding: '2px 8px', background: 'var(--color-error-bg)', color: 'var(--color-error)', borderRadius: 'var(--radius-sm)', fontSize: '11px', fontWeight: 700, marginLeft: '8px' } as React.CSSProperties,

  // Validation check
  checkRow:   { display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '6px', fontSize: '12px' } as React.CSSProperties,
  checkText:  (pass: boolean) => ({ color: pass ? 'var(--color-text-primary)' : 'var(--color-text-muted)' } as React.CSSProperties),

  // Toast
  toast: { position: 'fixed' as const, top: '20px', right: '20px', background: 'var(--color-nav)', color: '#fff', padding: '14px 20px', borderRadius: 'var(--radius-lg)', boxShadow: 'var(--shadow-lg)', zIndex: 9999, fontSize: '14px', fontWeight: 500 },

  // Error banner / modal
  errorBanner:  { padding: '12px 16px', background: 'var(--color-error-bg)', border: '1px solid #fca5a5', borderRadius: 'var(--radius-md)', color: '#b91c1c', fontSize: '13px', marginBottom: '16px' } as React.CSSProperties,
  modalOverlay: { position: 'fixed' as const, inset: 0, background: 'rgba(0,0,0,0.4)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 9998 },
  modal:        { background: '#fff', borderRadius: 'var(--radius-xl)', padding: '28px', maxWidth: '420px', width: '90%', boxShadow: 'var(--shadow-lg)' } as React.CSSProperties,
  modalTitle:   { margin: '0 0 12px', fontSize: '17px', fontWeight: 700, color: '#b91c1c' } as React.CSSProperties,
  modalBody:    { fontSize: '13px', color: 'var(--color-text-primary)', marginBottom: '20px', lineHeight: 1.6 } as React.CSSProperties,
  modalClose:   { padding: '8px 20px', background: 'var(--color-primary)', color: '#fff', border: 'none', borderRadius: 'var(--radius-md)', cursor: 'pointer', fontWeight: 600 } as React.CSSProperties,

  // GTD date picker wrapper
  datepickerWrapper:      { border: '1.5px solid var(--color-border)', borderRadius: 'var(--radius-md)', overflow: 'hidden' } as React.CSSProperties,
  datepickerWrapperError: { border: '1.5px solid var(--color-error)', borderRadius: 'var(--radius-md)', overflow: 'hidden' } as React.CSSProperties,
}

// ── Types ─────────────────────────────────────────────────────────────────────
interface FieldErrors { quantity?: string; limitPrice?: string; gtdDate?: string; symbol?: string }

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
  const [tif,         setTif]         = useState<TimeInForce>('DAY')
  const [gtdDate,     setGtdDate]     = useState<Date | null>(null)

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

  const symbolDebounce = useRef<ReturnType<typeof setTimeout> | null>(null)

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
    getPortfolio(clientId).then(setPortfolio).catch(() => setPortfolio(null))
  }, [clientId])

  useEffect(() => {
    if (symbolDebounce.current) clearTimeout(symbolDebounce.current)
    if (!symbol) { setQuote(null); return }
    symbolDebounce.current = setTimeout(async () => {
      setQuoteLoading(true)
      const q = await getInstrument(symbol)
      setQuote(q)
      if (q && !limitPrice) {
        setLimitPrice(side === 'BUY' ? q.askPrice : q.bidPrice)
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

  const filterWeekdays = (date: Date): boolean => date.getDay() !== 0 && date.getDay() !== 6

  const isWeekendOrHoliday = (dateStr: string): boolean => {
    if (!dateStr) return false
    const d = new Date(dateStr + 'T12:00:00')
    if (d.getDay() === 0 || d.getDay() === 6) return true
    return holidays.some((h) => h.holidayDate === dateStr)
  }

  const checks = {
    symbol:     symbol.length > 0 && symbol.length <= 20 && /^[A-Z0-9]+$/.test(symbol),
    quantity:   !isNaN(qty) && qty > 0,
    limitPrice: orderType === 'MARKET' || (!isNaN(parseFloat(limitPrice)) && parseFloat(limitPrice) > 0),
    gtdDate:    tif === 'DAY' || (gtdDate !== null && gtdDateStr >= tomorrowStr() && !isWeekendOrHoliday(gtdDateStr)),
    notHalted:  !quote || !quote.halted,
  }

  const allChecksPass = Object.values(checks).every(Boolean)

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
      })
      setToastMsg(`Order ${res.orderId} submitted successfully`)
      setShowToast(true)
      setTimeout(() => { setShowToast(false); navigate('/orders') }, 2500)
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { error?: { code?: string; message?: string } } } }
      const code    = axiosErr?.response?.data?.error?.code ?? ''
      const message = axiosErr?.response?.data?.error?.message ?? 'An error occurred. Please try again.'

      if (code === 'MARKET_CLOSED') {
        setSubmitError({ type: 'MARKET_CLOSED', message })
      } else if (code.includes('RISK')) {
        setSubmitError({ type: 'RISK_REJECTED', message, ruleCode: code })
        setShowModal(true)
      } else if (code === 'INVALID_GTD_DATE') {
        setSubmitError({ type: 'INVALID_GTD_DATE', message })
        setFieldErrors((f) => ({ ...f, gtdDate: message }))
      } else {
        setSubmitError({ type: 'VALIDATION_ERROR', message })
      }
    } finally {
      setSubmitting(false)
    }
  }

  // Side-aware accent color
  const sideAccent = side === 'BUY' ? 'var(--color-buy)' : 'var(--color-sell)'
  const submitBg   = submitting || !allChecksPass
    ? '#9ca3af'
    : side === 'BUY'
      ? 'linear-gradient(135deg, #2563eb, #1d4ed8)'
      : 'linear-gradient(135deg, #db2777, #be185d)'

  // ── Render ───────────────────────────────────────────────────────────────
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
        <div style={s.card}>
          <h2 style={s.cardTitle}>Order Details</h2>

          {submitError && submitError.type === 'MARKET_CLOSED' && (
            <div style={s.errorBanner}>Market is currently closed. {submitError.message}</div>
          )}
          {submitError && submitError.type === 'VALIDATION_ERROR' && (
            <div style={s.errorBanner}>{submitError.message}</div>
          )}

          {/* Client */}
          <div style={s.fieldGroup}>
            <label style={s.label}>Client</label>
            <select style={s.select} value={clientId} onChange={(e) => setClientId(e.target.value)}>
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
              style={fieldErrors.symbol ? s.inputError : s.input}
              placeholder="e.g. AAPL"
              value={symbolInput}
              onChange={(e) => {
                const v = e.target.value.toUpperCase()
                setSymbolInput(v)
                setSymbol(v)
              }}
              autoComplete="off"
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
            {fieldErrors.symbol && <div style={s.errorText}>{fieldErrors.symbol}</div>}
          </div>

          {/* Side */}
          <div style={s.fieldGroup}>
            <label style={s.label}>Side</label>
            <div style={s.toggleGroup}>
              <button style={s.toggleBtn(side === 'BUY',  '#2563eb')} onClick={() => setSide('BUY')}>BUY</button>
              <button style={s.toggleBtn(side === 'SELL', '#be185d')} onClick={() => setSide('SELL')}>SELL</button>
            </div>
          </div>

          {/* Order Type */}
          <div style={s.fieldGroup}>
            <label style={s.label}>Order Type</label>
            <div style={s.toggleGroup}>
              <button style={s.toggleBtn(orderType === 'MARKET')} onClick={() => setOrderType('MARKET')}>MARKET</button>
              <button style={s.toggleBtn(orderType === 'LIMIT')}  onClick={() => setOrderType('LIMIT')}>LIMIT</button>
            </div>
          </div>

          {/* Quantity */}
          <div style={s.fieldGroup}>
            <label style={s.label}>Quantity</label>
            <input
              style={fieldErrors.quantity ? s.inputError : s.input}
              type="number" min="1" step="1"
              placeholder="Number of shares"
              value={quantity}
              onChange={(e) => setQuantity(e.target.value)}
            />
            {fieldErrors.quantity && <div style={s.errorText}>{fieldErrors.quantity}</div>}
          </div>

          {/* Limit Price */}
          {orderType === 'LIMIT' && (
            <div style={s.fieldGroup}>
              <label style={s.label}>Limit Price ($)</label>
              <input
                style={fieldErrors.limitPrice ? s.inputError : s.input}
                type="number" min="0.01" step="0.01"
                placeholder="0.00"
                value={limitPrice}
                onChange={(e) => setLimitPrice(e.target.value)}
              />
              {fieldErrors.limitPrice && <div style={s.errorText}>{fieldErrors.limitPrice}</div>}
            </div>
          )}

          {/* TIF */}
          <div style={s.fieldGroup}>
            <label style={s.label}>Time in Force</label>
            <div style={s.toggleGroup}>
              <button
                style={s.toggleBtn(tif === 'DAY')}
                onClick={() => { setTif('DAY'); setGtdDate(null) }}
              >DAY</button>
              <button
                style={s.toggleBtn(tif === 'GTD')}
                onClick={() => setTif('GTD')}
              >GTD</button>
            </div>
          </div>

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

          {/* Submit */}
          <button
            style={{
              width: '100%', padding: '12px',
              borderRadius: 'var(--radius-md)', border: 'none',
              background: submitBg, color: '#fff',
              fontSize: '15px', fontWeight: 700,
              cursor: submitting || !allChecksPass ? 'not-allowed' : 'pointer',
              marginTop: '8px', transition: 'opacity 0.2s',
            }}
            onClick={handleSubmit}
            disabled={submitting || !allChecksPass}
          >
            {submitting ? 'Submitting…' : 'Submit Order'}
          </button>
        </div>

        {/* ── Right: Preview + Validation + Quote ── */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>

          {/* Order Preview — side-accented top border */}
          <div style={{ ...s.card, borderTop: `3px solid ${sideAccent}`, paddingTop: '21px' }}>
            <h2 style={s.cardTitle}>Order Preview</h2>

            <div style={s.row}>
              <span style={{ ...s.rowLabel, color: sideAccent, fontWeight: 600 }}>Estimated Value</span>
              <span style={{ ...s.rowValue(true), color: sideAccent }}>{orderValue > 0 ? fmt(orderValue) : '—'}</span>
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
              <span style={s.rowValue(true, 'var(--color-success)')}>{portfolio ? fmt(portfolio.buyingPower) : '—'}</span>
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
          </div>

          {/* Validation */}
          <div style={s.card}>
            <h2 style={s.cardTitle}>Validation</h2>
            {[
              { label: 'Symbol entered',                                                                                    pass: checks.symbol     },
              { label: 'Quantity > 0',                                                                                      pass: checks.quantity   },
              { label: orderType === 'LIMIT' ? 'Limit price > 0' : 'Market order — no price needed',                       pass: checks.limitPrice },
              { label: tif === 'GTD'   ? 'GTD date is a future trading day' : 'DAY order — no expiry needed',              pass: checks.gtdDate    },
              { label: 'Symbol not halted',                                                                                 pass: checks.notHalted  },
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

          {/* Market Quote */}
          <div style={s.card}>
            <h2 style={s.cardTitle}>Market Quote</h2>
            {!quote && !quoteLoading && (
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
                <div style={s.quoteSymbol}>
                  {quote.symbol}
                  {quote.halted && <span style={s.haltedBadge}>HALTED</span>}
                </div>
                <div style={s.quotePrice}>${quote.lastPrice}</div>
                <div style={s.quoteMeta}>
                  {[
                    { label: 'Bid',     value: `$${quote.bidPrice}`           },
                    { label: 'Ask',     value: `$${quote.askPrice}`           },
                    { label: 'Min Qty', value: quote.minQuantity?.toLocaleString() ?? '—' },
                    { label: 'Max Qty', value: quote.maxQuantity?.toLocaleString() ?? '—' },
                  ].map(({ label, value }) => (
                    <div key={label} style={s.quoteItem}>
                      <div style={s.quoteItemLabel}>{label}</div>
                      <div style={s.quoteItemVal}>{value}</div>
                    </div>
                  ))}
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
