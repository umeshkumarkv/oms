import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import ProtectedRoute from '../components/ProtectedRoute'
import Sidebar from '../components/Sidebar'
import { useAuth } from '../context/AuthContext'
import type { AuthUser } from '../context/AuthContext'

vi.mock('../context/AuthContext', () => ({
  useAuth: vi.fn(),
}))

const mockUseAuth = vi.mocked(useAuth)

const makeUser = (role: string): AuthUser => ({
  token: 'test-token',
  userId: '00000000-0000-0000-0000-000000000001',
  username: 'testuser',
  role,
  displayName: 'Test User',
})

const mockAuthContext = (user: AuthUser | null) =>
  mockUseAuth.mockReturnValue({
    user,
    sessionExpired: false,
    login: vi.fn(),
    logout: vi.fn(),
    clearSessionExpired: vi.fn(),
  })

const renderProtectedRoute = (user: AuthUser | null, allowedRoles?: string[]) => {
  mockAuthContext(user)
  return render(
    <MemoryRouter initialEntries={['/orders/new']}>
      <Routes>
        <Route element={<ProtectedRoute allowedRoles={allowedRoles} />}>
          <Route path="/orders/new" element={<div>Order Entry Page</div>} />
        </Route>
        <Route path="/" element={<div>Login Page</div>} />
        <Route path="/dashboard" element={<div>Dashboard Page</div>} />
        <Route path="/admin" element={<div>Admin Panel Page</div>} />
      </Routes>
    </MemoryRouter>
  )
}

const renderSidebar = (role: string) => {
  mockAuthContext(makeUser(role))
  return render(
    <MemoryRouter>
      <Sidebar collapsed={false} />
    </MemoryRouter>
  )
}

describe('ProtectedRoute', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('shouldRenderOutletForTraderRole', () => {
    renderProtectedRoute(makeUser('TRADER'), ['TRADER'])
    expect(screen.getByText('Order Entry Page')).toBeInTheDocument()
  })

  it('shouldRedirectToLoginForUnauthenticatedUser', () => {
    renderProtectedRoute(null, ['TRADER'])
    expect(screen.getByText('Login Page')).toBeInTheDocument()
    expect(screen.queryByText('Order Entry Page')).not.toBeInTheDocument()
  })

  it('shouldRedirectToDashboardForOperationsRole', () => {
    renderProtectedRoute(makeUser('OPERATIONS'), ['TRADER'])
    expect(screen.getByText('Dashboard Page')).toBeInTheDocument()
    expect(screen.queryByText('Order Entry Page')).not.toBeInTheDocument()
  })

  it('shouldRedirectAdminRoleToAdminPanel', () => {
    renderProtectedRoute(makeUser('ADMIN'), ['TRADER'])
    expect(screen.getByText('Admin Panel Page')).toBeInTheDocument()
    expect(screen.queryByText('Order Entry Page')).not.toBeInTheDocument()
  })

  it('shouldPassAuthOnlyCheckForAnyAuthenticatedUser', () => {
    renderProtectedRoute(makeUser('ADMIN'))
    expect(screen.getByText('Order Entry Page')).toBeInTheDocument()
  })
})

describe('Sidebar role-based visibility', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('shouldShowNewOrderLinkForTrader', () => {
    renderSidebar('TRADER')
    expect(screen.getByText('New Order')).toBeInTheDocument()
  })

  it('shouldHideNewOrderLinkForOperations', () => {
    renderSidebar('OPERATIONS')
    expect(screen.queryByText('New Order')).not.toBeInTheDocument()
  })

  it('shouldHideNewOrderLinkForAdmin', () => {
    renderSidebar('ADMIN')
    expect(screen.queryByText('New Order')).not.toBeInTheDocument()
    expect(screen.getByText('Admin Panel')).toBeInTheDocument()
  })

  it('shouldShowOnlyDashboardAndAdminPanelForAdmin', () => {
    renderSidebar('ADMIN')
    expect(screen.getByText('Dashboard')).toBeInTheDocument()
    expect(screen.getByText('Admin Panel')).toBeInTheDocument()
    expect(screen.queryByText('Order Blotter')).not.toBeInTheDocument()
    expect(screen.queryByText('Market Data')).not.toBeInTheDocument()
    expect(screen.queryByText('Portfolio')).not.toBeInTheDocument()
    expect(screen.queryByText('Audit Trail')).not.toBeInTheDocument()
  })

  it('shouldShowFullSidebarForTrader', () => {
    renderSidebar('TRADER')
    expect(screen.getByText('Dashboard')).toBeInTheDocument()
    expect(screen.getByText('Order Blotter')).toBeInTheDocument()
    expect(screen.getByText('Market Data')).toBeInTheDocument()
    expect(screen.getByText('Portfolio')).toBeInTheDocument()
    expect(screen.getByText('Audit Trail')).toBeInTheDocument()
    expect(screen.queryByText('Admin Panel')).not.toBeInTheDocument()
  })
})

const renderRouteAsRole = (role: string, path: string, _pageLabel: string) => {
  mockAuthContext(makeUser(role))
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route element={<ProtectedRoute allowedRoles={['TRADER', 'OPERATIONS', 'VIEWER']} />}>
          <Route path="/orders" element={<div>Order Blotter Page</div>} />
          <Route path="/portfolio" element={<div>Portfolio Page</div>} />
          <Route path="/audit" element={<div>Audit Trail Page</div>} />
          <Route path="/market" element={<div>Market Data Page</div>} />
        </Route>
        <Route path="/" element={<div>Login Page</div>} />
        <Route path="/dashboard" element={<div>Dashboard Page</div>} />
        <Route path="/admin" element={<div>Admin Panel Page</div>} />
      </Routes>
    </MemoryRouter>
  )
}

describe('Route guards for ADMIN role', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('shouldRedirectAdminFromOrders', () => {
    renderRouteAsRole('ADMIN', '/orders', 'Order Blotter Page')
    expect(screen.getByText('Admin Panel Page')).toBeInTheDocument()
    expect(screen.queryByText('Order Blotter Page')).not.toBeInTheDocument()
  })

  it('shouldRedirectAdminFromPortfolio', () => {
    renderRouteAsRole('ADMIN', '/portfolio', 'Portfolio Page')
    expect(screen.getByText('Admin Panel Page')).toBeInTheDocument()
    expect(screen.queryByText('Portfolio Page')).not.toBeInTheDocument()
  })

  it('shouldRedirectAdminFromAudit', () => {
    renderRouteAsRole('ADMIN', '/audit', 'Audit Trail Page')
    expect(screen.getByText('Admin Panel Page')).toBeInTheDocument()
    expect(screen.queryByText('Audit Trail Page')).not.toBeInTheDocument()
  })

  it('shouldRedirectAdminFromMarket', () => {
    renderRouteAsRole('ADMIN', '/market', 'Market Data Page')
    expect(screen.getByText('Admin Panel Page')).toBeInTheDocument()
    expect(screen.queryByText('Market Data Page')).not.toBeInTheDocument()
  })

  it('shouldAllowTraderOnOrders', () => {
    renderRouteAsRole('TRADER', '/orders', 'Order Blotter Page')
    expect(screen.getByText('Order Blotter Page')).toBeInTheDocument()
    expect(screen.queryByText('Dashboard Page')).not.toBeInTheDocument()
  })

  it('shouldAllowOperationsOnOrders', () => {
    renderRouteAsRole('OPERATIONS', '/orders', 'Order Blotter Page')
    expect(screen.getByText('Order Blotter Page')).toBeInTheDocument()
    expect(screen.queryByText('Dashboard Page')).not.toBeInTheDocument()
  })
})
