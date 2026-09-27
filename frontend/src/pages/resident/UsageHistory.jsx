import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { getMyDashboard, getMyUsageComparison, getMyUsageHistory } from '../../api/residentDashboardApi'
import './UsageHistory.css'

function monthLabel(ym, locale) {
  const [year, month] = ym.split('-')
  const date = new Date(Number(year), Number(month) - 1, 1)
  return date.toLocaleDateString(locale, { month: 'short' })
}

// No cross-resident comparison endpoint exists yet — the ranking list mirrors
// the Tariff Plans page's approach of previewing the intended UI with
// representative sample data until that data exists.
const PEER_RANKING = [
  { unit: '204', labelKey: 'usage.ranking.ecoSaver', value: 4.8, rank: 1 },
  { unit: '108', labelKey: null, value: 5.4, rank: 4 },
  { unit: null, labelKey: null, value: null, rank: null, isYou: true },
  { unit: '301', labelKey: null, value: 7.9, rank: 24 },
]

const HEATMAP_INTENSITY = [
  2, 1, 0, 2, 1, 3, 0,
  1, 0, 2, 3, 0, 1, 2,
  0, 3, 1, 0, 2, 1, 0,
  2, 0, 1, 3, 1, 0, 2,
]

function UsageHistory() {
  const { t, i18n } = useTranslation('resident')
  const locale = i18n.resolvedLanguage || 'en'
  const [readings, setReadings] = useState([])
  const [comparison, setComparison] = useState([])
  const [houseNumber, setHouseNumber] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    Promise.all([getMyUsageHistory(), getMyUsageComparison(), getMyDashboard().catch(() => null)])
      .then(([history, comparisonData, dashboard]) => {
        setReadings(history)
        setComparison(comparisonData)
        setHouseNumber(dashboard?.houseNumber ?? null)
      })
      .catch(() => setError(t('usage.loadError')))
      .finally(() => setLoading(false))
  }, [t])

  // The comparison endpoint already groups by consumptionMonth server-side (see WaterUsageReadingService.
  // getComparisonForResident) — consumption is the delta since the previous reading, so it's attributed to
  // the month the usage actually happened in, not the (later) month the confirming reading was recorded in.
  const monthlyData = useMemo(() => {
    return comparison
      .slice()
      .sort((a, b) => a.month.localeCompare(b.month))
      .slice(-6)
      .map((point) => ({
        month: monthLabel(point.month, locale),
        household: Number(point.household),
        apartmentAverage: Number(point.apartmentAverage),
        similarSizedAverage: Number(point.similarSizedAverage),
      }))
  }, [comparison, locale])

  // Only show a separate "similar-sized homes" series when it's meaningfully different from the
  // plain apartment-wide average — otherwise a third near-identical bar just adds visual noise.
  const showSimilarSizedSeries = useMemo(
    () => monthlyData.some((point) => Math.abs(point.similarSizedAverage - point.apartmentAverage) > 0.05),
    [monthlyData]
  )

  const peakLow = useMemo(() => {
    const withConsumption = readings.filter((r) => r.consumption != null)
    if (withConsumption.length === 0) return null
    const peak = withConsumption.reduce((a, b) => (Number(b.consumption) > Number(a.consumption) ? b : a))
    const lowest = withConsumption.reduce((a, b) => (Number(b.consumption) < Number(a.consumption) ? b : a))
    return { peak, lowest }
  }, [readings])

  if (loading) {
    return <p className="af-uh__empty">{t('usage.loading')}</p>
  }

  if (error) {
    return <div className="af-uh__error">{error}</div>
  }

  if (readings.length === 0) {
    return (
      <div className="af-uh__panel">
        <p className="af-uh__empty">{t('usage.empty')}</p>
      </div>
    )
  }

  return (
    <>
      <div className="af-uh__toolbar">
        <div className="af-uh__tabs">
          <button type="button" className="af-uh__tab af-uh__tab--active">{t('usage.tabs.lastSixMonths')}</button>
          <button type="button" className="af-uh__tab" disabled title={t('usage.tabs.comingSoon')}>{t('usage.tabs.customRange')}</button>
        </div>
        {peakLow && (
          <div className="af-uh__peaklow">
            <span className="af-uh__peak">{t('usage.peakDay')} <strong>{peakLow.peak.consumption} kL</strong> ({peakLow.peak.readingDate})</span>
            <span className="af-uh__low">{t('usage.lowestDay')} <strong>{peakLow.lowest.consumption} kL</strong> ({peakLow.lowest.readingDate})</span>
          </div>
        )}
      </div>

      <div className="af-uh__panel">
        <h3>{t('usage.comparison.title')}</h3>
        <p className="af-uh__subtitle">{t('usage.comparison.subtitle')}</p>
        <div className="af-uh__chart">
          <ResponsiveContainer width="100%" height={260}>
            <BarChart data={monthlyData} margin={{ top: 8, right: 16, left: 0, bottom: 0 }}>
              <CartesianGrid stroke="var(--afr-chart-grid)" vertical={false} />
              <XAxis dataKey="month" stroke="var(--afr-chart-axis)" tick={{ fontSize: 11 }} tickLine={false} axisLine={false} />
              <YAxis stroke="var(--afr-chart-axis)" tick={{ fontSize: 11 }} tickLine={false} axisLine={false} width={40} />
              <Tooltip contentStyle={{ borderRadius: 10, border: '1px solid var(--afr-border)', fontSize: 12 }} />
              <Legend wrapperStyle={{ fontSize: 12 }} />
              <Bar dataKey="household" name={t('usage.comparison.yourHousehold')} fill="var(--afr-chart-1)" radius={[4, 4, 0, 0]} maxBarSize={28} />
              <Bar dataKey="apartmentAverage" name={t('usage.comparison.apartmentAverage')} fill="var(--afr-chart-3)" radius={[4, 4, 0, 0]} maxBarSize={28} />
              {showSimilarSizedSeries && (
                <Bar dataKey="similarSizedAverage" name={t('usage.comparison.similarSized')} fill="var(--afr-chart-4)" radius={[4, 4, 0, 0]} maxBarSize={28} />
              )}
            </BarChart>
          </ResponsiveContainer>
        </div>
      </div>

      <div className="af-resident__lower-grid">
        <section className="af-uh__panel">
          <h3>{t('usage.heatmap.title')}</h3>
          <p className="af-uh__subtitle">{t('usage.heatmap.subtitle')}</p>
          <div className="af-uh__heatmap">
            {HEATMAP_INTENSITY.map((level, i) => (
              <span key={i} className={`af-uh__heat af-uh__heat--${level}`} />
            ))}
          </div>
        </section>

        <section className="af-uh__panel">
          <h3>{t('usage.ranking.title')}</h3>
          <p className="af-uh__subtitle">{t('usage.ranking.subtitle')}</p>
          <ul className="af-uh__ranking">
            {PEER_RANKING.map((row) => (
              <li key={row.unit || 'you'} className={row.isYou ? 'af-uh__ranking-row--you' : ''}>
                <span className="af-uh__ranking-label">
                  {row.isYou
                    ? t('usage.ranking.youRow', {
                        rank: 18,
                        unit: houseNumber ? t('usage.ranking.unitNumber', { number: houseNumber }) : t('usage.ranking.yourUnit'),
                      })
                    : row.labelKey
                      ? t('usage.ranking.rowWithLabel', { rank: row.rank, unit: t('usage.ranking.aptNumber', { number: row.unit }), label: t(row.labelKey) })
                      : t('usage.ranking.row', { rank: row.rank, unit: t('usage.ranking.aptNumber', { number: row.unit }) })}
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
