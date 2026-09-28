import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { getMyDashboard, getMyUsageComparison, getMyUsageHistory } from '../../api/residentDashboardApi'
import { getBillEstimate } from '../../api/tariffApi'
import { getMyInvoices, payAllOutstanding } from '../../api/billingApi'
import { getRecommendations } from '../../api/recommendationApi'
import { Icon } from '../../components/resident/Icon'
import { useAuth } from '../../context/useAuth'
import RazorpaySimulatorModal from './RazorpaySimulatorModal'

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
  const [invoices, setInvoices] = useState([])
  const [comparison, setComparison] = useState([])
  const [payingAll, setPayingAll] = useState(false)
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
      .then(setInvoices)
      .catch(() => {
        // No finalized invoices yet — the billing cycle panel falls back to the live estimate.
      })
    getMyUsageComparison()
      .then(setComparison)
      .catch(() => {
        // No comparison data — the savings stat shows its empty state.
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

  const latestInvoice = invoices[0] ?? null
  const unpaidInvoices = invoices.filter((inv) => !inv.paid)
  const outstandingTotal = unpaidInvoices.reduce((sum, inv) => sum + Number(inv.totalAmount), 0)

  function handlePayAllSuccess() {
    return payAllOutstanding().then((paid) => {
      const byId = new Map(paid.map((inv) => [inv.id, inv]))
      setInvoices((prev) => prev.map((inv) => byId.get(inv.id) ?? inv))
      setPayingAll(false)
    })
  }

  // Latest month with comparison data: household vs apartment average (positive = saved water).
  const savings = useMemo(() => {
    const latest = comparison.slice().sort((a, b) => a.month.localeCompare(b.month)).at(-1)
    if (!latest) return null
    const household = Number(latest.household)
    const average = Number(latest.apartmentAverage)
    if (!(average > 0)) return null
    const diffKl = average - household
    return { month: latest.month, household, average, diffKl, percent: Math.abs((diffKl / average) * 100) }
  }, [comparison])

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
          {savings ? (
            <>
              <strong className={savings.diffKl >= 0 ? 'af-resident__good' : 'af-resident__bad'}>
                {Math.abs(savings.diffKl) < 0.05
                  ? t('home.stats.savingsEqual')
                  : savings.diffKl > 0
                    ? t('home.stats.savingsLess', { percent: savings.percent.toFixed(0) })
                    : t('home.stats.savingsMore', { percent: savings.percent.toFixed(0) })}
              </strong>
              <span className="af-resident__muted">
                {t('home.stats.savingsDetail', {
                  household: savings.household.toFixed(1),
                  average: savings.average.toFixed(1),
                  month: yearMonthLabel(savings.month, locale),
                })}
              </span>
            </>
          ) : (
            <>
              <strong>--</strong>
              <span className="af-resident__muted">{t('home.stats.savingsNoData')}</span>
            </>
          )}
        </article>
      </div>

      <div className="af-resident__lower-grid af-resident__lower-grid--single">
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
              <span>{latestInvoice ? t('home.billing.commonArea') : t('home.billing.commonAreaEstimate')}</span>
              <strong>
                {latestInvoice
                  ? `₹${Number(latestInvoice.commonAreaAmount).toLocaleString('en-IN')}`
                  : billEstimate ? `₹${Number(billEstimate.commonAreaAmount).toLocaleString('en-IN')}` : '--'}
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
          {unpaidInvoices.length > 0 && (
            <button type="button" className="af-resident__cta" onClick={() => setPayingAll(true)}>
              {t('home.billing.payOutstanding', {
                amount: outstandingTotal.toLocaleString('en-IN', { minimumFractionDigits: 2 }),
              })}
            </button>
          )}
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

      {payingAll && (
        <RazorpaySimulatorModal
          amount={outstandingTotal}
          description={t('home.billing.payAllDescription', { number: unpaidInvoices.length })}
          onClose={() => setPayingAll(false)}
          onSuccess={handlePayAllSuccess}
        />
      )}

      {communityName && (
        <p className="af-resident__footnote">{t('home.community', { name: communityName })}</p>
      )}
    </>
  )
}

export default ResidentHome
