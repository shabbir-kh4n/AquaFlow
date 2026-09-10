import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { listCommunities } from '../../api/platformApi'
import './PlatformDashboard.css'

function PlatformDashboard() {
  const [communities, setCommunities] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    listCommunities()
      .then(setCommunities)
      .catch(() => setError('Could not load platform data right now.'))
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
          <h2>Platform Overview</h2>
          <p>Every registered community admin, across the whole HydroLedger platform.</p>
        </div>
        <Link to="/platform/communities" className="af-pdash__banner-btn">
          View All Communities
        </Link>
      </div>

      {error && <div className="af-pdash__error">{error}</div>}

      <div className="af-pdash__grid">
        <article className="af-pdash__card">
          <span className="af-pdash__label">Total Communities</span>
          <span className="af-pdash__value">{loading ? '—' : communities.length}</span>
          <span className="af-pdash__muted">Registered admins</span>
        </article>

        <article className="af-pdash__card">
          <span className="af-pdash__label">Total Residents</span>
          <span className="af-pdash__value">{loading ? '—' : totalResidents}</span>
          <span className="af-pdash__muted">Across all communities</span>
        </article>

        <article className="af-pdash__card">
          <span className="af-pdash__label">Active</span>
          <span className="af-pdash__value">{loading ? '—' : activeCount}</span>
          <span className="af-pdash__muted">In good standing</span>
        </article>

        <article className="af-pdash__card">
          <span className="af-pdash__label">Blocked / Terminated</span>
          <span className="af-pdash__value af-pdash__value--warn">
            {loading ? '—' : blockedCount + terminatedCount}
          </span>
          <span className="af-pdash__muted">{blockedCount} blocked, {terminatedCount} terminated</span>
        </article>
      </div>

      <div className="af-pdash__recent-card">
        <h3>Recently Registered Communities</h3>
        {loading ? (
          <p className="af-pdash__empty">Loading…</p>
        ) : recentCommunities.length === 0 ? (
          <p className="af-pdash__empty">No communities registered yet.</p>
        ) : (
          <table className="af-pdash__table">
            <thead>
              <tr>
                <th>Community</th>
                <th>Admin</th>
                <th>Status</th>
                <th>Residents</th>
              </tr>
            </thead>
            <tbody>
              {recentCommunities.map((c) => (
                <tr key={c.adminId}>
                  <td>{c.apartmentName || '—'}</td>
                  <td>{c.adminFullName}</td>
                  <td>
                    <span className={`af-pdash__badge af-pdash__badge--${c.status.toLowerCase()}`}>
                      {c.status}
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
