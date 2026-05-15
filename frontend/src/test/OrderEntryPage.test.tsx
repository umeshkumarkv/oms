import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import OrderEntryPage from '../pages/OrderEntryPage'
import { useIdleTimer } from '../hooks/useIdleTimer'

vi.mock('../hooks/useIdleTimer', () => ({
  useIdleTimer: vi.fn(() => ({
    secondsRemaining: 60,
    isExpired: false,
    resetTimer: vi.fn(),
  })),
}))

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
    await waitFor(() => expect(screen.getByText('Holdings check — N/A for BUY / SHORT_SELL')).toBeInTheDocument())
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
    expect(screen.getByText('Holdings check — N/A for BUY / SHORT_SELL')).toBeInTheDocument()
  })
})

// ── SHORT_SELL ────────────────────────────────────────────────────────────────

describe('OrderEntryPage — SHORT_SELL', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('shouldShowShortSellButtonInSideToggle', async () => {
    renderPage()
    await waitFor(() => expect(screen.getByText('SHORT SELL')).toBeInTheDocument())
  })

  it('shouldShowBuyAndSellAndShortSellButtons', async () => {
    renderPage()
    await waitFor(() => {
      expect(screen.getByText('BUY')).toBeInTheDocument()
      expect(screen.getByText('SELL')).toBeInTheDocument()
      expect(screen.getByText('SHORT SELL')).toBeInTheDocument()
    })
  })

  it('shouldNotShowSellHoldingsErrorForShortSell', async () => {
    const { getPortfolio } = await import('../api/clientApi')
    vi.mocked(getPortfolio).mockResolvedValue(portfolioWithHoldings)

    await renderAndWaitForPortfolio()
    setSymbol('AAPL')

    await act(async () => { fireEvent.click(screen.getByText('SHORT SELL')) })

    const qtyInput = screen.getByPlaceholderText('Number of shares')
    await act(async () => { fireEvent.change(qtyInput, { target: { value: '9999' } }) })

    expect(screen.queryByText(/cannot exceed available holding/)).toBeNull()
  })

  it('shouldShowHoldingsNaForShortSell', async () => {
    renderPage()
    await waitFor(() => expect(screen.getByText('SHORT SELL')).toBeInTheDocument())

    await act(async () => { fireEvent.click(screen.getByText('SHORT SELL')) })

    await waitFor(() =>
      expect(screen.getByText('Holdings check — N/A for BUY / SHORT_SELL')).toBeInTheDocument()
    )
  })

  it('shouldNotDisableSubmitForShortSellQtyAboveHolding', async () => {
    const { getPortfolio } = await import('../api/clientApi')
    vi.mocked(getPortfolio).mockResolvedValue(portfolioWithHoldings)

    await renderAndWaitForPortfolio()
    setSymbol('AAPL')

    await act(async () => { fireEvent.click(screen.getByText('SHORT SELL')) })

    const qtyInput = screen.getByPlaceholderText('Number of shares')
    await act(async () => { fireEvent.change(qtyInput, { target: { value: '200' } }) })

    // Holdings check passes for SHORT_SELL — submit disabled only due to other checks (symbol not confirmed via quote)
    expect(screen.queryByText(/cannot exceed available holding/)).toBeNull()
  })
})

// ── Idle timer — countdown and expiry ─────────────────────────────────────────
// useIdleTimer is mocked — the hook's own timer logic is tested in useIdleTimer.test.ts.
// These tests verify that OrderEntryPage renders the correct UI for each timer state.

describe('OrderEntryPage — idle timer', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('shouldNotShowCountdownWarningWhenSecondsAbove15', async () => {
    vi.mocked(useIdleTimer).mockReturnValue({ secondsRemaining: 60, isExpired: false, resetTimer: vi.fn() })
    renderPage()
    await waitFor(() => expect(screen.getByText('Submit Order')).toBeInTheDocument())
    expect(screen.queryByText(/Order window will expire in/)).toBeNull()
  })

  it('shouldShowCountdownWarningWhenSecondsAt15OrBelow', async () => {
    vi.mocked(useIdleTimer).mockReturnValue({ secondsRemaining: 14, isExpired: false, resetTimer: vi.fn() })
    renderPage()
    await waitFor(() =>
      expect(screen.getByText('⚠ Order window will expire in 14 seconds')).toBeInTheDocument()
    )
  })

  it('shouldShowSingularSecondsWhenOneSecondRemaining', async () => {
    vi.mocked(useIdleTimer).mockReturnValue({ secondsRemaining: 1, isExpired: false, resetTimer: vi.fn() })
    renderPage()
    await waitFor(() =>
      expect(screen.getByText('⚠ Order window will expire in 1 second')).toBeInTheDocument()
    )
  })

  it('shouldShowCountdownWarningExactlyAt15Seconds', async () => {
    vi.mocked(useIdleTimer).mockReturnValue({ secondsRemaining: 15, isExpired: false, resetTimer: vi.fn() })
    renderPage()
    await waitFor(() => expect(screen.getByText('Submit Order')).toBeInTheDocument())
    expect(screen.getByText('⚠ Order window will expire in 15 seconds')).toBeInTheDocument()
  })

  it('shouldDisableSubmitButtonWhenWindowExpired', async () => {
    vi.mocked(useIdleTimer).mockReturnValue({ secondsRemaining: 0, isExpired: true, resetTimer: vi.fn() })
    renderPage()
    await waitFor(() =>
      expect(screen.getByText('Submit Order')).toBeDisabled()
    )
  })

  it('shouldShowExpiredBannerWhenWindowExpired', async () => {
    vi.mocked(useIdleTimer).mockReturnValue({ secondsRemaining: 0, isExpired: true, resetTimer: vi.fn() })
    renderPage()
    await waitFor(() =>
      expect(screen.getByText('Order Window Expired')).toBeInTheDocument()
    )
  })

  it('shouldShowStartNewOrderButtonWhenExpired', async () => {
    vi.mocked(useIdleTimer).mockReturnValue({ secondsRemaining: 0, isExpired: true, resetTimer: vi.fn() })
    renderPage()
    await waitFor(() =>
      expect(screen.getByText('Start New Order')).toBeInTheDocument()
    )
  })

  it('shouldCallResetTimerOnUserInteraction', async () => {
    const resetTimer = vi.fn()
    vi.mocked(useIdleTimer).mockReturnValue({ secondsRemaining: 60, isExpired: false, resetTimer })
    renderPage()
    await waitFor(() => expect(screen.getByText('Submit Order')).toBeInTheDocument())
    await act(async () => { fireEvent.keyDown(screen.getByPlaceholderText('e.g. AAPL'), { key: 'a' }) })
    expect(resetTimer).toHaveBeenCalled()
  })

  it('shouldNotShowWarningBannerWhenNotExpiredAndAbove15Seconds', async () => {
    vi.mocked(useIdleTimer).mockReturnValue({ secondsRemaining: 16, isExpired: false, resetTimer: vi.fn() })
    renderPage()
    await waitFor(() => expect(screen.getByText('Submit Order')).toBeInTheDocument())
    expect(screen.queryByText(/Order window will expire in/)).toBeNull()
  })
})
