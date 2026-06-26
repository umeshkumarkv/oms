import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import OrderBlotterPage from '../pages/OrderBlotterPage'
import { formatBlotterTimestamp } from '../utils/dateUtils'

// ── Mock API modules ──────────────────────────────────────────────────────────

vi.mock('../api/orderApi', () => ({
  getOrders:    vi.fn(),
  getOrder:     vi.fn(),
  getExecutions: vi.fn().mockResolvedValue([]),
}))

vi.mock('../api/clientApi', () => ({
  getClients: vi.fn().mockResolvedValue([]),
}))

vi.mock('../api/auditApi', () => ({
  getOrderAudit: vi.fn().mockResolvedValue([]),
}))

vi.mock('../context/NotificationsContext', () => ({
  useNotificationsContext: vi.fn(() => ({
    notifications: [],
    sseConnected: false,
    dismiss: vi.fn(),
  })),
}))

// ── Fixtures ──────────────────────────────────────────────────────────────────

const TODAY_ISO = new Date().toISOString()

const makeOrder = (overrides = {}) => ({
  id: 'aaaaaaaa-0000-0000-0000-000000000001',
  clientId: 'cccccccc-0000-0000-0000-000000000001',
  clientOrderId: 'REF-001',
  symbol: 'AAPL',
  side: 'BUY' as const,
  orderType: 'LIMIT' as const,
  quantity: 100,
  limitPrice: '150.00',
  status: 'NEW' as const,
  timeInForce: 'DAY' as const,
  createdAt: TODAY_ISO,
  filledQuantity: null,
  avgFillPrice: null,
  routingDestination: null,
  expiryDate: null,
  ...overrides,
})

// ── Helper unit tests — pure function, no render required ─────────────────────

describe('formatBlotterTimestamp — helper unit tests', () => {
  it('fmtBlotterTimestamp_formatsIsoUtcAsEasternTime', () => {
    // 2026-06-04T13:30:15Z → EDT (UTC-4) = 09:30:15 ET
    expect(formatBlotterTimestamp('2026-06-04T13:30:15Z')).toBe('04 Jun 2026, 09:30:15 ET')
  })

  it('fmtBlotterTimestamp_appliesEstOffsetInWinter', () => {
    // 2026-01-15T15:00:00Z → EST (UTC-5) = 10:00:00 ET
    expect(formatBlotterTimestamp('2026-01-15T15:00:00Z')).toBe('15 Jan 2026, 10:00:00 ET')
  })

  it('fmtBlotterTimestamp_handlesEndOfDayBoundary', () => {
    // 2026-06-04T20:59:59Z → EDT (UTC-4) = 16:59:59 ET
    expect(formatBlotterTimestamp('2026-06-04T20:59:59Z')).toBe('04 Jun 2026, 16:59:59 ET')
  })
})

// ── Component render tests ────────────────────────────────────────────────────

