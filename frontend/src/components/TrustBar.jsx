import { useTranslation } from 'react-i18next'
import './TrustBar.css'

const partners = [
  'Avalon Communities',
  'Apex Apartments',
  'Summit Living',
  'Riverstone Multi-Family',
  'Metro Resident Partners',
]

function TrustBar() {
  const { t } = useTranslation('landing')

  return (
    <section className="af-trust">
      <p className="af-trust__label">{t('trustBar.label')}</p>
      <div className="af-trust__row">
        {partners.map((name) => (
          <span className="af-trust__item" key={name}>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
              <path
                d="M4 21V6l8-3 8 3v15M4 21h16M9 21v-4h6v4M9 11h1M9 15h1M14 11h1M14 15h1"
                stroke="currentColor"
                strokeWidth="1.6"
                strokeLinecap="round"
                strokeLinejoin="round"
              />
            </svg>
            {name}
          </span>
        ))}
      </div>
    </section>
  )
}

export default TrustBar
