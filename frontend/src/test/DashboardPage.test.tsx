import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import DashboardPage from '../pages/DashboardPage'
import * as marketApi from '../api/marketApi'
import * as clientApi from '../api/clientApi'

// ── Mock API modules ──────────────────────────────────────────────────────────

const mockGetOrders = vi.fn()
vi.mock('../api/orderApi', () => ({
  getOrders: (params: Record<string, unknown>) => mockGetOrders(params),
}))

vi.mock('../api/marketApi', () => ({
  getMarketHours:   vi.fn(),
  getMarketMovers:  vi.fn(),
  getMarketIndices: vi.fn(),
}))

vi.mock('../api/clientApi', () => ({
  getActiveClients: vi.fn(),
}))

// ── Helpers ───────────────────────────────────────────────────────────────────

const emptyPage = { orders: [], page: 0, pageSize: 50, totalCount: 0, totalPages: 0 }
const pageWithCount = (n: number) => ({ ...emptyPage, totalCount: n })

const mockMovers = {
  gainers: [
    { symbol: 'NVDA', percentChange: '3.21' },
    { symbol: 'AAPL', percentChange: '1.85' },
  ],
  losers: [
    { symbol: 'TSLA', percentChange: '-2.10' },
    { symbol: 'META', percentChange: '-0.95' },
  ],
}

const mockIndices = [
  { name: 'S&P 500',   currentValue: '5321.8400',  percentChange: '0.65'  },
  { name: 'Nasdaq',    currentValue: '16748.2000', percentChange: '1.02'  },
  { name: 'Dow Jones', currentValue: '38492.5000', percentChange: '-0.18' },
]

