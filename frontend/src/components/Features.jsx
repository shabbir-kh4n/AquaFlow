import './Features.css'

const features = [
  {
    tag: 'Water Tracking',
    title: 'Real-Time Sub-Metering',
    text: 'Measure exact, minute-by-minute water consumption across individual units instead of relying on outdated master estimates.',
    icon: (
      <path d="M3 12h4l3 8 4-16 3 8h4" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
    ),
  },
  {
    tag: 'Billing Automation',
    title: 'Automated Community Billing',
    text: 'Direct-to-resident invoice generation mapped dynamically to proportional use. Eliminate billing overhead instantly.',
    icon: (
      <path
        d="M7 3h8l4 4v14H7z M7 3v18M11 8h4M11 12h4M11 16h4"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    ),
  },
  {
    tag: 'Risk Mitigation',
    title: 'Early Leak Detection Alerts',
    text: 'Instant automated alerts flag abnormal flow patterns and catch major water damage within minutes, not months.',
    icon: (
      <path
        d="M12 3s7 8 7 12.5A7 7 0 0 1 5 15.5C5 11 12 3 12 3z"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    ),
  },
  {
    tag: 'Cost Allocation',
    title: 'Fair Cost Distribution',
    text: 'Track shared common-area usage and distribute structural costs transparently to build household trust and conservation.',
    icon: (
      <path
        d="M12 21a9 9 0 1 0 0-18M12 3v18M12 12h9"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    ),
  },
]

function Features() {
  return (
    <section className="af-section" id="features">
      <div className="af-section__inner">
        <div className="af-section__head">
          <span className="af-pill">Core Capabilities</span>
          <h2>
            Everything Needed to <span className="af-gradient-text">Monitor &amp; Bill</span> Fairly
          </h2>
          <p>
            Say goodbye to flat-rate utility splits. HydroLedger drives structural equity through precise
            measurement and intelligent automation.
          </p>
        </div>

        <div className="af-features__grid">
          {features.map((f) => (
            <div className="af-features__card" key={f.title}>
              <div className="af-features__icon">
                <svg width="22" height="22" viewBox="0 0 24 24" fill="none">
                  {f.icon}
                </svg>
              </div>
              <h3>{f.title}</h3>
              <p>{f.text}</p>
              <div className="af-features__foot">
                <span className="af-features__tag">{f.tag}</span>
                <span className="af-features__more">
                  Learn more
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
