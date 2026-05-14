import React from 'react'
import { NavLink } from 'react-router-dom'
import {
  LayoutDashboard,
  FilePlus,
  ClipboardList,
  LineChart,
  Briefcase,
  ScrollText,
  Settings,
} from 'lucide-react'
import { useAuth } from '../context/AuthContext'

interface SidebarProps {
  collapsed: boolean
}

const ICON = 18

interface NavItem {
  to: string
  icon: React.ReactNode
  label: string
  end?: boolean
  roles?: string[]
}

interface NavGroup {
  label: string
  items: NavItem[]
}

const GROUPS: NavGroup[] = [
  {
    label: 'Main',
    items: [
      { to: '/dashboard', icon: <LayoutDashboard size={ICON} />, label: 'Dashboard' },
    ],
  },
  {
    label: 'Trading',
    items: [
      { to: '/orders/new', icon: <FilePlus size={ICON} />, label: 'New Order', roles: ['TRADER'] },
      { to: '/orders', icon: <ClipboardList size={ICON} />, label: 'Order Blotter', end: true },
    ],
  },
  {
    label: 'Market & Portfolio',
    items: [
      { to: '/market', icon: <LineChart size={ICON} />, label: 'Market Data' },
      { to: '/portfolio', icon: <Briefcase size={ICON} />, label: 'Portfolio' },
    ],
  },
  {
    label: 'Compliance',
    items: [
      { to: '/audit', icon: <ScrollText size={ICON} />, label: 'Audit Trail' },
    ],
  },
]

const ADMIN_GROUP: NavGroup = {
  label: 'Admin',
  items: [
    { to: '/admin', icon: <Settings size={ICON} />, label: 'Admin Panel' },
  ],
}

const Sidebar: React.FC<SidebarProps> = ({ collapsed }) => {
  const { user } = useAuth()
  const userRole = user?.role ?? ''
  const allGroups = userRole === 'ADMIN' ? [GROUPS[0], ADMIN_GROUP] : GROUPS
  const visibleGroups = allGroups.map(group => ({
    ...group,
    items: group.items.filter(item => !item.roles || item.roles.includes(userRole)),
  }))

  return (
    <aside
      className={`sidebar${collapsed ? ' collapsed' : ''}`}
      style={{
        background: 'var(--color-nav)',
        height: 'calc(100vh - 52px)',
        position: 'sticky',
        top: '52px',
        flexShrink: 0,
        display: 'flex',
        flexDirection: 'column',
        overflowX: 'hidden',
        overflowY: 'auto',
        paddingBottom: '16px',
        paddingTop: '6px',
        borderRight: '1px solid rgba(255,255,255,0.08)',
      }}
    >
      {visibleGroups.map((group) => (
        <div key={group.label}>
          <div className="sidebar-group-header">{group.label}</div>
          {group.items.map(({ to, icon, label, end }) => (
            <NavLink
              key={to}
              to={to}
              end={end}
              className={({ isActive }) => `sidebar-link${isActive ? ' active' : ''}`}
              style={{ margin: '1px 8px', width: 'calc(100% - 16px)', display: 'flex' }}
              title={collapsed ? label : undefined}
            >
              <span style={{ flexShrink: 0, display: 'flex', alignItems: 'center' }}>{icon}</span>
              <span className="sidebar-label">{label}</span>
            </NavLink>
          ))}
        </div>
      ))}
    </aside>
  )
}

export default Sidebar
