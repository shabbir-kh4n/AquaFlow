import { useCallback, useEffect, useMemo, useState } from 'react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../../context/useAuth'
import { listAlerts } from '../../api/alertApi'
import Logo from '../Logo'
import Avatar from '../Avatar'
import { STUB_PAGES } from './adminRoutes'
import ChatWidget from '../chat/ChatWidget'
import LanguageSwitcher from '../LanguageSwitcher'
import TopbarSearch from '../TopbarSearch'
import { listResidents } from '../../api/residentApi'
import './AdminLayout.css'

// labelKey values are translation keys (common namespace), resolved with t() at render time.
const NAV_ITEMS = [
  { to: '/admin/dashboard', labelKey: 'adminLayout.nav.dashboard', icon: 'grid', built: true },
  { to: '/admin/households', labelKey: 'adminLayout.nav.households', icon: 'home', built: true },
  { to: '/admin/meter-readings', labelKey: 'adminLayout.nav.meterReadings', icon: 'gauge', built: true },
  { to: '/admin/billing', labelKey: 'adminLayout.nav.billing', icon: 'invoice', built: true },
  { to: '/admin/bulk-purchases', labelKey: 'adminLayout.nav.bulkPurchases', icon: 'truck', built: true },
  { to: '/admin/reports', labelKey: 'adminLayout.nav.reports', icon: 'chart', built: true },
  { to: '/admin/water-leakage', labelKey: 'adminLayout.nav.waterLeakage', icon: 'drop-alert', built: true },
  { to: '/admin/support', labelKey: 'adminLayout.nav.support', icon: 'help', built: true },
  { to: '/admin/tariff-plans', labelKey: 'adminLayout.nav.tariffPlans', icon: 'sliders', built: true },
  { to: '/admin/announcements', labelKey: 'adminLayout.nav.announcements', icon: 'megaphone', built: true },
  { to: '/admin/profile', labelKey: 'adminLayout.nav.profile', icon: 'user', built: true },
]

