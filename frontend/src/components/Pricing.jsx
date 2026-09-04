import { useAuthModal } from '../context/useAuthModal'
import './Pricing.css'

const tiers = [
  {
    name: 'Starter',
    price: '₹125',
    unit: 'per unit / month',
    desc: 'Perfect for smaller townhome associations needing core sub-metering capabilities.',
    features: ['Real-Time Meter Readings', 'SMS Leak Alerts', 'Standard Billing Exports', 'Self-Serve Portal'],
    cta: 'Activate Starter',
    highlight: false,
  },
  {
    name: 'Professional',
    price: '₹210',
    unit: 'per unit / month',
    desc: 'Our gold standard package for classic apartment communities wanting fully automated resident billing.',
    features: [
      'Everything in Starter',
      'Automated PDF Invoice Dispatches',
      'Proportional Common-Area Allocations',
      'Priority Customer Support',
    ],
    cta: 'Go Pro (Most Popular)',
    highlight: true,
  },
  {
    name: 'Enterprise',
    price: 'Custom',
    unit: 'on request',
    desc: 'For expansive institutional communities requiring bespoke API pipelines and billing configurations.',
    features: [
      'Everything in Professional',
      'Bespoke Billing Software Integrations',
      'Dedicated Utility Consultant',
      'Hardware SLA Warranties',
    ],
    cta: 'Contact Enterprise Sales',
    highlight: false,
  },
]

function Pricing() {
  const { openAuthModal } = useAuthModal()

  return (
    <section className="af-section" id="pricing">
      <div className="af-section__inner">
        <div className="af-section__head">
          <span className="af-pill">Transparent Packages</span>
          <h2>High Return-on-Investment Operational Costs</h2>
          <p>
            Choose a plan scaled to your exact community density. HydroLedger usually offsets its own platform
            fees in the first month through reduced common-area waste.
          </p>
        </div>

        <div className="af-pricing__grid">
          {tiers.map((t) => (
            <div className={`af-pricing__card ${t.highlight ? 'af-pricing__card--highlight' : ''}`} key={t.name}>
              {t.highlight && <span className="af-pricing__badge">Best Value</span>}
              <h3>{t.name}</h3>
              <div className="af-pricing__price">
                {t.price}
                <span>{t.unit}</span>
              </div>
              <p className="af-pricing__desc">{t.desc}</p>
              <ul>
                {t.features.map((f) => (
                  <li key={f}>{f}</li>
                ))}
              </ul>
              <button
                type="button"
                className={`af-btn ${t.highlight ? 'af-btn--cyan' : 'af-btn--outline'} af-pricing__cta`}
                onClick={() => openAuthModal('signup')}
              >
                {t.cta}
              </button>
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}

export default Pricing
