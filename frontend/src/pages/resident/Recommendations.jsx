import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { getRecommendations } from '../../api/recommendationApi'
import { Icon } from '../../components/resident/Icon'
import './Recommendations.css'

function formatGeneratedAt(generatedAt, sourcePeriodMonth, t, locale) {
  if (!generatedAt) return null
  const date = new Date(generatedAt).toLocaleDateString(locale, { month: 'short', day: 'numeric', year: 'numeric' })
  return sourcePeriodMonth
    ? t('recommendations.generatedForPeriod', { date, period: sourcePeriodMonth })
    : t('recommendations.generated', { date })
}

function Recommendations() {
  const { t, i18n } = useTranslation('resident')
  const locale = i18n.resolvedLanguage || 'en'
  const [tips, setTips] = useState([])
  const [generatedAt, setGeneratedAt] = useState(null)
  const [sourcePeriodMonth, setSourcePeriodMonth] = useState(null)
  const [error, setError] = useState('')
  // Language the current tips/error were fetched for — loading whenever it lags the active language.
  const [loadedLang, setLoadedLang] = useState(null)
  const loading = loadedLang !== locale

  // Tips are generated in the active UI language, so refetch (showing the loading state, since the
  // first load in a new language waits on regeneration) whenever the language changes.
  useEffect(() => {
    let cancelled = false
    getRecommendations()
      .then((data) => {
        if (cancelled) return
        setTips(data.tips || [])
        setGeneratedAt(data.generatedAt)
        setSourcePeriodMonth(data.sourcePeriodMonth)
        setError('')
        setLoadedLang(locale)
      })
      .catch(() => {
        if (cancelled) return
        setError(t('recommendations.loadError'))
        setLoadedLang(locale)
      })
    return () => {
      cancelled = true
    }
  }, [t, locale])

  if (loading) {
    return <p className="af-rec__empty">{t('recommendations.loading')}</p>
  }

  if (error) {
    return <div className="af-rec__error">{error}</div>
  }

  const meta = formatGeneratedAt(generatedAt, sourcePeriodMonth, t, locale)

  return (
    <section className="af-rec__panel">
      <div className="af-rec__heading">
        <div>
          <p className="af-rec__eyebrow">{t('recommendations.eyebrow')}</p>
          <h3>{t('recommendations.title')}</h3>
        </div>
        {meta && <span className="af-rec__meta">{meta}</span>}
      </div>

      {tips.length === 0 ? (
        <p className="af-rec__empty">
          {t('recommendations.empty')}
        </p>
      ) : (
        <ul className="af-rec__list">
          {tips.map((tip) => (
            <li key={tip}>
              <span className="af-rec__tip-icon"><Icon name="bulb" /></span>
              <span>{tip}</span>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

export default Recommendations
