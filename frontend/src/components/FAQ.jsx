import { useState } from 'react'
import './FAQ.css'

const items = [
  {
    q: 'Do smart sub-meters require mechanical service shut-offs?',
    a: 'No. Our innovative riser meter units fit non-invasively directly over existing lines in individual crawlspaces. Setups complete cleanly in minutes without multi-tenant utility cutoffs.',
  },
  {
    q: 'How are communal/shared water costs calculated?',
    a: 'The central common-area pool (e.g. pool facilities, landscaping) is subtracted cleanly from main master meters first. Property groups allocate those structurally or pro-rate transparently.',
  },
  {
    q: 'How rapidly does HydroLedger detect continuous leak flows?',
    a: "Our background alert pipelines check continuous draw signals. If flow doesn't touch zero over a rolling 10-minute cycle, warning dispatches instantly route to minimize damage risks.",
  },
  {
    q: 'Does HydroLedger handle security or resident privacy?',
    a: 'Yes. HydroLedger protects and stores resident portal identifiers behind standard encrypted channels. Operational data is kept secure strictly for consumption tracking.',
  },
]

function FAQ() {
  const [open, setOpen] = useState(0)

  return (
    <section className="af-section af-faq" id="faq">
      <div className="af-section__inner">
        <div className="af-section__head">
          <span className="af-pill">FAQ Support</span>
          <h2>Frequently Answered Questions</h2>
        </div>

        <div className="af-faq__list">
          {items.map((item, i) => {
            const isOpen = open === i
            return (
              <div className={`af-faq__item ${isOpen ? 'af-faq__item--open' : ''}`} key={item.q}>
                <button
                  type="button"
                  className="af-faq__question"
                  onClick={() => setOpen(isOpen ? -1 : i)}
                  aria-expanded={isOpen}
                >
                  {item.q}
                  <span className="af-faq__icon">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
                      <circle cx="12" cy="12" r="9" stroke="currentColor" strokeWidth="1.6" />
                      <path d="M9 12h6" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
                      {!isOpen && <path d="M12 9v6" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />}
                    </svg>
                  </span>
                </button>
                {isOpen && <p className="af-faq__answer">{item.a}</p>}
              </div>
            )
          })}
        </div>
      </div>
    </section>
  )
}

export default FAQ
