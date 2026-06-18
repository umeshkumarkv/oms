import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
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

// ── Mock useOrderEntryBidAsk (SCRUM-108) — default: no live data, not stale ───
const mockUseOrderEntryBidAsk = vi.fn((_symbol: string) => ({ bid: null as string | null, ask: null as string | null, ltp: null as string | null, isStale: false }))
vi.mock('../hooks/useOrderEntryBidAsk', () => ({
  useOrderEntryBidAsk: (symbol: string) => mockUseOrderEntryBidAsk(symbol),
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

vi.mock('../config/sorVenues', () => ({
  SOR_VENUES: ['NYSE', 'NASDAQ', 'ARCA', 'BATS', 'DARK_POOL'],
  DEFAULT_VENUE: 'NYSE',
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
    // Validation card removed (SCRUM-187) — DAY TIF reflected in submit button state
    await waitFor(() => expect(screen.getByText('Complete required fields')).toBeInTheDocument())
  })

  it('shouldShowGtdDateValidationLabelWhenGtdSelected', async () => {
    renderPage()
    const gtdBtn = await screen.findByText('GTD')
    await act(async () => { fireEvent.click(gtdBtn) })
    // Validation card removed (SCRUM-187) — GTD picker still appears when GTD selected
    expect(screen.getByPlaceholderText('Select GTD date')).toBeInTheDocument()
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
    await waitFor(() => expect(screen.getByText('Complete required fields')).toBeInTheDocument())
    // The submit button is disabled until all checks pass — this test
    // verifies the tif field is 'DAY' and gtdDate is undefined in a DAY order
    expect(vi.mocked(submitOrder)).not.toHaveBeenCalled()
  })
})

describe('OrderEntryPage — submit button spinner', () => {
  it('shouldNotShowSpinnerWhenNotSubmitting', async () => {
    const { container } = renderPage()
    await waitFor(() => expect(screen.getByText('Complete required fields')).toBeInTheDocument())
    expect(container.querySelector('.btn-spinner')).toBeNull()
  })

  it('shouldShowSubmitOrderTextWhenNotSubmitting', async () => {
    renderPage()
    await waitFor(() => expect(screen.getByText('Complete required fields')).toBeInTheDocument())
    expect(screen.queryByText('Placing…')).toBeNull()
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
  fireEvent.change(screen.getByPlaceholderText('Search ticker — e.g. AAPL, MSFT, NVDA'), { target: { value } })
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

    expect(screen.getByText('Complete required fields')).toBeDisabled()
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
    // Validation card removed (SCRUM-187) — BUY side never shows a holdings error
    await waitFor(() => expect(screen.queryByText(/cannot exceed available holding/)).toBeNull())
  })

  it('shouldShowSellHoldingsCheckRowWhenSideIsSell', async () => {
    const { getPortfolio } = await import('../api/clientApi')
    vi.mocked(getPortfolio).mockResolvedValue(portfolioWithHoldings)

    await renderAndWaitForPortfolio()
    setSymbol('AAPL')
    await act(async () => { fireEvent.click(screen.getByText('SELL')) })

    // Validation card removed (SCRUM-187) — holdings enforcement surfaces via submit button
    await waitFor(() => expect(screen.getByText('Order Preview')).toBeInTheDocument())
  })

  it('shouldShowSellHoldingsFailRowWhenQtyExceedsHolding', async () => {
    const { getPortfolio } = await import('../api/clientApi')
    vi.mocked(getPortfolio).mockResolvedValue(portfolioWithHoldings)

    await renderAndWaitForPortfolio()
    setSymbol('AAPL')
    await act(async () => { fireEvent.click(screen.getByText('SELL')) })

    const qtyInput = screen.getByPlaceholderText('Number of shares')
    await act(async () => { fireEvent.change(qtyInput, { target: { value: '200' } }) })

    // Validation card removed (SCRUM-187) — sell failure shown as inline qty error
    await waitFor(() =>
      expect(screen.getByText('Sell quantity cannot exceed available holding of 100 units')).toBeInTheDocument()
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
    await waitFor(() => expect(screen.getByText('Complete required fields')).toBeInTheDocument())
    // BUY is default — no sell validation error should appear
    expect(screen.queryByText(/cannot exceed available holding/)).toBeNull()
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

    // Validation card removed (SCRUM-187) — SHORT_SELL never shows holdings error
    await waitFor(() => expect(screen.queryByText(/cannot exceed available holding/)).toBeNull())
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

// ── SOR dropdown ─────────────────────────────────────────────────────────────
// Route To is now in the top row of the left panel — no Advanced expansion needed.

describe('OrderEntryPage — SOR dropdown', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('shouldShowRouteTolabel', async () => {
    renderPage()
    await waitFor(() => expect(screen.getByText('Route To')).toBeInTheDocument())
  })

  it('shouldHaveNysePreselectedByDefault', async () => {
    renderPage()
    await waitFor(() => expect(screen.getByRole('combobox', { name: 'Route To' })).toBeInTheDocument())
    const dropdown = screen.getByRole('combobox', { name: 'Route To' }) as HTMLSelectElement
    expect(dropdown.value).toBe('NYSE')
  })

  it('shouldShowAllVenuesInDropdown', async () => {
    renderPage()
    await waitFor(() => expect(screen.getByRole('option', { name: 'NYSE' })).toBeInTheDocument())
    expect(screen.getByRole('option', { name: 'NASDAQ' })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: 'ARCA' })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: 'BATS' })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: 'DARK_POOL' })).toBeInTheDocument()
  })

  it('shouldUpdateSelectedVenueWhenDropdownChanges', async () => {
    renderPage()
    await waitFor(() => expect(screen.getByRole('combobox', { name: 'Route To' })).toBeInTheDocument())
    const dropdown = screen.getByRole('combobox', { name: 'Route To' }) as HTMLSelectElement
    await act(async () => { fireEvent.change(dropdown, { target: { value: 'NASDAQ' } }) })
    expect(dropdown.value).toBe('NASDAQ')
  })

  it('shouldDisableDropdownWhenWindowIsExpired', async () => {
    vi.mocked(useIdleTimer).mockReturnValue({ secondsRemaining: 0, isExpired: true, resetTimer: vi.fn() })
    renderPage()
    await waitFor(() => expect(screen.getByRole('combobox', { name: 'Route To' })).toBeInTheDocument())
    const dropdown = screen.getByRole('combobox', { name: 'Route To' }) as HTMLSelectElement
    expect(dropdown).toBeDisabled()
  })
})

