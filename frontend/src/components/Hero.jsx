import { useTranslation } from 'react-i18next'
import { useAuthModal } from '../context/useAuthModal'
import Logo from './Logo'
import './Hero.css'

function Hero() {
  const { openAuthModal } = useAuthModal()
  const { t } = useTranslation('landing')

  return (
    <section className="af-hero">
      <div className="af-hero__inner">
        <div className="af-hero__copy">
          <span className="af-hero__eyebrow">{t('hero.eyebrow')}</span>

          <h1>
            {t('hero.titleLine1')}
            <br />
            <span>{t('hero.titleHighlight')}</span> {t('hero.titleLine2')}
          </h1>

          <p>{t('hero.paragraph')}</p>

          <div className="af-hero__actions">
            <button type="button" className="af-btn af-btn--cyan" onClick={() => openAuthModal('signup')}>
              {t('hero.ctaPrimary')}
            </button>
            <button type="button" className="af-btn af-btn--outline" onClick={() => openAuthModal('signin')}>
              {t('hero.ctaSecondary')}
            </button>
          </div>
        </div>

        <div className="af-hero__panel">
          <div className="af-hero__panel-icon">
            <Logo size={48} />
          </div>

          <span className="af-hero__panel-label">{t('hero.panelLabel')}</span>
          <span className="af-hero__panel-value">12.4 kL</span>
          <span className="af-hero__panel-sub">{t('hero.panelSub')}</span>

          <div className="af-hero__chart">
            <span style={{ height: '42%' }} />
            <span style={{ height: '58%' }} />
            <span style={{ height: '38%' }} />
            <span style={{ height: '71%' }} />
            <span style={{ height: '50%' }} />
            <span className="af-hero__chart-bar--active" style={{ height: '64%' }} />
            <span style={{ height: '46%' }} />
          </div>
          <div className="af-hero__chart-labels">
            <span>{t('hero.chartLabelStart')}</span>
            <span>{t('hero.chartLabelEnd')}</span>
          </div>
        </div>
      </div>
    </section>
  )
}

export default Hero
