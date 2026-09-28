import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { getMyPeerRanking, getMyUsageComparison, getMyUsageHistory } from '../../api/residentDashboardApi'
import './UsageHistory.css'

const HEATMAP_MONTHS = 12
// Above this many households, the ranking list shows the top few plus the rows around "You".
const RANKING_FULL_LIST_MAX = 7

function monthLabel(ym, locale, withYear = false) {
  const [year, month] = ym.split('-')
  const date = new Date(Number(year), Number(month) - 1, 1)
  return date.toLocaleDateString(locale, withYear ? { month: 'short', year: '2-digit' } : { month: 'short' })
}

function shiftMonth(ym, delta) {
  const [year, month] = ym.split('-').map(Number)
  const date = new Date(year, month - 1 + delta, 1)
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
}

function currentYearMonth() {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}

// Picks which ranking rows to show: all of them for small apartments, otherwise the top 3 plus
// the caller's row and its immediate neighbours, with `null` marking a gap.
function visibleRankingRows(entries) {
  if (entries.length <= RANKING_FULL_LIST_MAX) return entries
  const selfIndex = entries.findIndex((e) => e.self)
  const keep = new Set([0, 1, 2, selfIndex - 1, selfIndex, selfIndex + 1].filter((i) => i >= 0 && i < entries.length))
  const rows = []
  let last = -1
  ;[...keep].sort((a, b) => a - b).forEach((i) => {
    if (i - last > 1) rows.push(null)
    rows.push(entries[i])
    last = i
  })
  if (last < entries.length - 1) rows.push(null)
  return rows
}

