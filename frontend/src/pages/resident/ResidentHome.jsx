import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Cell, Line, LineChart, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { getMyDashboard, getMyUsageHistory } from '../../api/residentDashboardApi'
import { getBillEstimate } from '../../api/tariffApi'
import { getMyInvoices } from '../../api/billingApi'
import { getRecommendations } from '../../api/recommendationApi'
import { Icon } from '../../components/resident/Icon'
import { useAuth } from '../../context/useAuth'

// Usage doesn't yet break down by fixture, and the billing engine (Phase 2)
// isn't built — these mirror the Tariff Plans page's approach of previewing
// the intended UI with representative sample data until that data exists.
const USAGE_BREAKDOWN = [
  { nameKey: 'home.breakdown.bathroom', value: 45, color: 'var(--afr-chart-1)' },
  { nameKey: 'home.breakdown.kitchen', value: 35, color: 'var(--afr-chart-2)' },
  { nameKey: 'home.breakdown.garden', value: 15, color: 'var(--afr-chart-3)' },
  { nameKey: 'home.breakdown.other', value: 5, color: 'var(--afr-chart-5)' },
]

function monthLabel(date, locale) {
  return new Date(date).toLocaleDateString(locale, { month: 'short', day: 'numeric' })
}

function yearMonthLabel(ym, locale) {
  const [year, month] = ym.split('-')
  return new Date(Number(year), Number(month) - 1, 1).toLocaleDateString(locale, { month: 'short', year: 'numeric' })
}

