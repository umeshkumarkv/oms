import React, { useEffect, useState } from 'react'
import { NavLink, useNavigate } from 'react-router-dom'
import { Bell, Menu, Plus, TrendingUp, X } from 'lucide-react'
import { useAuth } from '../context/AuthContext'

const getMarketStatus = (): { open: boolean; label: string } => {
  const etNow = new Date(new Date().toLocaleString('en-US', { timeZone: 'America/New_York' }))
  const day  = etNow.getDay()
  const mins = etNow.getHours() * 60 + etNow.getMinutes()
  const open = day >= 1 && day <= 5 && mins >= 9 * 60 + 30 && mins < 16 * 60
  return { open, label: open ? 'OPEN' : 'CLOSED' }
}

const getInitials = (name: string): string => {
  const parts = name.trim().split(/\s+/)
  if (parts.length >= 2) return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase()
  return name.slice(0, 2).toUpperCase() || 'U'
}

interface NavBarProps {
  onBurgerClick: () => void
  sidebarCollapsed: boolean
}

const NavBar: React.FC<NavBarProps> = ({ onBurgerClick, sidebarCollapsed }) => {
  const navigate = useNavigate()
  const [market, setMarket] = useState(getMarketStatus())
  const { user: authUser, logout } = useAuth()

  useEffect(() => {
    const id = setInterval(() => setMarket(getMarketStatus()), 60_000)
    return () => clearInterval(id)
  }, [])

  const displayName = authUser?.displayName ?? ''
  const role        = authUser?.role ?? ''
  const initials    = getInitials(displayName || 'User')

  return (
    <nav style={{
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'space-between',
      background: 'var(--color-nav)',
      padding: '0 24px',
      height: '52px',
      boxShadow: '0 1px 0 rgba(255,255,255,0.06)',
      position: 'sticky',
      top: 0,
      zIndex: 100,
    }}>

      {/* Left: burger toggle + logo */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '12px', flexShrink: 0 }}>
        <button
          onClick={onBurgerClick}
          aria-label={sidebarCollapsed ? 'Expand sidebar' : 'Collapse sidebar'}
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            width: '32px',
            height: '32px',
            borderRadius: 'var(--radius-md)',
            border: 'none',
            background: 'rgba(255,255,255,0.07)',
            color: 'rgba(255,255,255,0.5)',
            cursor: 'pointer',
            flexShrink: 0,
            transition: 'background 0.15s, color 0.15s',
          }}
          onMouseEnter={e => {
            const el = e.currentTarget as HTMLButtonElement
            el.style.background = 'rgba(255,255,255,0.12)'
            el.style.color = '#fff'
          }}
          onMouseLeave={e => {
            const el = e.currentTarget as HTMLButtonElement
            el.style.background = 'rgba(255,255,255,0.07)'
            el.style.color = 'rgba(255,255,255,0.5)'
          }}
        >
          {sidebarCollapsed ? <Menu size={16} /> : <X size={16} />}
        </button>

        <NavLink
          to="/dashboard"
          style={{
            display: 'flex', alignItems: 'center', gap: '9px',
            textDecoration: 'none', color: '#fff', flexShrink: 0,
          }}
        >
          <div style={{
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            width: '28px', height: '28px',
            borderRadius: 'var(--radius-md)',
            background: 'var(--color-primary)',
          }}>
            <TrendingUp size={15} strokeWidth={2.5} />
          </div>
          <span style={{ fontWeight: 700, fontSize: '15px', letterSpacing: '-0.01em' }}>
            Broker OMS
          </span>
        </NavLink>
      </div>

      {/* Right section */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '12px', flexShrink: 0 }}>

        {/* New Order CTA */}
        <button
          onClick={() => navigate('/orders/new')}
          style={{
            display: 'inline-flex', alignItems: 'center', gap: '5px',
            padding: '6px 14px',
            borderRadius: 'var(--radius-md)',
            border: 'none',
            background: 'var(--color-primary)',
            color: '#fff',
            fontSize: '13px', fontWeight: 600, cursor: 'pointer',
            transition: 'background 0.15s',
            flexShrink: 0,
          }}
          onMouseEnter={e => { (e.currentTarget as HTMLButtonElement).style.background = 'var(--color-primary-dark)' }}
          onMouseLeave={e => { (e.currentTarget as HTMLButtonElement).style.background = 'var(--color-primary)' }}
        >
          <Plus size={14} strokeWidth={2.5} />
          New Order
        </button>

        {/* Market status pill */}
        <span style={{
          display: 'inline-flex', alignItems: 'center', gap: '5px',
          padding: '3px 10px',
          borderRadius: 'var(--radius-pill)',
          fontSize: '11px', fontWeight: 700,
          background: market.open ? 'rgba(52,211,153,0.12)' : 'rgba(156,163,175,0.1)',
          color:      market.open ? '#34d399'                : '#9ca3af',
          border:    `1px solid ${market.open ? 'rgba(52,211,153,0.35)' : 'rgba(156,163,175,0.25)'}`,
          flexShrink: 0,
        }}>
          <span
            className={market.open ? 'pulse-dot' : undefined}
            style={{
              width: '6px', height: '6px', borderRadius: '50%',
              background: market.open ? '#34d399' : '#6b7280',
              flexShrink: 0,
            }}
          />
          {market.label}
        </span>

        {/* Notification bell */}
        <span title="Notifications" style={{ display: 'flex', alignItems: 'center', cursor: 'pointer' }}>
          <Bell size={16} strokeWidth={1.75} style={{ color: 'rgba(255,255,255,0.4)', flexShrink: 0 }} />
        </span>

        {/* User initials avatar + name + role */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <div style={{
            width: '30px', height: '30px',
            borderRadius: 'var(--radius-pill)',
            background: 'rgba(99,102,241,0.3)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            fontSize: '11px', fontWeight: 700, color: '#c7d2fe',
            flexShrink: 0,
            border: '1px solid rgba(99,102,241,0.4)',
          }}>
            {initials}
          </div>
          <span style={{ fontSize: '13px', color: 'rgba(255,255,255,0.7)', whiteSpace: 'nowrap' }}>
            {displayName}
          </span>
          {role && (
            <span style={{
              fontSize: '10px', fontWeight: 700,
              padding: '2px 6px',
              borderRadius: 'var(--radius-sm)',
              background: 'rgba(99,102,241,0.22)',
              color: '#a5b4fc',
              whiteSpace: 'nowrap',
            }}>
              {role}
            </span>
          )}
        </div>

        {/* Sign out */}
        <button
          onClick={logout}
          style={{
            padding: '5px 12px',
            borderRadius: 'var(--radius-md)',
            border: '1px solid rgba(255,255,255,0.12)',
            background: 'transparent',
            color: 'rgba(255,255,255,0.45)',
            fontSize: '12px', cursor: 'pointer',
            transition: 'color 0.15s, border-color 0.15s',
            whiteSpace: 'nowrap',
          }}
          onMouseEnter={e => {
            const b = e.currentTarget as HTMLButtonElement
            b.style.color = '#fff'
            b.style.borderColor = 'rgba(255,255,255,0.3)'
          }}
          onMouseLeave={e => {
            const b = e.currentTarget as HTMLButtonElement
            b.style.color = 'rgba(255,255,255,0.45)'
            b.style.borderColor = 'rgba(255,255,255,0.12)'
          }}
        >
          Sign out
        </button>
      </div>
    </nav>
  )
}

export default NavBar
