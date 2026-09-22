import { useEffect, useState } from 'react'
import { getMyAlerts } from '../../api/alertApi'
import { getVapidPublicKey, subscribe, unsubscribe } from '../../api/pushApi'
import { urlBase64ToUint8Array } from '../../utils/webPush'
import './ResidentNotifications.css'

const TYPE_LABELS = {
  THRESHOLD_EXCEEDED: 'Consumption Threshold Exceeded',
  STATISTICAL_OUTLIER: 'Unusual Consumption Detected',
}

const PUSH_UNSUPPORTED = typeof window === 'undefined' || !('serviceWorker' in navigator) || !('PushManager' in window)

function ResidentNotifications() {
  const [alerts, setAlerts] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [pushEnabled, setPushEnabled] = useState(false)
  const [pushBusy, setPushBusy] = useState(false)
  const [pushError, setPushError] = useState('')

  useEffect(() => {
    getMyAlerts()
      .then(setAlerts)
      .catch(() => setError('Could not load your notifications. Please try again.'))
      .finally(() => setLoading(false))
  }, [])

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
        setPushError('Notification permission was not granted.')
        return
      }
      const { publicKey } = await getVapidPublicKey()
      if (!publicKey) {
        setPushError('Push notifications are not configured yet. Contact your admin.')
        return
      }
      const subscription = await registration.pushManager.subscribe({
        userVisibleOnly: true,
        applicationServerKey: urlBase64ToUint8Array(publicKey),
      })
      await subscribe(subscription.toJSON())
      setPushEnabled(true)
    } catch {
      setPushError('Could not enable push notifications. Please try again.')
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
      setPushError('Could not disable push notifications. Please try again.')
    } finally {
      setPushBusy(false)
    }
  }

  return (
    <>
      {!PUSH_UNSUPPORTED && (
        <section className="af-notif__panel af-notif__push-panel">
          <div className="af-notif__push-text">
            <strong>Push notifications</strong>
            <span>Get notified on this device when a new bill or alert comes in.</span>
          </div>
          <div className="af-notif__push-actions">
            {pushError && <span className="af-notif__push-error">{pushError}</span>}
            <button
              type="button"
              className={`af-notif__push-toggle${pushEnabled ? ' af-notif__push-toggle--on' : ''}`}
              disabled={pushBusy}
              onClick={pushEnabled ? handleDisablePush : handleEnablePush}
            >
              {pushEnabled ? 'Disable' : 'Enable'}
            </button>
          </div>
        </section>
      )}

      {loading ? (
        <p className="af-notif__empty">Loading your notifications…</p>
      ) : error ? (
        <div className="af-notif__error">{error}</div>
      ) : alerts.length === 0 ? (
        <div className="af-notif__panel">
          <p className="af-notif__empty">
            No notifications yet — you'll see something here if your community admin has enabled
            usage-threshold or unusual-consumption alerts and one is triggered for your household.
          </p>
        </div>
      ) : (
        <section className="af-notif__panel">
          <div className="af-notif__list">
            {alerts.map((alert) => (
              <div key={alert.id} className={`af-notif__card af-notif__card--${alert.status.toLowerCase()}`}>
                <div className="af-notif__card-heading">
                  <strong>{TYPE_LABELS[alert.alertType] || alert.alertType}</strong>
                  <span className={`af-notif__badge af-notif__badge--${alert.status.toLowerCase()}`}>
                    {alert.status}
                  </span>
                </div>
                <p className="af-notif__message">{alert.message}</p>
                <div className="af-notif__meta">
                  <span>Billing period: {alert.periodMonth}</span>
                  <span>Consumption: {Number(alert.consumptionValue).toFixed(2)} kL</span>
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