function UsageHistory() {
  const { t, i18n } = useTranslation('resident')
  const locale = i18n.resolvedLanguage || 'en'
  const [readings, setReadings] = useState([])
  const [comparison, setComparison] = useState([])
  const [ranking, setRanking] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [mode, setMode] = useState('sixMonths') // sixMonths | custom
  const [customFrom, setCustomFrom] = useState('')
  const [customTo, setCustomTo] = useState('')

  useEffect(() => {
    Promise.all([getMyUsageHistory(), getMyUsageComparison(), getMyPeerRanking().catch(() => null)])
      .then(([history, comparisonData, rankingData]) => {
        setReadings(history)
        setComparison(comparisonData)
        setRanking(rankingData)
      })
      .catch(() => setError(t('usage.loadError')))
      .finally(() => setLoading(false))
  }, [t])

  // The comparison endpoint already groups by consumptionMonth server-side (see WaterUsageReadingService.
  // getComparisonForResident) — consumption is the delta since the previous reading, so it's attributed to
  // the month the usage actually happened in, not the (later) month the confirming reading was recorded in.
  const sortedComparison = useMemo(
    () => comparison.slice().sort((a, b) => a.month.localeCompare(b.month)),
    [comparison],
  )
  const availableMonths = useMemo(() => sortedComparison.map((p) => p.month), [sortedComparison])

  const effectiveFrom = customFrom || availableMonths[0] || ''
  const effectiveTo = customTo || availableMonths.at(-1) || ''
  const customInvalid = mode === 'custom' && effectiveFrom && effectiveTo && effectiveFrom > effectiveTo

  const selectedPoints = useMemo(() => {
    if (mode === 'custom') {
      if (customInvalid) return []
      return sortedComparison.filter((p) => p.month >= effectiveFrom && p.month <= effectiveTo)
    }
    return sortedComparison.slice(-6)
  }, [mode, sortedComparison, effectiveFrom, effectiveTo, customInvalid])

  const selectedMonths = useMemo(() => new Set(selectedPoints.map((p) => p.month)), [selectedPoints])
  const spansYears = useMemo(
    () => new Set(selectedPoints.map((p) => p.month.slice(0, 4))).size > 1,
    [selectedPoints],
  )

  const monthlyData = useMemo(
    () =>
      selectedPoints.map((point) => ({
        month: monthLabel(point.month, locale, spansYears),
        household: Number(point.household),
        apartmentAverage: Number(point.apartmentAverage),
        similarSizedAverage: Number(point.similarSizedAverage),
      })),
    [selectedPoints, locale, spansYears],
  )

  // Only show a separate "similar-sized homes" series when it's meaningfully different from the
  // plain apartment-wide average — otherwise a third near-identical bar just adds visual noise.
  const showSimilarSizedSeries = useMemo(
    () => monthlyData.some((point) => Math.abs(point.similarSizedAverage - point.apartmentAverage) > 0.05),
    [monthlyData],
  )

  // Peak/lowest readings within the months currently shown in the chart.
  const peakLow = useMemo(() => {
    const withConsumption = readings.filter((r) => r.consumption != null && selectedMonths.has(r.consumptionMonth))
    if (withConsumption.length === 0) return null
    const peak = withConsumption.reduce((a, b) => (Number(b.consumption) > Number(a.consumption) ? b : a))
    const lowest = withConsumption.reduce((a, b) => (Number(b.consumption) < Number(a.consumption) ? b : a))
    return { peak, lowest }
  }, [readings, selectedMonths])

  // Real monthly heatmap: the household's own kL for the last 12 months (ending at the latest
  // month with data), intensity bucketed relative to the busiest month in that window.
  const heatmap = useMemo(() => {
    const byMonth = new Map(sortedComparison.map((p) => [p.month, Number(p.household)]))
    const end = availableMonths.at(-1) || currentYearMonth()
    const cells = []
    for (let i = HEATMAP_MONTHS - 1; i >= 0; i -= 1) {
      const ym = shiftMonth(end, -i)
      cells.push({ ym, value: byMonth.has(ym) ? byMonth.get(ym) : null })
    }
    const max = Math.max(0, ...cells.map((c) => c.value ?? 0))
    return cells.map((c) => ({
      ...c,
      level: c.value == null ? 'none' : max === 0 ? 0 : Math.min(4, Math.ceil((c.value / max) * 4)),
    }))
  }, [sortedComparison, availableMonths])

  const rankingRows = useMemo(() => visibleRankingRows(ranking?.entries ?? []), [ranking])

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
          <button
            type="button"
            className={`af-uh__tab ${mode === 'sixMonths' ? 'af-uh__tab--active' : ''}`}
            onClick={() => setMode('sixMonths')}
          >
            {t('usage.tabs.lastSixMonths')}
          </button>
          <button
            type="button"
            className={`af-uh__tab ${mode === 'custom' ? 'af-uh__tab--active' : ''}`}
            onClick={() => setMode('custom')}
          >
            {t('usage.tabs.customRange')}
          </button>
          {mode === 'custom' && (
            <div className="af-uh__range">
              <label>
                {t('usage.range.from')}
                <input type="month" value={effectiveFrom} onChange={(e) => setCustomFrom(e.target.value)} />
              </label>
              <label>
                {t('usage.range.to')}
                <input type="month" value={effectiveTo} onChange={(e) => setCustomTo(e.target.value)} />
              </label>
            </div>
          )}
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
        {customInvalid ? (
          <p className="af-uh__empty">{t('usage.range.invalid')}</p>
        ) : monthlyData.length === 0 ? (
          <p className="af-uh__empty">{t('usage.range.noData')}</p>
        ) : (
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
        )}
      </div>

      <div className="af-resident__lower-grid">
        <section className="af-uh__panel">
          <h3>{t('usage.heatmap.title')}</h3>
          <p className="af-uh__subtitle">{t('usage.heatmap.subtitle')}</p>
          <div className="af-uh__heatmap">
            {heatmap.map((cell) => (
              <div
                key={cell.ym}
                className={`af-uh__heat af-uh__heat--${cell.level}`}
                title={cell.value == null
                  ? t('usage.heatmap.cellNoData', { month: monthLabel(cell.ym, locale, true) })
                  : t('usage.heatmap.cellValue', { month: monthLabel(cell.ym, locale, true), value: cell.value.toFixed(1) })}
              >
                <span className="af-uh__heat-month">{monthLabel(cell.ym, locale, true)}</span>
                <span className="af-uh__heat-value">{cell.value == null ? '–' : cell.value.toFixed(1)}</span>
              </div>
            ))}
          </div>
          <div className="af-uh__heat-legend">
            <span>{t('usage.heatmap.less')}</span>
            {[0, 1, 2, 3, 4].map((level) => (
              <span key={level} className={`af-uh__heat-swatch af-uh__heat--${level}`} />
            ))}
            <span>{t('usage.heatmap.more')}</span>
            <span className="af-uh__heat-swatch af-uh__heat--none" />
            <span>{t('usage.heatmap.noData')}</span>
          </div>
        </section>

        <section className="af-uh__panel">
          <h3>{t('usage.ranking.title')}</h3>
          {ranking?.rank ? (
            <>
              <p className="af-uh__subtitle">
                {t('usage.ranking.subtitle', { month: monthLabel(ranking.month, locale, true) })}
              </p>
              <p className="af-uh__ranking-headline">
                {t('usage.ranking.headline', { rank: ranking.rank, total: ranking.totalHouseholds })}
              </p>
              {ranking.percentile != null && (
                <p className="af-uh__ranking-sub">
                  {t('usage.ranking.percentile', { percent: Number(ranking.percentile).toFixed(0) })}
                </p>
              )}
              <div className="af-uh__ranking-stats">
                <div><span>{t('usage.ranking.min')}</span><strong>{Number(ranking.apartmentMin).toFixed(1)} kL</strong></div>
                <div><span>{t('usage.ranking.median')}</span><strong>{Number(ranking.apartmentMedian).toFixed(1)} kL</strong></div>
                <div><span>{t('usage.ranking.max')}</span><strong>{Number(ranking.apartmentMax).toFixed(1)} kL</strong></div>
              </div>
              <ul className="af-uh__ranking">
                {rankingRows.map((row, i) =>
                  row == null ? (
                    <li key={`gap-${i}`} className="af-uh__ranking-gap" aria-hidden="true">⋯</li>
                  ) : (
                    <li key={`${row.rank}-${i}`} className={row.self ? 'af-uh__ranking-row--you' : ''}>
                      <span className="af-uh__ranking-label">
                        {row.self ? t('usage.ranking.youRow', { rank: row.rank }) : t('usage.ranking.neighbourRow', { rank: row.rank })}
                      </span>
                      <span className="af-uh__ranking-value">{Number(row.consumption).toFixed(1)} kL</span>
                    </li>
                  ),
                )}
              </ul>
              <p className="af-uh__ranking-note">{t('usage.ranking.privacyNote')}</p>
            </>
          ) : (
            <p className="af-uh__empty">{t('usage.ranking.empty')}</p>
          )}
        </section>
      </div>
    </>
  )
}

export default UsageHistory
