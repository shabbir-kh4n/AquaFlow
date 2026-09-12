import { useTranslation } from 'react-i18next'
import './PortalPreview.css'

const BAR_DATA = [
  { value: 720, height: 60 },
  { value: 1080, height: 90 },
  { value: 540, height: 45 },
  { value: 855, height: 71 },
  { value: 990, height: 82 },
]

function PortalPreview() {
  const { t } = useTranslation('landing')
  const barLabels = t('portalPreview.bars', { returnObjects: true })

  return (
    <section className="af-section af-portal" id="portal">
      <div className="af-section__inner">
        <div className="af-section__head">
          <span className="af-pill">{t('portalPreview.pill')}</span>
          <h2>
            {t('portalPreview.headingPrefix')} <span className="af-gradient-text">{t('portalPreview.headingHighlight')}</span>
          </h2>
          <p>{t('portalPreview.paragraph')}</p>
        </div>

        <div className="af-portal__panel">
          <div className="af-portal__header">
            <span className="af-portal__brand">
              <svg width="16" height="16" viewBox="0 0 24 24">
                <path
                  d="M12 3C12 3 5 11 5 15.5C5 19.09 8.13 22 12 22C15.87 22 19 19.09 19 15.5C19 11 12 3 12 3Z"
                  fill="#ffffff"
                />
              </svg>
              {t('portalPreview.brandLabel')}
            </span>
            <span className="af-portal__unit">{t('portalPreview.unitLabel')}</span>
          </div>

          <div className="af-portal__body">
            <div className="af-portal__main">
              <div className="af-portal__stats">
                <div className="af-portal__stat">
                  <span className="af-portal__stat-label">{t('portalPreview.stat1Label')}</span>
                  <span className="af-portal__stat-value">3,240 Gal</span>
                  <span className="af-portal__stat-trend af-portal__stat-trend--good">{t('portalPreview.stat1Trend')}</span>
                </div>
                <div className="af-portal__stat">
                  <span className="af-portal__stat-label">{t('portalPreview.stat2Label')}</span>
                  <span className="af-portal__stat-value">₹3,500</span>
                  <span className="af-portal__stat-trend af-portal__stat-trend--good">{t('portalPreview.stat2Trend')}</span>
                </div>
                <div className="af-portal__stat">
                  <span className="af-portal__stat-label">{t('portalPreview.stat3Label')}</span>
                  <span className="af-portal__stat-value">{t('portalPreview.stat3Value')}</span>
                  <span className="af-portal__stat-trend af-portal__stat-trend--good">{t('portalPreview.stat3Trend')}</span>
                </div>
              </div>

              <div className="af-portal__chart-card">
                <span className="af-portal__chart-title">{t('portalPreview.chartTitle')}</span>
                <div className="af-portal__chart">
                  {BAR_DATA.map((b, i) => (
                    <div className="af-portal__bar-col" key={barLabels[i]}>
                      <span className="af-portal__bar-value">{b.value}</span>
                      <span className="af-portal__bar" style={{ height: `${b.height}%` }} />
                      <span className="af-portal__bar-label">{barLabels[i]}</span>
                    </div>
                  ))}
                </div>
              </div>
            </div>

            <div className="af-portal__side">
              <div className="af-portal__tip">
                <h4>{t('portalPreview.tipTitle')}</h4>
                <p>{t('portalPreview.tipText')}</p>
                <span className="af-pill af-pill--light">{t('portalPreview.tipBadge')}</span>
              </div>

              <div className="af-portal__autopay">
                <h4>{t('portalPreview.autopayTitle')}</h4>
                <div className="af-portal__autopay-row">
                  <span>{t('portalPreview.autopayLabel')}</span>
                  <strong>Oct 01</strong>
                </div>
                <button type="button" className="af-btn af-btn--outline af-portal__invoice-btn">
                  {t('portalPreview.autopayButton')}
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  )
}

export default PortalPreview
