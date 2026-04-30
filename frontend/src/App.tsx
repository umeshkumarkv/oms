import React, { useState } from 'react'
import { BrowserRouter, Navigate, Outlet, Route, Routes } from 'react-router-dom'
import { AuthProvider, useAuth } from './context/AuthContext'
import NavBar from './components/NavBar'
import Sidebar from './components/Sidebar'
import LoginPage from './pages/LoginPage'
import DashboardPage from './pages/DashboardPage'
import OrderBlotterPage from './pages/OrderBlotterPage'
import PortfolioPage from './pages/PortfolioPage'
import AuditTrailPage from './pages/AuditTrailPage'
import OrderEntryPage from './pages/OrderEntryPage'
import AdminPanelPage from './pages/AdminPanelPage'
import MarketDataPage from './pages/MarketDataPage'

// Redirects unauthenticated users to / (login)
const ProtectedRoute: React.FC = () => {
  const { user } = useAuth()
  return user ? <Outlet /> : <Navigate to="/" replace />
}

const SIDEBAR_KEY = 'oms-sidebar-collapsed'

// Layout wrapper: NavBar + sidebar + page content (authenticated only)
const AppLayout: React.FC = () => {
  const [collapsed, setCollapsed] = useState<boolean>(
    () => localStorage.getItem(SIDEBAR_KEY) === 'true'
  )

  const toggleSidebar = () => {
    setCollapsed((prev) => {
      const next = !prev
      localStorage.setItem(SIDEBAR_KEY, String(next))
      return next
    })
  }

  return (
    <div style={{ minHeight: '100vh', background: '#f5f6fa', display: 'flex', flexDirection: 'column' }}>
      <NavBar onBurgerClick={toggleSidebar} sidebarCollapsed={collapsed} />
      <div style={{ display: 'flex', flex: 1 }}>
        <Sidebar collapsed={collapsed} />
        <main style={{ flex: 1, minWidth: 0 }}>
          <Outlet />
        </main>
      </div>
    </div>
  )
}

const AppRoutes: React.FC = () => (
  <Routes>
    {/* Login — no nav bar, no auth required */}
    <Route path="/" element={<LoginPage />} />

    {/* Authenticated pages — redirect to / if no token */}
    <Route element={<ProtectedRoute />}>
      <Route element={<AppLayout />}>
        <Route path="/dashboard" element={<DashboardPage />} />
        <Route path="/orders" element={<OrderBlotterPage />} />
        <Route path="/orders/new" element={<OrderEntryPage />} />
        <Route path="/portfolio" element={<PortfolioPage />} />
        <Route path="/audit" element={<AuditTrailPage />} />
        <Route path="/admin" element={<AdminPanelPage />} />
        <Route path="/market" element={<MarketDataPage />} />
      </Route>
    </Route>
  </Routes>
)

const App: React.FC = () => (
  <BrowserRouter>
    <AuthProvider>
      <AppRoutes />
    </AuthProvider>
  </BrowserRouter>
)

export default App