import './Stats.css'

const stats = [
  { value: '40%', label: 'Average Water Saved' },
  { value: '10,000+', label: 'Active Households' },
  { value: '99.9%', label: 'System Uptime' },
  { value: '500+', label: 'Communities Managed' },
]

function Stats() {
  return (
    <section className="af-stats af-dark-gradient">
      <div className="af-stats__grid">
        {stats.map((s) => (
          <div className="af-stats__item" key={s.label}>
            <span className="af-stats__value">{s.value}</span>
            <span className="af-stats__label">{s.label}</span>
          </div>
        ))}
      </div>
    </section>
  )
}

export default Stats
