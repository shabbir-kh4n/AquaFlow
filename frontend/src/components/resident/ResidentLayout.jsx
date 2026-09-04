import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../../context/useAuth'
import Logo from '../Logo'
import { Icon } from './Icon'
import '../../pages/resident/ResidentHome.css'

const NAV_ITEMS = [
  { label: 'Dashboard', icon: 'grid', to: '/resident/home' },
  { label: 'Usage History', icon: 'drop', to: '/resident/usage-history' },
  { label: 'My Bills', icon: 'bill', to: '/resident/billing' },
  { label: 'Reports', icon: 'chart' },
  { label: 'Support / Concerns', icon: 'help' },
  { label: 'Notifications', icon: 'bell', badge: 3 },
  { label: 'Profile', icon: 'user' },
]

// Per-route topbar copy. The Dashboard's title is dynamic (greets the resident
// by name) so it's handled separately below, mirroring AdminLayout's PAGE_META.
const PAGE_META = {
  '/resident/usage-history': {
    eyebrow: 'Usage insights',
    title: 'Your usage history',
  },
  '/resident/billing': {
    eyebrow: 'Billing',
    title: 'Billing & Invoices',
  },
}

function ResidentLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  function handleLogout() {
    logout()
    navigate('/')
  }

  const meta = PAGE_META[location.pathname]
  const title = meta?.title || 'Resident Dashboard'
  const initials = (user?.fullName || 'Resident')
    .split(' ')
    .map((part) => part[0])
    .slice(0, 2)
    .join('')
    .toUpperCase()

  return (
    <div className="af-resident">
      <aside className="af-resident__sidebar">
        <div className="af-resident__brand">
          <Logo size={32} />
          <span>HydroLedger</span>
        </div>

        <nav className="af-resident__nav" aria-label="Resident navigation">
          {NAV_ITEMS.map((item) =>
            item.to ? (
              <NavLink
                key={item.label}
                to={item.to}
                className={({ isActive }) =>
                  `af-resident__nav-item ${isActive ? 'af-resident__nav-item--active' : ''}`
                }
              >
                <Icon name={item.icon} />
                <span>{item.label}</span>
                {item.badge && <b>{item.badge}</b>}
              </NavLink>
            ) : (
              <button
                key={item.label}
                type="button"
                className="af-resident__nav-item"
                disabled
                title={`${item.label} is coming soon`}
              >
                <Icon name={item.icon} />
                <span>{item.label}</span>
                {item.badge && <b>{item.badge}</b>}
              </button>
            ),
          )}
        </nav>

        <div className="af-resident__sidebar-footer">
          <button type="button" className="af-resident__profile" title="Profile">
            <span className="af-resident__avatar" aria-hidden="true">{initials}</span>
            <span className="af-resident__profile-text">
              <strong>{user?.fullName || 'Resident'}</strong>
              <span>{user?.houseNumber ? `Unit ${user.houseNumber}` : 'Resident'}</span>
            </span>
          </button>
          <button type="button" className="af-resident__logout" onClick={handleLogout} title="Log out">
            <span aria-hidden="true">↪</span>
          </button>
        </div>
      </aside>

      <main className="af-resident__main">
        <header className="af-resident__topbar">
          <h1>{title}</h1>

          <div className="af-resident__topbar-actions">
            <label className="af-resident__search">
              <Icon name="search" />
              <input type="search" placeholder="Search analytics, bills…" aria-label="Search" />
            </label>
            <button type="button" className="af-resident__icon-button" aria-label="Notifications" title="Notifications">
              <Icon name="bell" />
              <b>2</b>
            </button>
            {user?.communityName && (
              <button type="button" className="af-resident__community-pill" title={user.communityName}>
                <span>{user.communityName}</span>
                <Icon name="chevronDown" />
              </button>
            )}
          </div>
        </header>

        <section className="af-resident__content">
          <Outlet />
        </section>
      </main>
    </div>
  )
}

export default ResidentLayout
