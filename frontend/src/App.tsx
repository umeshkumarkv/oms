import React from 'react'
import { BrowserRouter, Routes, Route, Outlet } from 'react-router-dom'
import NavBar from './components/NavBar'
import LoginPage from './pages/LoginPage'
import DashboardPage from './pages/DashboardPage'
import OrderBlotterPage from './pages/OrderBlotterPage'
import PortfolioPage from './pages/PortfolioPage'
import AuditTrailPage from './pages/AuditTrailPage'

// Layout wrapper: NavBar + page content
// TODO: add ProtectedRoute (redirect to / if no JWT) — JIRA SCRUM-18
const AppLayout: React.FC = () => (
  <div style={{ minHeight: '100vh', background: '#f5f6fa' }}>
    <NavBar />
    <Outlet />
  </div>
)

const App: React.FC = () => {
  return (
    <BrowserRouter>
      <Routes>
        {/* Login — no nav bar */}
        <Route path="/" element={<LoginPage />} />

        {/* Authenticated pages — nav bar shown */}
        <Route element={<AppLayout />}>
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/orders" element={<OrderBlotterPage />} />
          <Route path="/portfolio" element={<PortfolioPage />} />
          <Route path="/audit" element={<AuditTrailPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}

export default App