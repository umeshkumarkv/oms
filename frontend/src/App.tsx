import React from 'react'
import { BrowserRouter, Routes, Route } from 'react-router-dom'
import LoginPage from './pages/LoginPage'
import DashboardPage from './pages/DashboardPage'
import OrderBlotterPage from './pages/OrderBlotterPage'
import PortfolioPage from './pages/PortfolioPage'
import AuditTrailPage from './pages/AuditTrailPage'

// TODO: implement ProtectedRoute component — JIRA story BD-XX
const App: React.FC = () => {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<LoginPage />} />
        <Route path="/dashboard" element={<DashboardPage />} />
        <Route path="/orders" element={<OrderBlotterPage />} />
        <Route path="/portfolio" element={<PortfolioPage />} />
        <Route path="/audit" element={<AuditTrailPage />} />
      </Routes>
    </BrowserRouter>
  )
}

export default App