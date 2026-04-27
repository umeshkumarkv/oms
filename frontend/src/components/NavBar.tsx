import React, { useEffect, useState } from 'react'
import { NavLink, useNavigate } from 'react-router-dom'

// Compute market status based on current America/New_York time
const getMarketStatus = (): { open: boolean; label: string } => {
  const etNow = new Date(new Date().toLocaleString('en-US', { timeZone: 'America/New_York' }))
  const day = etNow.getDay() // 0=Sun, 6=Sat
  const mins = etNow.getHours() * 60 + etNow.getMinutes()
  const open = day >= 1 && day <= 5 && mins >= 9 * 60 + 30 && mins < 16 * 60
  return { open, label: open ? 'OPEN' : 'CLOSED' }
}

const navStyle: React.CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'space-between',
  background: '#1a2236',
  padding: '0 24px',
  height: '52px',
  boxShadow: '0 2px 6px rgba(0,0,0,0.25)',
  position: 'sticky',
  top: 0,
  zIndex: 100,
}

const logoStyle: React.CSSProperties = {
  color: '#fff',
  fontWeight: 700,
  fontSize: '16px',
  letterSpacing: '0.03em',
  textDecoration: 'none',
  whiteSpace: 'nowrap',
}

const linksStyle: React.CSSProperties = {
  display: 'flex',
  gap: '4px',
  alignItems: 'center',
}

const linkStyle: React.CSSProperties = {
  color: '#9ca3af',
  textDecoration: 'none',
  fontSize: '13px',
  fontWeight: 500,
  padding: '6px 12px',
  borderRadius: '6px',
  transition: 'background 0.15s, color 0.15s',
}

const activeLinkStyle: React.CSSProperties = {
  ...linkStyle,
  color: '#fff',
  background: 'rgba(255,255,255,0.1)',
}

const rightStyle: React.CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  gap: '16px',
}

const NavBar: React.FC = () => {
  const navigate = useNavigate()
  const [market, setMarket] = useState(getMarketStatus())

  // Refresh market status every minute
  useEffect(() => {
    const id = setInterval(() => setMarket(getMarketStatus()), 60_000)
    return () => clearInterval(id)
  }, [])

  // TODO: replace with real user from auth context — JIRA SCRUM-16
  const user = { name: 'trader.john', role: 'TRADER' }

  return (
    <nav style={navStyle}>
      {/* Logo */}
      <NavLink to="/dashboard" style={logoStyle}>Broker OMS</NavLink>

      {/* Centre links */}
      <div style={linksStyle}>
        {[
          { to: '/dashboard', label: 'Dashboard' },
          { to: '/orders', label: 'Blotter' },
          { to: '/market', label: 'Market Data' },
          { to: '/portfolio', label: 'Portfolio' },
        ].map(({ to, label }) => (
          <NavLink
            key={to}
            to={to}
            style={({ isActive }) => (isActive ? activeLinkStyle : linkStyle)}
          >
            {label}
          </NavLink>
        ))}
      </div>

      {/* New Order CTA — visible from every page */}
      <button
        onClick={() => navigate('/orders/new')}
        style={{
          padding: '7px 16px', borderRadius: '6px', border: 'none',
          background: '#6366f1', color: '#fff',
          fontSize: '13px', fontWeight: 600, cursor: 'pointer',
          display: 'flex', alignItems: 'center', gap: '6px',
          transition: 'background 0.15s',
        }}
        onMouseEnter={(e) => { (e.currentTarget as HTMLButtonElement).style.background = '#4f46e5' }}
        onMouseLeave={(e) => { (e.currentTarget as HTMLButtonElement).style.background = '#6366f1' }}
      >
        + New Order
      </button>

      {/* Right: market status + user */}
      <div style={rightStyle}>
        {/* Market status pill */}
        <span style={{
          padding: '3px 10px',
          borderRadius: '12px',
          fontSize: '11px',
          fontWeight: 700,
          background: market.open ? 'rgba(16,185,129,0.2)' : 'rgba(156,163,175,0.2)',
          color: market.open ? '#10b981' : '#9ca3af',
          border: `1px solid ${market.open ? '#10b981' : '#6b7280'}`,
        }}>
          {market.label}
        </span>

        {/* Notification bell */}
        <span style={{ color: '#9ca3af', fontSize: '18px', cursor: 'pointer' }} title="Notifications">
          🔔
        </span>

        {/* User info */}
        <span style={{ fontSize: '13px', color: '#d1d5db' }}>
          {user.name}{' '}
          <span style={{
            fontSize: '10px', fontWeight: 700, padding: '2px 6px',
            borderRadius: '4px', background: 'rgba(99,102,241,0.3)', color: '#a5b4fc',
          }}>
            {user.role}
          </span>
        </span>
      </div>
    </nav>
  )
}

export default NavBar
