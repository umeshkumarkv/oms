import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import NavBar from '../components/NavBar'
import * as marketApi from '../api/marketApi'
import * as AuthContext from '../context/AuthContext'
import type { AuthUser } from '../context/AuthContext'

vi.mock('../api/marketApi')

vi.mock('../context/AuthContext', async () => {
  const actual = await vi.importActual<typeof import('../context/AuthContext')>('../context/AuthContext')
  return { ...actual, useAuth: vi.fn() }
})

vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom')
  return {
    ...actual,
    NavLink: ({ children, ...props }: React.AnchorHTMLAttributes<HTMLAnchorElement> & { to: string }) =>
      <a {...props}>{children}</a>,
  }
})

const mockAuthUser: AuthUser = {
  token: 'test-token',
  userId: 'u1',
  username: 'trader.john',
  role: 'TRADER',
  displayName: 'John Doe',
}

const renderNavBar = () =>
  render(
    <MemoryRouter>
      <NavBar onBurgerClick={vi.fn()} sidebarCollapsed={false} />
    </MemoryRouter>
  )

describe('NavBar', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(AuthContext.useAuth).mockReturnValue({
      user: mockAuthUser,
      sessionExpired: false,
      login: vi.fn(),
      logout: vi.fn(),
      clearSessionExpired: vi.fn(),
    })
    vi.mocked(marketApi.getMarketHours).mockResolvedValue({
      marketOpen: true, openTime: '09:30', closeTime: '16:00', timezone: 'America/New_York',
    })
  })

  it('shouldDisplayTradingDateInDDMMMYYYYFormat', async () => {
    vi.mocked(marketApi.getHolidays).mockResolvedValue([])
    renderNavBar()
    await waitFor(() => {
      const dateEl = screen.getByText(/^\d{2} [A-Z][a-z]{2} \d{4}$/)
      expect(dateEl).toBeInTheDocument()
      expect(dateEl.textContent).not.toContain(',')
    })
  })

  it('shouldRenderCalendarIconAlongsideDate', async () => {
    vi.mocked(marketApi.getHolidays).mockResolvedValue([])
    renderNavBar()
    await waitFor(() => {
      expect(screen.getByText(/^\d{2} [A-Z][a-z]{2} \d{4}$/)).toBeInTheDocument()
    })
    // lucide-react renders icons as SVG elements
    const svgs = document.querySelectorAll('svg')
    expect(svgs.length).toBeGreaterThan(0)
  })

  it('shouldRenderDateBeforeTraderNameInDOM', async () => {
    vi.mocked(marketApi.getHolidays).mockResolvedValue([])
    renderNavBar()
    await waitFor(() => {
      expect(screen.getByText(/^\d{2} [A-Z][a-z]{2} \d{4}$/)).toBeInTheDocument()
    })
    const dateEl = screen.getByText(/^\d{2} [A-Z][a-z]{2} \d{4}$/)
    const nameEl = screen.getByText('John Doe')
    // Node.DOCUMENT_POSITION_FOLLOWING (4): nameEl follows dateEl in the DOM
    expect(dateEl.compareDocumentPosition(nameEl) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
  })

  it('shouldDateElementNotBeInteractive', async () => {
    vi.mocked(marketApi.getHolidays).mockResolvedValue([])
    renderNavBar()
    await waitFor(() => {
      expect(screen.getByText(/^\d{2} [A-Z][a-z]{2} \d{4}$/)).toBeInTheDocument()
    })
    const dateEl = screen.getByText(/^\d{2} [A-Z][a-z]{2} \d{4}$/)
    expect(dateEl.tagName.toLowerCase()).toBe('span')
    expect(dateEl.closest('button')).toBeNull()
  })

  it('shouldFallbackGracefullyWhenHolidayFetchFails', async () => {
    vi.mocked(marketApi.getHolidays).mockRejectedValue(new Error('network error'))
    renderNavBar()
    await waitFor(() => {
      expect(screen.getByText(/^\d{2} [A-Z][a-z]{2} \d{4}$/)).toBeInTheDocument()
    })
  })

  it('shouldShowMarketOpenPillWhenApiReturnsMarketOpenTrue', async () => {
    vi.mocked(marketApi.getHolidays).mockResolvedValue([])
    vi.mocked(marketApi.getMarketHours).mockResolvedValue({
      marketOpen: true, openTime: '09:30', closeTime: '16:00', timezone: 'America/New_York',
    })
    renderNavBar()
    await waitFor(() => expect(screen.getByText('MARKET OPEN')).toBeInTheDocument())
  })

  it('shouldShowMarketClosedPillWhenApiReturnsMarketOpenFalse', async () => {
    vi.mocked(marketApi.getHolidays).mockResolvedValue([])
    vi.mocked(marketApi.getMarketHours).mockResolvedValue({
      marketOpen: false, openTime: '09:30', closeTime: '16:00', timezone: 'America/New_York',
    })
    renderNavBar()
    await waitFor(() => expect(screen.getByText('MARKET CLOSED')).toBeInTheDocument())
  })

  it('shouldHidePillWhileMarketStatusIsLoading', () => {
    vi.mocked(marketApi.getHolidays).mockReturnValue(new Promise(() => {}))
    vi.mocked(marketApi.getMarketHours).mockReturnValue(new Promise(() => {}))
    renderNavBar()
    expect(screen.queryByText('MARKET OPEN')).toBeNull()
    expect(screen.queryByText('MARKET CLOSED')).toBeNull()
    expect(screen.queryByText('MARKET STATUS UNAVAILABLE')).toBeNull()
  })

  it('shouldShowUnavailablePillWhenApiThrows', async () => {
    vi.mocked(marketApi.getHolidays).mockResolvedValue([])
    vi.mocked(marketApi.getMarketHours).mockRejectedValue(new Error('network error'))
    renderNavBar()
    await waitFor(() => expect(screen.getByText('MARKET STATUS UNAVAILABLE')).toBeInTheDocument())
  })

  it('shouldShowWarningTooltipOnHoverWhenApiThrows', async () => {
    vi.mocked(marketApi.getHolidays).mockResolvedValue([])
    vi.mocked(marketApi.getMarketHours).mockRejectedValue(new Error('network error'))
    renderNavBar()
    await waitFor(() => expect(screen.getByText('MARKET STATUS UNAVAILABLE')).toBeInTheDocument())
    await act(async () => {
      await userEvent.hover(screen.getByText('MARKET STATUS UNAVAILABLE'))
    })
    expect(screen.getByText('Market status unavailable')).toBeInTheDocument()
    expect(screen.getByText(/verify the market is open before placing orders/i)).toBeInTheDocument()
  })

  it('shouldRefreshMarketStatusEvery60Seconds', async () => {
    vi.useFakeTimers()
    vi.mocked(marketApi.getHolidays).mockResolvedValue([])
    vi.mocked(marketApi.getMarketHours).mockResolvedValue({
      marketOpen: true, openTime: '09:30', closeTime: '16:00', timezone: 'America/New_York',
    })
    renderNavBar()
    await act(async () => { await Promise.resolve() })
    expect(vi.mocked(marketApi.getMarketHours)).toHaveBeenCalledTimes(1)
    await act(async () => { vi.advanceTimersByTime(60_000) })
    await act(async () => { await Promise.resolve() })
    expect(vi.mocked(marketApi.getMarketHours)).toHaveBeenCalledTimes(2)
    vi.useRealTimers()
  })

  it('shouldNotRenderLocalMarketStatusLabel', async () => {
    vi.mocked(marketApi.getHolidays).mockResolvedValue([])
    vi.mocked(marketApi.getMarketHours).mockResolvedValue({
      marketOpen: true, openTime: '09:30', closeTime: '16:00', timezone: 'America/New_York',
    })
    renderNavBar()
    await waitFor(() => expect(screen.getByText('MARKET OPEN')).toBeInTheDocument())
    expect(screen.queryByText('OPEN', { exact: true })).toBeNull()
    expect(screen.queryByText('CLOSED', { exact: true })).toBeNull()
  })
})
