import { useCallback, useEffect, useState } from 'react'
import i18next from 'i18next'
import { useTranslation } from 'react-i18next'
import { getCommonAreaConfig, saveCommonAreaConfig } from '../../api/commonAreaApi'
import { getTariffPlan, saveTariffPlan } from '../../api/tariffApi'
import './TariffPlans.css'

const DISTRIBUTION_METHODS = [
  { id: 'CONSUMPTION', labelKey: 'tariffPlans.distribution.consumption' },
  { id: 'FLOOR_AREA', labelKey: 'tariffPlans.distribution.floorArea' },
]

// Controlled <input type="number"> fields can end up displaying a stray leading zero (e.g. "010")
// after an edit: the browser shows exactly what was typed, but if the parsed number happens to
// match the previous value, React sees no change and never re-syncs the DOM back to "10". Strip
// leading zeros from what the user typed (preserving a genuine "0" or a decimal like "0.5") so the
// input always reflects the canonical text.
function sanitizeNumberInput(event) {
  const raw = event.target.value
  if (/^0+\d/.test(raw)) {
    event.target.value = raw.replace(/^0+/, '')
  }
  return event.target.value
}

function tierLevelLabel(t, index, total) {
  if (index === 0) return t('tariffPlans.tierLevel.base', { tier: 1 })
  if (index === total - 1) return t('tariffPlans.tierLevel.heavy', { tier: index + 1 })
  return t('tariffPlans.tierLevel.medium', { tier: index + 1 })
}

