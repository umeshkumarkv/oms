import React, { useEffect, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
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

// ── Styles ────────────────────────────────────────────────────────────────────
const s = {
  page:        { fontFamily: 'system-ui, sans-serif', padding: '24px' } as React.CSSProperties,
  header:      { marginBottom: '20px' } as React.CSSProperties,
  title:       { margin: 0, fontSize: '20px', fontWeight: 700, color: '#1a2236' } as React.CSSProperties,
  grid:        { display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '20px', alignItems: 'start' } as React.CSSProperties,
  card:        { background: '#fff', borderRadius: '10px', boxShadow: '0 1px 3px rgba(0,0,0,0.08)', padding: '24px' } as React.CSSProperties,
  cardTitle:   { margin: '0 0 20px', fontSize: '15px', fontWeight: 600, color: '#1a2236', paddingBottom: '12px', borderBottom: '1px solid #f0f0f0' } as React.CSSProperties,
  fieldGroup:  { marginBottom: '16px' } as React.CSSProperties,
  label:       { display: 'block', fontSize: '12px', fontWeight: 600, color: '#6b7280', marginBottom: '6px', textTransform: 'uppercase' as const, letterSpacing: '0.04em' },
  input:       { width: '100%', padding: '9px 12px', borderRadius: '6px', border: '1px solid #d1d5db', fontSize: '14px', boxSizing: 'border-box' as const, outline: 'none' },
  inputError:  { width: '100%', padding: '9px 12px', borderRadius: '6px', border: '1px solid #ef4444', fontSize: '14px', boxSizing: 'border-box' as const, outline: 'none' },
  select:      { width: '100%', padding: '9px 12px', borderRadius: '6px', border: '1px solid #d1d5db', fontSize: '14px', background: '#fff', boxSizing: 'border-box' as const },
  errorText:   { fontSize: '12px', color: '#ef4444', marginTop: '4px' } as React.CSSProperties,

  // Toggle group
  toggleGroup: { display: 'flex', borderRadius: '6px', overflow: 'hidden', border: '1px solid #d1d5db' } as React.CSSProperties,
  toggleBtn:   (active: boolean, color?: string) => ({
    flex: 1, padding: '9px', fontSize: '13px', fontWeight: 600, cursor: 'pointer', border: 'none',
    background: active ? (color ?? '#6366f1') : '#f9fafb',
    color:      active ? '#fff' : '#6b7280',
    transition: 'background 0.15s',
  } as React.CSSProperties),

  // Submit button
  submitBtn: (loading: boolean) => ({
    width: '100%', padding: '12px', borderRadius: '8px', border: 'none',
    background: loading ? '#9ca3af' : '#6366f1', color: '#fff',
    fontSize: '15px', fontWeight: 700, cursor: loading ? 'not-allowed' : 'pointer',
    marginTop: '8px', transition: 'background 0.2s',
  } as React.CSSProperties),

  // Right panel sections
  divider:    { borderTop: '1px solid #f0f0f0', margin: '16px 0' } as React.CSSProperties,
  row:        { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '10px' } as React.CSSProperties,
  rowLabel:   { fontSize: '13px', color: '#6b7280' } as React.CSSProperties,
  rowValue:   (bold?: boolean, color?: string) => ({ fontSize: '13px', fontWeight: bold ? 700 : 400, color: color ?? '#1a2236' } as React.CSSProperties),

  // Quote card
  quoteSymbol: { fontSize: '22px', fontWeight: 800, color: '#1a2236', marginBottom: '4px' } as React.CSSProperties,
  quotePrice:  { fontSize: '28px', fontWeight: 700, color: '#6366f1', marginBottom: '12px' } as React.CSSProperties,
  quoteMeta:   { display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px', marginBottom: '12px' } as React.CSSProperties,
  quoteItem:   { background: '#f9fafb', borderRadius: '6px', padding: '8px 10px' } as React.CSSProperties,
  quoteItemLabel: { fontSize: '10px', color: '#9ca3af', fontWeight: 600, textTransform: 'uppercase' as const } as React.CSSProperties,
  quoteItemVal:   { fontSize: '14px', fontWeight: 600, color: '#374151', marginTop: '2px' } as React.CSSProperties,
  haltedBadge: { display: 'inline-block', padding: '2px 8px', background: '#fee2e2', color: '#b91c1c', borderRadius: '4px', fontSize: '11px', fontWeight: 700, marginLeft: '8px' } as React.CSSProperties,

  // Validation check
  checkRow:   { display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '6px', fontSize: '12px' } as React.CSSProperties,
  checkDot:   (pass: boolean) => ({ fontSize: '14px', flexShrink: 0, color: pass ? '#10b981' : '#ef4444' } as React.CSSProperties),
  checkText:  (pass: boolean) => ({ color: pass ? '#374151' : '#9ca3af' } as React.CSSProperties),

  // Toast
  toast:      { position: 'fixed' as const, top: '20px', right: '20px', background: '#1a2236', color: '#fff', padding: '14px 20px', borderRadius: '10px', boxShadow: '0 4px 20px rgba(0,0,0,0.25)', zIndex: 9999, fontSize: '14px', fontWeight: 500 },

  // Error banner / modal
  errorBanner: { padding: '12px 16px', background: '#fef2f2', border: '1px solid #fca5a5', borderRadius: '8px', color: '#b91c1c', fontSize: '13px', marginBottom: '16px' } as React.CSSProperties,
  modalOverlay:{ position: 'fixed' as const, inset: 0, background: 'rgba(0,0,0,0.4)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 9998 },
  modal:       { background: '#fff', borderRadius: '12px', padding: '28px', maxWidth: '420px', width: '90%', boxShadow: '0 8px 40px rgba(0,0,0,0.2)' } as React.CSSProperties,
  modalTitle:  { margin: '0 0 12px', fontSize: '17px', fontWeight: 700, color: '#b91c1c' } as React.CSSProperties,
  modalBody:   { fontSize: '13px', color: '#374151', marginBottom: '20px', lineHeight: 1.6 } as React.CSSProperties,
  modalClose:  { padding: '8px 20px', background: '#6366f1', color: '#fff', border: 'none', borderRadius: '6px', cursor: 'pointer', fontWeight: 600 } as React.CSSProperties,
}

// ── Types ─────────────────────────────────────────────────────────────────────
interface FieldErrors { quantity?: string; limitPrice?: string; expiryDate?: string; symbol?: string }

interface SubmitError {
  type: 'MARKET_CLOSED' | 'RISK_REJECTED' | 'INVALID_GTD_DATE' | 'VALIDATION_ERROR' | 'GENERIC'
  message: string
  ruleCode?: string
}

// ── Component ─────────────────────────────────────────────────────────────────
const OrderEntryPage: React.FC = () => {
  const navigate      = useNavigate()
  const [params]      = useSearchParams()

  // Clients for dropdown
  const [clients, setClients] = useState<Client[]>([])

  // Form state
  const [clientId,    setClientId]    = useState('')
  const [symbol,      setSymbol]      = useState(params.get('symbol') ?? '')
  const [symbolInput, setSymbolInput] = useState(params.get('symbol') ?? '')
  const [side,        setSide]        = useState<OrderSide>('BUY')
  const [orderType,   setOrderType]   = useState<OrderType>('LIMIT')
  const [quantity,    setQuantity]    = useState('')
  const [limitPrice,  setLimitPrice]  = useState('')
  const [tif,         setTif]         = useState<TimeInForce>('DAY')
  const [expiryDate,  setExpiryDate]  = useState('')

  // Remote data
  const [instruments,  setInstruments]  = useState<Instrument[]>([])
  const [quote,        setQuote]        = useState<Instrument | null>(null)
  const [portfolio,    setPortfolio]    = useState<Portfolio | null>(null)
  const [quoteLoading, setQuoteLoading] = useState(false)
  const [holidays,     setHolidays]     = useState<Holiday[]>([])

  // Submission state
  const [submitting,   setSubmitting]   = useState(false)
  const [submitError,  setSubmitError]  = useState<SubmitError | null>(null)
  const [showToast,    setShowToast]    = useState(false)
  const [toastMsg,     setToastMsg]     = useState('')
  const [fieldErrors,  setFieldErrors]  = useState<FieldErrors>({})
  const [showModal,    setShowModal]    = useState(false)

  const symbolDebounce = useRef<ReturnType<typeof setTimeout> | null>(null)

  // Load instruments, holidays, and clients on mount
  useEffect(() => {
    getInstruments().then(setInstruments)
    getHolidays().then(setHolidays)
    getClients().then((list) => {
      setClients(list)
      if (list.length > 0) setClientId(list[0].clientId)
    }).catch(() => { /* client dropdown stays empty */ })
  }, [])

  useEffect(() => {
    getPortfolio(clientId).then(setPortfolio).catch(() => setPortfolio(null))
  }, [clientId])

  // Load quote when symbol changes (debounced)
  useEffect(() => {
    if (symbolDebounce.current) clearTimeout(symbolDebounce.current)
    if (!symbol) { setQuote(null); return }
    symbolDebounce.current = setTimeout(async () => {
      setQuoteLoading(true)
      const q = await getInstrument(symbol)
      setQuote(q)
      // Pre-fill limit price with ask (BUY) or bid (SELL)
      if (q && !limitPrice) {
        setLimitPrice(side === 'BUY' ? q.askPrice : q.bidPrice)
      }
      setQuoteLoading(false)
    }, 400)
  }, [symbol]) // eslint-disable-line react-hooks/exhaustive-deps

  // Update limit price pre-fill when side or quote changes
  useEffect(() => {
    if (quote) setLimitPrice(side === 'BUY' ? quote.askPrice : quote.bidPrice)
  }, [side, quote])

  // ── Filtered symbol suggestions ──────────────────────────────────────────
  const suggestions = symbolInput.length >= 1
    ? instruments.filter((i) => i.symbol.startsWith(symbolInput.toUpperCase())).slice(0, 6)
    : []

  // ── Computed values ──────────────────────────────────────────────────────
  const qty        = parseInt(quantity, 10)
  const price      = orderType === 'LIMIT' ? parseFloat(limitPrice) : parseFloat(quote?.lastPrice ?? '0')
  const orderValue = !isNaN(qty) && !isNaN(price) ? qty * price : 0
  const buyingPower   = portfolio ? parseFloat(portfolio.buyingPower) : 0
  const cashBalance   = portfolio ? parseFloat(portfolio.cashBalance) : 0
  const postOrderCash = side === 'BUY' ? cashBalance - orderValue : cashBalance + orderValue
  const bpUsedPct  = buyingPower > 0 ? Math.min((orderValue / buyingPower) * 100, 100) : 0

  // Holding quantity for SELL check
  const holding = portfolio?.holdings.find((h) => h.symbol === symbol)
  const heldQty = holding ? holding.quantity : 0

  // ── Helpers ───────────────────────────────────────────────────────────────
  const isWeekendOrHoliday = (dateStr: string): boolean => {
    if (!dateStr) return false
    // Parse as noon local to avoid timezone midnight-rollback
    const d = new Date(dateStr + 'T12:00:00')
    if (d.getDay() === 0 || d.getDay() === 6) return true
    return holidays.some((h) => h.holidayDate === dateStr)
  }

  // ── Level 1 validation ───────────────────────────────────────────────────
  const checks = {
    symbol:     symbol.length > 0 && symbol.length <= 20 && /^[A-Z0-9]+$/.test(symbol),
    quantity:   !isNaN(qty) && qty > 0,
    limitPrice: orderType === 'MARKET' || (!isNaN(parseFloat(limitPrice)) && parseFloat(limitPrice) > 0),
    gtdDate:    tif === 'DAY' || (expiryDate !== '' && expiryDate >= tomorrowStr() && !isWeekendOrHoliday(expiryDate)),
    notHalted:  !quote || !quote.halted,
  }

  const allChecksPass = Object.values(checks).every(Boolean)

  // ── Submit ───────────────────────────────────────────────────────────────
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
        expiryDate:  tif === 'GTC' && expiryDate ? expiryDate : undefined,
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
        setFieldErrors((f) => ({ ...f, expiryDate: message }))
      } else {
        setSubmitError({ type: 'VALIDATION_ERROR', message })
      }
    } finally {
      setSubmitting(false)
    }
  }

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

          {/* Error banner */}
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
              <div style={{ position: 'absolute', top: '100%', left: 0, right: 0, background: '#fff', border: '1px solid #d1d5db', borderRadius: '6px', boxShadow: '0 4px 12px rgba(0,0,0,0.1)', zIndex: 100 }}>
                {suggestions.map((i) => (
                  <div
                    key={i.symbol}
                    style={{ padding: '9px 12px', cursor: 'pointer', fontSize: '13px', display: 'flex', justifyContent: 'space-between' }}
                    onMouseDown={() => { setSymbol(i.symbol); setSymbolInput(i.symbol) }}
                  >
                    <span style={{ fontWeight: 600 }}>{i.symbol}</span>
                    <span style={{ color: '#6b7280' }}>{i.halted ? '⛔ HALTED' : `$${i.lastPrice}`}</span>
                  </div>
                ))}
              </div>
            )}
            {quoteLoading && <div style={{ fontSize: '11px', color: '#9ca3af', marginTop: '4px' }}>Fetching quote…</div>}
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

          {/* Limit Price — LIMIT only */}
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
              <button style={s.toggleBtn(tif === 'DAY')} onClick={() => setTif('DAY')}>DAY</button>
              <button style={s.toggleBtn(tif === 'GTC')} onClick={() => setTif('GTC')}>GTC</button>
            </div>
          </div>

          {/* GTD Date — GTC only */}
          {tif === 'GTC' && (
            <div style={s.fieldGroup}>
              <label style={s.label}>Expiry Date</label>
              <input
                style={(fieldErrors.expiryDate || (expiryDate && isWeekendOrHoliday(expiryDate))) ? s.inputError : s.input}
                type="date"
                min={tomorrowStr()}
                value={expiryDate}
                onChange={(e) => {
                  const v = e.target.value
                  setExpiryDate(v)
                  if (v && isWeekendOrHoliday(v)) {
                    setFieldErrors((f) => ({ ...f, expiryDate: 'Market is closed on weekends and holidays' }))
                  } else {
                    setFieldErrors((f) => ({ ...f, expiryDate: undefined }))
                  }
                }}
              />
              {(fieldErrors.expiryDate || (expiryDate && isWeekendOrHoliday(expiryDate))) && (
                <div style={s.errorText}>{fieldErrors.expiryDate ?? 'Market is closed on weekends and holidays'}</div>
              )}
            </div>
          )}

          {/* Submit */}
          <button style={s.submitBtn(submitting || !allChecksPass)} onClick={handleSubmit} disabled={submitting || !allChecksPass}>
            {submitting ? 'Submitting…' : 'Submit Order'}
          </button>
        </div>

        {/* ── Right: Preview + Validation + Quote ── */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>

          {/* Order Preview */}
          <div style={s.card}>
            <h2 style={s.cardTitle}>Order Preview</h2>

            <div style={s.row}>
              <span style={s.rowLabel}>Estimated Value</span>
              <span style={s.rowValue(true)}>{orderValue > 0 ? fmt(orderValue) : '—'}</span>
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
              <span style={s.rowValue(true, '#059669')}>{portfolio ? fmt(portfolio.buyingPower) : '—'}</span>
            </div>

            {orderValue > 0 && (
              <>
                <div style={s.divider} />
                <div style={s.row}>
                  <span style={s.rowLabel}>Post-order Cash</span>
                  <span style={s.rowValue(true, postOrderCash < 0 ? '#ef4444' : '#1a2236')}>{fmt(postOrderCash)}</span>
                </div>
                {side === 'BUY' && (
                  <div style={s.row}>
                    <span style={s.rowLabel}>Buying Power Used</span>
                    <span style={s.rowValue(false, bpUsedPct > 80 ? '#d97706' : '#374151')}>{bpUsedPct.toFixed(1)}%</span>
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

          {/* Level 1 Validation */}
          <div style={s.card}>
            <h2 style={s.cardTitle}>Validation</h2>
            {[
              { label: 'Symbol entered',                pass: checks.symbol },
              { label: 'Quantity > 0',                  pass: checks.quantity },
              { label: orderType === 'LIMIT' ? 'Limit price > 0' : 'Market order — no price needed', pass: checks.limitPrice },
              { label: tif === 'GTC' ? 'Expiry date is a future trading day' : 'DAY order — no expiry needed', pass: checks.gtdDate },
              { label: 'Symbol not halted',             pass: checks.notHalted },
            ].map(({ label, pass }) => (
              <div key={label} style={s.checkRow}>
                <span style={s.checkDot(pass)}>{pass ? '✓' : '✗'}</span>
                <span style={s.checkText(pass)}>{label}</span>
              </div>
            ))}
          </div>

          {/* Market Quote */}
          <div style={s.card}>
            <h2 style={s.cardTitle}>Market Quote</h2>
            {!quote && !quoteLoading && (
              <div style={{ color: '#9ca3af', fontSize: '13px' }}>Enter a symbol to see the current quote.</div>
            )}
            {quoteLoading && <div style={{ color: '#9ca3af', fontSize: '13px' }}>Loading quote…</div>}
            {quote && !quoteLoading && (
              <>
                <div style={s.quoteSymbol}>
                  {quote.symbol}
                  {quote.halted && <span style={s.haltedBadge}>HALTED</span>}
                </div>
                <div style={s.quotePrice}>${quote.lastPrice}</div>
                <div style={s.quoteMeta}>
                  {[
                    { label: 'Bid',  value: `$${quote.bidPrice}` },
                    { label: 'Ask',  value: `$${quote.askPrice}` },
                    { label: 'Min Qty', value: quote.minQuantity.toLocaleString() },
                    { label: 'Max Qty', value: quote.maxQuantity.toLocaleString() },
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

        </div>{/* end right col */}
      </div>
    </div>
  )
}

export default OrderEntryPage
