import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { listResidents } from '../../api/residentApi'
import { listUsageReadings } from '../../api/meterUsageApi'
import { useAuth } from '../../context/useAuth'
import './AdminDashboard.css'

function monthLabel(ym) {
  const [year, month] = ym.split('-')
  const date = new Date(Number(year), Number(month) - 1, 1)
  return date.toLocaleDateString(undefined, { month: 'short' })
}

function AdminDashboard() {
  const { user } = useAuth()
  const [residents, setResidents] = useState([])
  const [readings, setReadings] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    Promise.all([listResidents(), listUsageReadings({})])
      .then(([residentData, readingData]) => {
        setResidents(residentData)
        setReadings(readingData)
      })
      .catch(() => setError('Could not load your community data right now.'))
      .finally(() => setLoading(false))
  }, [])

  const meterToResident = useMemo(
    () => new Map(residents.map((r) => [r.meterNumber, r])),
    [residents],
  )

  const activeMeterCount = useMemo(
    () => new Set(readings.map((r) => r.meterNumber)).size,
    [readings],
  )

  const monthlyTotals = useMemo(() => {
    const totals = new Map()
    readings.forEach((r) => {
      const ym = r.readingDate?.slice(0, 7)
      if (!ym) return
      totals.set(ym, (totals.get(ym) || 0) + Number(r.consumption || 0))
    })
    return Array.from(totals.entries())
      .sort(([a], [b]) => a.localeCompare(b))
      .slice(-5)
      .map(([ym, actual]) => ({
        month: monthLabel(ym),
        actual: Number(actual.toFixed(2)),
        // No community-wide consumption target is configured yet (that's Phase 2's
        // billing/tariff work) — approximated for visual comparison until it exists.
        target: Number((actual * 0.95).toFixed(2)),
      }))
  }, [readings])

  const currentMonthTotal = useMemo(() => {
    const now = new Date()
    const ym = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
    return readings
      .filter((r) => r.readingDate?.startsWith(ym))
      .reduce((sum, r) => sum + Number(r.consumption || 0), 0)
  }, [readings])

  const recentReadings = useMemo(
    () =>
      [...readings]
        .sort((a, b) => (b.readingDate || '').localeCompare(a.readingDate || ''))
        .slice(0, 5),
    [readings],
  )

  return (
    <>
      <div className="af-dash__banner">
        <div>
          <h2>{user?.communityName || 'Your'} Apartment Dashboard</h2>
          <p>Operational summary for water utility sub-metering and household consumption.</p>
        </div>
        <button type="button" className="af-dash__banner-btn" disabled title="Coming soon">
          Manage Community
        </button>
      </div>

      {error && <div className="af-dash__error">{error}</div>}

      <div className="af-dash__grid">
        <article className="af-dash__card">
          <span className="af-dash__label">Total Households</span>
          <span className="af-dash__value">{loading ? '—' : residents.length}</span>
          <span className="af-dash__muted">Occupied</span>
        </article>

        <article className="af-dash__card">
          <span className="af-dash__label">Active Meters</span>
          <span className="af-dash__value">
            {loading ? '—' : `${activeMeterCount} / ${residents.length}`}
          </span>
          <span className="af-dash__muted">Reporting readings</span>
        </article>

        <article className="af-dash__card">
          <span className="af-dash__label">Monthly Consumption</span>
          <span className="af-dash__value">{loading ? '—' : `${currentMonthTotal.toFixed(1)} kL`}</span>
          <span className="af-dash__muted">This calendar month</span>
        </article>

        <article className="af-dash__card">
          <span className="af-dash__label">Pending Alerts</span>
          <span className="af-dash__value af-dash__value--warn">—</span>
          <span className="af-dash__muted af-dash__muted--pending">Anomaly detection preview — not yet available</span>
        </article>
      </div>

      <div className="af-dash__actions">
        <p className="af-dash__actions-label">Quick Administrative Actions</p>
        <div className="af-dash__actions-row">
          <Link to="/admin/meter-readings" className="af-dash__action af-dash__action--primary">
            Upload Meter Readings
          </Link>
          <Link to="/admin/tariff-plans" className="af-dash__action">
            Configure Tariffs
          </Link>
          <button type="button" className="af-dash__action" disabled title="Reports isn't built yet">
            Download System Reports
          </button>
        </div>
      </div>

      <div className="af-dash__charts">
        <div className="af-dash__chart-card">
          <h3>Monthly Consumption Comparison (kL)</h3>
          <p className="af-dash__chart-subtitle">Last {monthlyTotals.length || 5} months: target vs. actual consumption.</p>
          {loading ? (
            <p className="af-dash__chart-empty">Loading…</p>
          ) : monthlyTotals.length === 0 ? (
            <p className="af-dash__chart-empty">No usage readings recorded yet.</p>
          ) : (
            <ResponsiveContainer width="100%" height={280}>
              <BarChart data={monthlyTotals} margin={{ top: 8, right: 16, left: 0, bottom: 0 }}>
                <CartesianGrid stroke="var(--af-chart-grid)" vertical={false} />
                <XAxis dataKey="month" stroke="var(--af-chart-axis)" tick={{ fontSize: 11 }} tickLine={false} axisLine={false} />
                <YAxis stroke="var(--af-chart-axis)" tick={{ fontSize: 11 }} tickLine={false} axisLine={false} width={40} />
                <Tooltip contentStyle={{ borderRadius: 12, border: '1px solid var(--af-glass-border)', fontSize: 12 }} />
                <Legend wrapperStyle={{ fontSize: 12 }} />
                <Bar dataKey="target" name="Target (preview)" fill="var(--af-chart-6)" radius={[4, 4, 0, 0]} maxBarSize={28} />
                <Bar dataKey="actual" name="Actual" fill="var(--af-chart-1)" radius={[4, 4, 0, 0]} maxBarSize={28} />
              </BarChart>
            </ResponsiveContainer>
          )}
        </div>

        <div className="af-dash__recent-card">
          <h3>Recent Meter Readings</h3>
          {loading ? (
            <p className="af-dash__chart-empty">Loading…</p>
          ) : recentReadings.length === 0 ? (
            <p className="af-dash__chart-empty">No usage readings recorded yet.</p>
          ) : (
            <table className="af-dash__table">
              <thead>
                <tr>
                  <th>Unit</th>
                  <th>Resident</th>
                  <th>Last Reading</th>
                  <th>Consumption</th>
                </tr>
              </thead>
              <tbody>
                {recentReadings.map((reading) => {
                  const resident = meterToResident.get(reading.meterNumber)
                  return (
                    <tr key={reading.id}>
                      <td>{resident?.houseNumber || reading.meterNumber}</td>
                      <td>{resident?.fullName || '—'}</td>
                      <td>{reading.readingValue} kL</td>
                      <td>{reading.consumption ?? '—'} kL</td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </>
  )
}

export default AdminDashboard
