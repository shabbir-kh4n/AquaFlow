import { useEffect, useState } from 'react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../../context/useAuth'
import { listAlerts } from '../../api/alertApi'
import Logo from '../Logo'
import { STUB_PAGES } from './adminRoutes'
import './AdminLayout.css'

const NAV_ITEMS = [
  { to: '/admin/dashboard', label: 'Dashboard', icon: 'grid', built: true },
  { to: '/admin/households', label: 'Households Directory', icon: 'home', built: true },
  { to: '/admin/meter-readings', label: 'Meter Readings', icon: 'gauge', built: true },
  { to: '/admin/billing', label: 'Billing Management', icon: 'invoice', built: true },
  { to: '/admin/bulk-purchases', label: 'Bulk Purchases', icon: 'truck', built: true },
  { to: '/admin/reports', label: 'Reports', icon: 'chart', built: false },
  { to: '/admin/water-leakage', label: 'Water Leakage', icon: 'drop-alert', built: true },
  { to: '/admin/support', label: 'Support / Concerns', icon: 'help', built: false },
  { to: '/admin/tariff-plans', label: 'Tariff Plans', icon: 'sliders', built: true },
  { to: '/admin/announcements', label: 'Announcements', icon: 'megaphone', built: false },
  { to: '/admin/profile', label: 'Profile', icon: 'user', built: true },
]

// Static per-route topbar copy for the built pages. Dashboard's title is dynamic
// (greets the admin by name) so it's handled separately below.
const PAGE_META = {
  '/admin/dashboard': { subtitle: "Here's a snapshot of your community." },
  '/admin/households': {
    title: 'Households Directory',
    subtitle: 'Add, update, and manage the residents living in your community.',
  },
  '/admin/meter-readings': {
    title: 'Water Meter Readings Hub',
    subtitle: 'Search for a resident and record their water meter reading.',
  },
  '/admin/tariff-plans': {
    title: 'Tariff Configuration',
    subtitle:
      'Configure tiered rate limits, shared common-area costs, and distribution methods for your apartment community.',
  },
  '/admin/billing': {
    title: 'Billing Management',
    subtitle: 'Open, finalize, and archive billing cycles, and review itemized invoices per household.',
  },
  '/admin/bulk-purchases': {
    title: 'Bulk Purchases',
    subtitle: 'Record tanker deliveries and municipal supply purchases for your community.',
  },
  '/admin/water-leakage': {
    title: 'Water Leakage',
    subtitle: 'Configure threshold and outlier-based leak alerts, and review flagged households.',
  },
  '/admin/profile': {
    title: 'Settings & Profile',
    subtitle: 'Manage your account details and apartment information.',
  },
}

const ICONS = {
  grid: <path d="M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z" />,
  home: <path d="M4 11l8-7 8 7v9a1 1 0 0 1-1 1h-4v-6H9v6H5a1 1 0 0 1-1-1z" />,
  gauge: <path d="M12 3a9 9 0 1 0 9 9M12 12l5-5M12 12h.01M3.5 15.5a9 9 0 0 1-.5-3" />,
  invoice: <path d="M7 3h8l4 4v14H7zM7 3v18M11 8h4M11 12h4M11 16h4" />,
  truck: <path d="M3 7h11v9H3zM14 10h4l3 3v3h-7zM6.5 19a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3zM17.5 19a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3z" />,
  chart: <path d="M4 21V9M10 21V3M16 21v-7M22 21H2" />,
  'drop-alert': <path d="M12 3s7 8 7 12.5A7 7 0 0 1 5 15.5C5 11 12 3 12 3zM12 11v3M12 17h.01" />,
  help: <path d="M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM9.5 9a2.5 2.5 0 1 1 3.7 2.2c-.7.4-1.2 1-1.2 1.8v.3M12 17h.01" />,
  sliders: <path d="M4 6h10M18 6h2M4 12h4M12 12h8M4 18h13M21 18h-2M12 4v4M20 10v4M9 16v4" />,
  megaphone: <path d="M3 11v2a2 2 0 0 0 2 2h1l2 5h2l-1-5h4l6 4V6l-6 4H6a2 2 0 0 0-2 2z" />,
  user: <path d="M12 12a4.5 4.5 0 1 0 0-9 4.5 4.5 0 0 0 0 9zM4 21c1.5-4.5 5-6.5 8-6.5s6.5 2 8 6.5" />,
  search: <path d="M11 19a8 8 0 1 0 0-16 8 8 0 0 0 0 16ZM21 21l-4.35-4.35" />,
  bell: <path d="M18 9a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9ZM10 21h4" />,
  chevronDown: <path d="M6 9l6 6 6-6" />,
}

