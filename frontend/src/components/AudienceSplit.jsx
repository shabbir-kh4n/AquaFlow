import './AudienceSplit.css'

function AudienceSplit() {
  return (
    <section className="af-section af-audience">
      <div className="af-section__inner af-audience__grid">
        <div className="af-audience__card">
          <span className="af-pill">For Residents</span>
          <h3>Fair, Consumption-Based Utility Costs</h3>
          <p>
            Stop paying for your neighbor's excessive consumption. Pay strictly for the gallons you draw, backed
            by real-time dashboard data.
          </p>
          <ul>
            <li>Accurate monthly sub-metering data with transparent cost metrics.</li>
            <li>SMS &amp; mobile notifications if micro-leaks are detected in appliances.</li>
            <li>Simple, streamlined digital invoice payment system with automated receipts.</li>
          </ul>
        </div>

        <div className="af-audience__card af-audience__card--dark">
          <span className="af-pill af-pill--light">For Property Admins</span>
          <h3>Instant Operational ROI and Waste Control</h3>
          <p>
            Transform utility management from a high-loss headache into a seamless, high-margin pipeline with
            automated resident billing.
          </p>
          <ul>
            <li>Hands-free utility allocation directly connected to individual units.</li>
            <li>Immediate operational alerts to prevent high-cost structural water damage.</li>
            <li>Detailed dashboard reporting on master meter billing vs. distributed gallons.</li>
          </ul>
        </div>
      </div>
    </section>
  )
}

export default AudienceSplit
