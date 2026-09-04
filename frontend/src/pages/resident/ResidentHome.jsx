import { useEffect, useMemo, useState } from 'react'
import { Cell, Line, LineChart, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { getMyDashboard, getMyUsageHistory } from '../../api/residentDashboardApi'
import { getBillEstimate } from '../../api/tariffApi'
import { getMyInvoices } from '../../api/billingApi'
import { Icon } from '../../components/resident/Icon'
import { useAuth } from '../../context/useAuth'

// Usage doesn't yet break down by fixture, and the billing engine (Phase 2)
// isn't built — these mirror the Tariff Plans page's approach of previewing
// the intended UI with representative sample data until that data exists.
const USAGE_BREAKDOWN = [
  { name: 'Bathroom', value: 45, color: 'var(--afr-chart-1)' },
  { name: 'Kitchen', value: 35, color: 'var(--afr-chart-2)' },
  { name: 'Garden / Balcony', value: 15, color: 'var(--afr-chart-3)' },
  { name: 'Other / Laundry', value: 5, color: 'var(--afr-chart-5)' },
]

const SAVING_TIPS = [
  { icon: 'drop', text: 'Fix aerators in kitchen taps. Aerated faucets save up to 40% water flow.' },
  { icon: 'chart', text: 'Adjust balcony sprinkler timers. Watering at 7 AM minimizes evaporation.' },
  { icon: 'bulb', text: 'High usage detected in Bathroom at 3 AM. Check for a silent flush tank leak.' },
]

function monthLabel(date) {
  return new Date(date).toLocaleDateString(undefined, { month: 'short', day: 'numeric' })
}

function ResidentHome() {
  const { user } = useAuth()
  const [dashboard, setDashboard] = useState(null)
  const [readings, setReadings] = useState([])
  const [billEstimate, setBillEstimate] = useState(null)
  const [latestInvoice, setLatestInvoice] = useState(null)

  useEffect(() => {
    getMyDashboard()
      .then(setDashboard)
      .catch(() => {
        // Live dashboard fetch failed — fall back to the login-time snapshot in `user`.
      })
    getMyUsageHistory()
      .then(setReadings)
      .catch(() => {
        // No usage history yet — the trend chart just renders empty.
      })
    getBillEstimate()
      .then(setBillEstimate)
      .catch(() => {
        // No tariff configured yet or no readings — the bill panel falls back to '--'.
      })
    getMyInvoices()
      .then((invoices) => setLatestInvoice(invoices[0] || null))
      .catch(() => {
        // No finalized invoices yet — the billing cycle panel falls back to the live estimate.
      })
  }, [])

  const communityName = dashboard?.communityName ?? user?.communityName
  const hasReading = dashboard?.latestReadingValue != null && dashboard?.latestReadingDate != null

  const dailyTrend = useMemo(
    () =>
      readings.slice(-14).map((r) => ({
        date: monthLabel(r.readingDate),
        value: Number(r.consumption ?? 0),
      })),
    [readings],
  )

  const monthTotal = useMemo(() => {
    if (readings.length === 0) return null
    const now = new Date()
    const ym = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
    return readings
      .filter((r) => r.readingDate?.startsWith(ym))
      .reduce((sum, r) => sum + Number(r.consumption || 0), 0)
  }, [readings])

  const currentTierLabel = useMemo(() => {
    const breakdown = latestInvoice?.lineItems ?? billEstimate?.tierBreakdown ?? []
    const highest = breakdown.filter((line) => Number(line.unitsInTier) > 0).at(-1)
    return highest ? `Tier ${highest.tierOrder}` : '--'
  }, [billEstimate, latestInvoice])

  return (
    <>
      <div className="af-resident__welcome">
        <div>
          <p className="af-resident__eyebrow">Hello {user?.fullName?.split(' ')[0] || 'there'},</p>
          <h2>Stay close to your home&apos;s water story.</h2>
          <p>Usage and billing insights will appear here as your community records readings.</p>
        </div>
        <button type="button" className="af-resident__badges-btn" disabled title="Coming soon">
          <Icon name="award" /> View Eco-Badges
        </button>
      </div>

      <div className="af-resident__stats">
        <article className="af-resident__stat">
          <p>Today&apos;s Usage</p>
          <strong>{hasReading ? `${dashboard.latestReadingValue}` : '--'} <small>kL</small></strong>
          <span className="af-resident__muted">{hasReading ? `As of ${dashboard.latestReadingDate}` : 'No meter reading yet'}</span>
        </article>
        <article className="af-resident__stat">
          <p>Monthly Total (this month)</p>
          <strong>{monthTotal != null ? monthTotal.toFixed(1) : '--'} <small>kL</small></strong>
          <span className="af-resident__muted">Sum of recorded readings</span>
        </article>
        <article className="af-resident__stat">
          <p>Current Estimated Bill</p>
          <strong>{billEstimate ? `₹${Number(billEstimate.totalAmount).toLocaleString('en-IN')}` : '--'}</strong>
          <span className="af-resident__muted">Based on this month&apos;s metered consumption</span>
        </article>
        <article className="af-resident__stat">
          <p>Savings vs Building Avg</p>
          <strong className="af-resident__good">12% Less</strong>
          <span className="af-resident__muted af-resident__muted--pending">Preview only — comparison not yet live</span>
        </article>
      </div>

      <div className="af-resident__lower-grid">
        <section className="af-resident__panel">
          <div className="af-resident__panel-heading">
            <div>
              <p className="af-resident__eyebrow">Consumption</p>
              <h3>Daily Consumption (Last 14 Readings)</h3>
            </div>
          </div>
          {dailyTrend.length > 0 ? (
            <div className="af-resident__chart">
              <ResponsiveContainer width="100%" height={220}>
                <LineChart data={dailyTrend} margin={{ top: 8, right: 16, left: 0, bottom: 0 }}>
                  <XAxis dataKey="date" stroke="var(--afr-chart-axis)" tick={{ fontSize: 11 }} tickLine={false} axisLine={false} />
                  <YAxis stroke="var(--afr-chart-axis)" tick={{ fontSize: 11 }} tickLine={false} axisLine={false} width={36} />
                  <Tooltip contentStyle={{ borderRadius: 10, border: '1px solid var(--afr-border)', fontSize: 12 }} formatter={(v) => [`${v} kL`, 'Consumption']} />
                  <Line type="monotone" dataKey="value" stroke="var(--afr-chart-1)" strokeWidth={2} dot={{ r: 3 }} activeDot={{ r: 5 }} />
                </LineChart>
              </ResponsiveContainer>
            </div>
          ) : (
            <p className="af-resident__empty">No readings recorded yet.</p>
          )}
        </section>

        <section className="af-resident__panel">
          <p className="af-resident__eyebrow">Breakdown</p>
          <h3>Usage Breakdown</h3>
          <div className="af-resident__donut">
            <ResponsiveContainer width="100%" height={160}>
              <PieChart>
                <Pie data={USAGE_BREAKDOWN} dataKey="value" nameKey="name" innerRadius={48} outerRadius={70} paddingAngle={2}>
                  {USAGE_BREAKDOWN.map((entry) => (
                    <Cell key={entry.name} fill={entry.color} />
                  ))}
                </Pie>
                <Tooltip formatter={(v, n) => [`${v}%`, n]} contentStyle={{ borderRadius: 10, border: '1px solid var(--afr-border)', fontSize: 12 }} />
              </PieChart>
            </ResponsiveContainer>
          </div>
          <ul className="af-resident__legend">
            {USAGE_BREAKDOWN.map((entry) => (
              <li key={entry.name}>
                <span className="af-resident__legend-dot" style={{ background: entry.color }} />
                <span>{entry.name}</span>
                <strong>{entry.value}%</strong>
              </li>
            ))}
          </ul>
        </section>
      </div>

      <div className="af-resident__lower-grid">
        <section className="af-resident__panel">
          <div className="af-resident__panel-heading">
            <div>
              <p className="af-resident__eyebrow">Billing</p>
              <h3>Active Billing Cycle</h3>
            </div>
            <span className="af-resident__status">{latestInvoice ? 'Finalized' : 'Preview'}</span>
          </div>
          <div className="af-resident__details">
            <div>
              <span>Metered consumption</span>
              <strong>
                {latestInvoice
                  ? `${Number(latestInvoice.consumptionUnits).toFixed(1)} kL`
                  : monthTotal != null ? `${monthTotal.toFixed(1)} kL` : '--'}
              </strong>
            </div>
            <div><span>Tier price level</span><strong className="af-resident__warn">{currentTierLabel}</strong></div>
            <div>
              <span>{latestInvoice ? 'Common area apportionment' : 'Common area apportionment (preview)'}</span>
              <strong>
                {latestInvoice ? `₹${Number(latestInvoice.commonAreaAmount).toLocaleString('en-IN')}` : '₹240'}
              </strong>
            </div>
            <div>
              <span>{latestInvoice ? `${latestInvoice.periodMonth} invoice total` : 'Current estimated charge'}</span>
              <strong>
                {latestInvoice
                  ? `₹${Number(latestInvoice.totalAmount).toLocaleString('en-IN')}`
                  : billEstimate ? `₹${Number(billEstimate.totalAmount).toLocaleString('en-IN')}` : '--'}
              </strong>
            </div>
            {latestInvoice?.commonAreaCharges?.length > 0 && (
              <div className="af-resident__common-area-breakdown">
                <span>Common area breakdown</span>
                {latestInvoice.commonAreaCharges.map((charge) => (
                  <div key={charge.costItemId ?? charge.label}>
                    <span>{charge.label}</span>
                    <strong>₹{Number(charge.amount).toLocaleString('en-IN')}</strong>
                  </div>
                ))}
              </div>
            )}
          </div>
          <button type="button" className="af-resident__cta" disabled title="Billing engine not yet available">Pay Bill Preview</button>
        </section>

        <section className="af-resident__panel">
          <p className="af-resident__eyebrow">Tips</p>
          <h3>Smart Saving Tips</h3>
          <ul className="af-resident__tips">
            {SAVING_TIPS.map((tip) => (
              <li key={tip.text}>
                <span className="af-resident__tip-icon"><Icon name={tip.icon} /></span>
                <span>{tip.text}</span>
              </li>
            ))}
          </ul>
        </section>
      </div>

      {communityName && (
        <p className="af-resident__footnote">Community: {communityName}</p>
      )}
    </>
  )
}

export default ResidentHome
