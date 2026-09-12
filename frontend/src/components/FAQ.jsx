import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import './FAQ.css'

function FAQ() {
  const { t } = useTranslation('landing')
  const items = t('faq.items', { returnObjects: true })
  const [open, setOpen] = useState(0)

  return (
    <section className="af-section af-faq" id="faq">
      <div className="af-section__inner">
        <div className="af-section__head">
          <span className="af-pill">{t('faq.pill')}</span>
          <h2>{t('faq.heading')}</h2>
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
