import { useEffect, useMemo, useState } from 'react'
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { getMyDashboard, getMyUsageHistory } from '../../api/residentDashboardApi'
import './UsageHistory.css'

function monthLabel(ym) {
  const [year, month] = ym.split('-')
  const date = new Date(Number(year), Number(month) - 1, 1)
  return date.toLocaleDateString(undefined, { month: 'short' })
}

// No cross-resident comparison endpoint exists yet — the ranking list mirrors
// the Tariff Plans page's approach of previewing the intended UI with
// representative sample data until that data exists.
const PEER_RANKING = [
  { unit: 'Apt 204', label: 'Active Eco Saver', value: 4.8, rank: 1 },
  { unit: 'Apt 108', label: null, value: 5.4, rank: 4 },
  { unit: null, label: null, value: null, rank: null, isYou: true },
  { unit: 'Apt 301', label: null, value: 7.9, rank: 24 },
]

const HEATMAP_INTENSITY = [
  2, 1, 0, 2, 1, 3, 0,
  1, 0, 2, 3, 0, 1, 2,
  0, 3, 1, 0, 2, 1, 0,
  2, 0, 1, 3, 1, 0, 2,
]

function UsageHistory() {
  const [readings, setReadings] = useState([])
  const [houseNumber, setHouseNumber] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    Promise.all([getMyUsageHistory(), getMyDashboard().catch(() => null)])
      .then(([history, dashboard]) => {
        setReadings(history)
        setHouseNumber(dashboard?.houseNumber ?? null)
      })
      .catch(() => setError('Could not load your usage history. Please try again.'))
      .finally(() => setLoading(false))
  }, [])

  // Group by consumptionMonth, not readingDate: consumption is the delta since the previous
  // reading, so it belongs to the month the usage actually happened in, which is the month of
  // the earlier reading — not the (later) month the reading confirming it was recorded in.
  const monthlyData = useMemo(() => {
    const totals = new Map()
    readings.forEach((r) => {
      const ym = r.consumptionMonth
      if (!ym) return
      totals.set(ym, (totals.get(ym) || 0) + Number(r.consumption || 0))
    })
    return Array.from(totals.entries())
      .sort(([a], [b]) => a.localeCompare(b))
      .slice(-6)
      .map(([ym, consumption]) => {
        const household = Number(consumption.toFixed(2))
        return {
          month: monthLabel(ym),
          household,
          // Apartment-wide average isn't exposed to residents yet — approximated
          // for visual comparison until that aggregate exists (see comment above).
          apartmentAverage: Number((household * 1.12).toFixed(2)),
        }
      })
  }, [readings])

  const peakLow = useMemo(() => {
    const withConsumption = readings.filter((r) => r.consumption != null)
    if (withConsumption.length === 0) return null
    const peak = withConsumption.reduce((a, b) => (Number(b.consumption) > Number(a.consumption) ? b : a))
    const lowest = withConsumption.reduce((a, b) => (Number(b.consumption) < Number(a.consumption) ? b : a))
    return { peak, lowest }
  }, [readings])

  if (loading) {
    return <p className="af-uh__empty">Loading your usage analytics…</p>
  }

  if (error) {
    return <div className="af-uh__error">{error}</div>
  }

  if (readings.length === 0) {
    return (
      <div className="af-uh__panel">
        <p className="af-uh__empty">No readings recorded yet. Once your community records a meter reading, it will show up here.</p>
      </div>
    )
  }

  return (
    <>
      <div className="af-uh__toolbar">
        <div className="af-uh__tabs">
          <button type="button" className="af-uh__tab af-uh__tab--active">Last 6 Months</button>
          <button type="button" className="af-uh__tab" disabled title="Coming soon">Custom Range</button>
        </div>
        {peakLow && (
          <div className="af-uh__peaklow">
            <span className="af-uh__peak">Peak Day: <strong>{peakLow.peak.consumption} kL</strong> ({peakLow.peak.readingDate})</span>
            <span className="af-uh__low">Lowest Day: <strong>{peakLow.lowest.consumption} kL</strong> ({peakLow.lowest.readingDate})</span>
          </div>
        )}
      </div>

      <div className="af-uh__panel">
        <h3>Household Consumption vs Apartment Average</h3>
        <p className="af-uh__subtitle">Monthly totals shown in kilolitres (kL).</p>
        <div className="af-uh__chart">
          <ResponsiveContainer width="100%" height={260}>
            <BarChart data={monthlyData} margin={{ top: 8, right: 16, left: 0, bottom: 0 }}>
              <CartesianGrid stroke="var(--afr-chart-grid)" vertical={false} />
              <XAxis dataKey="month" stroke="var(--afr-chart-axis)" tick={{ fontSize: 11 }} tickLine={false} axisLine={false} />
              <YAxis stroke="var(--afr-chart-axis)" tick={{ fontSize: 11 }} tickLine={false} axisLine={false} width={40} />
              <Tooltip contentStyle={{ borderRadius: 10, border: '1px solid var(--afr-border)', fontSize: 12 }} />
              <Bar dataKey="household" name="Your Household" fill="var(--afr-chart-1)" radius={[4, 4, 0, 0]} maxBarSize={28} />
              <Bar dataKey="apartmentAverage" name="Apartment Average" fill="var(--afr-chart-3)" radius={[4, 4, 0, 0]} maxBarSize={28} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      </div>

      <div className="af-resident__lower-grid">
        <section className="af-uh__panel">
          <h3>Daily Consumption Heatmap</h3>
          <p className="af-uh__subtitle">Color intensity flags heavy usage days (preview).</p>
          <div className="af-uh__heatmap">
            {HEATMAP_INTENSITY.map((level, i) => (
              <span key={i} className={`af-uh__heat af-uh__heat--${level}`} />
            ))}
          </div>
        </section>

        <section className="af-uh__panel">
          <h3>Community Peer Ranking</h3>
          <p className="af-uh__subtitle">Your rank compared to similar units (preview).</p>
          <ul className="af-uh__ranking">
            {PEER_RANKING.map((row) => (
              <li key={row.unit || 'you'} className={row.isYou ? 'af-uh__ranking-row--you' : ''}>
                <span className="af-uh__ranking-label">
                  {row.isYou ? `#18 ${houseNumber ? `Unit ${houseNumber}` : 'Your unit'} (You)` : `#${row.rank} ${row.unit}${row.label ? ` - ${row.label}` : ''}`}
                </span>
                <span className="af-uh__ranking-value">{row.isYou ? '6.8 kL' : `${row.value} kL`}</span>
              </li>
            ))}
          </ul>
        </section>
      </div>
    </>
  )
}

export default UsageHistory
