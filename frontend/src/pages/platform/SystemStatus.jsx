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
import { getSystemMetrics, getSystemStatus } from '../../api/platformApi'
import './SystemStatus.css'

const RANGE_OPTIONS = [
  { value: '5m', label: '5 Minutes' },
  { value: '15m', label: '15 Minutes' },
  { value: '1h', label: 'Hour' },
  { value: '1d', label: 'Day' },
  { value: '1w', label: 'Week' },
  { value: '1mo', label: 'Month' },
  { value: '3mo', label: '3 Months' },
]

const REFRESH_MS = 15000

function formatUptime(ms) {
  if (!ms && ms !== 0) return '—'
  const totalSeconds = Math.floor(ms / 1000)
  const days = Math.floor(totalSeconds / 86400)
  const hours = Math.floor((totalSeconds % 86400) / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const parts = []
  if (days) parts.push(`${days}d`)
  if (hours) parts.push(`${hours}h`)
  parts.push(`${minutes}m`)
  return parts.join(' ')
}

function formatAxisTime(timestamp, range) {
  const date = new Date(timestamp)
  if (range === '1w' || range === '1mo' || range === '3mo') {
    return date.toLocaleDateString(undefined, { month: 'short', day: 'numeric' })
  }
  return date.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })
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

function chartPoints(metrics, range) {
  if (!metrics) return []
  return metrics.points.map((p) => ({ ...p, label: formatAxisTime(p.timestamp, range) }))
}