function ResidentHome() {
  const { user } = useAuth()
  const { t, i18n } = useTranslation('resident')
  const locale = i18n.resolvedLanguage || 'en'
  const [dashboard, setDashboard] = useState(null)
  const [readings, setReadings] = useState([])
  const [billEstimate, setBillEstimate] = useState(null)
  const [latestInvoice, setLatestInvoice] = useState(null)
  const [tips, setTips] = useState([])

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

  // Tips are generated in the active UI language, so refetch whenever the language changes.
  useEffect(() => {
    let cancelled = false
    getRecommendations()
      .then((data) => {
        if (!cancelled) setTips((data.tips || []).slice(0, 3))
      })
      .catch(() => {
        // No recommendations generated yet — the tips panel just shows its empty state.
      })
    return () => {
      cancelled = true
    }
  }, [i18n.resolvedLanguage])

  const communityName = dashboard?.communityName ?? user?.communityName
  const hasReading = dashboard?.latestReadingValue != null && dashboard?.latestReadingDate != null

  const dailyTrend = useMemo(
    () =>
      readings.slice(-14).map((r) => ({
        date: monthLabel(r.readingDate, locale),
        value: Number(r.consumption ?? 0),
      })),
    [readings, locale],
  )

  // Group by consumptionMonth, not readingDate: consumption is the delta since the previous
  // reading, so it belongs to the month the usage actually happened in, which is the month of
  // the earlier reading — not the (later) month the reading confirming it was recorded in.
  const monthTotal = useMemo(() => {
    if (readings.length === 0) return null

    const now = new Date()
    const currentYm = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
    const monthsWithReadings = new Set(readings.map((r) => r.consumptionMonth).filter(Boolean))
    // Fall back to the most recent month with readings if nothing's been logged yet for the
    // real current calendar month, rather than showing a misleading 0.0 kL.
    const ym = monthsWithReadings.has(currentYm) ? currentYm : [...monthsWithReadings].sort().at(-1)
    if (!ym) return null

    return {
      total: readings.filter((r) => r.consumptionMonth === ym).reduce((sum, r) => sum + Number(r.consumption || 0), 0),
      isCurrentMonth: ym === currentYm,
      ym,
    }
  }, [readings])

  const currentTierLabel = useMemo(() => {
    const breakdown = latestInvoice?.lineItems ?? billEstimate?.tierBreakdown ?? []
    const highest = breakdown.filter((line) => Number(line.unitsInTier) > 0).at(-1)
    return highest ? t('home.billing.tierLabel', { tier: highest.tierOrder }) : '--'
  }, [billEstimate, latestInvoice, t])

  const usageBreakdown = USAGE_BREAKDOWN.map((entry) => ({ ...entry, name: t(entry.nameKey) }))
  const firstName = user?.fullName?.split(' ')[0]

  return (
    <>
      <div className="af-resident__welcome">
        <div>
          <p className="af-resident__eyebrow">
            {firstName ? t('home.welcome.greeting', { name: firstName }) : t('home.welcome.greetingFallback')}
          </p>
          <h2>{t('home.welcome.title')}</h2>
          <p>{t('home.welcome.subtitle')}</p>
        </div>
        <button type="button" className="af-resident__badges-btn" disabled title={t('home.comingSoon')}>
          <Icon name="award" /> {t('home.welcome.viewBadges')}
        </button>
      </div>

      <div className="af-resident__stats">
        <article className="af-resident__stat">
          <p>{t('home.stats.todaysUsage')}</p>
          <strong>{hasReading ? `${dashboard.latestReadingValue}` : '--'} <small>kL</small></strong>
          <span className="af-resident__muted">{hasReading ? t('home.stats.asOf', { date: dashboard.latestReadingDate }) : t('home.stats.noReading')}</span>
        </article>
        <article className="af-resident__stat">
          <p>{monthTotal && !monthTotal.isCurrentMonth ? t('home.stats.monthlyTotalFor', { month: yearMonthLabel(monthTotal.ym, locale) }) : t('home.stats.monthlyTotalThisMonth')}</p>
          <strong>{monthTotal != null ? monthTotal.total.toFixed(1) : '--'} <small>kL</small></strong>
          <span className="af-resident__muted">{t('home.stats.sumOfReadings')}</span>
        </article>
        <article className="af-resident__stat">
          <p>{t('home.stats.estimatedBill')}</p>
          <strong>{billEstimate ? `₹${Number(billEstimate.totalAmount).toLocaleString('en-IN')}` : '--'}</strong>
          <span className="af-resident__muted">
            {billEstimate
              ? t('home.stats.estimatedBillFor', { month: yearMonthLabel(billEstimate.periodMonth, locale) })
              : t('home.stats.estimatedBillHint')}
          </span>
        </article>
        <article className="af-resident__stat">
          <p>{t('home.stats.savings')}</p>
          <strong className="af-resident__good">{t('home.stats.savingsValue', { percent: 12 })}</strong>
          <span className="af-resident__muted af-resident__muted--pending">{t('home.stats.savingsPreview')}</span>
        </article>
      </div>

      <div className="af-resident__lower-grid">
        <section className="af-resident__panel">
          <div className="af-resident__panel-heading">
            <div>
              <p className="af-resident__eyebrow">{t('home.consumption.eyebrow')}</p>
              <h3>{t('home.consumption.title')}</h3>
            </div>
          </div>
          {dailyTrend.length > 0 ? (
            <div className="af-resident__chart">
              <ResponsiveContainer width="100%" height={220}>
                <LineChart data={dailyTrend} margin={{ top: 8, right: 16, left: 0, bottom: 0 }}>
                  <XAxis dataKey="date" stroke="var(--afr-chart-axis)" tick={{ fontSize: 11 }} tickLine={false} axisLine={false} />
                  <YAxis stroke="var(--afr-chart-axis)" tick={{ fontSize: 11 }} tickLine={false} axisLine={false} width={36} />
                  <Tooltip contentStyle={{ borderRadius: 10, border: '1px solid var(--afr-border)', fontSize: 12 }} formatter={(v) => [`${v} kL`, t('home.consumption.seriesName')]} />
                  <Line type="monotone" dataKey="value" stroke="var(--afr-chart-1)" strokeWidth={2} dot={{ r: 3 }} activeDot={{ r: 5 }} />
                </LineChart>
              </ResponsiveContainer>
            </div>
          ) : (
            <p className="af-resident__empty">{t('home.consumption.empty')}</p>
          )}
        </section>

        <section className="af-resident__panel">
          <p className="af-resident__eyebrow">{t('home.breakdown.eyebrow')}</p>
          <h3>{t('home.breakdown.title')}</h3>
          <div className="af-resident__donut">
            <ResponsiveContainer width="100%" height={160}>
              <PieChart>
                <Pie data={usageBreakdown} dataKey="value" nameKey="name" innerRadius={48} outerRadius={70} paddingAngle={2}>
                  {usageBreakdown.map((entry) => (
                    <Cell key={entry.nameKey} fill={entry.color} />
                  ))}
                </Pie>
                <Tooltip formatter={(v, n) => [`${v}%`, n]} contentStyle={{ borderRadius: 10, border: '1px solid var(--afr-border)', fontSize: 12 }} />
              </PieChart>
            </ResponsiveContainer>
          </div>
          <ul className="af-resident__legend">
            {usageBreakdown.map((entry) => (
              <li key={entry.nameKey}>
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
              <p className="af-resident__eyebrow">{t('home.billing.eyebrow')}</p>
              <h3>{t('home.billing.title')}</h3>
            </div>
            <span className="af-resident__status">{latestInvoice ? t('home.billing.finalized') : t('home.billing.preview')}</span>
          </div>
          <div className="af-resident__details">
            <div>
              <span>{t('home.billing.meteredConsumption')}</span>
              <strong>
                {latestInvoice
                  ? `${Number(latestInvoice.consumptionUnits).toFixed(1)} kL`
                  : monthTotal != null ? `${monthTotal.total.toFixed(1)} kL` : '--'}
              </strong>
            </div>
            <div><span>{t('home.billing.tierPriceLevel')}</span><strong className="af-resident__warn">{currentTierLabel}</strong></div>
            <div>
              <span>{latestInvoice ? t('home.billing.commonArea') : t('home.billing.commonAreaPreview')}</span>
              <strong>
                {latestInvoice ? `₹${Number(latestInvoice.commonAreaAmount).toLocaleString('en-IN')}` : '₹240'}
              </strong>
            </div>
            <div>
              <span>{latestInvoice ? t('home.billing.invoiceTotal', { period: latestInvoice.periodMonth }) : t('home.billing.estimatedCharge')}</span>
              <strong>
                {latestInvoice
                  ? `₹${Number(latestInvoice.totalAmount).toLocaleString('en-IN')}`
                  : billEstimate ? `₹${Number(billEstimate.totalAmount).toLocaleString('en-IN')}` : '--'}
              </strong>
            </div>
            {latestInvoice?.commonAreaCharges?.length > 0 && (
              <div className="af-resident__common-area-breakdown">
                <span>{t('home.billing.commonAreaBreakdown')}</span>
                {latestInvoice.commonAreaCharges.map((charge) => (
                  <div key={charge.costItemId ?? charge.label}>
                    <span>{charge.label}</span>
                    <strong>₹{Number(charge.amount).toLocaleString('en-IN')}</strong>
                  </div>
                ))}
              </div>
            )}
          </div>
          <button type="button" className="af-resident__cta" disabled title={t('home.billing.payUnavailable')}>{t('home.billing.payPreview')}</button>
        </section>

        <section className="af-resident__panel">
          <div className="af-resident__panel-heading">
            <div>
              <p className="af-resident__eyebrow">{t('home.tips.eyebrow')}</p>
              <h3>{t('home.tips.title')}</h3>
            </div>
            <Link to="/resident/recommendations" className="af-resident__status">{t('home.tips.viewAll')}</Link>
          </div>
          {tips.length > 0 ? (
            <ul className="af-resident__tips">
              {tips.map((tip) => (
                <li key={tip}>
                  <span className="af-resident__tip-icon"><Icon name="bulb" /></span>
                  <span>{tip}</span>
                </li>
              ))}
            </ul>
          ) : (
            <p className="af-resident__muted">
              {t('home.tips.empty')}
            </p>
          )}
        </section>
      </div>

      {communityName && (
        <p className="af-resident__footnote">{t('home.community', { name: communityName })}</p>
      )}
    </>
  )
}

export default ResidentHome
