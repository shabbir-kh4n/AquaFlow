import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Area,
  AreaChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { getPlatformReport } from '../../api/platformApi'
import './PlatformReports.css'

const PAGE_SIZE = 10

const RANGE_OPTIONS = [
  { value: '3mo', label: '3 Months' },
  { value: '6mo', label: '6 Months' },
  { value: '12mo', label: '12 Months' },
  { value: 'custom', label: 'Custom' },
]

const SORT_OPTIONS = [
  { value: 'apartmentName', label: 'Community' },
  { value: 'totalConsumptionKl', label: 'Usage' },
  { value: 'totalBilledAmount', label: 'Billed' },
  { value: 'residentCount', label: 'Residents' },
]

function formatMonthLabel(periodMonth) {
  const [year, month] = periodMonth.split('-')
  return new Date(Number(year), Number(month) - 1, 1).toLocaleDateString(undefined, {
    month: 'short',
    year: '2-digit',
  })
}

function formatKl(value) {
  return `${Number(value ?? 0).toLocaleString('en-IN', { maximumFractionDigits: 2 })} kL`
}

function formatCurrency(value) {
  return `₹${Number(value ?? 0).toLocaleString('en-IN', { maximumFractionDigits: 2 })}`
}

function PlatformReports() {
  const navigate = useNavigate()
  const [rangeMode, setRangeMode] = useState('3mo')
  const [customFrom, setCustomFrom] = useState('')
  const [customTo, setCustomTo] = useState('')

  const [report, setReport] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const [search, setSearch] = useState('')
  const [sortField, setSortField] = useState('totalConsumptionKl')
  const [sortDirection, setSortDirection] = useState('desc')
  const [page, setPage] = useState(0)
  const [pageResetKey, setPageResetKey] = useState('')

  const customRangeReady = rangeMode !== 'custom' || (customFrom && customTo)

  useEffect(() => {
    if (!customRangeReady) {
      return
    }

    const params = rangeMode === 'custom' ? { from: customFrom, to: customTo } : { range: rangeMode }

    getPlatformReport(params)
      .then((data) => {
        setReport(data)
        setError('')
      })
      .catch((err) => setError(err.response?.data?.message || 'Could not load the platform report.'))
      .finally(() => setLoading(false))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [rangeMode, customFrom, customTo])

  const communities = useMemo(() => report?.communities ?? [], [report])

  const visibleCommunities = useMemo(() => {
    const term = search.trim().toLowerCase()
    let filtered = communities
    if (term) {
      filtered = filtered.filter((c) => [c.apartmentName, c.adminFullName].some((f) => f?.toLowerCase().includes(term)))
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
  }, [communities, search, sortField, sortDirection])

  const pageCount = Math.max(1, Math.ceil(visibleCommunities.length / PAGE_SIZE))
  const resetKey = `${search}|${sortField}|${sortDirection}|${communities.length}`
  if (resetKey !== pageResetKey) {
    setPageResetKey(resetKey)
    setPage(0)
  }

  const pagedCommunities = useMemo(
    () => visibleCommunities.slice(page * PAGE_SIZE, page * PAGE_SIZE + PAGE_SIZE),
    [visibleCommunities, page],
  )

  const trendPoints = useMemo(
    () => (report?.monthlyTrend ?? []).map((p) => ({ ...p, label: formatMonthLabel(p.periodMonth) })),
    [report],
  )

  function toggleSortDirection() {
    setSortDirection((prev) => (prev === 'asc' ? 'desc' : 'asc'))
  }

  const summary = report?.summary

  return (
    <>
      {error && <div className="af-rpt__error">{error}</div>}

      <div className="af-rpt__range-row">
        {RANGE_OPTIONS.map((opt) => (
          <button
            key={opt.value}
            type="button"
            className={`af-rpt__range-tab ${rangeMode === opt.value ? 'af-rpt__range-tab--active' : ''}`}
            onClick={() => setRangeMode(opt.value)}
          >
            {opt.label}
          </button>
        ))}

        {rangeMode === 'custom' && (
          <div className="af-rpt__custom-pickers">
            <label>
              From
              <input type="month" value={customFrom} onChange={(e) => setCustomFrom(e.target.value)} />
            </label>
            <label>
              To
              <input type="month" value={customTo} onChange={(e) => setCustomTo(e.target.value)} />
            </label>
          </div>
        )}
      </div>

      {rangeMode === 'custom' && !customRangeReady && (
        <p className="af-rpt__hint">Pick both a "from" and "to" month to load a custom report.</p>
      )}

      {loading && customRangeReady ? (
        <p className="af-rpt__empty">Loading report…</p>
      ) : (
        customRangeReady && (
          <>
            <div className="af-rpt__grid">
              <article className="af-rpt__card">
                <span className="af-rpt__label">Total Communities</span>
                <span className="af-rpt__value">{summary?.totalCommunities ?? 0}</span>
                <span className="af-rpt__muted">In this range</span>
              </article>
              <article className="af-rpt__card">
                <span className="af-rpt__label">Total Residents</span>
                <span className="af-rpt__value">{summary?.totalResidents ?? 0}</span>
                <span className="af-rpt__muted">Across all communities</span>
              </article>
              <article className="af-rpt__card">
                <span className="af-rpt__label">Overall Usage</span>
                <span className="af-rpt__value">{formatKl(summary?.totalConsumptionKl)}</span>
                <span className="af-rpt__muted">Metered consumption</span>
              </article>
              <article className="af-rpt__card">
                <span className="af-rpt__label">Overall Billed</span>
                <span className="af-rpt__value">{formatCurrency(summary?.totalBilledAmount)}</span>
                <span className="af-rpt__muted">Finalized invoices only</span>
              </article>
              <article className="af-rpt__card">
                <span className="af-rpt__label">Avg Usage / Resident</span>
                <span className="af-rpt__value">{formatKl(summary?.avgConsumptionPerResident)}</span>
              </article>
              <article className="af-rpt__card">
                <span className="af-rpt__label">Avg Bill / Resident</span>
                <span className="af-rpt__value">{formatCurrency(summary?.avgBilledPerResident)}</span>
              </article>
            </div>

            <div className="af-rpt__charts">
              <div className="af-rpt__chart-card">
                <div className="af-rpt__chart-header">
                  <span>Platform-wide Usage (kL / month)</span>
                </div>
                {trendPoints.length === 0 ? (
                  <p className="af-rpt__chart-empty">No usage recorded for this range.</p>
                ) : (
                  <ResponsiveContainer width="100%" height={220}>
                    <AreaChart data={trendPoints} margin={{ top: 8, right: 12, left: 0, bottom: 0 }}>
                      <CartesianGrid stroke="var(--af-chart-grid)" vertical={false} />
                      <XAxis dataKey="label" stroke="var(--af-chart-axis)" tick={{ fontSize: 11 }} tickLine={false} axisLine={false} />
                      <YAxis stroke="var(--af-chart-axis)" tick={{ fontSize: 11 }} tickLine={false} axisLine={false} width={48} />
                      <Tooltip
                        contentStyle={{ borderRadius: 12, border: '1px solid var(--af-glass-border)', fontSize: 12 }}
                        formatter={(value) => [formatKl(value), 'Usage']}
                      />
                      <Area type="monotone" dataKey="totalConsumptionKl" stroke="var(--af-chart-1)" fill="var(--af-chart-1)" fillOpacity={0.15} strokeWidth={2} />
                    </AreaChart>
                  </ResponsiveContainer>
                )}
              </div>

              <div className="af-rpt__chart-card">
                <div className="af-rpt__chart-header">
                  <span>Platform-wide Billed Amount (₹ / month)</span>
                </div>
                {trendPoints.length === 0 ? (
                  <p className="af-rpt__chart-empty">No invoices finalized for this range.</p>
                ) : (
                  <ResponsiveContainer width="100%" height={220}>
                    <AreaChart data={trendPoints} margin={{ top: 8, right: 12, left: 0, bottom: 0 }}>
                      <CartesianGrid stroke="var(--af-chart-grid)" vertical={false} />
                      <XAxis dataKey="label" stroke="var(--af-chart-axis)" tick={{ fontSize: 11 }} tickLine={false} axisLine={false} />
                      <YAxis stroke="var(--af-chart-axis)" tick={{ fontSize: 11 }} tickLine={false} axisLine={false} width={56} />
                      <Tooltip
                        contentStyle={{ borderRadius: 12, border: '1px solid var(--af-glass-border)', fontSize: 12 }}
                        formatter={(value) => [formatCurrency(value), 'Billed']}
                      />
                      <Area type="monotone" dataKey="totalBilledAmount" stroke="var(--af-chart-2)" fill="var(--af-chart-2)" fillOpacity={0.15} strokeWidth={2} />
                    </AreaChart>
                  </ResponsiveContainer>
                )}
              </div>
            </div>

            <div className="af-rpt__filters">
              <div className="af-rpt__search">
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <circle cx="11" cy="11" r="7" />
                  <path d="m21 21-4.35-4.35" />
                </svg>
                <input
                  type="text"
                  placeholder="Filter by community or admin name"
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                />
              </div>

              <div className="af-rpt__sort">
                <label htmlFor="sortField">Sort by</label>
                <select id="sortField" value={sortField} onChange={(e) => setSortField(e.target.value)}>
                  {SORT_OPTIONS.map((opt) => (
                    <option key={opt.value} value={opt.value}>
                      {opt.label}
                    </option>
                  ))}
                </select>
                <button type="button" className="af-rpt__sort-dir" onClick={toggleSortDirection} title="Toggle sort direction">
                  {sortDirection === 'asc' ? '↑ Asc' : '↓ Desc'}
                </button>
              </div>
            </div>

            <div className="af-rpt__table-wrap">
              {communities.length === 0 ? (
                <p className="af-rpt__empty">No communities have usage or billing data for this range yet.</p>
              ) : visibleCommunities.length === 0 ? (
                <p className="af-rpt__empty">No communities match your filter.</p>
              ) : (
                <table className="af-rpt__table">
                  <thead>
                    <tr>
                      <th>Community</th>
                      <th>Admin</th>
                      <th>Residents</th>
                      <th>Usage (kL)</th>
                      <th>Billed (₹)</th>
                      <th>Avg Usage / Resident</th>
                      <th>Avg Bill / Resident</th>
                    </tr>
                  </thead>
                  <tbody>
                    {pagedCommunities.map((c) => (
                      <tr
                        key={c.adminId}
                        className="af-rpt__row--clickable"
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
                        <td>{c.apartmentName || '—'}</td>
                        <td>{c.adminFullName}</td>
                        <td>{c.residentCount}</td>
                        <td>{formatKl(c.totalConsumptionKl)}</td>
                        <td>{formatCurrency(c.totalBilledAmount)}</td>
                        <td>{formatKl(c.avgConsumptionPerResident)}</td>
                        <td>{formatCurrency(c.avgBilledPerResident)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>

            {visibleCommunities.length > 0 && (
              <div className="af-rpt__pagination">
                <span className="af-rpt__pagination-info">
                  Showing {page * PAGE_SIZE + 1}–{Math.min(visibleCommunities.length, page * PAGE_SIZE + PAGE_SIZE)} of{' '}
                  {visibleCommunities.length}
                </span>
                <div className="af-rpt__pagination-controls">
                  <button type="button" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
                    ← Prev
                  </button>
                  <span className="af-rpt__pagination-page">
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
      )}
    </>
  )
}

export default PlatformReports
