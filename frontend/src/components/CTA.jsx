import { useTranslation } from 'react-i18next'
import { useAuthModal } from '../context/useAuthModal'
import './CTA.css'

function CTA() {
  const { openAuthModal } = useAuthModal()
  const { t } = useTranslation('landing')

  return (
    <section className="af-cta af-dark-gradient">
      <div className="af-cta__inner">
        <span className="af-pill af-pill--light">{t('cta.pill')}</span>
        <h2>{t('cta.heading')}</h2>
        <p>{t('cta.paragraph')}</p>
        <div className="af-cta__actions">
          <button type="button" className="af-btn af-btn--cyan" onClick={() => openAuthModal('signup')}>
            {t('cta.ctaPrimary')}
          </button>
          <a href="#contact" className="af-cta__link">
            {t('cta.ctaSecondary')}
          </a>
        </div>
      </div>
    </section>
  )
}

export default CTA