function Icon({ name }) {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
      {ICONS[name]}
    </svg>
  )
}

function AdminLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [activeAlertCount, setActiveAlertCount] = useState(0)

  useEffect(() => {
    listAlerts('ACTIVE')
      .then((alerts) => setActiveAlertCount(alerts.length))
      .catch(() => setActiveAlertCount(0))
  }, [])

  function handleLogout() {
    logout()
    navigate('/')
  }

  let title = ''
  let subtitle = ''
  if (location.pathname === '/admin/dashboard') {
    title = `Welcome back, ${user?.fullName || 'Admin'}`
    subtitle = PAGE_META['/admin/dashboard'].subtitle
  } else if (PAGE_META[location.pathname]) {
    title = PAGE_META[location.pathname].title
    subtitle = PAGE_META[location.pathname].subtitle
  } else {
    const stub = STUB_PAGES.find((page) => page.path === location.pathname)
    if (stub) {
      title = stub.title
      subtitle = stub.description
    }
  }

  const initials = (user?.fullName || 'Admin')
    .split(' ')
    .map((part) => part[0])
    .slice(0, 2)
    .join('')
    .toUpperCase()

  return (
    <div className="af-admin">
      <aside className="af-admin__sidebar">
        <div className="af-admin__brand">
          <Logo size={32} />
          HydroLedger
        </div>

        <nav className="af-admin__nav">
          {NAV_ITEMS.map((item) =>
            item.built ? (
              <NavLink
                key={item.to}
                to={item.to}
                className={({ isActive }) =>
                  `af-admin__nav-item ${isActive ? 'af-admin__nav-item--active' : ''}`
                }
              >
                <Icon name={item.icon} />
                {item.label}
              </NavLink>
            ) : (
              <button
                key={item.to}
                type="button"
                disabled
                title={`${item.label} isn't built yet`}
                className="af-admin__nav-item af-admin__nav-item--unbuilt"
              >
                <Icon name={item.icon} />
                {item.label}
              </button>
            ),
          )}
        </nav>

        <div className="af-admin__sidebar-footer">
          <span className="af-admin__avatar">{initials}</span>
          <div className="af-admin__account-text">
            <strong>{user?.fullName || 'Admin'}</strong>
            <span>{user?.communityName || 'Community Admin'}</span>
          </div>
          <button type="button" className="af-admin__logout" onClick={handleLogout} title="Log out">
            <span aria-hidden="true">↪</span>
          </button>
        </div>
      </aside>

      <div className="af-admin__main">
        <header className="af-admin__topbar">
          <div>
            <h1>{title}</h1>
            {subtitle && <p>{subtitle}</p>}
          </div>

          <div className="af-admin__account">
            <label className="af-admin__search">
              <Icon name="search" />
              <input type="search" placeholder="Search anything…" aria-label="Search" />
            </label>
            <button
              type="button"
              className="af-admin__icon-button"
              aria-label="Notifications"
              title="Notifications"
              onClick={() => navigate('/admin/water-leakage')}
            >
              <Icon name="bell" />
              {activeAlertCount > 0 && <b>{activeAlertCount}</b>}
            </button>
            {user?.communityName && (
              <button type="button" className="af-admin__community-pill" title={user.communityName}>
                <span>{user.communityName}</span>
                <Icon name="chevronDown" />
              </button>
            )}
          </div>
        </header>

        <main className="af-admin__content">
          <Outlet />
        </main>
      </div>
    </div>
  )
}

export default AdminLayout
