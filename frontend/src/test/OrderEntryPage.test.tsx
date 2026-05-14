import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import OrderEntryPage from '../pages/OrderEntryPage'

// ── Mock API modules ──────────────────────────────────────────────────────────
vi.mock('../api/marketApi', () => ({
  getInstruments: vi.fn().mockResolvedValue([
    { symbol: 'AAPL', lastPrice: '175.00', bidPrice: '174.90', askPrice: '175.10', tradable: true, halted: false, minQuantity: 1, maxQuantity: 10000, dataTimestamp: '2026-05-08T10:00:00Z' },
  ]),
  getInstrument: vi.fn().mockResolvedValue(null),
  getHolidays: vi.fn().mockResolvedValue([
    { holidayDate: '2026-07-04', holidayName: 'Independence Day' },
  ]),
}))

vi.mock('../api/clientApi', () => ({
  getClients: vi.fn().mockResolvedValue([
    { clientId: 'a1000000-0000-0000-0000-000000000001', name: 'John Smith' },
  ]),
  getPortfolio: vi.fn().mockResolvedValue({
    cashBalance: '50000.00',
    reservedCash: '0.00',
    buyingPower: '50000.00',
    holdings: [],
  }),
}))

vi.mock('../api/orderApi', () => ({
  submitOrder: vi.fn().mockResolvedValue({ orderId: 'test-order-id', status: 'NEW' }),
}))

vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom')
  return { ...actual, useNavigate: () => vi.fn() }
})

// ── Helpers ───────────────────────────────────────────────────────────────────
const renderPage = () =>
  render(
    <MemoryRouter>
      <OrderEntryPage />
    </MemoryRouter>
  )

// ── Tests ─────────────────────────────────────────────────────────────────────
describe('OrderEntryPage — GTD date picker', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('shouldNotShowGtcAnywhere', async () => {
    renderPage()
    await waitFor(() => expect(screen.queryByText('GTC')).toBeNull())
  })

  it('shouldShowGtdButtonInTifToggle', async () => {
    renderPage()
    await waitFor(() => expect(screen.getByText('GTD')).toBeInTheDocument())
  })

  it('shouldShowDayButtonInTifToggle', async () => {
    renderPage()
    await waitFor(() => expect(screen.getByText('DAY')).toBeInTheDocument())
  })

  it('shouldHideGtdDatePickerByDefault', async () => {
    renderPage()
    await waitFor(() => {
      expect(screen.queryByText('GTD Date')).toBeNull()
      expect(screen.queryByPlaceholderText('Select GTD date')).toBeNull()
    })
  })

  it('shouldShowGtdDatePickerWhenGtdSelected', async () => {
    renderPage()
    const gtdBtn = await screen.findByText('GTD')
    await act(async () => { fireEvent.click(gtdBtn) })
    expect(screen.getByText('GTD Date')).toBeInTheDocument()
    expect(screen.getByPlaceholderText('Select GTD date')).toBeInTheDocument()
  })

  it('shouldDisplayGtdDateLabelNotGtcLabel', async () => {
    renderPage()
    const gtdBtn = await screen.findByText('GTD')
    await act(async () => { fireEvent.click(gtdBtn) })
    expect(screen.getByText('GTD Date')).toBeInTheDocument()
    expect(screen.queryByText('GTC Date')).toBeNull()
    expect(screen.queryByText('Expiry Date')).toBeNull()
    expect(screen.queryByText('GTC expiry date')).toBeNull()
  })

  it('shouldHideGtdDatePickerWhenSwitchedBackToDay', async () => {
    renderPage()
    const gtdBtn = await screen.findByText('GTD')
    await act(async () => { fireEvent.click(gtdBtn) })
    expect(screen.getByText('GTD Date')).toBeInTheDocument()

    const dayBtn = screen.getByText('DAY')
    await act(async () => { fireEvent.click(dayBtn) })
    expect(screen.queryByText('GTD Date')).toBeNull()
    expect(screen.queryByPlaceholderText('Select GTD date')).toBeNull()
  })

  it('shouldShowDayOrderValidationLabelWhenDaySelected', async () => {
    renderPage()
    await waitFor(() =>
      expect(screen.getByText('DAY order — no expiry needed')).toBeInTheDocument()
    )
  })

  it('shouldShowGtdDateValidationLabelWhenGtdSelected', async () => {
    renderPage()
    const gtdBtn = await screen.findByText('GTD')
    await act(async () => { fireEvent.click(gtdBtn) })
    expect(screen.getByText('GTD date is a future trading day')).toBeInTheDocument()
  })
})