function SystemStatus() {
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
          if (!cancelled) setStatusError('Could not reach the backend to check system status.')
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
          if (!cancelled) setMetricsError('Could not load metrics history.')
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

  const points = chartPoints(metrics, range)
  const lastPoint = points[points.length - 1]

  return (
    <>
      {(statusError || metricsError) && (
        <div className="af-sys__error">{statusError || metricsError}</div>
      )}

      <div className="af-sys__range-row">
        {RANGE_OPTIONS.map((opt) => (
          <button
            key={opt.value}
            type="button"
            className={`af-sys__range-tab ${range === opt.value ? 'af-sys__range-tab--active' : ''}`}
            onClick={() => setRange(opt.value)}
          >
            {opt.label}
          </button>
        ))}
      </div>

      <div className="af-sys__charts">
        <div className="af-sys__chart-card">
          <div className="af-sys__chart-header">
            <span>Requests/sec</span>
            {lastPoint && <span className="af-sys__chart-last">{formatOps(lastPoint.opsPerSecond)} last</span>}
          </div>
          {metricsLoading ? (
            <p className="af-sys__chart-empty">Loading…</p>
          ) : points.length === 0 ? (
            <p className="af-sys__chart-empty">No metrics recorded for this range yet.</p>
          ) : (
            <ResponsiveContainer width="100%" height={220}>
              <AreaChart data={points} margin={{ top: 8, right: 12, left: 0, bottom: 0 }}>
                <CartesianGrid stroke="var(--af-chart-grid)" vertical={false} />
                <XAxis dataKey="label" stroke="var(--af-chart-axis)" tick={{ fontSize: 11 }} tickLine={false} axisLine={false} minTickGap={30} />
                <YAxis stroke="var(--af-chart-axis)" tick={{ fontSize: 11 }} tickLine={false} axisLine={false} width={36} />
                <Tooltip
                  contentStyle={{ borderRadius: 12, border: '1px solid var(--af-glass-border)', fontSize: 12 }}
                  formatter={(value) => [formatOps(value), 'Ops/sec']}
                />
                <Area type="monotone" dataKey="opsPerSecond" stroke="var(--af-chart-1)" fill="var(--af-chart-1)" fillOpacity={0.15} strokeWidth={2} />
              </AreaChart>
            </ResponsiveContainer>
          )}
          {metrics && (
            <div className="af-sys__stat-bar">
              <span>Min: <b>{formatOps(metrics.opsPerSecond.min)}</b></span>
              <span>Avg: <b>{formatOps(metrics.opsPerSecond.avg)}</b></span>
              <span>Max: <b>{formatOps(metrics.opsPerSecond.max)}</b></span>
              <span>Last: <b>{formatOps(metrics.opsPerSecond.last)}</b></span>
            </div>
          )}
        </div>

        <div className="af-sys__chart-card">
          <div className="af-sys__chart-header">
            <span>Latency</span>
            {lastPoint && <span className="af-sys__chart-last">{formatMs(lastPoint.latencyMs)} last</span>}
          </div>
          {metricsLoading ? (
            <p className="af-sys__chart-empty">Loading…</p>
          ) : points.length === 0 ? (
            <p className="af-sys__chart-empty">No metrics recorded for this range yet.</p>
          ) : (
            <ResponsiveContainer width="100%" height={220}>
              <AreaChart data={points} margin={{ top: 8, right: 12, left: 0, bottom: 0 }}>
                <CartesianGrid stroke="var(--af-chart-grid)" vertical={false} />
                <XAxis dataKey="label" stroke="var(--af-chart-axis)" tick={{ fontSize: 11 }} tickLine={false} axisLine={false} minTickGap={30} />
                <YAxis stroke="var(--af-chart-axis)" tick={{ fontSize: 11 }} tickLine={false} axisLine={false} width={44} />
                <Tooltip
                  contentStyle={{ borderRadius: 12, border: '1px solid var(--af-glass-border)', fontSize: 12 }}
                  formatter={(value) => [formatMs(value), 'Latency']}
                />
                <Area type="monotone" dataKey="latencyMs" stroke="var(--af-chart-2)" fill="var(--af-chart-2)" fillOpacity={0.15} strokeWidth={2} />
              </AreaChart>
            </ResponsiveContainer>
          )}
          {metrics && (
            <div className="af-sys__stat-bar">
              <span>Min: <b>{formatMs(metrics.latencyMs.min)}</b></span>
              <span>Avg: <b>{formatMs(metrics.latencyMs.avg)}</b></span>
              <span>Max: <b>{formatMs(metrics.latencyMs.max)}</b></span>
              <span>Last: <b>{formatMs(metrics.latencyMs.last)}</b></span>
            </div>
          )}
        </div>
      </div>

      <div className="af-sys__grid">
        <article className="af-sys__card">
          <div className="af-sys__card-top">
            <span className="af-sys__label">Backend API</span>
            <span className={`af-sys__dot af-sys__dot--${statusLoading ? 'unknown' : statusError ? 'down' : 'up'}`} />
          </div>
          <span className="af-sys__value">{statusLoading ? 'Checking…' : statusError ? 'Unreachable' : 'Reachable'}</span>
          <span className="af-sys__muted">This page loaded from the backend, so it responded.</span>
        </article>

        <article className="af-sys__card">
          <div className="af-sys__card-top">
            <span className="af-sys__label">Database</span>
            <span className={`af-sys__dot af-sys__dot--${statusLoading ? 'unknown' : status?.databaseUp ? 'up' : 'down'}`} />
          </div>
          <span className="af-sys__value">{statusLoading ? 'Checking…' : status?.databaseUp ? 'Connected' : 'Unreachable'}</span>
          <span className="af-sys__muted">PostgreSQL connectivity, checked live.</span>
        </article>

        <article className="af-sys__card">
          <div className="af-sys__card-top">
            <span className="af-sys__label">Backend Uptime</span>
          </div>
          <span className="af-sys__value">{statusLoading ? '—' : formatUptime(status?.uptimeMillis)}</span>
          <span className="af-sys__muted">Since the last application restart.</span>
        </article>

        <article className="af-sys__card">
          <div className="af-sys__card-top">
            <span className="af-sys__label">App Version</span>
          </div>
          <span className="af-sys__value">{statusLoading ? '—' : status?.appVersion || '—'}</span>
          <span className="af-sys__muted">Backend build version.</span>
        </article>
      </div>

      <div className="af-sys__mini-grid">
        <MiniMetricCard label="Latency" value={lastPoint ? formatMs(lastPoint.latencyMs) : '—'} points={points} dataKey="latencyMs" color="var(--af-chart-2)" />
        <MiniMetricCard label="Reads Latency" value={lastPoint ? formatMs(lastPoint.readLatencyMs) : '—'} points={points} dataKey="readLatencyMs" color="var(--af-chart-3)" />
        <MiniMetricCard label="Writes Latency" value={lastPoint ? formatMs(lastPoint.writeLatencyMs) : '—'} points={points} dataKey="writeLatencyMs" color="var(--af-chart-4)" />
        <MiniMetricCard label="Other Latency" value={lastPoint ? formatMs(lastPoint.otherLatencyMs) : '—'} points={points} dataKey="otherLatencyMs" color="var(--af-chart-5)" />
        <MiniMetricCard label="Connections" value={lastPoint ? formatCount(lastPoint.connections) : '—'} points={points} dataKey="connections" color="var(--af-chart-6)" />
        <MiniMetricCard label="Total Keys" value={lastPoint ? formatCount(lastPoint.totalKeys) : '—'} points={points} dataKey="totalKeys" color="var(--af-chart-1)" />
      </div>

      <div className="af-sys__note">
        Requests/sec and latency are sampled from live backend traffic every 15 seconds and kept for up to 100
        days. This is a basic connectivity + traffic check — it does not replace full application performance
        monitoring, log aggregation, or alerting.
      </div>
    </>
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
