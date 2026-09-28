import { useEffect, useMemo, useState } from 'react'
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { useTranslation } from 'react-i18next'
import { downloadAdminReportPdf, getAdminReport } from '../../api/adminReportApi'
import { downloadBlob } from '../../utils/downloadFile'
import '../platform/PlatformReports.css'
import './AdminReports.css'

const PAGE_SIZE = 10

const RANGE_OPTIONS = [
  { value: '3mo', label: 'reports.ranges.threeMonths' },
  { value: '6mo', label: 'reports.ranges.sixMonths' },
  { value: '12mo', label: 'reports.ranges.twelveMonths' },
  { value: 'custom', label: 'reports.ranges.custom' },
]

const SORT_OPTIONS = [
  { value: 'houseNumber', label: 'reports.table.house' },
  { value: 'residentName', label: 'reports.table.resident' },
  { value: 'totalConsumptionKl', label: 'reports.table.totalKl' },
  { value: 'avgMonthlyConsumptionKl', label: 'reports.table.avgMonthlyKl' },
  { value: 'billedAmount', label: 'reports.table.billed' },
  { value: 'paidAmount', label: 'reports.table.paid' },
  { value: 'outstandingAmount', label: 'reports.table.outstanding' },
]

function formatMonthLabel(periodMonth, locale) {
  const [year, month] = periodMonth.split('-')
  return new Date(Number(year), Number(month) - 1, 1).toLocaleDateString(locale, {
    month: 'short',
    year: '2-digit',
  })
}

function formatKl(value) {
  return `${Number(value ?? 0).toLocaleString('en-IN', { maximumFractionDigits: 2 })} kL`
}

