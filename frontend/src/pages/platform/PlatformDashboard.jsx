import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { listCommunities } from '../../api/platformApi'
import './PlatformDashboard.css'

const STATUS_KEYS = {
  ACTIVE: 'status.active',
  BLOCKED: 'status.blocked',
  TERMINATED: 'status.terminated',
}

function PlatformDashboard() {
  const { t } = useTranslation('platform')
  const [communities, setCommunities] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    listCommunities()
      .then(setCommunities)
      .catch(() => setError('dashboard.loadError'))
      .finally(() => setLoading(false))
  }, [])

  const totalResidents = useMemo(
    () => communities.reduce((sum, c) => sum + c.residentCount, 0),
    [communities],
  )

  const activeCount = communities.filter((c) => c.status === 'ACTIVE').length
  const blockedCount = communities.filter((c) => c.status === 'BLOCKED').length
  const terminatedCount = communities.filter((c) => c.status === 'TERMINATED').length

  const recentCommunities = useMemo(
    () =>
      [...communities]
        .sort((a, b) => (b.adminCreatedAt || '').localeCompare(a.adminCreatedAt || ''))
        .slice(0, 5),
    [communities],
  )

  return (
    <>
      <div className="af-pdash__banner">
        <div>
          <h2>{t('dashboard.title')}</h2>
          <p>{t('dashboard.subtitle')}</p>
        </div>
        <Link to="/platform/communities" className="af-pdash__banner-btn">
          {t('dashboard.viewAll')}
        </Link>
      </div>

      {error && <div className="af-pdash__error">{t(error)}</div>}

      <div className="af-pdash__grid">
        <article className="af-pdash__card">
          <span className="af-pdash__label">{t('dashboard.cards.totalCommunities')}</span>
          <span className="af-pdash__value">{loading ? '—' : communities.length}</span>
          <span className="af-pdash__muted">{t('dashboard.cards.registeredAdmins')}</span>
        </article>

        <article className="af-pdash__card">
          <span className="af-pdash__label">{t('dashboard.cards.totalResidents')}</span>
          <span className="af-pdash__value">{loading ? '—' : totalResidents}</span>
          <span className="af-pdash__muted">{t('dashboard.cards.acrossAllCommunities')}</span>
        </article>

        <article className="af-pdash__card">
          <span className="af-pdash__label">{t('dashboard.cards.active')}</span>
          <span className="af-pdash__value">{loading ? '—' : activeCount}</span>
          <span className="af-pdash__muted">{t('dashboard.cards.inGoodStanding')}</span>
        </article>

        <article className="af-pdash__card">
          <span className="af-pdash__label">{t('dashboard.cards.blockedTerminated')}</span>
          <span className="af-pdash__value af-pdash__value--warn">
            {loading ? '—' : blockedCount + terminatedCount}
          </span>
          <span className="af-pdash__muted">{t('dashboard.cards.blockedTerminatedBreakdown', { blocked: blockedCount, terminated: terminatedCount })}</span>
        </article>
      </div>

      <div className="af-pdash__recent-card">
        <h3>{t('dashboard.recent.title')}</h3>
        {loading ? (
          <p className="af-pdash__empty">{t('dashboard.recent.loading')}</p>
        ) : recentCommunities.length === 0 ? (
          <p className="af-pdash__empty">{t('dashboard.recent.empty')}</p>
        ) : (
          <table className="af-pdash__table">
            <thead>
              <tr>
                <th>{t('dashboard.recent.table.community')}</th>
                <th>{t('dashboard.recent.table.admin')}</th>
                <th>{t('dashboard.recent.table.status')}</th>
                <th>{t('dashboard.recent.table.residents')}</th>
              </tr>
            </thead>
            <tbody>
              {recentCommunities.map((c) => (
                <tr key={c.adminId}>
                  <td>{c.apartmentName || '—'}</td>
                  <td>{c.adminFullName}</td>
                  <td>
                    <span className={`af-pdash__badge af-pdash__badge--${c.status.toLowerCase()}`}>
                      {STATUS_KEYS[c.status] ? t(STATUS_KEYS[c.status]) : c.status}
                    </span>
                  </td>
                  <td>{c.residentCount}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </>
  )
}

export default PlatformDashboard
