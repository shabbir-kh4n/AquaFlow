import { useEffect, useMemo, useState } from 'react'
import i18next from 'i18next'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { listAlerts } from '../../api/alertApi'
import { listResidents } from '../../api/residentApi'
import { listUsageReadings } from '../../api/meterUsageApi'
import { useAuth } from '../../context/useAuth'
import './AdminDashboard.css'

function monthLabel(ym, locale) {
  const [year, month] = ym.split('-')
  const date = new Date(Number(year), Number(month) - 1, 1)
  return date.toLocaleDateString(locale, { month: 'short' })
}

function AdminDashboard() {
  const { user } = useAuth()
  const { t, i18n } = useTranslation('admin')
  const locale = i18n.resolvedLanguage || 'en'
  const [residents, setResidents] = useState([])
  const [readings, setReadings] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  // null = not loaded yet / failed to load, shown as a dash rather than a misleading 0.
  const [activeAlertCount, setActiveAlertCount] = useState(null)

  useEffect(() => {
    Promise.all([listResidents(), listUsageReadings({})])
      .then(([residentData, readingData]) => {
        setResidents(residentData)
        setReadings(readingData)
      })
      .catch(() => setError(i18next.t('dashboard.loadError', { ns: 'admin' })))
      .finally(() => setLoading(false))

    // Loaded separately so an alert-engine hiccup never blanks the rest of the dashboard.
    listAlerts('ACTIVE')
      .then((alerts) => setActiveAlertCount(alerts.length))
      .catch(() => setActiveAlertCount(null))
  }, [])

  const meterToResident = useMemo(
    () => new Map(residents.map((r) => [r.meterNumber, r])),
    [residents],
  )

  const activeMeterCount = useMemo(
    () => new Set(readings.map((r) => r.meterNumber)).size,
    [readings],
  )

  // Group by consumptionMonth, not readingDate: consumption is the delta since the previous
  // reading, so it belongs to the month the usage actually happened in, which is the month of
  // the earlier reading — not the (later) month the reading confirming it was recorded in.
  const monthlyTotals = useMemo(() => {
    const totals = new Map()
    readings.forEach((r) => {
      const ym = r.consumptionMonth
      if (!ym) return
      totals.set(ym, (totals.get(ym) || 0) + Number(r.consumption || 0))
    })
    return Array.from(totals.entries())
      .sort(([a], [b]) => a.localeCompare(b))
      .slice(-5)
      .map(([ym, actual]) => ({
        month: monthLabel(ym, locale),
        actual: Number(actual.toFixed(2)),
      }))
  }, [readings, locale])

  const currentMonthUsage = useMemo(() => {
    const now = new Date()
    const currentYm = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`

    const monthsWithReadings = new Set(readings.map((r) => r.consumptionMonth).filter(Boolean))
    // Fall back to the most recent month with readings if nothing's been logged yet for the
    // real current calendar month, rather than showing a misleading 0.0 kL.
    const ym = monthsWithReadings.has(currentYm) ? currentYm : [...monthsWithReadings].sort().at(-1)

    const total = ym
      ? readings.filter((r) => r.consumptionMonth === ym).reduce((sum, r) => sum + Number(r.consumption || 0), 0)
      : 0

    return { ym, total, isCurrentMonth: ym === currentYm }
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
          <h2>
            {user?.communityName
              ? t('dashboard.title', { community: user.communityName })
              : t('dashboard.titleFallback')}
          </h2>
          <p>{t('dashboard.subtitle')}</p>
        </div>
        <Link to="/admin/households" className="af-dash__banner-btn">
          {t('dashboard.manageCommunity')}
        </Link>
      </div>

      {error && <div className="af-dash__error">{error}</div>}

      <div className="af-dash__grid">
        <article className="af-dash__card">
          <span className="af-dash__label">{t('dashboard.cards.totalHouseholds')}</span>
          <span className="af-dash__value">{loading ? '—' : residents.length}</span>
          <span className="af-dash__muted">{t('dashboard.cards.occupied')}</span>
        </article>

        <article className="af-dash__card">
          <span className="af-dash__label">{t('dashboard.cards.activeMeters')}</span>
          <span className="af-dash__value">
            {loading ? '—' : `${activeMeterCount} / ${residents.length}`}
          </span>
          <span className="af-dash__muted">{t('dashboard.cards.reportingReadings')}</span>
        </article>

        <article className="af-dash__card">
          <span className="af-dash__label">{t('dashboard.cards.monthlyConsumption')}</span>
          <span className="af-dash__value">{loading ? '—' : `${currentMonthUsage.total.toFixed(1)} kL`}</span>
          <span className="af-dash__muted">
            {loading || currentMonthUsage.isCurrentMonth
              ? t('dashboard.cards.thisCalendarMonth')
              : currentMonthUsage.ym
                ? t('dashboard.cards.latestRecordedMonth', { month: monthLabel(currentMonthUsage.ym, locale) })
                : t('dashboard.cards.noReadingsYet')}
          </span>
        </article>

        <Link to="/admin/water-leakage" className="af-dash__card af-dash__card--link">
          <span className="af-dash__label">{t('dashboard.cards.pendingAlerts')}</span>
          <span className={`af-dash__value${activeAlertCount ? ' af-dash__value--warn' : ''}`}>
            {activeAlertCount ?? '—'}
          </span>
          <span className="af-dash__muted">
            {activeAlertCount ? t('dashboard.cards.reviewAlerts') : t('dashboard.cards.noActiveAlerts')}
          </span>
        </Link>
      </div>

      <div className="af-dash__actions">
        <p className="af-dash__actions-label">{t('dashboard.actions.label')}</p>
        <div className="af-dash__actions-row">
          <Link to="/admin/meter-readings" className="af-dash__action af-dash__action--primary">
            {t('dashboard.actions.uploadReadings')}
          </Link>
          <Link to="/admin/tariff-plans" className="af-dash__action">
            {t('dashboard.actions.configureTariffs')}
          </Link>
          <Link to="/admin/reports" className="af-dash__action">
            {t('dashboard.actions.downloadReports')}
          </Link>
        </div>
      </div>

      <div className="af-dash__charts">
        <div className="af-dash__chart-card">
          <h3>{t('dashboard.chart.title')}</h3>
          <p className="af-dash__chart-subtitle">{t('dashboard.chart.subtitle', { months: monthlyTotals.length || 5 })}</p>
          {loading ? (
            <p className="af-dash__chart-empty">{t('shared.loading')}</p>
          ) : monthlyTotals.length === 0 ? (
            <p className="af-dash__chart-empty">{t('dashboard.noUsageReadings')}</p>
          ) : (
            <ResponsiveContainer width="100%" height={280}>
              <BarChart data={monthlyTotals} margin={{ top: 8, right: 16, left: 0, bottom: 0 }}>
                <CartesianGrid stroke="var(--af-chart-grid)" vertical={false} />
                <XAxis dataKey="month" stroke="var(--af-chart-axis)" tick={{ fontSize: 11 }} tickLine={false} axisLine={false} />
                <YAxis stroke="var(--af-chart-axis)" tick={{ fontSize: 11 }} tickLine={false} axisLine={false} width={40} />
                <Tooltip contentStyle={{ borderRadius: 12, border: '1px solid var(--af-glass-border)', fontSize: 12 }} />
                <Bar dataKey="actual" name={t('dashboard.chart.actualSeries')} fill="var(--af-chart-1)" radius={[4, 4, 0, 0]} maxBarSize={28} />
              </BarChart>
            </ResponsiveContainer>
          )}
        </div>

        <div className="af-dash__recent-card">
          <h3>{t('dashboard.recent.title')}</h3>
          {loading ? (
            <p className="af-dash__chart-empty">{t('shared.loading')}</p>
          ) : recentReadings.length === 0 ? (
            <p className="af-dash__chart-empty">{t('dashboard.noUsageReadings')}</p>
          ) : (
            <table className="af-dash__table">
              <thead>
                <tr>
                  <th>{t('dashboard.recent.unit')}</th>
                  <th>{t('dashboard.recent.resident')}</th>
                  <th>{t('dashboard.recent.lastReading')}</th>
                  <th>{t('dashboard.recent.consumption')}</th>
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