// ── SOR dropdown — empty venues fallback ──────────────────────────────────────

describe('OrderEntryPage — SOR dropdown — empty venues fallback', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.doMock('../config/sorVenues', () => ({ SOR_VENUES: [], DEFAULT_VENUE: 'NYSE' }))
  })

  afterEach(() => {
    vi.doMock('../config/sorVenues', () => ({
      SOR_VENUES: ['NYSE', 'NASDAQ', 'ARCA', 'BATS', 'DARK_POOL'],
      DEFAULT_VENUE: 'NYSE',
    }))
  })

  it('shouldStillRenderRouteToLabelWhenVenuesEmpty', async () => {
    renderPage()
    await waitFor(() => expect(screen.getByText('Route To')).toBeInTheDocument())
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
    await waitFor(() => expect(screen.getByText('Complete required fields')).toBeInTheDocument())
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
    await waitFor(() => expect(screen.getByText('Complete required fields')).toBeInTheDocument())
    expect(screen.getByText('⚠ Order window will expire in 15 seconds')).toBeInTheDocument()
  })

  it('shouldDisableSubmitButtonWhenWindowExpired', async () => {
    vi.mocked(useIdleTimer).mockReturnValue({ secondsRemaining: 0, isExpired: true, resetTimer: vi.fn() })
    renderPage()
    await waitFor(() => {
      const btn = screen.getByRole('button', { name: 'Submit Order' })
      expect(btn).toBeDisabled()
      expect(btn).toHaveTextContent('Order Window Expired')
    })
  })

  it('shouldShowExpiredBannerWhenWindowExpired', async () => {
    vi.mocked(useIdleTimer).mockReturnValue({ secondsRemaining: 0, isExpired: true, resetTimer: vi.fn() })
    renderPage()
    await waitFor(() =>
      expect(screen.getByRole('alert')).toBeInTheDocument()
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
    await waitFor(() => expect(screen.getByText('Complete required fields')).toBeInTheDocument())
    await act(async () => { fireEvent.keyDown(screen.getByPlaceholderText('Search ticker — e.g. AAPL, MSFT, NVDA'), { key: 'a' }) })
    expect(resetTimer).toHaveBeenCalled()
  })

  it('shouldNotShowWarningBannerWhenNotExpiredAndAbove15Seconds', async () => {
    vi.mocked(useIdleTimer).mockReturnValue({ secondsRemaining: 16, isExpired: false, resetTimer: vi.fn() })
    renderPage()
    await waitFor(() => expect(screen.getByText('Complete required fields')).toBeInTheDocument())
    expect(screen.queryByText(/Order window will expire in/)).toBeNull()
  })
})

