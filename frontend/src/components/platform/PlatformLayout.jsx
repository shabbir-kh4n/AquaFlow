import { useEffect, useState } from 'react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../../context/useAuth'
import Logo from '../Logo'
import ChatWidget from '../chat/ChatWidget'
import LanguageSwitcher from '../LanguageSwitcher'
import './PlatformLayout.css'

// labelKey/titleKey/subtitleKey values are translation keys (common namespace), resolved
// with t() at render time.
const NAV_ITEMS = [
  { to: '/platform/dashboard', labelKey: 'platformLayout.nav.dashboard', icon: 'grid' },
  { to: '/platform/communities', labelKey: 'platformLayout.nav.communities', icon: 'buildings' },
  { to: '/platform/reports', labelKey: 'platformLayout.nav.reports', icon: 'chart' },
  { to: '/platform/support-tickets', labelKey: 'platformLayout.nav.supportTickets', icon: 'help' },
  { to: '/platform/system-status', labelKey: 'platformLayout.nav.systemStatus', icon: 'pulse' },
]

const PAGE_META = {
  '/platform/dashboard': { subtitleKey: 'platformLayout.pages.dashboard.subtitle' },
  '/platform/communities': {
    titleKey: 'platformLayout.pages.communities.title',
    subtitleKey: 'platformLayout.pages.communities.subtitle',
  },
  '/platform/reports': {
    titleKey: 'platformLayout.pages.reports.title',
    subtitleKey: 'platformLayout.pages.reports.subtitle',
  },
  '/platform/support-tickets': {
    titleKey: 'platformLayout.pages.supportTickets.title',
    subtitleKey: 'platformLayout.pages.supportTickets.subtitle',
  },
  '/platform/system-status': {
    titleKey: 'platformLayout.pages.systemStatus.title',
    subtitleKey: 'platformLayout.pages.systemStatus.subtitle',
  },
}

const ICONS = {
  grid: <path d="M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z" />,
  buildings: <path d="M4 21V7l6-4 6 4v14M4 21h16M9 9h2M9 13h2M13 9h2M13 13h2M9 21v-4h4v4" />,
  chart: <path d="M4 21V9M10 21V3M16 21v-7M22 21H2" />,
  pulse: <path d="M3 12h4l2-7 4 14 2-7h6" />,
  help: <path d="M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM9.5 9a2.5 2.5 0 1 1 3.7 2.2c-.7.4-1.2 1-1.2 1.8v.3M12 17h.01" />,
  search: <path d="M11 19a8 8 0 1 0 0-16 8 8 0 0 0 0 16ZM21 21l-4.35-4.35" />,
  bell: <path d="M18 9a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9ZM10 21h4" />,
  menu: <path d="M4 6h16M4 12h16M4 18h16" />,
  close: <path d="M6 6l12 12M18 6L6 18" />,
}

function Icon({ name }) {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
      {ICONS[name]}
    </svg>
  )
}

function PlatformLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const { t } = useTranslation('common')
  const [navOpen, setNavOpen] = useState(false)
  const [lastPathname, setLastPathname] = useState(location.pathname)

  // Collapse the mobile drawer whenever the route changes so it never stays open over a new
  // page. Adjusted during render rather than in a useEffect, per this project's
  // react-hooks/set-state-in-effect rule.
  if (location.pathname !== lastPathname) {
    setLastPathname(location.pathname)
    setNavOpen(false)
  }

  useEffect(() => {
    document.body.style.overflow = navOpen ? 'hidden' : ''
    return () => {
      document.body.style.overflow = ''
    }
  }, [navOpen])

  function handleLogout() {
    logout()
    navigate('/')
  }

  let title = ''
  let subtitle = ''
  if (location.pathname === '/platform/dashboard') {
    title = t('shell.welcomeBack', { name: user?.fullName || t('platformLayout.fallbackName') })
    subtitle = t(PAGE_META['/platform/dashboard'].subtitleKey)
  } else if (PAGE_META[location.pathname]) {
    title = t(PAGE_META[location.pathname].titleKey)
    subtitle = t(PAGE_META[location.pathname].subtitleKey)
  }

  const initials = (user?.fullName || t('platformLayout.fallbackName'))
    .split(' ')
    .map((part) => part[0])
    .slice(0, 2)
    .join('')
    .toUpperCase()

  return (
    <div className="af-platform">
      {navOpen && (
        <div className="af-platform__backdrop" onClick={() => setNavOpen(false)} aria-hidden="true" />
      )}

      <aside className={`af-platform__sidebar${navOpen ? ' af-platform__sidebar--open' : ''}`}>
        <div className="af-platform__brand">
          <Logo size={32} />
          HydroLedger
          <button
            type="button"
            className="af-platform__nav-close"
            aria-label={t('shell.closeMenu')}
            onClick={() => setNavOpen(false)}
          >
            <Icon name="close" />
          </button>
        </div>

        <nav className="af-platform__nav">
          {NAV_ITEMS.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) =>
                `af-platform__nav-item ${isActive ? 'af-platform__nav-item--active' : ''}`
              }
            >
              <Icon name={item.icon} />
              {t(item.labelKey)}
            </NavLink>
          ))}
        </nav>

        <div className="af-platform__sidebar-footer">
          <span className="af-platform__avatar">{initials}</span>
          <div className="af-platform__account-text">
            <strong>{user?.fullName || t('platformLayout.fallbackName')}</strong>
            <span>{t('platformLayout.platformOwner')}</span>
          </div>
          <button type="button" className="af-platform__logout" onClick={handleLogout} title={t('shell.logout')}>
            <span aria-hidden="true">↪</span>
          </button>
        </div>
      </aside>

      <div className="af-platform__main">
        <header className="af-platform__topbar">
          <div className="af-platform__topbar-start">
            <button
              type="button"
              className="af-platform__hamburger"
              aria-label={t('shell.openMenu')}
              onClick={() => setNavOpen(true)}
            >
              <Icon name="menu" />
            </button>
            <div>
              <h1>{title}</h1>
              {subtitle && <p>{subtitle}</p>}
            </div>
          </div>

          <div className="af-platform__account">
            <label className="af-platform__search">
              <Icon name="search" />
              <input type="search" placeholder={t('platformLayout.searchPlaceholder')} aria-label={t('shell.search')} />
            </label>
            <button
              type="button"
              className="af-platform__icon-button"
              aria-label={t('shell.notifications')}
              title={t('shell.notifications')}
            >
              <Icon name="bell" />
            </button>
            <LanguageSwitcher variant="topbar" />
            <span className="af-platform__badge-pill" title={t('platformLayout.platformOwner')}>
              {t('platformLayout.platformOwner')}
            </span>
          </div>
        </header>

        <main className="af-platform__content">
          <Outlet />
        </main>
      </div>

      <ChatWidget endpoint="/platform/chat" persona="platform" />
    </div>
  )
}

export default PlatformLayout
