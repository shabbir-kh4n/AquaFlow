import { useTranslation } from 'react-i18next'
import './ComingSoon.css'

function ComingSoon({ titleKey, descriptionKey }) {
  const { t } = useTranslation('common')

  return (
    <div className="af-coming-soon">
      <div className="af-coming-soon__icon">
        <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
          <path d="M12 8v4l3 3M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z" />
        </svg>
      </div>
      <h2>{t('comingSoon.title', { title: t(titleKey) })}</h2>
      <p>{t(descriptionKey)}</p>
    </div>
  )
}

export default ComingSoon