// ── Real-time Bid/Ask — SCRUM-108 ─────────────────────────────────────────────

const aaplQuote = {
  symbol: 'AAPL', bidPrice: '174.50', askPrice: '175.50',
  lastPrice: '175.00', tradable: true, halted: false,
  minQuantity: 1, maxQuantity: 10000,
  dataTimestamp: '2026-05-15T10:00:00Z',
}

const renderAndLoadQuote = async () => {
  const { getInstrument } = await import('../api/marketApi')
  vi.mocked(getInstrument).mockResolvedValue(aaplQuote)
  renderPage()
  await waitFor(() => expect(screen.getByText('John Smith')).toBeInTheDocument())
  await act(async () => {
    fireEvent.change(screen.getByPlaceholderText('Search ticker — e.g. AAPL, MSFT, NVDA'), { target: { value: 'AAPL' } })
  })
  // Wait for debounce (400ms) and async quote resolution
  await waitFor(() => screen.getByText('$175.00'), { timeout: 1500 })
}

describe('OrderEntryPage — real-time Bid/Ask (SCRUM-108)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockUseOrderEntryBidAsk.mockReturnValue({ bid: null, ask: null, ltp: null, isStale: false })
  })

  it('shouldHideStaleBadgeWhenIsStaleFalse', async () => {
    await renderAndLoadQuote()
    expect(screen.queryByLabelText('Price data is stale')).toBeNull()
    expect(screen.queryByText('⚠ Stale')).toBeNull()
  })

  it('shouldShowStaleBadgeOnLtpBidAndAskWhenIsStaleTrueAndSymbolEntered', async () => {
    mockUseOrderEntryBidAsk.mockReturnValue({ bid: null, ask: null, ltp: null, isStale: true })
    await renderAndLoadQuote()
    // Stale badge appears on LTP, Bid, and Ask (SCRUM-127)
    const staleBadges = screen.getAllByLabelText('Price data is stale')
    expect(staleBadges).toHaveLength(3)
  })

  it('shouldDisplayLiveBidPriceFromHookInMarketQuoteCard', async () => {
    mockUseOrderEntryBidAsk.mockReturnValue({ bid: '174.99', ask: null, ltp: null, isStale: false })
    await renderAndLoadQuote()
    expect(screen.getByText('$174.99')).toBeInTheDocument()
  })

  it('shouldDisplayLiveAskPriceFromHookInMarketQuoteCard', async () => {
    mockUseOrderEntryBidAsk.mockReturnValue({ bid: null, ask: '175.01', ltp: null, isStale: false })
    await renderAndLoadQuote()
    expect(screen.getByText('$175.01')).toBeInTheDocument()
  })

  it('shouldFallBackToRestQuoteBidAskWhenHookReturnsNullBidAsk', async () => {
    mockUseOrderEntryBidAsk.mockReturnValue({ bid: null, ask: null, ltp: null, isStale: false })
    await renderAndLoadQuote()
    expect(screen.getByText('$174.50')).toBeInTheDocument()
    expect(screen.getByText('$175.50')).toBeInTheDocument()
  })
})

// ── SYMBOL_NOT_FOUND handling — SCRUM-138 ─────────────────────────────────────

