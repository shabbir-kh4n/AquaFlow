import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Bar, CartesianGrid, ComposedChart, Legend, Line, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { getMyReport } from '../../api/residentDashboardApi'
import { downloadMyStatementPdf } from '../../api/billingApi'
import { downloadCsv } from '../../utils/csv'
import { downloadBlob } from '../../utils/downloadFile'
import './ResidentReports.css'

const RANGE_OPTIONS = [
  { value: '3mo', labelKey: 'reports.ranges.threeMonths' },
  { value: '6mo', labelKey: 'reports.ranges.sixMonths' },
  { value: '12mo', labelKey: 'reports.ranges.twelveMonths' },
  { value: 'custom', labelKey: 'reports.ranges.custom' },
]

function monthLabel(ym, locale) {
  const [year, month] = ym.split('-')
  return new Date(Number(year), Number(month) - 1, 1).toLocaleDateString(locale, { month: 'short', year: '2-digit' })
}

function formatKl(value) {
  return `${Number(value ?? 0).toLocaleString('en-IN', { maximumFractionDigits: 2 })} kL`
}

function formatCurrency(value) {
  return `₹${Number(value ?? 0).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

function ResidentReports() {
  const { t, i18n } = useTranslation('resident')
  const locale = i18n.resolvedLanguage || 'en'
  const [rangeMode, setRangeMode] = useState('6mo')
  const [customFrom, setCustomFrom] = useState('')
  const [customTo, setCustomTo] = useState('')
  const [report, setReport] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [downloadingPdf, setDownloadingPdf] = useState(false)
  const [pdfError, setPdfError] = useState('')

  const customRangeReady = rangeMode !== 'custom' || Boolean(customFrom && customTo)

  useEffect(() => {
    if (!customRangeReady) return
    let cancelled = false
    const params = rangeMode === 'custom' ? { from: customFrom, to: customTo } : { range: rangeMode }
    getMyReport(params)
      .then((data) => {
        if (cancelled) return
        setReport(data)
        setError('')
      })
      .catch((err) => {
        // Backend validation messages (e.g. "'from' must not be after 'to'") are shown as-is.
        if (!cancelled) setError(err.response?.data?.message || t('reports.loadError'))
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [rangeMode, customFrom, customTo, customRangeReady, t])

  const chartData = useMemo(
    () =>
      (report?.months ?? []).map((row) => ({
        label: monthLabel(row.periodMonth, locale),
        consumption: Number(row.consumptionKl),
        billed: Number(row.billedAmount),
      })),
    [report, locale],
  )

  function handleDownloadCsv() {
    if (!report) return
    downloadCsv(
      `usage-report-${report.range.from}-to-${report.range.to}.csv`,
      [
        t('reports.table.month'),
        t('reports.table.consumption'),
        t('reports.table.billed'),
        t('reports.table.paid'),
        t('reports.table.status'),
      ],
      report.months.map((row) => [
        row.periodMonth,
        row.consumptionKl,
        row.billedAmount,
        row.paidAmount,
        row.invoiced ? (row.paid ? 'PAID' : 'UNPAID') : 'NOT_BILLED',
      ]),
    )
  }

  function handleDownloadPdf() {
    setPdfError('')
    setDownloadingPdf(true)
    downloadMyStatementPdf()
      .then((blob) => downloadBlob(blob, 'account-statement.pdf'))
      .catch(() => setPdfError(t('reports.pdfError')))
      .finally(() => setDownloadingPdf(false))
  }

  const summary = report?.summary
  const change = summary?.consumptionChangePercent
  const hasChange = change != null

  return (
    <>
      <div className="af-rrpt__toolbar">
        <div className="af-rrpt__range-row">
          {RANGE_OPTIONS.map((opt) => (
            <button
              key={opt.value}
              type="button"
              className={`af-rrpt__range-tab ${rangeMode === opt.value ? 'af-rrpt__range-tab--active' : ''}`}
              onClick={() => {
                if (opt.value !== rangeMode) setLoading(true)
                setRangeMode(opt.value)
              }}
            >
              {t(opt.labelKey)}
            </button>
          ))}
          {rangeMode === 'custom' && (
            <div className="af-rrpt__pickers">
              <label>
                {t('reports.customRange.from')}
                <input type="month" value={customFrom} onChange={(e) => setCustomFrom(e.target.value)} />
              </label>
              <label>
                {t('reports.customRange.to')}
                <input type="month" value={customTo} onChange={(e) => setCustomTo(e.target.value)} />
              </label>
            </div>
          )}
        </div>
        <div className="af-rrpt__actions">
          <button type="button" className="af-rrpt__btn af-rrpt__btn--outline" onClick={handleDownloadCsv} disabled={!report || !customRangeReady}>
            {t('reports.downloadCsv')}
          </button>
          <button type="button" className="af-rrpt__btn" onClick={handleDownloadPdf} disabled={downloadingPdf}>
            {downloadingPdf ? t('reports.downloading') : t('reports.downloadStatement')}
          </button>
        </div>
      </div>

      {pdfError && <div className="af-rrpt__error">{pdfError}</div>}

      {!customRangeReady ? (
        <p className="af-rrpt__hint">{t('reports.customRange.hint')}</p>
      ) : error ? (
        <div className="af-rrpt__error">{error}</div>
      ) : loading || !report ? (
        <p className="af-rrpt__empty">{t('reports.loading')}</p>
      ) : (
        <>
          <p className="af-rrpt__period">
            {t('reports.period', { from: monthLabel(report.range.from, locale), to: monthLabel(report.range.to, locale) })}
          </p>

          <div className="af-rrpt__grid">
            <article className="af-rrpt__card">
              <span className="af-rrpt__label">{t('reports.cards.totalUsage')}</span>
              <span className="af-rrpt__value">{formatKl(summary.totalConsumptionKl)}</span>
            </article>
            <article className="af-rrpt__card">
              <span className="af-rrpt__label">{t('reports.cards.avgMonthly')}</span>
              <span className="af-rrpt__value">{formatKl(summary.avgMonthlyConsumptionKl)}</span>
              <span className="af-rrpt__muted">{t('reports.cards.avgMonthlyHint')}</span>
            </article>
            <article className="af-rrpt__card">
              <span className="af-rrpt__label">{t('reports.cards.change')}</span>
              <span className={`af-rrpt__value ${hasChange ? (Number(change) <= 0 ? 'af-rrpt__good' : 'af-rrpt__bad') : ''}`}>
                {hasChange ? `${Number(change) > 0 ? '+' : ''}${Number(change).toFixed(1)}%` : '--'}
              </span>
              <span className="af-rrpt__muted">
                {hasChange
                  ? t('reports.cards.changeHint', {
                      previous: formatKl(summary.previousPeriodConsumptionKl),
                      from: monthLabel(report.range.previousFrom, locale),
                      to: monthLabel(report.range.previousTo, locale),
                    })
                  : t('reports.cards.changeNoData')}
              </span>
            </article>
            <article className="af-rrpt__card">
              <span className="af-rrpt__label">{t('reports.cards.totalBilled')}</span>
              <span className="af-rrpt__value">{formatCurrency(summary.totalBilledAmount)}</span>
            </article>
            <article className="af-rrpt__card">
              <span className="af-rrpt__label">{t('reports.cards.totalPaid')}</span>
              <span className="af-rrpt__value af-rrpt__good">{formatCurrency(summary.totalPaidAmount)}</span>
            </article>
            <article className="af-rrpt__card">
              <span className="af-rrpt__label">{t('reports.cards.outstanding')}</span>
              <span className={`af-rrpt__value ${Number(summary.outstandingAmount) > 0 ? 'af-rrpt__bad' : ''}`}>
                {formatCurrency(summary.outstandingAmount)}
              </span>
            </article>
          </div>

          <section className="af-rrpt__panel">
            <h3>{t('reports.chart.title')}</h3>
            <p className="af-rrpt__subtitle">{t('reports.chart.subtitle')}</p>
            <ResponsiveContainer width="100%" height={260}>
              <ComposedChart data={chartData} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
                <CartesianGrid stroke="var(--afr-chart-grid)" vertical={false} />
                <XAxis dataKey="label" stroke="var(--afr-chart-axis)" tick={{ fontSize: 11 }} tickLine={false} axisLine={false} />
                <YAxis yAxisId="kl" stroke="var(--afr-chart-axis)" tick={{ fontSize: 11 }} tickLine={false} axisLine={false} width={44} />
                <YAxis yAxisId="amount" orientation="right" stroke="var(--afr-chart-axis)" tick={{ fontSize: 11 }} tickLine={false} axisLine={false} width={56} />
                <Tooltip
                  contentStyle={{ borderRadius: 10, border: '1px solid var(--afr-border)', fontSize: 12 }}
                  formatter={(value, name, item) => [item.dataKey === 'billed' ? formatCurrency(value) : formatKl(value), name]}
                />
                <Legend wrapperStyle={{ fontSize: 12 }} />
                <Bar yAxisId="kl" dataKey="consumption" name={t('reports.chart.consumption')} fill="var(--afr-chart-1)" radius={[4, 4, 0, 0]} maxBarSize={32} />
                <Line yAxisId="amount" type="monotone" dataKey="billed" name={t('reports.chart.billed')} stroke="var(--afr-chart-2)" strokeWidth={2} dot={{ r: 3 }} />
              </ComposedChart>
            </ResponsiveContainer>
          </section>

          <section className="af-rrpt__panel">
            <h3>{t('reports.table.title')}</h3>
            <div className="af-rrpt__table-wrap">
              <table className="af-rrpt__table">
                <thead>
                  <tr>
                    <th>{t('reports.table.month')}</th>
                    <th>{t('reports.table.consumption')}</th>
                    <th>{t('reports.table.billed')}</th>
                    <th>{t('reports.table.paid')}</th>
                    <th>{t('reports.table.status')}</th>
                  </tr>
                </thead>
                <tbody>
                  {report.months.map((row) => (
                    <tr key={row.periodMonth}>
                      <td>{monthLabel(row.periodMonth, locale)}</td>
                      <td>{formatKl(row.consumptionKl)}</td>
                      <td>{row.invoiced ? formatCurrency(row.billedAmount) : '—'}</td>
                      <td>{row.invoiced ? formatCurrency(row.paidAmount) : '—'}</td>
                      <td>
                        <span className={`af-rrpt__badge af-rrpt__badge--${row.invoiced ? (row.paid ? 'paid' : 'unpaid') : 'none'}`}>
                          {row.invoiced ? (row.paid ? t('reports.status.paid') : t('reports.status.unpaid')) : t('reports.status.notBilled')}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        </>
      )}
    </>
  )
}

export default ResidentReports
