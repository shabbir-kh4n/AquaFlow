import { useEffect, useState } from 'react'
import {
  Area,
  AreaChart,
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { Trans, useTranslation } from 'react-i18next'
import { getSystemMetrics, getSystemStatus } from '../../api/platformApi'
import './SystemStatus.css'

const RANGE_OPTIONS = [
  { value: '5m', label: 'systemStatus.ranges.fiveMinutes' },
  { value: '15m', label: 'systemStatus.ranges.fifteenMinutes' },
  { value: '1h', label: 'systemStatus.ranges.hour' },
  { value: '1d', label: 'systemStatus.ranges.day' },
  { value: '1w', label: 'systemStatus.ranges.week' },
  { value: '1mo', label: 'systemStatus.ranges.month' },
  { value: '3mo', label: 'systemStatus.ranges.threeMonths' },
]

const REFRESH_MS = 15000

function formatUptime(ms, t) {
  if (!ms && ms !== 0) return '—'
  const totalSeconds = Math.floor(ms / 1000)
  const days = Math.floor(totalSeconds / 86400)
  const hours = Math.floor((totalSeconds % 86400) / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const parts = []
  if (days) parts.push(t('systemStatus.uptime.days', { value: days }))
  if (hours) parts.push(t('systemStatus.uptime.hours', { value: hours }))
  parts.push(t('systemStatus.uptime.minutes', { value: minutes }))
  return parts.join(' ')
}

function formatAxisTime(timestamp, range, locale) {
  const date = new Date(timestamp)
  if (range === '1w' || range === '1mo' || range === '3mo') {
    return date.toLocaleDateString(locale, { month: 'short', day: 'numeric' })
  }
  return date.toLocaleTimeString(locale, { hour: '2-digit', minute: '2-digit' })
}

function formatOps(value) {
  return `${(value ?? 0).toFixed(2)}`
}

function formatMs(value) {
  return `${(value ?? 0).toFixed(2)}ms`
}

function formatCount(value) {
  return `${Math.round(value ?? 0)}`
}

function chartPoints(metrics, range, locale) {
  if (!metrics) return []
  return metrics.points.map((p) => ({ ...p, label: formatAxisTime(p.timestamp, range, locale) }))
}

function SystemStatus() {
  const { t, i18n } = useTranslation('platform')
  const locale = i18n.resolvedLanguage || 'en'
  const [status, setStatus] = useState(null)
  const [statusLoading, setStatusLoading] = useState(true)
  const [statusError, setStatusError] = useState('')

  const [range, setRange] = useState('1h')
  const [metrics, setMetrics] = useState(null)
  const [metricsLoading, setMetricsLoading] = useState(true)
  const [metricsError, setMetricsError] = useState('')

  useEffect(() => {
    let cancelled = false

    function run() {
      getSystemStatus()
        .then((data) => {
          if (!cancelled) {
            setStatus(data)
            setStatusError('')
          }
        })
        .catch(() => {
          if (!cancelled) setStatusError('systemStatus.statusError')
        })
        .finally(() => {
          if (!cancelled) setStatusLoading(false)
        })
    }

    run()
    const interval = setInterval(run, REFRESH_MS)
    return () => {
      cancelled = true
      clearInterval(interval)
    }
  }, [])

  useEffect(() => {
    let cancelled = false

    function run() {
      getSystemMetrics(range)
        .then((data) => {
          if (!cancelled) {
            setMetrics(data)
            setMetricsError('')
          }
        })
        .catch(() => {
          if (!cancelled) setMetricsError('systemStatus.metricsError')
        })
        .finally(() => {
          if (!cancelled) setMetricsLoading(false)
        })
    }

    run()
    const interval = setInterval(run, REFRESH_MS)
    return () => {
      cancelled = true
      clearInterval(interval)
    }
  }, [range])

  const points = chartPoints(metrics, range, locale)
  const lastPoint = points[points.length - 1]

  return (
    <>
      {(statusError || metricsError) && (
        <div className="af-sys__error">{t(statusError || metricsError)}</div>
      )}

      <div className="af-sys__range-row">
        {RANGE_OPTIONS.map((opt) => (
          <button
            key={opt.value}
            type="button"
            className={`af-sys__range-tab ${range === opt.value ? 'af-sys__range-tab--active' : ''}`}
            onClick={() => setRange(opt.value)}
          >
            {t(opt.label)}
          </button>
        ))}
      </div>

      <div className="af-sys__charts">
        <div className="af-sys__chart-card">
          <div className="af-sys__chart-header">
            <span>{t('systemStatus.charts.requestsPerSec')}</span>
            {lastPoint && <span className="af-sys__chart-last">{t('systemStatus.charts.lastValue', { value: formatOps(lastPoint.opsPerSecond) })}</span>}
          </div>
          {metricsLoading ? (
            <p className="af-sys__chart-empty">{t('systemStatus.charts.loading')}</p>
          ) : points.length === 0 ? (
            <p className="af-sys__chart-empty">{t('systemStatus.charts.empty')}</p>
          ) : (
            <ResponsiveContainer width="100%" height={220}>
              <AreaChart data={points} margin={{ top: 8, right: 12, left: 0, bottom: 0 }}>
                <CartesianGrid stroke="var(--af-chart-grid)" vertical={false} />
                <XAxis dataKey="label" stroke="var(--af-chart-axis)" tick={{ fontSize: 11 }} tickLine={false} axisLine={false} minTickGap={30} />
                <YAxis stroke="var(--af-chart-axis)" tick={{ fontSize: 11 }} tickLine={false} axisLine={false} width={36} />
                <Tooltip
                  contentStyle={{ borderRadius: 12, border: '1px solid var(--af-glass-border)', fontSize: 12 }}
                  formatter={(value) => [formatOps(value), t('systemStatus.charts.opsPerSec')]}
                />
                <Area type="monotone" dataKey="opsPerSecond" stroke="var(--af-chart-1)" fill="var(--af-chart-1)" fillOpacity={0.15} strokeWidth={2} />
              </AreaChart>
            </ResponsiveContainer>
          )}
          {metrics && (
            <div className="af-sys__stat-bar">
              <StatItem t={t} i18nKey="systemStatus.stats.min" value={formatOps(metrics.opsPerSecond.min)} />
              <StatItem t={t} i18nKey="systemStatus.stats.avg" value={formatOps(metrics.opsPerSecond.avg)} />
              <StatItem t={t} i18nKey="systemStatus.stats.max" value={formatOps(metrics.opsPerSecond.max)} />
              <StatItem t={t} i18nKey="systemStatus.stats.last" value={formatOps(metrics.opsPerSecond.last)} />
            </div>
          )}
        </div>

        <div className="af-sys__chart-card">
          <div className="af-sys__chart-header">
            <span>{t('systemStatus.charts.latency')}</span>
            {lastPoint && <span className="af-sys__chart-last">{t('systemStatus.charts.lastValue', { value: formatMs(lastPoint.latencyMs) })}</span>}
          </div>
          {metricsLoading ? (
            <p className="af-sys__chart-empty">{t('systemStatus.charts.loading')}</p>
          ) : points.length === 0 ? (
            <p className="af-sys__chart-empty">{t('systemStatus.charts.empty')}</p>
          ) : (
            <ResponsiveContainer width="100%" height={220}>
              <AreaChart data={points} margin={{ top: 8, right: 12, left: 0, bottom: 0 }}>
                <CartesianGrid stroke="var(--af-chart-grid)" vertical={false} />
                <XAxis dataKey="label" stroke="var(--af-chart-axis)" tick={{ fontSize: 11 }} tickLine={false} axisLine={false} minTickGap={30} />
                <YAxis stroke="var(--af-chart-axis)" tick={{ fontSize: 11 }} tickLine={false} axisLine={false} width={44} />
                <Tooltip
                  contentStyle={{ borderRadius: 12, border: '1px solid var(--af-glass-border)', fontSize: 12 }}
                  formatter={(value) => [formatMs(value), t('systemStatus.charts.latency')]}
                />
                <Area type="monotone" dataKey="latencyMs" stroke="var(--af-chart-2)" fill="var(--af-chart-2)" fillOpacity={0.15} strokeWidth={2} />
              </AreaChart>
            </ResponsiveContainer>
          )}
          {metrics && (
            <div className="af-sys__stat-bar">
              <StatItem t={t} i18nKey="systemStatus.stats.min" value={formatMs(metrics.latencyMs.min)} />
              <StatItem t={t} i18nKey="systemStatus.stats.avg" value={formatMs(metrics.latencyMs.avg)} />
              <StatItem t={t} i18nKey="systemStatus.stats.max" value={formatMs(metrics.latencyMs.max)} />
              <StatItem t={t} i18nKey="systemStatus.stats.last" value={formatMs(metrics.latencyMs.last)} />
            </div>
          )}
        </div>
      </div>

      <div className="af-sys__grid">
        <article className="af-sys__card">
          <div className="af-sys__card-top">
            <span className="af-sys__label">{t('systemStatus.cards.backendApi')}</span>
            <span className={`af-sys__dot af-sys__dot--${statusLoading ? 'unknown' : statusError ? 'down' : 'up'}`} />
          </div>
          <span className="af-sys__value">{statusLoading ? t('systemStatus.cards.checking') : statusError ? t('systemStatus.cards.unreachable') : t('systemStatus.cards.reachable')}</span>
          <span className="af-sys__muted">{t('systemStatus.cards.backendApiHint')}</span>
        </article>

        <article className="af-sys__card">
          <div className="af-sys__card-top">
            <span className="af-sys__label">{t('systemStatus.cards.database')}</span>
            <span className={`af-sys__dot af-sys__dot--${statusLoading ? 'unknown' : status?.databaseUp ? 'up' : 'down'}`} />
          </div>
          <span className="af-sys__value">{statusLoading
              ? t('systemStatus.cards.checking')
              : status?.databaseUp
                ? t('systemStatus.cards.connected')
                : t('systemStatus.cards.unreachable')}</span>
          <span className="af-sys__muted">{t('systemStatus.cards.databaseHint')}</span>
        </article>

        <article className="af-sys__card">
          <div className="af-sys__card-top">
            <span className="af-sys__label">{t('systemStatus.cards.uptime')}</span>
          </div>
          <span className="af-sys__value">{statusLoading ? '—' : formatUptime(status?.uptimeMillis, t)}</span>
          <span className="af-sys__muted">{t('systemStatus.cards.uptimeHint')}</span>
        </article>

        <article className="af-sys__card">
          <div className="af-sys__card-top">
            <span className="af-sys__label">{t('systemStatus.cards.appVersion')}</span>
          </div>
          <span className="af-sys__value">{statusLoading ? '—' : status?.appVersion || '—'}</span>
          <span className="af-sys__muted">{t('systemStatus.cards.appVersionHint')}</span>
        </article>
      </div>

      <div className="af-sys__mini-grid">
        <MiniMetricCard label={t('systemStatus.mini.latency')} value={lastPoint ? formatMs(lastPoint.latencyMs) : '—'} points={points} dataKey="latencyMs" color="var(--af-chart-2)" />
        <MiniMetricCard label={t('systemStatus.mini.readsLatency')} value={lastPoint ? formatMs(lastPoint.readLatencyMs) : '—'} points={points} dataKey="readLatencyMs" color="var(--af-chart-3)" />
        <MiniMetricCard label={t('systemStatus.mini.writesLatency')} value={lastPoint ? formatMs(lastPoint.writeLatencyMs) : '—'} points={points} dataKey="writeLatencyMs" color="var(--af-chart-4)" />
        <MiniMetricCard label={t('systemStatus.mini.otherLatency')} value={lastPoint ? formatMs(lastPoint.otherLatencyMs) : '—'} points={points} dataKey="otherLatencyMs" color="var(--af-chart-5)" />
        <MiniMetricCard label={t('systemStatus.mini.connections')} value={lastPoint ? formatCount(lastPoint.connections) : '—'} points={points} dataKey="connections" color="var(--af-chart-6)" />
        <MiniMetricCard label={t('systemStatus.mini.totalKeys')} value={lastPoint ? formatCount(lastPoint.totalKeys) : '—'} points={points} dataKey="totalKeys" color="var(--af-chart-1)" />
      </div>

      <div className="af-sys__note">
        {t('systemStatus.note')}
      </div>
    </>
  )
}

function StatItem({ t, i18nKey, value }) {
  return (
    <span>
      <Trans t={t} i18nKey={i18nKey} values={{ value }} components={{ b: <b /> }} />
    </span>
  )
}

function MiniMetricCard({ label, value, points, dataKey, color }) {
  return (
    <article className="af-sys__mini-card">
      <div className="af-sys__mini-top">
        <span className="af-sys__label">{label}</span>
        <span className="af-sys__mini-value">{value}</span>
      </div>
      <div className="af-sys__sparkline">
        {points.length > 1 && (
          <ResponsiveContainer width="100%" height={44}>
            <LineChart data={points}>
              <Line type="monotone" dataKey={dataKey} stroke={color} strokeWidth={2} dot={false} isAnimationActive={false} />
            </LineChart>
          </ResponsiveContainer>
        )}
      </div>
    </article>
  )
}

export default SystemStatus