function TariffPlans() {
  const { t } = useTranslation('admin')
  const [autoGenerateInvoices, setAutoGenerateInvoices] = useState(true)

  const [basePricePerUnit, setBasePricePerUnit] = useState(0)
  const [firstTierMin, setFirstTierMin] = useState(0)
  const [tiers, setTiers] = useState([])
  const [persisted, setPersisted] = useState(false)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [saveError, setSaveError] = useState(null)
  const [saveSuccess, setSaveSuccess] = useState(false)
  // Saved configs show as a read-only summary with an Edit button; an unsaved one opens straight in edit mode.
  const [editingTariff, setEditingTariff] = useState(false)

  const [distributionMethod, setDistributionMethod] = useState('CONSUMPTION')
  const [costItems, setCostItems] = useState([])
  const [commonAreaPersisted, setCommonAreaPersisted] = useState(false)
  const [commonAreaLoading, setCommonAreaLoading] = useState(true)
  const [commonAreaSaving, setCommonAreaSaving] = useState(false)
  const [commonAreaSaveError, setCommonAreaSaveError] = useState(null)
  const [commonAreaSaveSuccess, setCommonAreaSaveSuccess] = useState(false)
  const [editingCommonArea, setEditingCommonArea] = useState(false)

  const loadCommonArea = useCallback(() => getCommonAreaConfig()
    .then((data) => {
      setDistributionMethod(data.distributionMethod)
      setCostItems(data.costItems.map((item) => ({
        id: item.id,
        label: item.label,
        monthlyAmount: Number(item.monthlyAmount),
        active: item.active,
      })))
      setCommonAreaPersisted(data.persisted)
      return data.persisted
    })
    .catch(() => {
      setCommonAreaSaveError(i18next.t('tariffPlans.commonArea.loadError', { ns: 'admin' }))
      return true
    })
    .finally(() => setCommonAreaLoading(false)), [])

  useEffect(() => {
    loadCommonArea().then((persisted) => setEditingCommonArea(!persisted))
  }, [loadCommonArea])

  function cancelCommonAreaEdit() {
    setCommonAreaSaveError(null)
    loadCommonArea().then(() => setEditingCommonArea(false))
  }

  function addCostItem() {
    setCommonAreaSaveSuccess(false)
    setCostItems((prev) => [...prev, { label: '', monthlyAmount: 0, active: true }])
  }

  function removeCostItem(index) {
    setCommonAreaSaveSuccess(false)
    setCostItems((prev) => prev.filter((_, idx) => idx !== index))
  }

  function updateCostItemLabel(index, value) {
    setCommonAreaSaveSuccess(false)
    setCostItems((prev) => prev.map((item, idx) => (idx === index ? { ...item, label: value } : item)))
  }

  function updateCostItemAmount(index, value) {
    setCommonAreaSaveSuccess(false)
    setCostItems((prev) => prev.map((item, idx) => (idx === index ? { ...item, monthlyAmount: Number(value) } : item)))
  }

  function toggleCostItemActive(index) {
    setCommonAreaSaveSuccess(false)
    setCostItems((prev) => prev.map((item, idx) => (idx === index ? { ...item, active: !item.active } : item)))
  }

  async function handleSaveCommonArea() {
    setCommonAreaSaving(true)
    setCommonAreaSaveError(null)
    setCommonAreaSaveSuccess(false)
    try {
      const payloadItems = costItems.map((item) => ({
        label: item.label,
        monthlyAmount: item.monthlyAmount,
        active: item.active,
      }))
      const data = await saveCommonAreaConfig(distributionMethod, payloadItems)
      setDistributionMethod(data.distributionMethod)
      setCostItems(data.costItems.map((item) => ({
        id: item.id,
        label: item.label,
        monthlyAmount: Number(item.monthlyAmount),
        active: item.active,
      })))
      setCommonAreaPersisted(true)
      setCommonAreaSaveSuccess(true)
      setEditingCommonArea(false)
    } catch (err) {
      setCommonAreaSaveError(err.response?.data?.message || t('tariffPlans.commonArea.saveError'))
    } finally {
      setCommonAreaSaving(false)
    }
  }

  const loadTariff = useCallback(() => getTariffPlan()
    .then((data) => {
      setTiers(data.tiers.map((tier) => ({
        boundary: tier.maxUnits != null ? Number(tier.maxUnits) : null,
        rate: Number(tier.ratePerUnit),
      })))
      setFirstTierMin(data.tiers.length > 0 ? Number(data.tiers[0].minUnits) : 0)
      setBasePricePerUnit(Number(data.basePricePerUnit ?? 0))
      setPersisted(data.persisted)
      return data.persisted
    })
    .catch(() => {
      setSaveError(i18next.t('tariffPlans.loadError', { ns: 'admin' }))
      return true
    })
    .finally(() => setLoading(false)), [])

  useEffect(() => {
    loadTariff().then((isPersisted) => setEditingTariff(!isPersisted))
  }, [loadTariff])

  function cancelTariffEdit() {
    setSaveError(null)
    loadTariff().then(() => setEditingTariff(false))
  }

  const tierRows = tiers.map((tier, index) => ({
    level: tierLevelLabel(t, index, tiers.length),
    min: index === 0 ? firstTierMin : tiers[index - 1].boundary,
    max: tier.boundary,
    rate: tier.rate,
  }))
  const maxRate = Math.max(...tierRows.map((t) => t.rate), 1)

  function updateRate(index, value) {
    setSaveSuccess(false)
    setTiers((prev) => prev.map((t, idx) => (idx === index ? { ...t, rate: Number(value) } : t)))
  }

  function updateBoundary(index, value) {
    setSaveSuccess(false)
    setTiers((prev) => prev.map((t, idx) => (idx === index ? { ...t, boundary: Number(value) } : t)))
  }

  function updateFirstTierMin(value) {
    setSaveSuccess(false)
    setFirstTierMin(Number(value))
  }

  function updateBasePricePerUnit(value) {
    setSaveSuccess(false)
    setBasePricePerUnit(Number(value))
  }

  function addTier() {
    setSaveSuccess(false)
    setTiers((prev) => {
      if (prev.length === 0) return [{ boundary: null, rate: 0 }]
      const last = prev[prev.length - 1]
      const previousBoundary = prev.length >= 2 ? prev[prev.length - 2].boundary : firstTierMin
      const newTier = { boundary: (previousBoundary ?? 0) + 10, rate: last.rate }
      return [...prev.slice(0, -1), newTier, last]
    })
  }

  function removeTier(index) {
    setSaveSuccess(false)
    setTiers((prev) => {
      if (prev.length <= 1) return prev
      const next = prev.filter((_, idx) => idx !== index)
      return next.map((t, idx) => (idx === next.length - 1 ? { ...t, boundary: null } : t))
    })
  }

  async function handleSave() {
    setSaving(true)
    setSaveError(null)
    setSaveSuccess(false)
    try {
      const payloadTiers = tiers.map((tier, index) => ({
        minUnits: index === 0 ? firstTierMin : tiers[index - 1].boundary,
        maxUnits: tier.boundary,
        ratePerUnit: tier.rate,
      }))
      const data = await saveTariffPlan(basePricePerUnit, payloadTiers)
      setTiers(data.tiers.map((tier) => ({
        boundary: tier.maxUnits != null ? Number(tier.maxUnits) : null,
        rate: Number(tier.ratePerUnit),
      })))
      setFirstTierMin(data.tiers.length > 0 ? Number(data.tiers[0].minUnits) : 0)
      setBasePricePerUnit(Number(data.basePricePerUnit ?? 0))
      setPersisted(true)
      setSaveSuccess(true)
      setEditingTariff(false)
    } catch (err) {
      setSaveError(err.response?.data?.message || t('tariffPlans.saveError'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <>
      <div className="af-tariff__grid">
        <section className="af-tariff__panel af-tariff__panel--tiers">
          <h2>{t('tariffPlans.chartTitle')}</h2>
          <div className="af-tariff__bars">
            {tierRows.map((tier) => (
              <div key={tier.level} className="af-tariff__bar-col">
                <div className="af-tariff__bar-track">
                  <div className="af-tariff__bar" style={{ height: `${(tier.rate / maxRate) * 100}%` }} />
                </div>
                <strong>₹{tier.rate}</strong>
                <span>{tier.max != null ? `${tier.min} - ${tier.max}` : `${tier.min}+`}</span>
              </div>
            ))}
          </div>
          <p className="af-tariff__axis-note">{t('tariffPlans.axisNote')}</p>
        </section>

        <section className="af-tariff__panel af-tariff__panel--tiers">
          <h2>{t('tariffPlans.tableTitle')}</h2>
          {!persisted && !loading && (
            <p className="af-tariff__preview-note">{t('tariffPlans.defaultRatesNote')}</p>
          )}
          <div className="af-tariff__base-price">
            <label>
              <span>{t('tariffPlans.basePrice.label')}</span>
              {editingTariff ? (
                <input
                  type="number"
                  className="af-tariff__cell-input"
                  min="0"
                  step="0.01"
                  value={basePricePerUnit}
                  onChange={(e) => updateBasePricePerUnit(sanitizeNumberInput(e))}
                />
              ) : (
                <strong className="af-tariff__view-value">₹{basePricePerUnit}</strong>
              )}
            </label>
            <p className="af-tariff__axis-note">
              {t('tariffPlans.basePrice.note')}
            </p>
          </div>
          <div className="af-tariff__table-scroll">
            <table className="af-tariff__table">
              <thead>
                <tr>
                  <th>{t('tariffPlans.table.tierLevel')}</th>
                  <th>{t('tariffPlans.table.slabLimit')}</th>
                  <th>{t('tariffPlans.table.rate')}</th>
                  {editingTariff && <th>{t('tariffPlans.table.action')}</th>}
                </tr>
              </thead>
              <tbody>
                {tierRows.map((tier, index) => (
                  <tr key={tier.level}>
                    <td>{tier.level}</td>
                    <td>
                      {index === 0 && editingTariff ? (
                        <input
                          type="number"
                          className="af-tariff__cell-input"
                          min="0"
                          value={tier.min}
                          onChange={(e) => updateFirstTierMin(sanitizeNumberInput(e))}
                        />
                      ) : (
                        tier.min
                      )}
                      {' - '}
                      {tier.max != null && editingTariff ? (
                        <input
                          type="number"
                          className="af-tariff__cell-input"
                          min={tier.min + 1}
                          value={tier.max}
                          onChange={(e) => updateBoundary(index, sanitizeNumberInput(e))}
                        />
                      ) : (
                        tier.max ?? '∞'
                      )}
                    </td>
                    <td>
                      ₹
                      {editingTariff ? (
                        <input
                          type="number"
                          className="af-tariff__cell-input"
                          min="0"
                          step="0.01"
                          value={tier.rate}
                          onChange={(e) => updateRate(index, sanitizeNumberInput(e))}
                        />
                      ) : (
                        tier.rate
                      )}
                    </td>
                    {editingTariff && (
                      <td>
                        <button
                          type="button"
                          className="af-tariff__edit-link"
                          disabled={tiers.length <= 1}
                          onClick={() => removeTier(index)}
                        >
                          {t('shared.remove')}
                        </button>
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="af-tariff__tier-actions">
            {editingTariff ? (
              <button type="button" className="af-tariff__add-tier" onClick={addTier}>
                {t('tariffPlans.addTier')}
              </button>
            ) : (
              <span />
            )}
            <div className="af-tariff__save-area">
              {saveError && <span className="af-tariff__save-error">{saveError}</span>}
              {saveSuccess && !editingTariff && <span className="af-tariff__save-success">{t('shared.saved')}</span>}
              {editingTariff ? (
                <>
                  {persisted && (
                    <button type="button" className="af-tariff__cancel" disabled={saving} onClick={cancelTariffEdit}>
                      {t('shared.cancel')}
                    </button>
                  )}
                  <button type="button" className="af-tariff__save" disabled={saving || loading} onClick={handleSave}>
                    {saving ? t('shared.saving') : t('tariffPlans.saveRates')}
                  </button>
                </>
              ) : (
                <button
                  type="button"
                  className="af-tariff__edit-btn"
                  disabled={loading}
                  onClick={() => {
                    setSaveSuccess(false)
                    setEditingTariff(true)
                  }}
                >
                  {t('shared.edit')}
                </button>
              )}
            </div>
          </div>
        </section>
      </div>

      <div className="af-tariff__grid">
        <section className="af-tariff__panel">
          <h2>{t('tariffPlans.commonArea.title')}</h2>
          {!commonAreaPersisted && !commonAreaLoading && (
            <p className="af-tariff__preview-note">{t('tariffPlans.commonArea.empty')}</p>
          )}
          <ul className="af-tariff__cost-list">
            {costItems.map((item, index) => (
              <li key={item.id ?? index}>
                {editingCommonArea ? (
                  <>
                    <input
                      type="text"
                      className="af-tariff__cell-input"
                      placeholder={t('tariffPlans.commonArea.labelPlaceholder')}
                      value={item.label}
                      onChange={(e) => updateCostItemLabel(index, e.target.value)}
                    />
                    <span>₹</span>
                    <input
                      type="number"
                      className="af-tariff__cell-input"
                      min="0"
                      step="0.01"
                      value={item.monthlyAmount}
                      onChange={(e) => updateCostItemAmount(index, e.target.value)}
                    />
                    <span>{t('tariffPlans.commonArea.perMonth')}</span>
                    <label className="af-tariff__cost-active">
                      <input type="checkbox" checked={item.active} onChange={() => toggleCostItemActive(index)} />
                      {t('tariffPlans.commonArea.active')}
                    </label>
                    <button type="button" className="af-tariff__edit-link" onClick={() => removeCostItem(index)}>
                      {t('shared.remove')}
                    </button>
                  </>
                ) : (
                  <>
                    <strong className="af-tariff__cost-name">{item.label}</strong>
                    <span className="af-tariff__view-value">₹{item.monthlyAmount}</span>
                    <span>{t('tariffPlans.commonArea.perMonth')}</span>
                    <span className={`af-tariff__pill ${item.active ? 'af-tariff__pill--on' : ''}`}>
                      {item.active ? t('tariffPlans.commonArea.active') : t('tariffPlans.commonArea.inactive')}
                    </span>
                  </>
                )}
              </li>
            ))}
          </ul>
          <div className="af-tariff__tier-actions">
            {editingCommonArea ? (
              <button type="button" className="af-tariff__add-tier" onClick={addCostItem}>
                {t('tariffPlans.commonArea.addItem')}
              </button>
            ) : (
              <span />
            )}
            <div className="af-tariff__save-area">
              {commonAreaSaveError && <span className="af-tariff__save-error">{commonAreaSaveError}</span>}
              {commonAreaSaveSuccess && !editingCommonArea && (
                <span className="af-tariff__save-success">{t('shared.saved')}</span>
              )}
              {editingCommonArea ? (
                <>
                  {commonAreaPersisted && (
                    <button
                      type="button"
                      className="af-tariff__cancel"
                      disabled={commonAreaSaving}
                      onClick={cancelCommonAreaEdit}
                    >
                      {t('shared.cancel')}
                    </button>
                  )}
                  <button
                    type="button"
                    className="af-tariff__save"
                    disabled={commonAreaSaving || commonAreaLoading || costItems.length === 0}
                    onClick={handleSaveCommonArea}
                  >
                    {commonAreaSaving ? t('shared.saving') : t('tariffPlans.commonArea.save')}
                  </button>
                </>
              ) : (
                <button
                  type="button"
                  className="af-tariff__edit-btn"
                  disabled={commonAreaLoading}
                  onClick={() => {
                    setCommonAreaSaveSuccess(false)
                    setEditingCommonArea(true)
                  }}
                >
                  {t('shared.edit')}
                </button>
              )}
            </div>
          </div>
        </section>

        <section className="af-tariff__panel">
          <h2>{t('tariffPlans.distribution.title')}</h2>
          <div className="af-tariff__radio-group">
            {DISTRIBUTION_METHODS.map((method) => (
              <label key={method.id} className="af-tariff__radio">
                <input
                  type="radio"
                  name="distributionMethod"
                  disabled={!editingCommonArea}
                  checked={distributionMethod === method.id}
                  onChange={() => {
                    setCommonAreaSaveSuccess(false)
                    setDistributionMethod(method.id)
                  }}
                />
                <span>{t(method.labelKey)}</span>
              </label>
            ))}
          </div>
        </section>
      </div>

      <section className="af-tariff__panel af-tariff__footer">
        <div className="af-tariff__cycle">
          <span className="af-tariff__cycle-label">{t('tariffPlans.footer.cycleStart')}</span>
          <strong>{t('tariffPlans.footer.cycleStartValue')}</strong>
        </div>

        <label className="af-tariff__toggle-field">
          <span>{t('tariffPlans.footer.autoInvoices')}</span>
          <span
            className={`af-tariff__toggle ${autoGenerateInvoices ? 'af-tariff__toggle--on' : ''}`}
            onClick={() => setAutoGenerateInvoices((prev) => !prev)}
            role="switch"
            aria-checked={autoGenerateInvoices}
            tabIndex={0}
          >
            <span className="af-tariff__toggle-knob" />
          </span>
        </label>
      </section>
    </>
  )
}

export default TariffPlans
