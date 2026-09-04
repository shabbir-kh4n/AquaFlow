import { useAuthModal } from '../context/useAuthModal'
import './CTA.css'

function CTA() {
  const { openAuthModal } = useAuthModal()

  return (
    <section className="af-cta af-dark-gradient">
      <div className="af-cta__inner">
        <span className="af-pill af-pill--light">Join HydroLedger Today</span>
        <h2>Start Monitoring Your Water Usage Today</h2>
        <p>
          Boost conservation metrics, simplify resident billing overhead, and protect valuable multi-household
          infrastructure. Let us calculate your custom community ROI estimate.
        </p>
        <div className="af-cta__actions">
          <button type="button" className="af-btn af-btn--cyan" onClick={() => openAuthModal('signup')}>
            Request Quote
          </button>
          <a href="#contact" className="af-cta__link">
            Contact Sales
          </a>
        </div>
      </div>
    </section>
  )
}

export default CTA