function formatCurrency(value) {
  return `₹${Number(value ?? 0).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

function csvCell(value) {
  const text = value == null ? '' : String(value)
  return /[",\n\r]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text
}

function AdminReports() {
  const { t, i18n } = useTranslation('admin')
  const locale = i18n.resolvedLanguage || 'en'
  const [rangeMode, setRangeMode] = useState('3mo')
  const [customFrom, setCustomFrom] = useState('')
  const [customTo, setCustomTo] = useState('')

  const [report, setReport] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [pdfBusy, setPdfBusy] = useState(false)

  const [search, setSearch] = useState('')
  const [sortField, setSortField] = useState('totalConsumptionKl')
  const [sortDirection, setSortDirection] = useState('desc')
  const [page, setPage] = useState(0)
  const [pageResetKey, setPageResetKey] = useState('')

  const customRangeReady = rangeMode !== 'custom' || (customFrom && customTo)
  const params = useMemo(
    () => (rangeMode === 'custom' ? { from: customFrom, to: customTo } : { range: rangeMode }),
    [rangeMode, customFrom, customTo],
  )

  useEffect(() => {
    if (!customRangeReady) {
      return
    }

    getAdminReport(params)
      .then((data) => {
        setReport(data)
        setError('')
      })
      .catch((err) => setError(err.response?.data?.message || t('reports.loadError')))
      .finally(() => setLoading(false))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [params])

  const households = useMemo(() => report?.households ?? [], [report])

  const visibleHouseholds = useMemo(() => {
    const term = search.trim().toLowerCase()
    let filtered = households
    if (term) {
      filtered = filtered.filter((h) =>
        [h.houseNumber, h.residentName, h.meterNumber].some((f) => f?.toLowerCase().includes(term)),
      )
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
  }, [households, search, sortField, sortDirection])

  const pageCount = Math.max(1, Math.ceil(visibleHouseholds.length / PAGE_SIZE))
  const resetKey = `${search}|${sortField}|${sortDirection}|${households.length}`
  if (resetKey !== pageResetKey) {
    setPageResetKey(resetKey)
    setPage(0)
  }

  const pagedHouseholds = useMemo(
    () => visibleHouseholds.slice(page * PAGE_SIZE, page * PAGE_SIZE + PAGE_SIZE),
    [visibleHouseholds, page],
  )

  const trendPoints = useMemo(
    () => (report?.monthlyTrend ?? []).map((p) => ({ ...p, label: formatMonthLabel(p.periodMonth, locale) })),
    [report, locale],
  )

  const topConsumers = useMemo(
    () => (report?.topConsumers ?? []).map((h) => ({ ...h, label: h.houseNumber })),
    [report],
  )

  function toggleSortDirection() {
    setSortDirection((prev) => (prev === 'asc' ? 'desc' : 'asc'))
  }

  function handleDownloadCsv() {
    const header = [
      t('reports.table.house'),
      t('reports.table.resident'),
      t('reports.table.meter'),
      t('reports.table.totalKl'),
      t('reports.table.avgMonthlyKl'),
      t('reports.table.billed'),
      t('reports.table.paid'),
      t('reports.table.outstanding'),
    ]
    const rows = visibleHouseholds.map((h) => [
      h.houseNumber,
      h.residentName,
      h.meterNumber,
      Number(h.totalConsumptionKl ?? 0).toFixed(3),
      Number(h.avgMonthlyConsumptionKl ?? 0).toFixed(3),
      Number(h.billedAmount ?? 0).toFixed(2),
      Number(h.paidAmount ?? 0).toFixed(2),
      Number(h.outstandingAmount ?? 0).toFixed(2),
    ])
    const csv = [header, ...rows].map((row) => row.map(csvCell).join(',')).join('\r\n')
    // Leading BOM so Excel opens the UTF-8 file with resident names intact.
    const blob = new Blob([String.fromCharCode(0xfeff), csv], { type: 'text/csv;charset=utf-8' })
    downloadBlob(blob, `community-report-${report.range.from}-to-${report.range.to}.csv`)
  }

  async function handleDownloadPdf() {
    setPdfBusy(true)
    try {
      const blob = await downloadAdminReportPdf(params)
      downloadBlob(blob, `community-report-${report.range.from}-to-${report.range.to}.pdf`)
    } catch {
      setError(t('reports.pdfError'))
    } finally {
      setPdfBusy(false)
    }
  }

  const summary = report?.summary
  const collectionRate = Number(summary?.collectionRatePercent ?? 0)

  return (
    <>
      {error && <div className="af-rpt__error">{error}</div>}

      <div className="af-arpt__toolbar">
        <div className="af-rpt__range-row af-arpt__range-row">
          {RANGE_OPTIONS.map((opt) => (
            <button
              key={opt.value}
              type="button"
              className={`af-rpt__range-tab ${rangeMode === opt.value ? 'af-rpt__range-tab--active' : ''}`}
              onClick={() => setRangeMode(opt.value)}
            >
              {t(opt.label)}
            </button>
          ))}

          {rangeMode === 'custom' && (
            <div className="af-rpt__custom-pickers">
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

        {report && customRangeReady && (
          <div className="af-arpt__downloads">
            <button type="button" className="af-arpt__download" onClick={handleDownloadCsv} disabled={households.length === 0}>
              {t('reports.downloadCsv')}
            </button>
            <button type="button" className="af-arpt__download af-arpt__download--primary" onClick={handleDownloadPdf} disabled={pdfBusy}>
              {pdfBusy ? t('reports.downloadingPdf') : t('reports.downloadPdf')}
            </button>
          </div>
        )}
      </div>

      {rangeMode === 'custom' && !customRangeReady && (
        <p className="af-rpt__hint">{t('reports.customRange.hint')}</p>
      )}

      {loading && customRangeReady ? (
        <p className="af-rpt__empty">{t('reports.loading')}</p>
      ) : (
        customRangeReady && report && (
          <>
            <div className="af-rpt__grid">
              <article className="af-rpt__card">
                <span className="af-rpt__label">{t('reports.cards.totalConsumption')}</span>
                <span className="af-rpt__value">{formatKl(summary?.totalConsumptionKl)}</span>
                <span className="af-rpt__muted">{t('reports.cards.households', { total: summary?.householdCount ?? 0 })}</span>
              </article>
              <article className="af-rpt__card">
                <span className="af-rpt__label">{t('reports.cards.avgMonthly')}</span>
                <span className="af-rpt__value">{formatKl(summary?.avgMonthlyConsumptionKl)}</span>
                <span className="af-rpt__muted">{t('reports.cards.communityWide')}</span>
              </article>
              <article className="af-rpt__card">
                <span className="af-rpt__label">{t('reports.cards.billed')}</span>
                <span className="af-rpt__value">{formatCurrency(summary?.totalBilledAmount)}</span>
                <span className="af-rpt__muted">{t('reports.cards.finalizedOnly')}</span>
              </article>
              <article className="af-rpt__card">
                <span className="af-rpt__label">{t('reports.cards.paid')}</span>
                <span className="af-rpt__value">{formatCurrency(summary?.totalPaidAmount)}</span>
                <span className="af-rpt__muted">{t('reports.cards.invoices', { total: summary?.invoiceCount ?? 0 })}</span>
              </article>
              <article className="af-rpt__card">
                <span className="af-rpt__label">{t('reports.cards.outstanding')}</span>
                <span className="af-rpt__value af-arpt__value--warn">{formatCurrency(summary?.totalOutstandingAmount)}</span>
                <span className="af-rpt__muted">{t('reports.cards.unpaidInvoices', { total: summary?.unpaidInvoiceCount ?? 0 })}</span>
              </article>
              <article className="af-rpt__card">
                <span className="af-rpt__label">{t('reports.cards.collectionRate')}</span>
                <span className="af-rpt__value">{collectionRate.toFixed(2)}%</span>
                <div className="af-arpt__meter" aria-hidden="true">
                  <span style={{ width: `${Math.min(100, Math.max(0, collectionRate))}%` }} />
                </div>
              </article>
            </div>

            <div className="af-arpt__panels">
              <section className="af-arpt__panel">
                <h3>{t('reports.costs.title')}</h3>
                <dl className="af-arpt__stats">
                  <div>
                    <dt>{t('reports.costs.commonArea')}</dt>
                    <dd>{formatCurrency(summary?.commonAreaChargesTotal)}</dd>
                  </div>
                  <div>
                    <dt>{t('reports.costs.bulkCost')}</dt>
                    <dd>{formatCurrency(summary?.bulkPurchaseTotalCost)}</dd>
                  </div>
                  <div>
                    <dt>{t('reports.costs.bulkVolume')}</dt>
                    <dd>
                      {formatKl(summary?.bulkPurchaseVolumeKl)}
                      <small>{t('reports.costs.bulkCount', { total: summary?.bulkPurchaseCount ?? 0 })}</small>
                    </dd>
                  </div>
                  <div>
                    <dt>{t('reports.costs.billedMinusBulk')}</dt>
                    <dd className={Number(summary?.billedMinusBulkCost ?? 0) < 0 ? 'af-arpt__negative' : ''}>
                      {formatCurrency(summary?.billedMinusBulkCost)}
                    </dd>
                  </div>
                </dl>
                <p className="af-arpt__note">{t('reports.costs.note')}</p>
              </section>

              <section className="af-arpt__panel">
                <h3>{t('reports.top.title')}</h3>
                {topConsumers.length === 0 ? (
                  <p className="af-rpt__chart-empty">{t('reports.top.empty')}</p>
                ) : (
                  <ResponsiveContainer width="100%" height={Math.max(160, topConsumers.length * 40)}>
                    <BarChart data={topConsumers} layout="vertical" margin={{ top: 4, right: 16, left: 0, bottom: 0 }}>
                      <CartesianGrid stroke="var(--af-chart-grid)" horizontal={false} />
                      <XAxis type="number" stroke="var(--af-chart-axis)" tick={{ fontSize: 11 }} tickLine={false} axisLine={false} />
                      <YAxis type="category" dataKey="label" stroke="var(--af-chart-axis)" tick={{ fontSize: 11 }} tickLine={false} axisLine={false} width={64} />
                      <Tooltip
                        contentStyle={{ borderRadius: 12, border: '1px solid var(--af-glass-border)', fontSize: 12 }}
                        formatter={(value) => [formatKl(value), t('reports.charts.consumptionSeries')]}
                        labelFormatter={(label, payload) => {
                          const name = payload?.[0]?.payload?.residentName
                          return name ? `${label} · ${name}` : label
                        }}
                      />
                      <Bar dataKey="totalConsumptionKl" fill="var(--af-chart-1)" radius={[0, 4, 4, 0]} maxBarSize={22} />
                    </BarChart>
                  </ResponsiveContainer>
                )}
              </section>
            </div>

            <div className="af-rpt__charts">
              <div className="af-rpt__chart-card">
                <div className="af-rpt__chart-header">
                  <span>{t('reports.charts.consumptionTitle')}</span>
                </div>
                {trendPoints.length === 0 ? (
                  <p className="af-rpt__chart-empty">{t('reports.charts.empty')}</p>
                ) : (
                  <ResponsiveContainer width="100%" height={220}>
                    <AreaChart data={trendPoints} margin={{ top: 8, right: 12, left: 0, bottom: 0 }}>
                      <CartesianGrid stroke="var(--af-chart-grid)" vertical={false} />
                      <XAxis dataKey="label" stroke="var(--af-chart-axis)" tick={{ fontSize: 11 }} tickLine={false} axisLine={false} />
                      <YAxis stroke="var(--af-chart-axis)" tick={{ fontSize: 11 }} tickLine={false} axisLine={false} width={48} />
                      <Tooltip
                        contentStyle={{ borderRadius: 12, border: '1px solid var(--af-glass-border)', fontSize: 12 }}
                        formatter={(value) => [formatKl(value), t('reports.charts.consumptionSeries')]}
                      />
                      <Area type="monotone" dataKey="totalConsumptionKl" stroke="var(--af-chart-1)" fill="var(--af-chart-1)" fillOpacity={0.15} strokeWidth={2} />
                    </AreaChart>
                  </ResponsiveContainer>
                )}
              </div>

              <div className="af-rpt__chart-card">
                <div className="af-rpt__chart-header">
                  <span>{t('reports.charts.billingTitle')}</span>
                </div>
                {trendPoints.length === 0 ? (
                  <p className="af-rpt__chart-empty">{t('reports.charts.empty')}</p>
                ) : (
                  <ResponsiveContainer width="100%" height={220}>
                    <BarChart data={trendPoints} margin={{ top: 8, right: 12, left: 0, bottom: 0 }}>
                      <CartesianGrid stroke="var(--af-chart-grid)" vertical={false} />
                      <XAxis dataKey="label" stroke="var(--af-chart-axis)" tick={{ fontSize: 11 }} tickLine={false} axisLine={false} />
                      <YAxis stroke="var(--af-chart-axis)" tick={{ fontSize: 11 }} tickLine={false} axisLine={false} width={56} />
                      <Tooltip
                        contentStyle={{ borderRadius: 12, border: '1px solid var(--af-glass-border)', fontSize: 12 }}
                        formatter={(value, name) => [formatCurrency(value), name]}
                      />
                      <Legend wrapperStyle={{ fontSize: 12 }} />
                      <Bar dataKey="totalBilledAmount" name={t('reports.charts.billedSeries')} fill="var(--af-chart-2)" radius={[4, 4, 0, 0]} maxBarSize={20} />
                      <Bar dataKey="totalPaidAmount" name={t('reports.charts.paidSeries')} fill="var(--af-chart-1)" radius={[4, 4, 0, 0]} maxBarSize={20} />
                      <Bar dataKey="bulkPurchaseCost" name={t('reports.charts.bulkSeries')} fill="var(--af-chart-4)" radius={[4, 4, 0, 0]} maxBarSize={20} />
                    </BarChart>
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
                  placeholder={t('reports.searchPlaceholder')}
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                />
              </div>

              <div className="af-rpt__sort">
                <label htmlFor="reportSortField">{t('reports.sort.label')}</label>
                <select id="reportSortField" value={sortField} onChange={(e) => setSortField(e.target.value)}>
                  {SORT_OPTIONS.map((opt) => (
                    <option key={opt.value} value={opt.value}>
                      {t(opt.label)}
                    </option>
                  ))}
                </select>
                <button type="button" className="af-rpt__sort-dir" onClick={toggleSortDirection} title={t('reports.sort.toggleDirection')}>
                  {sortDirection === 'asc' ? t('reports.sort.asc') : t('reports.sort.desc')}
                </button>
              </div>
            </div>

            <div className="af-rpt__table-wrap">
              {households.length === 0 ? (
                <p className="af-rpt__empty">{t('reports.empty')}</p>
              ) : visibleHouseholds.length === 0 ? (
                <p className="af-rpt__empty">{t('reports.noMatch', { search })}</p>
              ) : (
                <table className="af-rpt__table">
                  <thead>
                    <tr>
                      <th>{t('reports.table.house')}</th>
                      <th>{t('reports.table.resident')}</th>
                      <th>{t('reports.table.meter')}</th>
                      <th>{t('reports.table.totalKl')}</th>
                      <th>{t('reports.table.avgMonthlyKl')}</th>
                      <th>{t('reports.table.billed')}</th>
                      <th>{t('reports.table.paid')}</th>
                      <th>{t('reports.table.outstanding')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {pagedHouseholds.map((h) => (
                      <tr key={h.householdId}>
                        <td>{h.houseNumber}</td>
                        <td>{h.residentName || '—'}</td>
                        <td>{h.meterNumber || '—'}</td>
                        <td>{formatKl(h.totalConsumptionKl)}</td>
                        <td>{formatKl(h.avgMonthlyConsumptionKl)}</td>
                        <td>{formatCurrency(h.billedAmount)}</td>
                        <td>{formatCurrency(h.paidAmount)}</td>
                        <td className={Number(h.outstandingAmount) > 0 ? 'af-arpt__value--warn' : ''}>
                          {formatCurrency(h.outstandingAmount)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>

            {visibleHouseholds.length > 0 && (
              <div className="af-rpt__pagination">
                <span className="af-rpt__pagination-info">
                  {t('pagination.showing', {
                    from: page * PAGE_SIZE + 1,
                    to: Math.min(visibleHouseholds.length, page * PAGE_SIZE + PAGE_SIZE),
                    total: visibleHouseholds.length,
                  })}
                </span>
                <div className="af-rpt__pagination-controls">
                  <button type="button" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
                    {t('pagination.prev')}
                  </button>
                  <span className="af-rpt__pagination-page">
                    {t('pagination.page', { page: page + 1, pageCount })}
                  </span>
                  <button type="button" disabled={page >= pageCount - 1} onClick={() => setPage((p) => p + 1)}>
                    {t('pagination.next')}
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

export default AdminReports
