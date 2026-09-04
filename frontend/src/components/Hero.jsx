import { useAuthModal } from '../context/useAuthModal'
import Logo from './Logo'
import './Hero.css'

function Hero() {
  const { openAuthModal } = useAuthModal()

  return (
    <section className="af-hero">
      <div className="af-hero__inner">
        <div className="af-hero__copy">
          <span className="af-hero__eyebrow">Smart Water Monitoring</span>

          <h1>
            Track usage.
            <br />
            <span>Split fairly.</span> Save water.
          </h1>

          <p>
            HydroLedger gives apartment communities real-time consumption
            insights, transparent shared-cost billing, and early leak alerts
            — all from one dashboard.
          </p>

          <div className="af-hero__actions">
            <button type="button" className="af-btn af-btn--cyan" onClick={() => openAuthModal('signup')}>
              Get Started
            </button>
            <button type="button" className="af-btn af-btn--outline" onClick={() => openAuthModal('signin')}>
              Sign In
            </button>
          </div>
        </div>

        <div className="af-hero__panel">
          <div className="af-hero__panel-icon">
            <Logo size={48} />
          </div>

          <span className="af-hero__panel-label">This month's usage</span>
          <span className="af-hero__panel-value">12.4 kL</span>
          <span className="af-hero__panel-sub">8% below household average</span>

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
            <span>Mon</span>
            <span>Sun</span>
          </div>
        </div>
      </div>
    </section>
  )
}

export default Hero
