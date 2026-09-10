import { useEffect, useMemo, useState } from 'react'
import {
  getAlertConfig,
  listAlerts,
  runAlertDetectionNow,
  saveAlertConfig,
  updateAlertStatus,
} from '../../api/alertApi'
import '../../components/AuthForm.css'
import './WaterLeakage.css'

const PAGE_SIZE = 10

const EMPTY_CONFIG_FORM = {
  monthlyThresholdKl: '',
  outlierStdDevMultiplier: '2.0',
  thresholdAlertsEnabled: true,
  outlierAlertsEnabled: true,
}

const TYPE_LABELS = {
  THRESHOLD_EXCEEDED: 'Threshold Exceeded',
  STATISTICAL_OUTLIER: 'Statistical Outlier',
}

function WaterLeakage() {
  const [configForm, setConfigForm] = useState(EMPTY_CONFIG_FORM)
  const [configLoading, setConfigLoading] = useState(true)
  const [configError, setConfigError] = useState('')
  const [configSaving, setConfigSaving] = useState(false)

  const [alerts, setAlerts] = useState([])
  const [alertsLoading, setAlertsLoading] = useState(true)
  const [alertsError, setAlertsError] = useState('')
  const [statusFilter, setStatusFilter] = useState('')
  const [page, setPage] = useState(0)
  const [running, setRunning] = useState(false)

  function loadConfig() {
    setConfigLoading(true)
    return getAlertConfig()
      .then((config) =>
        setConfigForm({
          monthlyThresholdKl: config.monthlyThresholdKl != null ? String(config.monthlyThresholdKl) : '',
          outlierStdDevMultiplier: String(config.outlierStdDevMultiplier),
          thresholdAlertsEnabled: config.thresholdAlertsEnabled,
          outlierAlertsEnabled: config.outlierAlertsEnabled,
        }),
      )
      .catch(() => setConfigError('Could not load alert configuration. Please try again.'))
      .finally(() => setConfigLoading(false))
  }

  function loadAlerts(silent = false) {
    if (!silent) setAlertsLoading(true)
    setAlertsError('')
    return listAlerts(statusFilter || undefined)
      .then(setAlerts)
      .catch(() => setAlertsError('Could not load alerts. Please try again.'))
      .finally(() => {
        if (!silent) setAlertsLoading(false)
      })
  }

  useEffect(() => {
    getAlertConfig()
      .then((config) =>
        setConfigForm({
          monthlyThresholdKl: config.monthlyThresholdKl != null ? String(config.monthlyThresholdKl) : '',
          outlierStdDevMultiplier: String(config.outlierStdDevMultiplier),
          thresholdAlertsEnabled: config.thresholdAlertsEnabled,
          outlierAlertsEnabled: config.outlierAlertsEnabled,
        }),
      )
      .catch(() => setConfigError('Could not load alert configuration. Please try again.'))
      .finally(() => setConfigLoading(false))
  }, [])

  // Refetches whenever the status filter changes. Deliberately doesn't reset alertsLoading/alertsError
  // synchronously here (only inside the resolved promise callbacks below) to avoid re-flashing the
  // "Loading…" placeholder on every filter change, matching the app's existing silent-refresh convention.
  useEffect(() => {
    listAlerts(statusFilter || undefined)
      .then((data) => {
        setAlerts(data)
        setAlertsError('')
      })
      .catch(() => setAlertsError('Could not load alerts. Please try again.'))
      .finally(() => setAlertsLoading(false))
  }, [statusFilter])

  const pageCount = Math.max(1, Math.ceil(alerts.length / PAGE_SIZE))
  const safePage = Math.min(page, pageCount - 1)
  const pagedAlerts = useMemo(
    () => alerts.slice(safePage * PAGE_SIZE, safePage * PAGE_SIZE + PAGE_SIZE),
    [alerts, safePage],
  )

  function handleConfigChange(event) {
    const { name, value, type, checked } = event.target
    setConfigForm((prev) => ({ ...prev, [name]: type === 'checkbox' ? checked : value }))
  }

  async function handleConfigSubmit(event) {
    event.preventDefault()
    setConfigError('')

    if (configForm.thresholdAlertsEnabled && !configForm.monthlyThresholdKl) {
      setConfigError('A monthly threshold (kL) is required when threshold alerts are enabled.')
      return
    }

    setConfigSaving(true)
    try {
      await saveAlertConfig({
        monthlyThresholdKl: configForm.monthlyThresholdKl ? Number(configForm.monthlyThresholdKl) : null,
        outlierStdDevMultiplier: Number(configForm.outlierStdDevMultiplier),
        thresholdAlertsEnabled: configForm.thresholdAlertsEnabled,
        outlierAlertsEnabled: configForm.outlierAlertsEnabled,
      })
      await loadConfig()
    } catch (err) {
      setConfigError(err.response?.data?.message || 'Could not save this configuration.')
    } finally {
      setConfigSaving(false)
    }
  }

  async function handleRunNow() {
    setRunning(true)
    try {
      await runAlertDetectionNow()
      await loadAlerts(true)
    } catch {
      setAlertsError('Could not run leak detection right now. Please try again.')
    } finally {
      setRunning(false)
    }
  }

  async function handleStatusChange(alert, status) {
    try {
      await updateAlertStatus(alert.id, status)
      await loadAlerts(true)
    } catch {
      setAlertsError('Could not update this alert. Please try again.')
    }
  }

  return (
    <>
      <section className="af-leak__config-panel">
        <h2>Leak Detection Configuration</h2>
        <p className="af-leak__config-sub">
          Set a monthly consumption threshold and/or enable statistical outlier detection (flags a
          household whose monthly consumption is unusually high compared to its own history).
        </p>

        {configLoading ? (
          <p className="af-leak__empty">Loading configuration…</p>
        ) : (
          <form onSubmit={handleConfigSubmit}>
            {configError && <div className="af-form__error">{configError}</div>}

            <div className="af-form__row">
              <div className="af-form__field">
                <label htmlFor="monthlyThresholdKl">Monthly threshold (kL)</label>
                <input
                  id="monthlyThresholdKl"
                  name="monthlyThresholdKl"
                  type="number"
                  min="0.01"
                  step="0.01"
                  value={configForm.monthlyThresholdKl}
                  onChange={handleConfigChange}
                />
              </div>
              <div className="af-form__field">
                <label htmlFor="outlierStdDevMultiplier">Outlier sensitivity (σ multiplier)</label>
                <input
                  id="outlierStdDevMultiplier"
                  name="outlierStdDevMultiplier"
                  type="number"
                  min="0.1"
                  step="0.1"
                  value={configForm.outlierStdDevMultiplier}
                  onChange={handleConfigChange}
                />
              </div>
            </div>

            <div className="af-leak__toggles">
              <label className="af-leak__toggle">
                <input
                  type="checkbox"
                  name="thresholdAlertsEnabled"
                  checked={configForm.thresholdAlertsEnabled}
                  onChange={handleConfigChange}
                />
                Enable threshold alerts
              </label>
              <label className="af-leak__toggle">
                <input
                  type="checkbox"
                  name="outlierAlertsEnabled"
                  checked={configForm.outlierAlertsEnabled}
                  onChange={handleConfigChange}
                />
                Enable statistical outlier alerts
              </label>
            </div>

            <div className="af-leak__config-actions">
              <button type="submit" className="af-form__submit" disabled={configSaving}>
                {configSaving ? 'Saving…' : 'Save Configuration'}
              </button>
              <button type="button" className="af-btn af-btn--outline" onClick={handleRunNow} disabled={running}>
                {running ? 'Running…' : 'Run Detection Now'}
              </button>
            </div>
          </form>
        )}
      </section>

      <section className="af-leak__alerts-panel">
        <div className="af-leak__alerts-heading">
          <h2>Flagged Households</h2>
          <select
            value={statusFilter}
            onChange={(event) => {
              setStatusFilter(event.target.value)
              setPage(0)
            }}
          >
            <option value="">All statuses</option>
            <option value="ACTIVE">Active</option>
            <option value="RESOLVED">Resolved</option>
            <option value="DISMISSED">Dismissed</option>
          </select>
        </div>

        {alertsError && <div className="af-form__error">{alertsError}</div>}

        <div className="af-leak__table-wrap">
          {alertsLoading ? (
            <p className="af-leak__empty">Loading alerts…</p>
          ) : alerts.length === 0 ? (
            <p className="af-leak__empty">
              No alerts to show. Configure a threshold or enable outlier detection above, then click
              "Run Detection Now" to evaluate your community's current usage.
            </p>
          ) : (
            <table className="af-leak__table">
              <thead>
                <tr>
                  <th>Household</th>
                  <th>Resident</th>
                  <th>Meter</th>
                  <th>Type</th>
                  <th>Period</th>
                  <th>Consumption</th>
                  <th>Reference</th>
                  <th>Status</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {pagedAlerts.map((alert) => (
                  <tr key={alert.id}>
                    <td>{alert.houseNumber}</td>
                    <td>{alert.residentName || '—'}</td>
                    <td>{alert.meterNumber || '—'}</td>
                    <td>{TYPE_LABELS[alert.alertType] || alert.alertType}</td>
                    <td>{alert.periodMonth}</td>
                    <td>{Number(alert.consumptionValue).toFixed(2)} kL</td>
                    <td>{Number(alert.referenceValue).toFixed(2)} kL</td>
                    <td>
                      <span className={`af-leak__badge af-leak__badge--${alert.status.toLowerCase()}`}>
                        {alert.status}
                      </span>
                    </td>
                    <td className="af-leak__row-actions">
                      {alert.status === 'ACTIVE' && (
                        <>
                          <button type="button" onClick={() => handleStatusChange(alert, 'RESOLVED')}>
                            Resolve
                          </button>
                          <button type="button" className="af-leak__dismiss" onClick={() => handleStatusChange(alert, 'DISMISSED')}>
                            Dismiss
                          </button>
                        </>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>

        {alerts.length > 0 && (
          <div className="af-leak__pagination">
            <span className="af-leak__pagination-info">
              Showing {safePage * PAGE_SIZE + 1}–{Math.min(alerts.length, safePage * PAGE_SIZE + PAGE_SIZE)} of{' '}
              {alerts.length}
            </span>
            <div className="af-leak__pagination-controls">
              <button type="button" disabled={safePage === 0} onClick={() => setPage(safePage - 1)}>
                ← Prev
              </button>
              <span>
                Page {safePage + 1} of {pageCount}
              </span>
              <button type="button" disabled={safePage >= pageCount - 1} onClick={() => setPage(safePage + 1)}>
                Next →
              </button>
            </div>
          </div>
        )}
      </section>
    </>
  )
}

export default WaterLeakage
