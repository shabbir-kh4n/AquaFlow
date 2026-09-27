import { useEffect, useMemo, useState } from 'react'
import i18next from 'i18next'
import { useTranslation } from 'react-i18next'
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
  THRESHOLD_EXCEEDED: 'waterLeakage.type.thresholdExceeded',
  STATISTICAL_OUTLIER: 'waterLeakage.type.statisticalOutlier',
}

const STATUS_LABELS = {
  ACTIVE: 'waterLeakage.status.active',
  RESOLVED: 'waterLeakage.status.resolved',
  DISMISSED: 'waterLeakage.status.dismissed',
}

function WaterLeakage() {
  const { t } = useTranslation('admin')
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
      .catch(() => setConfigError(t('waterLeakage.config.loadError')))
      .finally(() => setConfigLoading(false))
  }

  function loadAlerts(silent = false) {
    if (!silent) setAlertsLoading(true)
    setAlertsError('')
    return listAlerts(statusFilter || undefined)
      .then(setAlerts)
      .catch(() => setAlertsError(t('waterLeakage.alerts.loadError')))
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
      .catch(() => setConfigError(i18next.t('waterLeakage.config.loadError', { ns: 'admin' })))
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
      .catch(() => setAlertsError(i18next.t('waterLeakage.alerts.loadError', { ns: 'admin' })))
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
      setConfigError(t('waterLeakage.config.thresholdRequired'))
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
      setConfigError(err.response?.data?.message || t('waterLeakage.config.saveError'))
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
      setAlertsError(t('waterLeakage.alerts.runError'))
    } finally {
      setRunning(false)
    }
  }

  async function handleStatusChange(alert, status) {
    try {
      await updateAlertStatus(alert.id, status)
      await loadAlerts(true)
    } catch {
      setAlertsError(t('waterLeakage.alerts.updateError'))
    }
  }

  return (
    <>
      <section className="af-leak__config-panel">
        <h2>{t('waterLeakage.config.title')}</h2>
        <p className="af-leak__config-sub">
          {t('waterLeakage.config.subtitle')}
        </p>

        {configLoading ? (
          <p className="af-leak__empty">{t('waterLeakage.config.loading')}</p>
        ) : (
          <form onSubmit={handleConfigSubmit}>
            {configError && <div className="af-form__error">{configError}</div>}

            <div className="af-form__row">
              <div className="af-form__field">
                <label htmlFor="monthlyThresholdKl">{t('waterLeakage.config.threshold')}</label>
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
                <label htmlFor="outlierStdDevMultiplier">{t('waterLeakage.config.sensitivity')}</label>
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
                {t('waterLeakage.config.enableThreshold')}
              </label>
              <label className="af-leak__toggle">
                <input
                  type="checkbox"
                  name="outlierAlertsEnabled"
                  checked={configForm.outlierAlertsEnabled}
                  onChange={handleConfigChange}
                />
                {t('waterLeakage.config.enableOutlier')}
              </label>
            </div>

            <div className="af-leak__config-actions">
              <button type="submit" className="af-form__submit" disabled={configSaving}>
                {configSaving ? t('shared.saving') : t('waterLeakage.config.save')}
              </button>
              <button type="button" className="af-btn af-btn--outline" onClick={handleRunNow} disabled={running}>
                {running ? t('waterLeakage.config.running') : t('waterLeakage.config.runNow')}
              </button>
            </div>
          </form>
        )}
      </section>

      <section className="af-leak__alerts-panel">
        <div className="af-leak__alerts-heading">
          <h2>{t('waterLeakage.alerts.title')}</h2>
          <select
            value={statusFilter}
            onChange={(event) => {
              setStatusFilter(event.target.value)
              setPage(0)
            }}
          >
            <option value="">{t('waterLeakage.alerts.allStatuses')}</option>
            <option value="ACTIVE">{t(STATUS_LABELS.ACTIVE)}</option>
            <option value="RESOLVED">{t(STATUS_LABELS.RESOLVED)}</option>
            <option value="DISMISSED">{t(STATUS_LABELS.DISMISSED)}</option>
          </select>
        </div>

        {alertsError && <div className="af-form__error">{alertsError}</div>}

        <div className="af-leak__table-wrap">
          {alertsLoading ? (
            <p className="af-leak__empty">{t('waterLeakage.alerts.loading')}</p>
          ) : alerts.length === 0 ? (
            <p className="af-leak__empty">
              {t('waterLeakage.alerts.empty')}
            </p>
          ) : (
            <table className="af-leak__table">
              <thead>
                <tr>
                  <th>{t('waterLeakage.table.household')}</th>
                  <th>{t('waterLeakage.table.resident')}</th>
                  <th>{t('waterLeakage.table.meter')}</th>
                  <th>{t('waterLeakage.table.type')}</th>
                  <th>{t('waterLeakage.table.period')}</th>
                  <th>{t('waterLeakage.table.consumption')}</th>
                  <th>{t('waterLeakage.table.reference')}</th>
                  <th>{t('waterLeakage.table.status')}</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {pagedAlerts.map((alert) => (
                  <tr key={alert.id}>
                    <td>{alert.houseNumber}</td>
                    <td>{alert.residentName || '—'}</td>
                    <td>{alert.meterNumber || '—'}</td>
                    <td>{TYPE_LABELS[alert.alertType] ? t(TYPE_LABELS[alert.alertType]) : alert.alertType}</td>
                    <td>{alert.periodMonth}</td>
                    <td>{Number(alert.consumptionValue).toFixed(2)} kL</td>
                    <td>{Number(alert.referenceValue).toFixed(2)} kL</td>
                    <td>
                      <span className={`af-leak__badge af-leak__badge--${alert.status.toLowerCase()}`}>
                        {STATUS_LABELS[alert.status] ? t(STATUS_LABELS[alert.status]) : alert.status}
                      </span>
                    </td>
                    <td>
                      <div className="af-leak__row-actions">
                        {alert.status === 'ACTIVE' && (
                          <>
                            <button type="button" onClick={() => handleStatusChange(alert, 'RESOLVED')}>
                              {t('waterLeakage.alerts.resolve')}
                            </button>
                            <button type="button" className="af-leak__dismiss" onClick={() => handleStatusChange(alert, 'DISMISSED')}>
                              {t('waterLeakage.alerts.dismiss')}
                            </button>
                          </>
                        )}
                      </div>
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
              {t('pagination.showing', {
                from: safePage * PAGE_SIZE + 1,
                to: Math.min(alerts.length, safePage * PAGE_SIZE + PAGE_SIZE),
                total: alerts.length,
              })}
            </span>
            <div className="af-leak__pagination-controls">
              <button type="button" disabled={safePage === 0} onClick={() => setPage(safePage - 1)}>
                {t('pagination.prev')}
              </button>
              <span>
                {t('pagination.page', { page: safePage + 1, pageCount })}
              </span>
              <button type="button" disabled={safePage >= pageCount - 1} onClick={() => setPage(safePage + 1)}>
                {t('pagination.next')}
              </button>
            </div>
          </div>
        )}
      </section>
    </>
  )
}

export default WaterLeakage
