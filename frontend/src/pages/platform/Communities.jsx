import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { listCommunities } from '../../api/platformApi'
import './Communities.css'

const PAGE_SIZE = 10

const SORT_OPTIONS = [
  { value: 'apartmentName', label: 'Community' },
  { value: 'adminFullName', label: 'Admin' },
  { value: 'residentCount', label: 'Residents' },
]

function Communities() {
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
      .catch(() => setError('Could not load communities. Please try again.'))
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
          {communities.length} communit{communities.length === 1 ? 'y' : 'ies'}
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
            placeholder="Filter by community, admin name, or email"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>

        <div className="af-comm__sort">
          <label htmlFor="statusFilter">Status</label>
          <select id="statusFilter" value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
            <option value="ALL">All</option>
            <option value="ACTIVE">Active</option>
            <option value="BLOCKED">Blocked</option>
            <option value="TERMINATED">Terminated</option>
          </select>

          <label htmlFor="sortField">Sort by</label>
          <select id="sortField" value={sortField} onChange={(e) => setSortField(e.target.value)}>
            {SORT_OPTIONS.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))}
          </select>
          <button type="button" className="af-comm__sort-dir" onClick={toggleSortDirection} title="Toggle sort direction">
            {sortDirection === 'asc' ? '↑ Asc' : '↓ Desc'}
          </button>
        </div>
      </div>

      {error && <div className="af-comm__error">{error}</div>}

      <div className="af-comm__table-wrap">
        {loading ? (
          <p className="af-comm__empty">Loading communities…</p>
        ) : communities.length === 0 ? (
          <p className="af-comm__empty">No communities registered yet.</p>
        ) : visibleCommunities.length === 0 ? (
          <p className="af-comm__empty">No communities match your filters.</p>
        ) : (
          <table className="af-comm__table">
            <thead>
              <tr>
                <th>Community</th>
                <th>Admin</th>
                <th>Email</th>
                <th>Status</th>
                <th>Households</th>
                <th>Residents</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {pagedCommunities.map((c) => (
                <tr key={c.adminId}>
                  <td>{c.apartmentName || '—'}</td>
                  <td>{c.adminFullName}</td>
                  <td>{c.adminEmail}</td>
                  <td>
                    <span className={`af-comm__badge af-comm__badge--${c.status.toLowerCase()}`}>{c.status}</span>
                  </td>
                  <td>{c.householdCount}</td>
                  <td>{c.residentCount}</td>
                  <td>
                    <div className="af-comm__row-actions">
                      <Link to={`/platform/communities/${c.adminId}`}>View</Link>
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
            Showing {page * PAGE_SIZE + 1}–{Math.min(visibleCommunities.length, page * PAGE_SIZE + PAGE_SIZE)} of{' '}
            {visibleCommunities.length}
          </span>
          <div className="af-comm__pagination-controls">
            <button type="button" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
              ← Prev
            </button>
            <span className="af-comm__pagination-page">
              Page {page + 1} of {pageCount}
            </span>
            <button type="button" disabled={page >= pageCount - 1} onClick={() => setPage((p) => p + 1)}>
              Next →
            </button>
          </div>
        </div>
      )}
    </>
  )
}

export default Communities
