import { render, screen, waitFor } from '@testing-library/react'
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
})
