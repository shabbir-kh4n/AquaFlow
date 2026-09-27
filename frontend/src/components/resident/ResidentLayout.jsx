import { useEffect, useState } from 'react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../../context/useAuth'
import { getMyAlerts } from '../../api/alertApi'
import Logo from '../Logo'
import { Icon } from './Icon'
import ChatWidget from '../chat/ChatWidget'
import LanguageSwitcher from '../LanguageSwitcher'
import '../../pages/resident/ResidentHome.css'

// labelKey values are translation keys (common namespace), resolved with t() at render time.
const NAV_ITEMS = [
  { labelKey: 'residentLayout.nav.dashboard', icon: 'grid', to: '/resident/home' },
  { labelKey: 'residentLayout.nav.usageHistory', icon: 'drop', to: '/resident/usage-history' },
  { labelKey: 'residentLayout.nav.bills', icon: 'bill', to: '/resident/billing' },
  { labelKey: 'residentLayout.nav.recommendations', icon: 'bulb', to: '/resident/recommendations' },
  { labelKey: 'residentLayout.nav.reports', icon: 'chart' },
  { labelKey: 'residentLayout.nav.support', icon: 'help', to: '/resident/support' },
  { labelKey: 'residentLayout.nav.notifications', icon: 'bell', to: '/resident/notifications' },
  { labelKey: 'residentLayout.nav.profile', icon: 'user' },
]

// Per-route topbar copy (translation keys). The Dashboard's title is dynamic (greets the
// resident by name) so it's handled separately below, mirroring AdminLayout's PAGE_META.
// (eyebrow isn't rendered by the layout, so it's left as plain English.)
const PAGE_META = {
  '/resident/usage-history': {
    eyebrow: 'Usage insights',
    titleKey: 'residentLayout.pages.usageHistory',
  },
  '/resident/billing': {
    eyebrow: 'Billing',
    titleKey: 'residentLayout.pages.billing',
  },
  '/resident/notifications': {
    eyebrow: 'Alerts',
    titleKey: 'residentLayout.pages.notifications',
  },
  '/resident/recommendations': {
    eyebrow: 'Personalized for you',
    titleKey: 'residentLayout.pages.recommendations',
  },
  '/resident/support': {
    eyebrow: 'Support',
    titleKey: 'residentLayout.pages.support',
  },
}

function ResidentLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const { t } = useTranslation('common')
  const [activeAlertCount, setActiveAlertCount] = useState(0)
  const [navOpen, setNavOpen] = useState(false)
  const [lastPathname, setLastPathname] = useState(location.pathname)

  useEffect(() => {
    getMyAlerts()
      .then((alerts) => setActiveAlertCount(alerts.filter((alert) => alert.status === 'ACTIVE').length))
      .catch(() => setActiveAlertCount(0))
  }, [])

  // Collapse the mobile drawer whenever the route changes (navigating via the sidebar,
  // browser back/forward, etc.) so it never stays open over a new page. Adjusted during
  // render rather than in a useEffect, per this project's react-hooks/set-state-in-effect rule.
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

  const meta = PAGE_META[location.pathname]
  const title = meta ? t(meta.titleKey) : t('residentLayout.defaultTitle')
  const initials = (user?.fullName || t('residentLayout.fallbackName'))
    .split(' ')
    .map((part) => part[0])
    .slice(0, 2)
    .join('')
    .toUpperCase()

  return (
    <div className="af-resident">
      {navOpen && (
        <div
          className="af-resident__backdrop"
          onClick={() => setNavOpen(false)}
          aria-hidden="true"
        />
      )}

      <aside className={`af-resident__sidebar${navOpen ? ' af-resident__sidebar--open' : ''}`}>
        <div className="af-resident__brand">
          <Logo size={32} />
          <span>HydroLedger</span>
          <button
            type="button"
            className="af-resident__nav-close"
            aria-label={t('shell.closeMenu')}
            onClick={() => setNavOpen(false)}
          >
            <Icon name="close" />
          </button>
        </div>

        <nav className="af-resident__nav" aria-label={t('residentLayout.navLabel')}>
          {NAV_ITEMS.map((item) =>
            item.to ? (
              <NavLink
                key={item.labelKey}
                to={item.to}
                className={({ isActive }) =>
                  `af-resident__nav-item ${isActive ? 'af-resident__nav-item--active' : ''}`
                }
              >
                <Icon name={item.icon} />
                <span>{t(item.labelKey)}</span>
                {item.badge && <b>{item.badge}</b>}
              </NavLink>
            ) : (
              <button
                key={item.labelKey}
                type="button"
                className="af-resident__nav-item"
                disabled
                title={t('residentLayout.comingSoon', { label: t(item.labelKey) })}
              >
                <Icon name={item.icon} />
                <span>{t(item.labelKey)}</span>
                {item.badge && <b>{item.badge}</b>}
              </button>
            ),
          )}
        </nav>

        <div className="af-resident__sidebar-footer">
          <button type="button" className="af-resident__profile" title={t('residentLayout.profile')}>
            <span className="af-resident__avatar" aria-hidden="true">{initials}</span>
            <span className="af-resident__profile-text">
              <strong>{user?.fullName || t('residentLayout.fallbackName')}</strong>
              <span>
                {user?.houseNumber
                  ? t('residentLayout.unit', { number: user.houseNumber })
                  : t('residentLayout.fallbackName')}
              </span>
            </span>
          </button>
          <button type="button" className="af-resident__logout" onClick={handleLogout} title={t('shell.logout')}>
            <span aria-hidden="true">↪</span>
          </button>
        </div>
      </aside>

      <main className="af-resident__main">
        <header className="af-resident__topbar">
          <div className="af-resident__topbar-start">
            <button
              type="button"
              className="af-resident__hamburger"
              aria-label={t('shell.openMenu')}
              onClick={() => setNavOpen(true)}
            >
              <Icon name="menu" />
            </button>
            <h1>{title}</h1>
          </div>

          <div className="af-resident__topbar-actions">
            <label className="af-resident__search">
              <Icon name="search" />
              <input type="search" placeholder={t('residentLayout.searchPlaceholder')} aria-label={t('shell.search')} />
            </label>
            <button
              type="button"
              className="af-resident__icon-button"
              aria-label={t('shell.notifications')}
              title={t('shell.notifications')}
              onClick={() => navigate('/resident/notifications')}
            >
              <Icon name="bell" />
              {activeAlertCount > 0 && <b>{activeAlertCount}</b>}
            </button>
            <LanguageSwitcher variant="topbar" />
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

      <ChatWidget endpoint="/resident/chat" persona="resident" />
    </div>
  )
}

export default ResidentLayout