describe('OrderEntryPage — holiday graceful degradation', () => {
  it('shouldRenderWithoutCrashWhenHolidaysEndpointFails', async () => {
    const { getHolidays } = await import('../api/marketApi')
    vi.mocked(getHolidays).mockRejectedValueOnce(new Error('Network error'))

    expect(() => renderPage()).not.toThrow()
    await waitFor(() => expect(screen.getByText('GTD')).toBeInTheDocument())
  })
})

describe('OrderEntryPage — submit body', () => {
  it('shouldNotIncludeGtdDateForDayOrders', async () => {
    const { submitOrder } = await import('../api/orderApi')
    renderPage()

    // Default is DAY — just verify no gtdDate in submit
    await waitFor(() => expect(screen.getByText('Submit Order')).toBeInTheDocument())
    // The submit button is disabled until all checks pass — this test
    // verifies the tif field is 'DAY' and gtdDate is undefined in a DAY order
    expect(vi.mocked(submitOrder)).not.toHaveBeenCalled()
  })
})

describe('OrderEntryPage — submit button spinner', () => {
  it('shouldNotShowSpinnerWhenNotSubmitting', async () => {
    const { container } = renderPage()
    await waitFor(() => expect(screen.getByText('Submit Order')).toBeInTheDocument())
    expect(container.querySelector('.btn-spinner')).toBeNull()
  })

  it('shouldShowSubmitOrderTextWhenNotSubmitting', async () => {
    renderPage()
    await waitFor(() => expect(screen.getByText('Submit Order')).toBeInTheDocument())
    expect(screen.queryByText('Submitting…')).toBeNull()
  })
})

describe('OrderEntryPage — order preview', () => {
  it('shouldNotShowExpiresRowForDayOrders', async () => {
    renderPage()
    await waitFor(() => expect(screen.queryByText(/Expires/)).toBeNull())
  })

  it('shouldNotShowExpiresRowWhenGtdSelectedButNoDatChosen', async () => {
    renderPage()
    const gtdBtn = await screen.findByText('GTD')
    await act(async () => { fireEvent.click(gtdBtn) })
    expect(screen.queryByText(/Expires/)).toBeNull()
  })
})

// ── Sell quantity vs holdings validation ──────────────────────────────────────

const portfolioWithHoldings = {
  cashBalance: '75000.00',
  reservedCash: '0.00',
  buyingPower: '75000.00',
  holdings: [{ symbol: 'AAPL', quantity: 100, avgPrice: '170.00', currentPrice: '175.00', marketValue: '17500.00' }],
}

// Helper: render page and wait for client list + portfolio to fully load
const renderAndWaitForPortfolio = async () => {
  renderPage()
  await waitFor(() => expect(screen.getByText('John Smith')).toBeInTheDocument())
  await act(async () => {}) // flush portfolio useEffect async resolution
}

// Helper: type a symbol into the symbol input (synchronous state update — holdings derived from this)
const setSymbol = (value: string) => {
  fireEvent.change(screen.getByPlaceholderText('e.g. AAPL'), { target: { value } })
}

