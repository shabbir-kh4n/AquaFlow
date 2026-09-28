import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { getMyAlerts } from '../../api/alertApi'
import { getMyAnnouncements } from '../../api/announcementApi'
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

const CATEGORY_LABEL_KEYS = {
  GENERAL: 'notifications.announcements.category.general',
  MAINTENANCE: 'notifications.announcements.category.maintenance',
  WATER_SUPPLY: 'notifications.announcements.category.waterSupply',
  BILLING: 'notifications.announcements.category.billing',
  URGENT: 'notifications.announcements.category.urgent',
}

function formatDateTime(iso, locale) {
  if (!iso) return ''
  return new Date(iso).toLocaleString(locale, { dateStyle: 'medium', timeStyle: 'short' })
}

const PUSH_UNSUPPORTED = typeof window === 'undefined' || !('serviceWorker' in navigator) || !('PushManager' in window)

function ResidentNotifications() {
  const { t, i18n } = useTranslation('resident')
  const locale = i18n.resolvedLanguage || 'en'
  const [activeTab, setActiveTab] = useState('alerts')
  const [alerts, setAlerts] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [announcements, setAnnouncements] = useState([])
  const [announcementsLoading, setAnnouncementsLoading] = useState(true)
  const [announcementsError, setAnnouncementsError] = useState('')
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
    getMyAnnouncements()
      .then(setAnnouncements)
      .catch(() => setAnnouncementsError(t('notifications.announcements.loadError')))
      .finally(() => setAnnouncementsLoading(false))
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

      <div className="af-notif__tabs" role="tablist">
        <button
          type="button"
          role="tab"
          aria-selected={activeTab === 'alerts'}
          className={`af-notif__tab${activeTab === 'alerts' ? ' af-notif__tab--active' : ''}`}
          onClick={() => setActiveTab('alerts')}
        >
          {t('notifications.tabs.alerts')}
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={activeTab === 'announcements'}
          className={`af-notif__tab${activeTab === 'announcements' ? ' af-notif__tab--active' : ''}`}
          onClick={() => setActiveTab('announcements')}
        >
          {t('notifications.tabs.announcements')}
          {announcements.length > 0 && <span className="af-notif__tab-count">{announcements.length}</span>}
        </button>
      </div>

      {activeTab === 'alerts' ? (
        loading ? (
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
        )
      ) : announcementsLoading ? (
        <p className="af-notif__empty">{t('notifications.announcements.loading')}</p>
      ) : announcementsError ? (
        <div className="af-notif__error">{announcementsError}</div>
      ) : announcements.length === 0 ? (
        <div className="af-notif__panel">
          <p className="af-notif__empty">{t('notifications.announcements.empty')}</p>
        </div>
      ) : (
        <section className="af-notif__panel">
          <div className="af-notif__list">
            {announcements.map((a) => (
              <div key={a.id} className={`af-notif__card af-notif__card--announcement${a.pinned ? ' af-notif__card--pinned' : ''}`}>
                <div className="af-notif__card-heading">
                  <strong>{a.title}</strong>
                  <span className="af-notif__badges">
                    {a.pinned && (
                      <span className="af-notif__badge af-notif__badge--pinned">{t('notifications.announcements.pinned')}</span>
                    )}
                    <span className={`af-notif__badge af-notif__badge--cat-${a.category.toLowerCase()}`}>
                      {CATEGORY_LABEL_KEYS[a.category] ? t(CATEGORY_LABEL_KEYS[a.category]) : a.category}
                    </span>
                  </span>
                </div>
                <p className="af-notif__message af-notif__message--body">{a.body}</p>
                <div className="af-notif__meta">
                  <span>{t('notifications.announcements.postedAt', { date: formatDateTime(a.createdAt, locale) })}</span>
                  {a.expiresAt && (
                    <span>{t('notifications.announcements.expiresAt', { date: formatDateTime(a.expiresAt, locale) })}</span>
                  )}
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