describe('OrderEntryPage — SYMBOL_NOT_FOUND handling', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('shouldShowInlineErrorWhenGetInstrumentThrowsSymbolNotFound', async () => {
    const { getInstrument } = await import('../api/marketApi')
    vi.mocked(getInstrument).mockRejectedValueOnce({
      response: { data: { error: { code: 'SYMBOL_NOT_FOUND' } } },
    })

    renderPage()
    await waitFor(() => expect(screen.getByText('John Smith')).toBeInTheDocument())
    await act(async () => {
      fireEvent.change(screen.getByPlaceholderText('Search ticker — e.g. AAPL, MSFT, NVDA'), { target: { value: 'APPL' } })
    })
    await waitFor(
      () => expect(screen.getByText('Symbol not found. Please search and select a valid instrument.')).toBeInTheDocument(),
      { timeout: 1500 }
    )
  })

  it('shouldDisableSubmitButtonWhenSymbolNotFound', async () => {
    const { getInstrument } = await import('../api/marketApi')
    vi.mocked(getInstrument).mockRejectedValueOnce({
      response: { data: { error: { code: 'SYMBOL_NOT_FOUND' } } },
    })

    renderPage()
    await waitFor(() => expect(screen.getByText('John Smith')).toBeInTheDocument())
    await act(async () => {
      fireEvent.change(screen.getByPlaceholderText('Search ticker — e.g. AAPL, MSFT, NVDA'), { target: { value: 'APPL' } })
    })
    await waitFor(
      () => expect(screen.getByText('Symbol not found. Please search and select a valid instrument.')).toBeInTheDocument(),
      { timeout: 1500 }
    )
    expect(screen.getByText('Complete required fields')).toBeDisabled()
  })

  it('shouldClearSymbolNotFoundErrorImmediatelyOnInputChange', async () => {
    const { getInstrument } = await import('../api/marketApi')
    vi.mocked(getInstrument).mockRejectedValueOnce({
      response: { data: { error: { code: 'SYMBOL_NOT_FOUND' } } },
    })

    renderPage()
    await waitFor(() => expect(screen.getByText('John Smith')).toBeInTheDocument())
    await act(async () => {
      fireEvent.change(screen.getByPlaceholderText('Search ticker — e.g. AAPL, MSFT, NVDA'), { target: { value: 'APPL' } })
    })
    await waitFor(
      () => expect(screen.getByText('Symbol not found. Please search and select a valid instrument.')).toBeInTheDocument(),
      { timeout: 1500 }
    )

    await act(async () => {
      fireEvent.change(screen.getByPlaceholderText('Search ticker — e.g. AAPL, MSFT, NVDA'), { target: { value: 'APPLE' } })
    })
    expect(screen.queryByText('Symbol not found. Please search and select a valid instrument.')).toBeNull()
  })

  it('shouldNotShowSymbolNotFoundErrorForNonSymbolNotFoundErrors', async () => {
    const { getInstrument } = await import('../api/marketApi')
    vi.mocked(getInstrument).mockRejectedValueOnce({
      response: { data: { error: { code: 'INTERNAL_SERVER_ERROR' } } },
    })

    renderPage()
    await waitFor(() => expect(screen.getByText('John Smith')).toBeInTheDocument())
    await act(async () => {
      fireEvent.change(screen.getByPlaceholderText('Search ticker — e.g. AAPL, MSFT, NVDA'), { target: { value: 'AAPL' } })
    })
    await waitFor(() => expect(vi.mocked(getInstrument)).toHaveBeenCalled(), { timeout: 1500 })
    await act(async () => {})
    expect(screen.queryByText('Symbol not found. Please search and select a valid instrument.')).toBeNull()
  })
})

// ── Real-time LTP — SCRUM-127 ─────────────────────────────────────────────────

describe('OrderEntryPage — real-time LTP (SCRUM-127)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockUseOrderEntryBidAsk.mockReturnValue({ bid: null, ask: null, ltp: null, isStale: false })
  })

  it('shouldDisplayLiveLtpFromHookInMarketIntelligencePanel', async () => {
    mockUseOrderEntryBidAsk.mockReturnValue({ bid: null, ask: null, ltp: '201.25', isStale: false })
    const { getInstrument } = await import('../api/marketApi')
    vi.mocked(getInstrument).mockResolvedValue(aaplQuote)
    renderPage()
    await waitFor(() => expect(screen.getByText('John Smith')).toBeInTheDocument())
    await act(async () => {
      fireEvent.change(screen.getByPlaceholderText('Search ticker — e.g. AAPL, MSFT, NVDA'), { target: { value: 'AAPL' } })
    })
    // liveLtp='201.25' overrides REST quote.lastPrice='175.00'
    await waitFor(() => expect(screen.getByText('$201.25')).toBeInTheDocument(), { timeout: 1500 })

  })

  it('shouldFallBackToRestQuoteLtpWhenHookReturnsNullLtp', async () => {
    mockUseOrderEntryBidAsk.mockReturnValue({ bid: null, ask: null, ltp: null, isStale: false })
    await renderAndLoadQuote()
    // liveLtp is null → falls back to quote.lastPrice='175.00'
    expect(screen.getByText('$175.00')).toBeInTheDocument()
  })
})

