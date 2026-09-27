import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { getMyAlerts } from '../../api/alertApi'
import { getVapidPublicKey, subscribe, unsubscribe } from '../../api/pushApi'
import { urlBase64ToUint8Array } from '../../utils/webPush'
import './ResidentNotifications.css'

const TYPE_LABEL_KEYS = {
  THRESHOLD_EXCEEDED: 'notifications.type.thresholdExceeded',
  STATISTICAL_OUTLIER: 'notifications.type.statisticalOutlier',
}

const STATUS_LABEL_KEYS = {
  ACTIVE: 'notifications.status.active',
  RESOLVED: 'notifications.status.resolved',
  DISMISSED: 'notifications.status.dismissed',
}

const PUSH_UNSUPPORTED = typeof window === 'undefined' || !('serviceWorker' in navigator) || !('PushManager' in window)

function ResidentNotifications() {
  const { t } = useTranslation('resident')
  const [alerts, setAlerts] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [pushEnabled, setPushEnabled] = useState(false)
  const [pushBusy, setPushBusy] = useState(false)
  const [pushError, setPushError] = useState('')

  useEffect(() => {
    getMyAlerts()
      .then(setAlerts)
      .catch(() => setError(t('notifications.loadError')))
      .finally(() => setLoading(false))
  }, [t])

  useEffect(() => {
    if (PUSH_UNSUPPORTED) return
    navigator.serviceWorker.ready
      .then((registration) => registration.pushManager.getSubscription())
      .then((subscription) => setPushEnabled(Boolean(subscription)))
      .catch(() => {})
  }, [])

  async function handleEnablePush() {
    setPushError('')
    setPushBusy(true)
    try {
      const registration = await navigator.serviceWorker.ready
      const permission = await Notification.requestPermission()
      if (permission !== 'granted') {
        setPushError(t('notifications.push.permissionDenied'))
        return
      }
      const { publicKey } = await getVapidPublicKey()
      if (!publicKey) {
        setPushError(t('notifications.push.notConfigured'))
        return
      }
      const subscription = await registration.pushManager.subscribe({
        userVisibleOnly: true,
        applicationServerKey: urlBase64ToUint8Array(publicKey),
      })
      await subscribe(subscription.toJSON())
      setPushEnabled(true)
    } catch {
      setPushError(t('notifications.push.enableError'))
    } finally {
      setPushBusy(false)
    }
  }

  async function handleDisablePush() {
    setPushError('')
    setPushBusy(true)
    try {
      const registration = await navigator.serviceWorker.ready
      const subscription = await registration.pushManager.getSubscription()
      if (subscription) {
        await unsubscribe(subscription.endpoint)
        await subscription.unsubscribe()
      }
      setPushEnabled(false)
    } catch {
      setPushError(t('notifications.push.disableError'))
    } finally {
      setPushBusy(false)
    }
  }

  return (
    <>
      {!PUSH_UNSUPPORTED && (
        <section className="af-notif__panel af-notif__push-panel">
          <div className="af-notif__push-text">
            <strong>{t('notifications.push.title')}</strong>
            <span>{t('notifications.push.description')}</span>
          </div>
          <div className="af-notif__push-actions">
            {pushError && <span className="af-notif__push-error">{pushError}</span>}
            <button
              type="button"
              className={`af-notif__push-toggle${pushEnabled ? ' af-notif__push-toggle--on' : ''}`}
              disabled={pushBusy}
              onClick={pushEnabled ? handleDisablePush : handleEnablePush}
            >
              {pushEnabled ? t('notifications.push.disable') : t('notifications.push.enable')}
            </button>
          </div>
        </section>
      )}

      {loading ? (
        <p className="af-notif__empty">{t('notifications.loading')}</p>
      ) : error ? (
        <div className="af-notif__error">{error}</div>
      ) : alerts.length === 0 ? (
        <div className="af-notif__panel">
          <p className="af-notif__empty">
            {t('notifications.empty')}
          </p>
        </div>
      ) : (
        <section className="af-notif__panel">
          <div className="af-notif__list">
            {alerts.map((alert) => (
              <div key={alert.id} className={`af-notif__card af-notif__card--${alert.status.toLowerCase()}`}>
                <div className="af-notif__card-heading">
                  <strong>{TYPE_LABEL_KEYS[alert.alertType] ? t(TYPE_LABEL_KEYS[alert.alertType]) : alert.alertType}</strong>
                  <span className={`af-notif__badge af-notif__badge--${alert.status.toLowerCase()}`}>
                    {STATUS_LABEL_KEYS[alert.status] ? t(STATUS_LABEL_KEYS[alert.status]) : alert.status}
                  </span>
                </div>
                <p className="af-notif__message">{alert.message}</p>
                <div className="af-notif__meta">
                  <span>{t('notifications.billingPeriod', { period: alert.periodMonth })}</span>
                  <span>{t('notifications.consumption', { value: Number(alert.consumptionValue).toFixed(2) })}</span>
                </div>
              </div>
            ))}
          </div>
        </section>
      )}
    </>
  )
}

export default ResidentNotifications