describe('OrderBlotterPage — Created column', () => {
  beforeEach(() => { vi.clearAllMocks() })

  const renderPage = () =>
    render(
      <MemoryRouter>
        <OrderBlotterPage />
      </MemoryRouter>
    )

  it('shouldRenderFormattedTimestampInCreatedColumnWithoutTooltip', async () => {
    const { getOrders } = await import('../api/orderApi')
    vi.mocked(getOrders).mockResolvedValue({
      orders: [makeOrder({ createdAt: '2026-06-04T13:30:15Z' })],
      page: 0, pageSize: 25, totalCount: 1, totalPages: 1,
    } as never)

    renderPage()

    await waitFor(() => {
      // Full formatted timestamp visible in the DOM
      expect(screen.getByText(/Jun 2026/)).toBeInTheDocument()
    })

    // The Created cell span must NOT carry a title attribute (tooltip removed)
    const createdSpan = screen.getByText(/Jun 2026/)
    expect(createdSpan.hasAttribute('title')).toBe(false)
  })

  it('shouldNotRenderRelativeTimeInCreatedColumn', async () => {
    const { getOrders } = await import('../api/orderApi')
    vi.mocked(getOrders).mockResolvedValue({
      orders: [makeOrder({ createdAt: '2026-06-04T13:30:15Z' })],
      page: 0, pageSize: 25, totalCount: 1, totalPages: 1,
    } as never)

    renderPage()

    await waitFor(() => screen.getByText(/Jun 2026/))

    // Relative time patterns must not appear in any Created cell
    expect(screen.queryByText(/\d+[mhd] ago/)).toBeNull()
    expect(screen.queryByText('just now')).toBeNull()
  })

  it('shouldRenderBlotterTableWithoutCrashing', async () => {
    const { getOrders } = await import('../api/orderApi')
    vi.mocked(getOrders).mockResolvedValue({
      orders: [],
      page: 0, pageSize: 25, totalCount: 0, totalPages: 0,
    } as never)

    const { container } = renderPage()

    await waitFor(() => expect(container).toBeInTheDocument())
    expect(container.querySelector('table')).toBeInTheDocument()
  })
})

// ── Rejection Reason drawer tests ─────────────────────────────────────────────

describe('OrderBlotterPage — Rejection Reason drawer', () => {
  beforeEach(() => { vi.clearAllMocks() })

  const renderPage = () =>
    render(
      <MemoryRouter>
        <OrderBlotterPage />
      </MemoryRouter>
    )

  it('shouldShowRejectionReasonSectionForRiskRejectedOrderWithReason', async () => {
    const { getOrders } = await import('../api/orderApi')
    vi.mocked(getOrders).mockResolvedValue({
      orders: [makeOrder({ status: 'RISK_REJECTED', rejectionReason: 'ORDER_VALUE_LIMIT exceeded' })],
      page: 0, pageSize: 25, totalCount: 1, totalPages: 1,
    } as never)

    renderPage()

    await waitFor(() => screen.getByText('AAPL'))

    const row = screen.getByText('AAPL').closest('tr')!
    fireEvent.click(row)

    await waitFor(() => {
      expect(screen.getByText('Rejection Reason')).toBeInTheDocument()
      expect(screen.getByText('ORDER_VALUE_LIMIT exceeded')).toBeInTheDocument()
    })
  })

  it('shouldNotShowRejectionReasonSectionForNonRejectedOrder', async () => {
    const { getOrders } = await import('../api/orderApi')
    vi.mocked(getOrders).mockResolvedValue({
      orders: [makeOrder({ status: 'FILLED', rejectionReason: null })],
      page: 0, pageSize: 25, totalCount: 1, totalPages: 1,
    } as never)

    renderPage()

    await waitFor(() => screen.getByText('AAPL'))

    const row = screen.getByText('AAPL').closest('tr')!
    fireEvent.click(row)

    // 'Order Details' section heading only exists inside the open drawer
    await waitFor(() => expect(screen.getByText('Order Details')).toBeInTheDocument())
    expect(screen.queryByText('Rejection Reason')).toBeNull()
  })

  it('shouldNotShowRejectionReasonSectionForRiskRejectedOrderWithNullReason', async () => {
    const { getOrders } = await import('../api/orderApi')
    vi.mocked(getOrders).mockResolvedValue({
      orders: [makeOrder({ status: 'RISK_REJECTED', rejectionReason: null })],
      page: 0, pageSize: 25, totalCount: 1, totalPages: 1,
    } as never)

    renderPage()

    await waitFor(() => screen.getByText('AAPL'))

    const row = screen.getByText('AAPL').closest('tr')!
    fireEvent.click(row)

    // 'Order Details' section heading only exists inside the open drawer
    await waitFor(() => expect(screen.getByText('Order Details')).toBeInTheDocument())
    expect(screen.queryByText('Rejection Reason')).toBeNull()
  })
})
