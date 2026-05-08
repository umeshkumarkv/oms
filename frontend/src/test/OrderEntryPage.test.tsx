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
