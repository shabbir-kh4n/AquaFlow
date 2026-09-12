import { useTranslation } from 'react-i18next'
import './Testimonials.css'

// Names/roles are proper nouns - kept local (not translated), paired by index
// with the translated quote text from landing.json.
const AUTHORS = [
  { name: 'Marcus Vance', role: 'Operations VP, Lincoln Community Group' },
  { name: 'Samantha Cole', role: 'Property Manager, Summit Ridge Highrise' },
]

function Testimonials() {
  const { t } = useTranslation('landing')
  const quotes = t('testimonials.quotes', { returnObjects: true })

  return (
    <section className="af-section af-testimonials">
      <div className="af-section__inner">
        <div className="af-section__head">
          <span className="af-pill">{t('testimonials.pill')}</span>
          <h2>{t('testimonials.heading')}</h2>
        </div>

        <div className="af-testimonials__grid">
          {quotes.map((q, i) => (
            <div className="af-testimonials__card" key={AUTHORS[i].name}>
              <span className="af-testimonials__quote-icon">&ldquo;</span>
              <p>{q.text}</p>
              <div className="af-testimonials__author">
                <span className="af-testimonials__avatar">{AUTHORS[i].name.charAt(0)}</span>
                <div>
                  <strong>{AUTHORS[i].name}</strong>
                  <span>{AUTHORS[i].role}</span>
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
