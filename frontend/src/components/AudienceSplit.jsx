import { useTranslation } from 'react-i18next'
import './AudienceSplit.css'

function AudienceSplit() {
  const { t } = useTranslation('landing')
  const residentBullets = t('audienceSplit.residentBullets', { returnObjects: true })
  const adminBullets = t('audienceSplit.adminBullets', { returnObjects: true })

  return (
    <section className="af-section af-audience">
      <div className="af-section__inner af-audience__grid">
        <div className="af-audience__card">
          <span className="af-pill">{t('audienceSplit.residentPill')}</span>
          <h3>{t('audienceSplit.residentHeading')}</h3>
          <p>{t('audienceSplit.residentParagraph')}</p>
          <ul>
            {residentBullets.map((bullet) => (
              <li key={bullet}>{bullet}</li>
            ))}
          </ul>
        </div>

        <div className="af-audience__card af-audience__card--dark">
          <span className="af-pill af-pill--light">{t('audienceSplit.adminPill')}</span>
          <h3>{t('audienceSplit.adminHeading')}</h3>
          <p>{t('audienceSplit.adminParagraph')}</p>
          <ul>
            {adminBullets.map((bullet) => (
              <li key={bullet}>{bullet}</li>
            ))}
          </ul>
        </div>
      </div>
    </section>
  )
}

export default AudienceSplit
