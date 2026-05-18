import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import DashboardPage from '../pages/DashboardPage'

// ── Mock API modules ──────────────────────────────────────────────────────────

const mockGetOrders = vi.fn()
vi.mock('../api/orderApi', () => ({
  getOrders: (params: Record<string, unknown>) => mockGetOrders(params),
}))

vi.mock('../api/marketApi', () => ({
  getMarketHours: vi.fn().mockResolvedValue({ marketOpen: true }),
}))

// ── Helpers ───────────────────────────────────────────────────────────────────

const emptyPage = { orders: [], page: 0, pageSize: 50, totalCount: 0, totalPages: 0 }
const pageWithCount = (n: number) => ({ ...emptyPage, totalCount: n })

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
})