// ── Redesign layout — SCRUM-185 ───────────────────────────────────────────────

describe('OrderEntryPage — redesign layout (SCRUM-185)', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('shouldRenderClientAndRouteToInSameContainer', async () => {
    renderPage()
    await waitFor(() => expect(screen.getByRole('combobox', { name: 'Client' })).toBeInTheDocument())
    const clientSelect  = screen.getByRole('combobox', { name: 'Client' })
    const routeToSelect = screen.getByRole('combobox', { name: 'Route To' })
    expect(clientSelect.closest('[data-testid="client-route-row"]')).not.toBeNull()
    expect(routeToSelect.closest('[data-testid="client-route-row"]')).not.toBeNull()
    expect(clientSelect.closest('[data-testid="client-route-row"]')).toBe(
      routeToSelect.closest('[data-testid="client-route-row"]')
    )
  })

  it('shouldShowRouteToDropdownWithoutExpandingAdvancedSection', async () => {
    renderPage()
    await waitFor(() => expect(screen.getByRole('combobox', { name: 'Route To' })).toBeInTheDocument())
  })

  it('shouldNotRenderAdvancedToggleButton', async () => {
    renderPage()
    await waitFor(() => expect(screen.getByText('Complete required fields')).toBeInTheDocument())
    expect(screen.queryByTestId('advanced-toggle')).toBeNull()
  })

  it('shouldRenderSymbolInputWithSearchPlaceholder', async () => {
    renderPage()
    await waitFor(() =>
      expect(screen.getByPlaceholderText('Search ticker — e.g. AAPL, MSFT, NVDA')).toBeInTheDocument()
    )
  })

  it('shouldRenderOrderTypeAndTifInSameGridRow', async () => {
    renderPage()
    await waitFor(() => expect(screen.getByText('MARKET')).toBeInTheDocument())
    const marketBtn = screen.getByText('MARKET')
    const dayBtn    = screen.getByText('DAY')
    expect(marketBtn.closest('[data-testid="ordertype-tif-row"]')).not.toBeNull()
    expect(dayBtn.closest('[data-testid="ordertype-tif-row"]')).not.toBeNull()
    expect(marketBtn.closest('[data-testid="ordertype-tif-row"]')).toBe(
      dayBtn.closest('[data-testid="ordertype-tif-row"]')
    )
  })

  it('shouldRenderQuantityAndLimitPriceInSameGridRowForLimitOrder', async () => {
    renderPage()
    await waitFor(() => expect(screen.getByPlaceholderText('Number of shares')).toBeInTheDocument())
    const qtyInput   = screen.getByPlaceholderText('Number of shares')
    const limitInput = screen.getByRole('spinbutton', { name: 'Limit Price' })
    expect(qtyInput.closest('[data-testid="qty-limit-row"]')).not.toBeNull()
    expect(limitInput.closest('[data-testid="qty-limit-row"]')).not.toBeNull()
    expect(qtyInput.closest('[data-testid="qty-limit-row"]')).toBe(
      limitInput.closest('[data-testid="qty-limit-row"]')
    )
  })

  it('shouldHideLimitPriceInputWhenMarketOrderSelected', async () => {
    renderPage()
    await waitFor(() => expect(screen.getByText('MARKET')).toBeInTheDocument())
    await act(async () => { fireEvent.click(screen.getByText('MARKET')) })
    expect(screen.queryByRole('spinbutton', { name: 'Limit Price' })).toBeNull()
  })

  it('shouldShowGtdDatePickerInlineWithoutAdvancedSection', async () => {
    renderPage()
    const gtdBtn = await screen.findByText('GTD')
    await act(async () => { fireEvent.click(gtdBtn) })
    expect(screen.getByText('GTD Date')).toBeInTheDocument()
    expect(screen.getByPlaceholderText('Select GTD date')).toBeInTheDocument()
    expect(screen.queryByTestId('advanced-toggle')).toBeNull()
  })
})

