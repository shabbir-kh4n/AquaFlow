import './PortalPreview.css'

const bars = [
  { label: 'Wk 1', value: 720, height: 60 },
  { label: 'Wk 2', value: 1080, height: 90 },
  { label: 'Wk 3', value: 540, height: 45 },
  { label: 'Wk 4', value: 855, height: 71 },
  { label: 'Wk 5 (Proj)', value: 990, height: 82 },
]

function PortalPreview() {
  return (
    <section className="af-section af-portal" id="portal">
      <div className="af-section__inner">
        <div className="af-section__head">
          <span className="af-pill">Live Resident Portal Mockup</span>
          <h2>
            Empower Residents With <span className="af-gradient-text">Consumption Clarity</span>
          </h2>
          <p>
            Transparency builds community alignment. Residents enjoy simple dashboards with real-time feedback,
            predictive cost projections, and proactive leak warnings.
          </p>
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
              Resident Portal
            </span>
            <span className="af-portal__unit">Unit 4B — Current Bill Cycle</span>
          </div>

          <div className="af-portal__body">
            <div className="af-portal__main">
              <div className="af-portal__stats">
                <div className="af-portal__stat">
                  <span className="af-portal__stat-label">This Month's Use</span>
                  <span className="af-portal__stat-value">3,240 Gal</span>
                  <span className="af-portal__stat-trend af-portal__stat-trend--good">-12% vs Avg</span>
                </div>
                <div className="af-portal__stat">
                  <span className="af-portal__stat-label">Est. Bill Amount</span>
                  <span className="af-portal__stat-value">₹3,500</span>
                  <span className="af-portal__stat-trend af-portal__stat-trend--good">On Track</span>
                </div>
                <div className="af-portal__stat">
                  <span className="af-portal__stat-label">Leak Status</span>
                  <span className="af-portal__stat-value">All Clear</span>
                  <span className="af-portal__stat-trend af-portal__stat-trend--good">Safe Flow</span>
                </div>
              </div>

              <div className="af-portal__chart-card">
                <span className="af-portal__chart-title">Weekly Usage (Gallons)</span>
                <div className="af-portal__chart">
                  {bars.map((b) => (
                    <div className="af-portal__bar-col" key={b.label}>
                      <span className="af-portal__bar-value">{b.value}</span>
                      <span className="af-portal__bar" style={{ height: `${b.height}%` }} />
                      <span className="af-portal__bar-label">{b.label}</span>
                    </div>
                  ))}
                </div>
              </div>
            </div>

            <div className="af-portal__side">
              <div className="af-portal__tip">
                <h4>Conservation Tip</h4>
                <p>
                  Your kitchen faucet shows slightly continuous flow in the morning. Checking aerators could save
                  up to 10 gallons/week.
                </p>
                <span className="af-pill af-pill--light">Automated Insight</span>
              </div>

              <div className="af-portal__autopay">
                <h4>Autopay Enabled</h4>
                <div className="af-portal__autopay-row">
                  <span>Next Auto-Bill date</span>
                  <strong>Oct 01</strong>
                </div>
                <button type="button" className="af-btn af-btn--outline af-portal__invoice-btn">
                  View Detailed PDF Invoice
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
