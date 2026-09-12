import { useTranslation } from 'react-i18next'
import './Stats.css'

const VALUES = ['40%', '10,000+', '99.9%', '500+']

function Stats() {
  const { t } = useTranslation('landing')
  const labels = t('stats.labels', { returnObjects: true })

  return (
    <section className="af-stats af-dark-gradient">
      <div className="af-stats__grid">
        {VALUES.map((value, i) => (
          <div className="af-stats__item" key={labels[i]}>
            <span className="af-stats__value">{value}</span>
            <span className="af-stats__label">{labels[i]}</span>
          </div>
        ))}
      </div>
    </section>
  )
}

export default Stats
