import React from 'react'
import { BrowserRouter, Navigate, Outlet, Route, Routes } from 'react-router-dom'
import { AuthProvider, useAuth } from './context/AuthContext'
import NavBar from './components/NavBar'
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

// Layout wrapper: NavBar + page content (authenticated only)
const AppLayout: React.FC = () => (
  <div style={{ minHeight: '100vh', background: '#f5f6fa' }}>
    <NavBar />
    <Outlet />
  </div>
)

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