import './Testimonials.css'

const quotes = [
  {
    text:
      "HydroLedger fundamentally changed our operational P&L. We discovered three major hidden service leaks in mechanical lines that saved us over ₹11 lakh in our first quarter alone.",
    name: 'Marcus Vance',
    role: 'Operations VP, Lincoln Community Group',
  },
  {
    text:
      "Resident trust has rocketed. Billing our tenants based strictly on water use instead of generic multi-bedroom master meter calculations solved our most painful tenant disputes.",
    name: 'Samantha Cole',
    role: 'Property Manager, Summit Ridge Highrise',
  },
]

function Testimonials() {
  return (
    <section className="af-section af-testimonials">
      <div className="af-section__inner">
        <div className="af-section__head">
          <span className="af-pill">Operational Realities</span>
          <h2>What Property Managers Say</h2>
        </div>

        <div className="af-testimonials__grid">
          {quotes.map((q) => (
            <div className="af-testimonials__card" key={q.name}>
              <span className="af-testimonials__quote-icon">&ldquo;</span>
              <p>{q.text}</p>
              <div className="af-testimonials__author">
                <span className="af-testimonials__avatar">{q.name.charAt(0)}</span>
                <div>
                  <strong>{q.name}</strong>
                  <span>{q.role}</span>
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}

export default Testimonials