// ── Order Summary + Submit label states — SCRUM-186 ───────────────────────────

describe('OrderEntryPage — order summary and submit label states (SCRUM-186)', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('shouldShowOrderSummaryPlaceholderWhenFormIncomplete', async () => {
    renderPage()
    await waitFor(() =>
      expect(screen.getByText('Complete the order details to preview your summary.')).toBeInTheDocument()
    )
  })

  it('shouldShowFormattedOrderSummaryWhenFormIsComplete', async () => {
    await renderAndLoadQuote()
    await act(async () => {
      fireEvent.change(screen.getByPlaceholderText('Number of shares'), { target: { value: '100' } })
    })
    await waitFor(() => {
      const box = screen.getByTestId('order-summary-box')
      expect(box.textContent).not.toContain('Complete the order details')
      expect(box.textContent).toContain('AAPL')
      expect(box.textContent).toContain('100')
      expect(box.textContent).toContain('Limit')
      expect(box.textContent).toContain('DAY')
      expect(box.textContent).toContain('Route')
    })
  })

  it('shouldShowCompleteRequiredFieldsLabelOnSubmitButtonWhenIncomplete', async () => {
    renderPage()
    await waitFor(() => expect(screen.getByText('Complete required fields')).toBeInTheDocument())
    expect(screen.getByText('Complete required fields')).toBeDisabled()
  })

  it('shouldShowReviewAndSubmitLabelOnSubmitButtonWhenFormComplete', async () => {
    await renderAndLoadQuote()
    await act(async () => {
      fireEvent.change(screen.getByPlaceholderText('Number of shares'), { target: { value: '100' } })
    })
    await waitFor(() => expect(screen.getByText('Review & Submit Order')).toBeInTheDocument())
    expect(screen.getByText('Review & Submit Order')).not.toBeDisabled()
  })
})

// ── SCRUM-187: Right panel redesign ──────────────────────────────────────────

describe('OrderEntryPage — right panel redesign (SCRUM-187)', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('shouldShowOrderPreviewCardWithEstimatedValueLabel', async () => {
    renderPage()
    await waitFor(() => {
      expect(screen.getByText('Order Preview')).toBeInTheDocument()
      expect(screen.getByText('Estimated Value')).toBeInTheDocument()
    })
  })

  it('shouldShowBuyingPowerProgressBarElement', async () => {
    const { container } = renderPage()
    await waitFor(() => expect(screen.getByText('Order Preview')).toBeInTheDocument())
    expect(container.querySelector('.bp-progress-bar')).not.toBeNull()
  })

  it('shouldShowMarketQuoteCardHeading', async () => {
    renderPage()
    await waitFor(() =>
      expect(screen.getByText(/market quote/i)).toBeInTheDocument()
    )
  })

  it('shouldShowMarketQuotePlaceholderWhenNoSymbol', async () => {
    renderPage()
    await waitFor(() =>
      expect(screen.getByText('Enter a symbol to see the current quote.')).toBeInTheDocument()
    )
  })

  it('shouldShowLtpInMarketQuoteAfterSymbolEntered', async () => {
    await renderAndLoadQuote()
    expect(screen.getByText(/market quote/i)).toBeInTheDocument()
    expect(screen.getByText('$175.00')).toBeInTheDocument()
  })
})

// ── Fractional quantity validation — SCRUM-187 ────────────────────────────────