function renderDashboard() {
  return render(
    <MemoryRouter>
      <DashboardPage />
    </MemoryRouter>
  )
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe('DashboardPage — createdAfter date scoping', () => {
  beforeEach(() => {
    mockGetOrders.mockResolvedValue(emptyPage)
    vi.mocked(marketApi.getMarketMovers).mockResolvedValue(mockMovers)
    vi.mocked(marketApi.getMarketIndices).mockResolvedValue(mockIndices)
    vi.mocked(clientApi.getActiveClients).mockResolvedValue([])
  })

  it('passes createdAfter=todayUTCMidnight to FILLED call', async () => {
    mockGetOrders.mockImplementation((params: Record<string, unknown>) => {
      if (params.status === 'FILLED') return Promise.resolve(pageWithCount(3))
      return Promise.resolve(emptyPage)
    })

    renderDashboard()

    await waitFor(() => {
      const filledCall = mockGetOrders.mock.calls.find(
        ([p]: [Record<string, unknown>]) => p.status === 'FILLED'
      )
      expect(filledCall).toBeDefined()
      const createdAfter = filledCall![0].createdAfter as string
      expect(createdAfter).toMatch(/^\d{4}-\d{2}-\d{2}T00:00:00\.000Z$/)
    })
  })

  it('passes createdAfter=todayUTCMidnight to EXPIRED call', async () => {
    renderDashboard()

    await waitFor(() => {
      const expiredCall = mockGetOrders.mock.calls.find(
        ([p]: [Record<string, unknown>]) => p.status === 'EXPIRED'
      )
      expect(expiredCall).toBeDefined()
      const createdAfter = expiredCall![0].createdAfter as string
      expect(createdAfter).toMatch(/^\d{4}-\d{2}-\d{2}T00:00:00\.000Z$/)
    })
  })

  it('does NOT pass createdAfter to Open Orders call', async () => {
    renderDashboard()

    await waitFor(() => {
      const openCall = mockGetOrders.mock.calls.find(
        ([p]: [Record<string, unknown>]) => p.status === 'OPEN' && !p.timeInForce
      )
      expect(openCall).toBeDefined()
      expect(openCall![0].createdAfter).toBeUndefined()
    })
  })

  it('does NOT pass createdAfter to GTD Active call', async () => {
    renderDashboard()

    await waitFor(() => {
      const gtdCall = mockGetOrders.mock.calls.find(
        ([p]: [Record<string, unknown>]) => p.timeInForce === 'GTD'
      )
      expect(gtdCall).toBeDefined()
      expect(gtdCall![0].createdAfter).toBeUndefined()
    })
  })

  it('displays totalCount from FILLED response in the Filled Today card', async () => {
    mockGetOrders.mockImplementation((params: Record<string, unknown>) => {
      if (params.status === 'FILLED') return Promise.resolve(pageWithCount(7))
      return Promise.resolve(emptyPage)
    })

    renderDashboard()

    await waitFor(() => {
      expect(screen.getByText('7')).toBeDefined()
    })
  })

  it('shouldNotRenderMarketStatusPillInDashboardHeader', async () => {
    renderDashboard()
    await waitFor(() => expect(screen.getByText('Dashboard')).toBeInTheDocument())
    expect(screen.queryByText('MARKET OPEN')).toBeNull()
    expect(screen.queryByText('MARKET CLOSED')).toBeNull()
  })

  it('shouldNotCallGetMarketHoursFromDashboard', async () => {
    renderDashboard()
    await waitFor(() => expect(screen.getByText('Dashboard')).toBeInTheDocument())
    expect(vi.mocked(marketApi.getMarketHours)).not.toHaveBeenCalled()
  })
})

describe('DashboardPage — Gainers & Losers tile', () => {
  beforeEach(() => {
    mockGetOrders.mockResolvedValue(emptyPage)
    vi.mocked(marketApi.getMarketMovers).mockResolvedValue(mockMovers)
    vi.mocked(marketApi.getMarketIndices).mockResolvedValue(mockIndices)
    vi.mocked(clientApi.getActiveClients).mockResolvedValue([])
  })

  it('shouldRenderGainersSectionHeader', async () => {
    renderDashboard()
    await waitFor(() => {
      expect(screen.getByText('Gainers & Losers')).toBeInTheDocument()
      expect(screen.getByText('Gainers')).toBeInTheDocument()
    })
  })

  it('shouldRenderLosersSectionHeader', async () => {
    renderDashboard()
    await waitFor(() => expect(screen.getByText('Losers')).toBeInTheDocument())
  })

  it('shouldDisplayTopGainerSymbolAndPercent', async () => {
    renderDashboard()
    await waitFor(() => {
      expect(screen.getByText('NVDA')).toBeInTheDocument()
      expect(screen.getByText('+3.21%')).toBeInTheDocument()
    })
  })

  it('shouldDisplayTopLoserSymbolAndPercent', async () => {
    renderDashboard()
    await waitFor(() => {
      expect(screen.getByText('TSLA')).toBeInTheDocument()
      expect(screen.getByText('-2.10%')).toBeInTheDocument()
    })
  })

  it('shouldShowDashInGainersLosersWhenMoversFails', async () => {
    vi.mocked(marketApi.getMarketMovers).mockRejectedValue(new Error('Network error'))
    renderDashboard()
    await waitFor(() => {
      const dashes = screen.getAllByText('--')
      expect(dashes.length).toBeGreaterThanOrEqual(1)
    })
  })
})

describe('DashboardPage — Index Snapshot tile', () => {
  beforeEach(() => {
    mockGetOrders.mockResolvedValue(emptyPage)
    vi.mocked(marketApi.getMarketMovers).mockResolvedValue(mockMovers)
    vi.mocked(marketApi.getMarketIndices).mockResolvedValue(mockIndices)
    vi.mocked(clientApi.getActiveClients).mockResolvedValue([])
  })

  it('shouldRenderIndexSnapshotSectionHeader', async () => {
    renderDashboard()
    await waitFor(() => expect(screen.getByText('Index Snapshot')).toBeInTheDocument())
  })

  it('shouldDisplayIndexNameValueAndPercent', async () => {
    renderDashboard()
    await waitFor(() => {
      expect(screen.getByText('S&P 500')).toBeInTheDocument()
      expect(screen.getByText('5321.8400')).toBeInTheDocument()
      expect(screen.getByText('+0.65%')).toBeInTheDocument()
    })
  })

  it('shouldShowDashInIndexSnapshotWhenIndicesFails', async () => {
    vi.mocked(marketApi.getMarketIndices).mockRejectedValue(new Error('Network error'))
    renderDashboard()
    await waitFor(() => {
      const dashes = screen.getAllByText('--')
      expect(dashes.length).toBeGreaterThanOrEqual(1)
    })
  })
})

describe('DashboardPage — Most Active Clients panel', () => {
  beforeEach(() => {
    mockGetOrders.mockResolvedValue(emptyPage)
    vi.mocked(marketApi.getMarketMovers).mockResolvedValue(mockMovers)
    vi.mocked(marketApi.getMarketIndices).mockResolvedValue(mockIndices)
    vi.mocked(clientApi.getActiveClients).mockResolvedValue([])
  })

  it('shouldCallGetActiveClientsOnMount', async () => {
    renderDashboard()
    await waitFor(() => {
      expect(vi.mocked(clientApi.getActiveClients)).toHaveBeenCalledWith(10)
    })
  })

  it('shouldRenderMostActiveClientsSectionHeader', async () => {
    renderDashboard()
    await waitFor(() => {
      expect(screen.getByText('Most Active Clients')).toBeInTheDocument()
    })
  })
})