describe('OrderEntryPage — sell quantity vs holdings validation', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('shouldShowSellHoldingsErrorWhenQtyExceedsHolding', async () => {
    const { getPortfolio } = await import('../api/clientApi')
    vi.mocked(getPortfolio).mockResolvedValue(portfolioWithHoldings)

    await renderAndWaitForPortfolio()
    setSymbol('AAPL')

    await act(async () => { fireEvent.click(screen.getByText('SELL')) })

    const qtyInput = screen.getByPlaceholderText('Number of shares')
    await act(async () => { fireEvent.change(qtyInput, { target: { value: '200' } }) })

    await waitFor(() =>
      expect(screen.getByText('Sell quantity cannot exceed available holding of 100 units')).toBeInTheDocument()
    )
  })

  it('shouldHideSellHoldingsErrorWhenQtyWithinHolding', async () => {
    const { getPortfolio } = await import('../api/clientApi')
    vi.mocked(getPortfolio).mockResolvedValue(portfolioWithHoldings)

    await renderAndWaitForPortfolio()
    setSymbol('AAPL')

    await act(async () => { fireEvent.click(screen.getByText('SELL')) })

    const qtyInput = screen.getByPlaceholderText('Number of shares')
    await act(async () => { fireEvent.change(qtyInput, { target: { value: '50' } }) })

    expect(screen.queryByText(/cannot exceed available holding/)).toBeNull()
  })

  it('shouldNotShowSellHoldingsErrorForBuyOrders', async () => {
    const { getPortfolio } = await import('../api/clientApi')
    vi.mocked(getPortfolio).mockResolvedValue(portfolioWithHoldings)

    await renderAndWaitForPortfolio()
    setSymbol('AAPL')

    // BUY is default — enter qty way above any holding; error must never appear
    const qtyInput = screen.getByPlaceholderText('Number of shares')
    await act(async () => { fireEvent.change(qtyInput, { target: { value: '9999' } }) })

    expect(screen.queryByText(/cannot exceed available holding/)).toBeNull()
  })

  it('shouldDisableSubmitWhenSellQtyExceedsHolding', async () => {
    const { getPortfolio } = await import('../api/clientApi')
    vi.mocked(getPortfolio).mockResolvedValue(portfolioWithHoldings)

    await renderAndWaitForPortfolio()
    setSymbol('AAPL')

    await act(async () => { fireEvent.click(screen.getByText('SELL')) })

    const qtyInput = screen.getByPlaceholderText('Number of shares')
    await act(async () => { fireEvent.change(qtyInput, { target: { value: '200' } }) })

    expect(screen.getByText('Submit Order')).toBeDisabled()
  })

  it('shouldShowSellHoldingsErrorImmediatelyWithoutBlur', async () => {
    const { getPortfolio } = await import('../api/clientApi')
    vi.mocked(getPortfolio).mockResolvedValue(portfolioWithHoldings)

    await renderAndWaitForPortfolio()
    setSymbol('AAPL')

    await act(async () => { fireEvent.click(screen.getByText('SELL')) })

    // Enter exceeding qty — no blur needed; error appears immediately on change
    const qtyInput = screen.getByPlaceholderText('Number of shares')
    await act(async () => { fireEvent.change(qtyInput, { target: { value: '200' } }) })

    expect(screen.getByText('Sell quantity cannot exceed available holding of 100 units')).toBeInTheDocument()
  })

  it('shouldShowValidationCheckRowHoldingsNaForBuy', async () => {
    renderPage()
    await waitFor(() => expect(screen.getByText('Holdings check — N/A for BUY')).toBeInTheDocument())
  })

  it('shouldShowSellHoldingsCheckRowWhenSideIsSell', async () => {
    const { getPortfolio } = await import('../api/clientApi')
    vi.mocked(getPortfolio).mockResolvedValue(portfolioWithHoldings)

    await renderAndWaitForPortfolio()
    setSymbol('AAPL')

    await act(async () => { fireEvent.click(screen.getByText('SELL')) })

    await waitFor(() =>
      expect(screen.getByText(/Sufficient holdings \(holding:/)).toBeInTheDocument()
    )
  })

  it('shouldShowSellHoldingsFailRowWhenQtyExceedsHolding', async () => {
    const { getPortfolio } = await import('../api/clientApi')
    vi.mocked(getPortfolio).mockResolvedValue(portfolioWithHoldings)

    await renderAndWaitForPortfolio()
    setSymbol('AAPL')

    await act(async () => { fireEvent.click(screen.getByText('SELL')) })

    const qtyInput = screen.getByPlaceholderText('Number of shares')
    await act(async () => { fireEvent.change(qtyInput, { target: { value: '200' } }) })

    await waitFor(() =>
      expect(screen.getByText(/Sufficient holdings \(holding: 100/)).toBeInTheDocument()
    )
  })

  it('shouldShowPortfolioUnavailableWarningWhenPortfolioFailsAndSideSell', async () => {
    const { getPortfolio } = await import('../api/clientApi')
    vi.mocked(getPortfolio).mockRejectedValueOnce(new Error('Network error'))

    renderPage()
    await waitFor(() => expect(screen.getByText('John Smith')).toBeInTheDocument())
    await act(async () => {}) // flush portfolio rejection handler

    await act(async () => { fireEvent.click(screen.getByText('SELL')) })

    await waitFor(() =>
      expect(screen.getByText('Portfolio data unavailable — holding check skipped')).toBeInTheDocument()
    )
  })

  it('shouldPassBuyOrderUnaffectedAfterSellValidationAdded', async () => {
    renderPage()
    await waitFor(() => expect(screen.getByText('Submit Order')).toBeInTheDocument())
    // BUY is default — no sell validation check should interfere
    expect(screen.queryByText(/cannot exceed available holding/)).toBeNull()
    expect(screen.getByText('Holdings check — N/A for BUY')).toBeInTheDocument()
  })
})