// Static per-route topbar copy (translation keys) for the built pages. Dashboard's title is
// dynamic (greets the admin by name) so it's handled separately below.
const PAGE_META = {
  '/admin/dashboard': { subtitleKey: 'adminLayout.pages.dashboard.subtitle' },
  '/admin/households': {
    titleKey: 'adminLayout.pages.households.title',
    subtitleKey: 'adminLayout.pages.households.subtitle',
  },
  '/admin/meter-readings': {
    titleKey: 'adminLayout.pages.meterReadings.title',
    subtitleKey: 'adminLayout.pages.meterReadings.subtitle',
  },
  '/admin/tariff-plans': {
    titleKey: 'adminLayout.pages.tariffPlans.title',
    subtitleKey: 'adminLayout.pages.tariffPlans.subtitle',
  },
  '/admin/billing': {
    titleKey: 'adminLayout.pages.billing.title',
    subtitleKey: 'adminLayout.pages.billing.subtitle',
  },
  '/admin/bulk-purchases': {
    titleKey: 'adminLayout.pages.bulkPurchases.title',
    subtitleKey: 'adminLayout.pages.bulkPurchases.subtitle',
  },
  '/admin/water-leakage': {
    titleKey: 'adminLayout.pages.waterLeakage.title',
    subtitleKey: 'adminLayout.pages.waterLeakage.subtitle',
  },
  '/admin/support': {
    titleKey: 'adminLayout.pages.support.title',
    subtitleKey: 'adminLayout.pages.support.subtitle',
  },
  '/admin/reports': {
    titleKey: 'adminLayout.pages.reports.title',
    subtitleKey: 'adminLayout.pages.reports.subtitle',
  },
  '/admin/announcements': {
    titleKey: 'adminLayout.pages.announcements.title',
    subtitleKey: 'adminLayout.pages.announcements.subtitle',
  },
  '/admin/profile': {
    titleKey: 'adminLayout.pages.profile.title',
    subtitleKey: 'adminLayout.pages.profile.subtitle',
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

function AdminLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const { t } = useTranslation('common')
  const [activeAlertCount, setActiveAlertCount] = useState(0)
  const [navOpen, setNavOpen] = useState(false)
  const [lastPathname, setLastPathname] = useState(location.pathname)

  useEffect(() => {
    listAlerts('ACTIVE')
      .then((alerts) => setActiveAlertCount(alerts.length))
      .catch(() => setActiveAlertCount(0))
  }, [])

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

  const searchPages = useMemo(
    () => NAV_ITEMS.filter((item) => item.built).map((item) => ({ id: item.to, label: t(item.labelKey), to: item.to })),
    [t],
  )
  // A resident hit opens the Households Directory pre-filtered to them (it reads ?q=).
  const loadSearchRecords = useCallback(
    () => listResidents().then((residents) => residents.map((resident) => ({
      id: resident.id,
      label: resident.fullName,
      sublabel: [resident.houseNumber, resident.meterNumber, resident.email].filter(Boolean).join(' · '),
      to: `/admin/households?q=${encodeURIComponent(resident.houseNumber || resident.fullName)}`,
    }))),
    [],
  )

  let title = ''
  let subtitle = ''
  if (location.pathname === '/admin/dashboard') {
    title = t('shell.welcomeBack', { name: user?.fullName || t('adminLayout.fallbackName') })
    subtitle = t(PAGE_META['/admin/dashboard'].subtitleKey)
  } else if (PAGE_META[location.pathname]) {
    title = t(PAGE_META[location.pathname].titleKey)
    subtitle = t(PAGE_META[location.pathname].subtitleKey)
  } else {
    const stub = STUB_PAGES.find((page) => page.path === location.pathname)
    if (stub) {
      title = t(stub.titleKey)
      subtitle = t(stub.descriptionKey)
    }
  }

  return (
    <div className="af-admin">
      {navOpen && (
        <div className="af-admin__backdrop" onClick={() => setNavOpen(false)} aria-hidden="true" />
      )}

      <aside className={`af-admin__sidebar${navOpen ? ' af-admin__sidebar--open' : ''}`}>
        <div className="af-admin__brand">
          <Logo size={32} />
          HydroLedger
          <button
            type="button"
            className="af-admin__nav-close"
            aria-label={t('shell.closeMenu')}
            onClick={() => setNavOpen(false)}
          >
            <Icon name="close" />
          </button>
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
                {t(item.labelKey)}
              </NavLink>
            ) : (
              <button
                key={item.to}
                type="button"
                disabled
                title={t('adminLayout.notBuilt', { label: t(item.labelKey) })}
                className="af-admin__nav-item af-admin__nav-item--unbuilt"
              >
                <Icon name={item.icon} />
                {t(item.labelKey)}
              </button>
            ),
          )}
        </nav>

        <div className="af-admin__sidebar-footer">
          <Avatar avatarKey={user?.avatarKey} name={user?.fullName} fallbackName={t('adminLayout.fallbackName')} className="af-admin__avatar" />
          <div className="af-admin__account-text">
            <strong>{user?.fullName || t('adminLayout.fallbackName')}</strong>
            <span>{user?.communityName || t('adminLayout.fallbackCommunity')}</span>
          </div>
          <button type="button" className="af-admin__logout" onClick={handleLogout} title={t('shell.logout')}>
            <span aria-hidden="true">↪</span>
          </button>
        </div>
      </aside>

      <div className="af-admin__main">
        <header className="af-admin__topbar">
          <div className="af-admin__topbar-start">
            <button
              type="button"
              className="af-admin__hamburger"
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

          <div className="af-admin__account">
            <TopbarSearch
              className="af-admin__search"
              icon={<Icon name="search" />}
              placeholder={t('adminLayout.searchPlaceholder')}
              pages={searchPages}
              loadRecords={loadSearchRecords}
            />
            <button
              type="button"
              className="af-admin__icon-button"
              aria-label={t('shell.notifications')}
              title={t('shell.notifications')}
              onClick={() => navigate('/admin/water-leakage')}
            >
              <Icon name="bell" />
              {activeAlertCount > 0 && <b>{activeAlertCount}</b>}
            </button>
            <LanguageSwitcher variant="topbar" />
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

      <ChatWidget endpoint="/admin/chat" persona="admin" />
    </div>
  )
}

export default AdminLayout
