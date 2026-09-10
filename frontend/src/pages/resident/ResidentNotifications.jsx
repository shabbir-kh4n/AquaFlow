import { useEffect, useState } from 'react'
import { getMyAlerts } from '../../api/alertApi'
import './ResidentNotifications.css'

const TYPE_LABELS = {
  THRESHOLD_EXCEEDED: 'Consumption Threshold Exceeded',
  STATISTICAL_OUTLIER: 'Unusual Consumption Detected',
}

function ResidentNotifications() {
  const [alerts, setAlerts] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    getMyAlerts()
      .then(setAlerts)
      .catch(() => setError('Could not load your notifications. Please try again.'))
      .finally(() => setLoading(false))
  }, [])

  if (loading) {
    return <p className="af-notif__empty">Loading your notifications…</p>
  }

  if (error) {
    return <div className="af-notif__error">{error}</div>
  }

  if (alerts.length === 0) {
    return (
      <div className="af-notif__panel">
        <p className="af-notif__empty">
          No notifications yet — you'll see something here if your community admin has enabled
          usage-threshold or unusual-consumption alerts and one is triggered for your household.
        </p>
      </div>
    )
  }

  return (
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
  )
}

export default ResidentNotifications
