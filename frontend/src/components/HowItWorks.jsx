import { useTranslation } from 'react-i18next'
import './HowItWorks.css'

const STEP_NUMBERS = ['01', '02', '03']

function HowItWorks() {
  const { t } = useTranslation('landing')
  const steps = t('howItWorks.steps', { returnObjects: true })

  return (
    <section className="af-section af-howitworks" id="how-it-works">
      <div className="af-section__inner">
        <div className="af-section__head">
          <span className="af-pill">{t('howItWorks.pill')}</span>
          <h2>{t('howItWorks.heading')}</h2>
          <p>{t('howItWorks.paragraph')}</p>
        </div>

        <div className="af-howitworks__grid">
          {steps.map((s, i) => (
            <div className="af-howitworks__step" key={s.title}>
              <div className="af-howitworks__num-row">
                <span className="af-howitworks__num">{STEP_NUMBERS[i]}</span>
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
