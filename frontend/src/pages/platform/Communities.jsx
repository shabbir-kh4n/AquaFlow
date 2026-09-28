import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { listCommunities } from '../../api/platformApi'
import Avatar from '../../components/Avatar'
import './Communities.css'

const PAGE_SIZE = 10

const SORT_OPTIONS = [
  { value: 'apartmentName', label: 'communities.sortOptions.community' },
  { value: 'adminFullName', label: 'communities.sortOptions.admin' },
  { value: 'residentCount', label: 'communities.sortOptions.residents' },
]

const STATUS_KEYS = {
  ACTIVE: 'status.active',
  BLOCKED: 'status.blocked',
  TERMINATED: 'status.terminated',
}

function Communities() {
  const { t } = useTranslation('platform')
  const navigate = useNavigate()
  const [communities, setCommunities] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState('ALL')
  const [sortField, setSortField] = useState('apartmentName')
  const [sortDirection, setSortDirection] = useState('asc')
  const [page, setPage] = useState(0)
  const [pageResetKey, setPageResetKey] = useState('')

  useEffect(() => {
    listCommunities()
      .then(setCommunities)
      .catch(() => setError('communities.loadError'))
      .finally(() => setLoading(false))
  }, [])

  const visibleCommunities = useMemo(() => {
    const term = search.trim().toLowerCase()
    let filtered = communities
    if (statusFilter !== 'ALL') {
      filtered = filtered.filter((c) => c.status === statusFilter)
    }
    if (term) {
      filtered = filtered.filter((c) =>
        [c.apartmentName, c.adminFullName, c.adminEmail].some((field) => field?.toLowerCase().includes(term)),
      )
    }

    const sorted = [...filtered].sort((a, b) => {
      const aVal = a[sortField]
      const bVal = b[sortField]
      if (typeof aVal === 'number' && typeof bVal === 'number') {
        return aVal - bVal
      }
      return String(aVal || '').localeCompare(String(bVal || ''), undefined, { numeric: true, sensitivity: 'base' })
    })

    return sortDirection === 'asc' ? sorted : sorted.reverse()
  }, [communities, search, statusFilter, sortField, sortDirection])

  const pageCount = Math.max(1, Math.ceil(visibleCommunities.length / PAGE_SIZE))

  const resetKey = `${search}|${statusFilter}|${sortField}|${sortDirection}|${communities.length}`
  if (resetKey !== pageResetKey) {
    setPageResetKey(resetKey)
    setPage(0)
  }

  const pagedCommunities = useMemo(
    () => visibleCommunities.slice(page * PAGE_SIZE, page * PAGE_SIZE + PAGE_SIZE),
    [visibleCommunities, page],
  )

  function toggleSortDirection() {
    setSortDirection((prev) => (prev === 'asc' ? 'desc' : 'asc'))
  }

  return (
    <>
      <div className="af-comm__toolbar">
        <span className="af-comm__count">
          {t('communities.count', { count: communities.length })}
        </span>
      </div>

      <div className="af-comm__filters">
        <div className="af-comm__search">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="11" cy="11" r="7" />
            <path d="m21 21-4.35-4.35" />
          </svg>
          <input
            type="text"
            placeholder={t('communities.searchPlaceholder')}
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>

        <div className="af-comm__sort">
          <label htmlFor="statusFilter">{t('communities.statusFilter.label')}</label>
          <select id="statusFilter" value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
            <option value="ALL">{t('communities.statusFilter.all')}</option>
            <option value="ACTIVE">{t('status.active')}</option>
            <option value="BLOCKED">{t('status.blocked')}</option>
            <option value="TERMINATED">{t('status.terminated')}</option>
          </select>

          <label htmlFor="sortField">{t('sort.sortBy')}</label>
          <select id="sortField" value={sortField} onChange={(e) => setSortField(e.target.value)}>
            {SORT_OPTIONS.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {t(opt.label)}
              </option>
            ))}
          </select>
          <button type="button" className="af-comm__sort-dir" onClick={toggleSortDirection} title={t('sort.toggleDirection')}>
            {sortDirection === 'asc' ? t('sort.asc') : t('sort.desc')}
          </button>
        </div>
      </div>

      {error && <div className="af-comm__error">{t(error)}</div>}

      <div className="af-comm__table-wrap">
        {loading ? (
          <p className="af-comm__empty">{t('communities.loading')}</p>
        ) : communities.length === 0 ? (
          <p className="af-comm__empty">{t('communities.empty')}</p>
        ) : visibleCommunities.length === 0 ? (
          <p className="af-comm__empty">{t('communities.noMatch')}</p>
        ) : (
          <table className="af-comm__table">
            <thead>
              <tr>
                <th className="af-comm__avatar-col"></th>
                <th>{t('communities.table.community')}</th>
                <th>{t('communities.table.admin')}</th>
                <th>{t('communities.table.email')}</th>
                <th>{t('communities.table.status')}</th>
                <th>{t('communities.table.residents')}</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {pagedCommunities.map((c) => (
                <tr
                  key={c.adminId}
                  className="af-comm__row--clickable"
                  tabIndex={0}
                  role="button"
                  onClick={() => navigate(`/platform/communities/${c.adminId}`)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' || e.key === ' ') {
                      e.preventDefault()
                      navigate(`/platform/communities/${c.adminId}`)
                    }
                  }}
                >
                  <td className="af-comm__avatar-col">
                    <Avatar avatarKey={c.avatarKey} name={c.adminFullName} className="af-comm__avatar" />
                  </td>
                  <td>{c.apartmentName || '—'}</td>
                  <td>{c.adminFullName}</td>
                  <td>{c.adminEmail}</td>
                  <td>
                    <span className={`af-comm__badge af-comm__badge--${c.status.toLowerCase()}`}>
                      {STATUS_KEYS[c.status] ? t(STATUS_KEYS[c.status]) : c.status}
                    </span>
                  </td>
                  <td>{c.residentCount}</td>
                  <td>
                    <div className="af-comm__row-actions">
                      <Link to={`/platform/communities/${c.adminId}`} onClick={(e) => e.stopPropagation()}>
                        {t('communities.table.view')}
                      </Link>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {visibleCommunities.length > 0 && (
        <div className="af-comm__pagination">
          <span className="af-comm__pagination-info">
            {t('pagination.showing', {
              from: page * PAGE_SIZE + 1,
              to: Math.min(visibleCommunities.length, page * PAGE_SIZE + PAGE_SIZE),
              total: visibleCommunities.length,
            })}
          </span>
          <div className="af-comm__pagination-controls">
            <button type="button" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
              {t('pagination.prev')}
            </button>
            <span className="af-comm__pagination-page">
              {t('pagination.page', { page: page + 1, pageCount })}
            </span>
            <button type="button" disabled={page >= pageCount - 1} onClick={() => setPage((p) => p + 1)}>
              {t('pagination.next')}
            </button>
          </div>
        </div>
      )}
    </>
  )
}

export default Communities