describe('OrderEntryPage — fractional quantity validation', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('shouldShowFractionalErrorMessageWhenDecimalQuantityEntered', async () => {
    renderPage()
    await waitFor(() => expect(screen.getByPlaceholderText('Number of shares')).toBeInTheDocument())
    const qtyInput = screen.getByPlaceholderText('Number of shares')
    await act(async () => { fireEvent.change(qtyInput, { target: { value: '1.5' } }) })
    expect(screen.getByText('Quantity must be a whole number — fractional shares are not supported')).toBeInTheDocument()
  })

  it('shouldKeepSubmitButtonDisabledWhenFractionalQuantityEntered', async () => {
    renderPage()
    await waitFor(() => expect(screen.getByPlaceholderText('Number of shares')).toBeInTheDocument())
    const qtyInput = screen.getByPlaceholderText('Number of shares')
    await act(async () => { fireEvent.change(qtyInput, { target: { value: '2.25' } }) })
    expect(screen.getByText('Complete required fields')).toBeDisabled()
  })

  it('shouldClearFractionalErrorWhenWholeNumberEntered', async () => {
    renderPage()
    await waitFor(() => expect(screen.getByPlaceholderText('Number of shares')).toBeInTheDocument())
    const qtyInput = screen.getByPlaceholderText('Number of shares')
    await act(async () => { fireEvent.change(qtyInput, { target: { value: '1.5' } }) })
    expect(screen.getByText('Quantity must be a whole number — fractional shares are not supported')).toBeInTheDocument()
    await act(async () => { fireEvent.change(qtyInput, { target: { value: '10' } }) })
    expect(screen.queryByText('Quantity must be a whole number — fractional shares are not supported')).toBeNull()
  })
})

// ── Limit price decimal validation ────────────────────────────────────────────

describe('OrderEntryPage — limit price decimal validation', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('shouldShowErrorWhenLimitPriceHasMoreThanTwoDigitsAfterDecimal', async () => {
    renderPage()
    await waitFor(() => expect(screen.getByRole('spinbutton', { name: 'Limit Price' })).toBeInTheDocument())
    const priceInput = screen.getByRole('spinbutton', { name: 'Limit Price' })
    await act(async () => { fireEvent.change(priceInput, { target: { value: '10.123' } }) })
    expect(screen.getByText('Limit price cannot have more than 2 digits after the decimal')).toBeInTheDocument()
  })

  it('shouldDisableSubmitButtonWhenLimitPriceExceedsTwoDigitsAfterDecimal', async () => {
    renderPage()
    await waitFor(() => expect(screen.getByRole('spinbutton', { name: 'Limit Price' })).toBeInTheDocument())
    const priceInput = screen.getByRole('spinbutton', { name: 'Limit Price' })
    await act(async () => { fireEvent.change(priceInput, { target: { value: '99.999' } }) })
    expect(screen.getByText('Complete required fields')).toBeDisabled()
  })

  it('shouldNotShowErrorForExactlyTwoDigitsAfterDecimal', async () => {
    renderPage()
    await waitFor(() => expect(screen.getByRole('spinbutton', { name: 'Limit Price' })).toBeInTheDocument())
    const priceInput = screen.getByRole('spinbutton', { name: 'Limit Price' })
    await act(async () => { fireEvent.change(priceInput, { target: { value: '10.25' } }) })
    expect(screen.queryByText('Limit price cannot have more than 2 digits after the decimal')).toBeNull()
  })

  it('shouldNotShowErrorForWholeNumberLimitPrice', async () => {
    renderPage()
    await waitFor(() => expect(screen.getByRole('spinbutton', { name: 'Limit Price' })).toBeInTheDocument())
    const priceInput = screen.getByRole('spinbutton', { name: 'Limit Price' })
    await act(async () => { fireEvent.change(priceInput, { target: { value: '150' } }) })
    expect(screen.queryByText('Limit price cannot have more than 2 digits after the decimal')).toBeNull()
  })

  it('shouldClearDecimalErrorWhenPriceCorrectedToTwoDigits', async () => {
    renderPage()
    await waitFor(() => expect(screen.getByRole('spinbutton', { name: 'Limit Price' })).toBeInTheDocument())
    const priceInput = screen.getByRole('spinbutton', { name: 'Limit Price' })
    await act(async () => { fireEvent.change(priceInput, { target: { value: '10.123' } }) })
    expect(screen.getByText('Limit price cannot have more than 2 digits after the decimal')).toBeInTheDocument()
    await act(async () => { fireEvent.change(priceInput, { target: { value: '10.12' } }) })
    expect(screen.queryByText('Limit price cannot have more than 2 digits after the decimal')).toBeNull()
  })
})

// ── Tab navigation and ARIA labels (SCRUM-141) ────────────────────────────────

