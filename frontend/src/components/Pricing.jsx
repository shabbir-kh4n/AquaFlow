import { useTranslation } from 'react-i18next'
import { useAuthModal } from '../context/useAuthModal'
import './Pricing.css'

const PRICING_META = [
  { priceValue: '125', isCustom: false, highlight: false },
  { priceValue: '210', isCustom: false, highlight: true },
  { priceValue: null, isCustom: true, highlight: false },
]

function Pricing() {
  const { openAuthModal } = useAuthModal()
  const { t } = useTranslation('landing')
  const tiers = t('pricing.tiers', { returnObjects: true })

  return (
    <section className="af-section" id="pricing">
      <div className="af-section__inner">
        <div className="af-section__head">
          <span className="af-pill">{t('pricing.pill')}</span>
          <h2>{t('pricing.heading')}</h2>
          <p>{t('pricing.paragraph')}</p>
        </div>

        <div className="af-pricing__grid">
          {tiers.map((tier, i) => {
            const meta = PRICING_META[i]
            return (
              <div className={`af-pricing__card ${meta.highlight ? 'af-pricing__card--highlight' : ''}`} key={tier.name}>
                {meta.highlight && <span className="af-pricing__badge">{t('pricing.highlightBadge')}</span>}
                <h3>{tier.name}</h3>
                <div className="af-pricing__price">
                  {meta.isCustom ? t('pricing.customPriceLabel') : `₹${meta.priceValue}`}
                  <span>{tier.unit}</span>
                </div>
                <p className="af-pricing__desc">{tier.desc}</p>
                <ul>
                  {tier.features.map((f) => (
                    <li key={f}>{f}</li>
                  ))}
                </ul>
                <button
                  type="button"
                  className={`af-btn ${meta.highlight ? 'af-btn--cyan' : 'af-btn--outline'} af-pricing__cta`}
                  onClick={() => openAuthModal('signup')}
                >
                  {tier.cta}
                </button>
              </div>
            )
          })}
        </div>
      </div>
    </section>
  )
}

export default Pricing
