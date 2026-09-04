import './HowItWorks.css'

const steps = [
  {
    n: '01',
    title: 'Connect Smart Meters',
    text: 'Our certified technicians attach non-invasive smart flow sub-meters directly to individual riser valves in under 15 minutes.',
  },
  {
    n: '02',
    title: 'Real-Time Tracking',
    text: 'Data streams continuously to the HydroLedger cloud platform, logging exact usage figures and monitoring abnormal activity.',
  },
  {
    n: '03',
    title: 'Automate Bills',
    text: 'At month-end, the engine combines common-area distributions and sub-metered volumes into clear, itemized invoices.',
  },
]

function HowItWorks() {
  return (
    <section className="af-section af-howitworks" id="how-it-works">
      <div className="af-section__inner">
        <div className="af-section__head">
          <span className="af-pill">Workflow Efficiency</span>
          <h2>Seamless Installation, Hands-Free Operation</h2>
          <p>
            Get set up rapidly. Once hardware units connect, the platform handles complex calculations, invoice
            dispatches, and leak alerts natively.
          </p>
        </div>

        <div className="af-howitworks__grid">
          {steps.map((s) => (
            <div className="af-howitworks__step" key={s.n}>
              <div className="af-howitworks__num-row">
                <span className="af-howitworks__num">{s.n}</span>
                <span className="af-howitworks__line" />
              </div>
              <h3>{s.title}</h3>
              <p>{s.text}</p>
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}

export default HowItWorks
