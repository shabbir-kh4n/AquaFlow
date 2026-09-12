import { useTranslation } from 'react-i18next'
import './Features.css'

const ICONS = [
  <path key="0" d="M3 12h4l3 8 4-16 3 8h4" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />,
  <path
    key="1"
    d="M7 3h8l4 4v14H7z M7 3v18M11 8h4M11 12h4M11 16h4"
    stroke="currentColor"
    strokeWidth="1.8"
    strokeLinecap="round"
    strokeLinejoin="round"
  />,
  <path
    key="2"
    d="M12 3s7 8 7 12.5A7 7 0 0 1 5 15.5C5 11 12 3 12 3z"
    stroke="currentColor"
    strokeWidth="1.8"
    strokeLinecap="round"
    strokeLinejoin="round"
  />,
  <path
    key="3"
    d="M12 21a9 9 0 1 0 0-18M12 3v18M12 12h9"
    stroke="currentColor"
    strokeWidth="1.8"
    strokeLinecap="round"
    strokeLinejoin="round"
  />,
]

function Features() {
  const { t } = useTranslation('landing')
  const items = t('features.items', { returnObjects: true })

  return (
    <section className="af-section" id="features">
      <div className="af-section__inner">
        <div className="af-section__head">
          <span className="af-pill">{t('features.pill')}</span>
          <h2>
            {t('features.headingPrefix')} <span className="af-gradient-text">{t('features.headingHighlight')}</span> {t('features.headingSuffix')}
          </h2>
          <p>{t('features.paragraph')}</p>
        </div>

        <div className="af-features__grid">
          {items.map((f, i) => (
            <div className="af-features__card" key={f.title}>
              <div className="af-features__icon">
                <svg width="22" height="22" viewBox="0 0 24 24" fill="none">
                  {ICONS[i]}
                </svg>
              </div>
              <h3>{f.title}</h3>
              <p>{f.text}</p>
              <div className="af-features__foot">
                <span className="af-features__tag">{f.tag}</span>
                <span className="af-features__more">
                  {t('features.learnMore')}
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none">
                    <path d="M5 12h14M13 6l6 6-6 6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
                  </svg>
                </span>
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}

export default Features
