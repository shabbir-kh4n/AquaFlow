import { useEffect, useState } from 'react'
import { getRecommendations } from '../../api/recommendationApi'
import { Icon } from '../../components/resident/Icon'
import './Recommendations.css'

function formatGeneratedAt(generatedAt, sourcePeriodMonth) {
  if (!generatedAt) return null
  const date = new Date(generatedAt).toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' })
  return sourcePeriodMonth ? `Generated ${date} for billing period ${sourcePeriodMonth}` : `Generated ${date}`
}

function Recommendations() {
  const [tips, setTips] = useState([])
  const [generatedAt, setGeneratedAt] = useState(null)
  const [sourcePeriodMonth, setSourcePeriodMonth] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    getRecommendations()
      .then((data) => {
        setTips(data.tips || [])
        setGeneratedAt(data.generatedAt)
        setSourcePeriodMonth(data.sourcePeriodMonth)
      })
      .catch(() => setError('Could not load your recommendations. Please try again.'))
      .finally(() => setLoading(false))
  }, [])

  if (loading) {
    return <p className="af-rec__empty">Loading your personalized tips…</p>
  }

  if (error) {
    return <div className="af-rec__error">{error}</div>
  }

  const meta = formatGeneratedAt(generatedAt, sourcePeriodMonth)

  return (
    <section className="af-rec__panel">
      <div className="af-rec__heading">
        <div>
          <p className="af-rec__eyebrow">Personalized for you</p>
          <h3>Smart Water-Saving Tips</h3>
        </div>
        {meta && <span className="af-rec__meta">{meta}</span>}
      </div>

      {tips.length === 0 ? (
        <p className="af-rec__empty">
          No tips generated yet — these are refreshed automatically each time your community
          finalizes a billing cycle for your household.
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