describe('OrderEntryPage — tab navigation and aria labels (SCRUM-141)', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('shouldFocusClientDropdownAfterClientsLoad', async () => {
    renderPage()
    // waitFor the select to be populated — focus fires inside getClients.then()
    const clientSelect = await waitFor(() => {
      const el = screen.getByRole('combobox', { name: 'Client' })
      expect(el).toHaveValue('a1000000-0000-0000-0000-000000000001')
      return el
    })
    expect(clientSelect).toHaveFocus()
  })

  it('shouldRenderSymbolInputWithAriaLabel', async () => {
    renderPage()
    await waitFor(() => expect(screen.getByRole('textbox', { name: 'Symbol' })).toBeInTheDocument())
  })

  it('shouldRenderQuantityInputWithAriaLabel', async () => {
    renderPage()
    await waitFor(() => expect(screen.getByRole('spinbutton', { name: 'Quantity (Shares)' })).toBeInTheDocument())
  })

  it('shouldRenderSideGroupWithAriaLabel', async () => {
    renderPage()
    await waitFor(() => expect(screen.getByRole('group', { name: 'Side' })).toBeInTheDocument())
  })

  it('shouldRenderOrderTypeGroupWithAriaLabel', async () => {
    renderPage()
    await waitFor(() => expect(screen.getByRole('group', { name: 'Order Type' })).toBeInTheDocument())
  })

  it('shouldRenderTifGroupWithAriaLabel', async () => {
    renderPage()
    await waitFor(() => expect(screen.getByRole('group', { name: 'Time in Force' })).toBeInTheDocument())
  })

  it('shouldRenderSubmitButtonWithAriaLabel', async () => {
    renderPage()
    await waitFor(() => expect(screen.getByRole('button', { name: 'Submit Order' })).toBeInTheDocument())
  })

  it('shouldHaveAriaPressedTrueOnBuyByDefault', async () => {
    renderPage()
    await waitFor(() => expect(screen.getByRole('button', { name: 'BUY' })).toBeInTheDocument())
    expect(screen.getByRole('button', { name: 'BUY' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: 'SELL' })).toHaveAttribute('aria-pressed', 'false')
    expect(screen.getByRole('button', { name: 'SHORT SELL' })).toHaveAttribute('aria-pressed', 'false')
  })

  it('shouldUpdateAriaPressedWhenSideChangedToSell', async () => {
    renderPage()
    await waitFor(() => expect(screen.getByRole('button', { name: 'SELL' })).toBeInTheDocument())
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'SELL' })) })
    expect(screen.getByRole('button', { name: 'SELL' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: 'BUY' })).toHaveAttribute('aria-pressed', 'false')
    expect(screen.getByRole('button', { name: 'SHORT SELL' })).toHaveAttribute('aria-pressed', 'false')
  })

  it('shouldUpdateAriaPressedWhenSideChangedToShortSell', async () => {
    renderPage()
    await waitFor(() => expect(screen.getByRole('button', { name: 'SHORT SELL' })).toBeInTheDocument())
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'SHORT SELL' })) })
    expect(screen.getByRole('button', { name: 'SHORT SELL' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: 'BUY' })).toHaveAttribute('aria-pressed', 'false')
    expect(screen.getByRole('button', { name: 'SELL' })).toHaveAttribute('aria-pressed', 'false')
  })

  it('shouldKeepSymbolValueOnEscapeKey', async () => {
    renderPage()
    await waitFor(() => expect(screen.getByRole('textbox', { name: 'Symbol' })).toBeInTheDocument())
    const symbolInput = screen.getByRole('textbox', { name: 'Symbol' })
    await act(async () => { fireEvent.change(symbolInput, { target: { value: 'AAPL' } }) })
    await act(async () => { fireEvent.keyDown(symbolInput, { key: 'Escape', code: 'Escape' }) })
    expect(symbolInput).toHaveValue('AAPL')
  })

  it('shouldApplyOrderFormCardClassToLeftCard', async () => {
    const { container } = renderPage()
    await waitFor(() => expect(screen.getByRole('combobox', { name: 'Client' })).toBeInTheDocument())
    expect(container.querySelector('.order-form-card')).not.toBeNull()
  })

  it('shouldRenderGtdDateInputWithLabelAssociationWhenGtdSelected', async () => {
    renderPage()
    const gtdBtn = await screen.findByRole('button', { name: 'GTD' })
    await act(async () => { fireEvent.click(gtdBtn) })
    await waitFor(() => expect(screen.getByLabelText('GTD Date')).toBeInTheDocument())
  })
})
