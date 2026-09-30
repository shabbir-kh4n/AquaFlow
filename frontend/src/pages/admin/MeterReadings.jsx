import { useEffect, useMemo, useRef, useState } from 'react'
import i18next from 'i18next'
import { Trans, useTranslation } from 'react-i18next'
import { listResidents } from '../../api/residentApi'
import { bulkUploadUsageCsv, listUsageReadings, recordUsageReading } from '../../api/meterUsageApi'
import '../../components/AuthForm.css'
import FilePicker from '../../components/FilePicker'
import './MeterReadings.css'

function today() {
  return new Date().toISOString().slice(0, 10)
}

const EMPTY_READING_FORM = {
  readingDate: today(),
  readingValue: '',
}

const TABS = [
  { id: 'single', labelKey: 'meterReadings.tabs.single' },
  { id: 'csv', labelKey: 'meterReadings.tabs.csv' },
  { id: 'history', labelKey: 'meterReadings.tabs.history' },
]

const SOURCE_LABELS = {
  MANUAL: 'meterReadings.source.manual',
  CSV: 'meterReadings.source.csv',
}

// Literal column names the backend CSV parser expects - not translated.
const CSV_COLUMNS = 'meterNumber,readingDate,readingValue'

function MeterReadings() {
  const { t } = useTranslation('admin')
  const [activeTab, setActiveTab] = useState('single')

  // Search combobox + selection
  const [residents, setResidents] = useState([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')

  const [search, setSearch] = useState('')
  const [dropdownOpen, setDropdownOpen] = useState(false)
  const [selected, setSelected] = useState(null)
  const [latestByMeter, setLatestByMeter] = useState({})
  const comboRef = useRef(null)
  const searchInputRef = useRef(null)

  // Single-entry form
  const [form, setForm] = useState(EMPTY_READING_FORM)
  const [formError, setFormError] = useState('')
  const [successMessage, setSuccessMessage] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const [recentReadings, setRecentReadings] = useState([])
  const [recentLoading, setRecentLoading] = useState(false)

  // Bulk CSV upload
  const [csvFile, setCsvFile] = useState(null)
  const [csvResult, setCsvResult] = useState(null)
  const [csvError, setCsvError] = useState('')
  const [csvUploading, setCsvUploading] = useState(false)

  // Reading history (whole apartment, no meter filter)
  const [allReadings, setAllReadings] = useState([])
  const [allReadingsLoading, setAllReadingsLoading] = useState(false)
  const [allReadingsError, setAllReadingsError] = useState('')

  useEffect(() => {
    listResidents()
      .then(setResidents)
      .catch(() => setLoadError(i18next.t('meterReadings.loadResidentsError', { ns: 'admin' })))
      .finally(() => setLoading(false))

    listUsageReadings({})
      .then((readings) => {
        const latest = {}
        for (const reading of readings) {
          const current = latest[reading.meterNumber]
          if (!current || reading.readingDate > current.readingDate) {
            latest[reading.meterNumber] = reading
          }
        }
        setLatestByMeter(latest)
      })
      .catch(() => setLatestByMeter({}))
  }, [])

  function handleTabChange(tabId) {
    setActiveTab(tabId)
    if (tabId === 'history') {
      setAllReadingsLoading(true)
      setAllReadingsError('')
      listUsageReadings({})
        .then(setAllReadings)
        .catch(() => setAllReadingsError(t('meterReadings.historyLoadError')))
        .finally(() => setAllReadingsLoading(false))
    }
  }

  const matches = useMemo(() => {
    const term = search.trim().toLowerCase()
    if (!term) return residents
    return residents.filter((r) =>
      [r.fullName, r.houseNumber, r.meterNumber].some((field) => field?.toLowerCase().includes(term)),
    )
  }, [residents, search])

  // consumptionMonth (not readingDate) is used here: consumption is the delta since the previous
  // reading, so it belongs to the month the usage actually happened in, which is the month of the
  // earlier reading — not the (later) month the reading confirming it was recorded in.
  const monthConsumption = useMemo(() => {
    const currentMonth = today().slice(0, 7)
    return recentReadings
      .filter((reading) => reading.consumptionMonth === currentMonth)
      .reduce((sum, reading) => sum + Number(reading.consumption || 0), 0)
  }, [recentReadings])

  const lastReading = recentReadings[0] || null

  useEffect(() => {
    function handleClickOutside(event) {
      if (comboRef.current && !comboRef.current.contains(event.target)) {
        setDropdownOpen(false)
      }
    }
    function handleEscape(event) {
      if (event.key === 'Escape') {
        setDropdownOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    document.addEventListener('keydown', handleEscape)
    return () => {
      document.removeEventListener('mousedown', handleClickOutside)
      document.removeEventListener('keydown', handleEscape)
    }
  }, [])

  function loadRecentReadings(meterNumber) {
    setRecentLoading(true)
    listUsageReadings({ meterNumber })
      .then(setRecentReadings)
      .catch(() => setRecentReadings([]))
      .finally(() => setRecentLoading(false))
  }

  function handleSelect(resident) {
    setSelected(resident)
    setSearch('')
    setDropdownOpen(false)
    setForm(EMPTY_READING_FORM)
    setFormError('')
    setSuccessMessage('')
    if (resident.meterNumber) {
      loadRecentReadings(resident.meterNumber)
    } else {
      setRecentReadings([])
    }
  }

  function handleClearSelection() {
    setSelected(null)
    setForm(EMPTY_READING_FORM)
    setFormError('')
    setSuccessMessage('')
    setRecentReadings([])
  }

  function handleFormChange(event) {
    const { name, value } = event.target
    setForm((prev) => ({ ...prev, [name]: value }))
  }

  async function handleSubmit(event) {
    event.preventDefault()
    setFormError('')
    setSuccessMessage('')

    if (!selected?.meterNumber) {
      setFormError(t('meterReadings.errors.noMeter'))
      return
    }

    if (!form.readingDate) {
      setFormError(t('meterReadings.errors.dateRequired'))
      return
    }

    if (form.readingValue === '' || Number(form.readingValue) < 0) {
      setFormError(t('meterReadings.errors.invalidValue'))
      return
    }

    setSubmitting(true)

    try {
      const result = await recordUsageReading({
        meterNumber: selected.meterNumber,
        readingDate: form.readingDate,
        readingValue: form.readingValue,
      })
      setSuccessMessage(
        t('meterReadings.successMessage', {
          value: result.readingValue,
          house: selected.houseNumber,
          meter: selected.meterNumber,
          consumption: result.consumption,
        }),
      )
      setForm(EMPTY_READING_FORM)
      loadRecentReadings(selected.meterNumber)
    } catch (err) {
      const status = err.response?.status
      if (status === 409) {
        setFormError(t('meterReadings.errors.duplicate'))
      } else if (status === 404) {
        setFormError(t('meterReadings.errors.meterNotFound'))
      } else if (status === 422) {
        setFormError(err.response?.data?.message || t('meterReadings.errors.lowerThanPrevious'))
      } else {
        setFormError(err.response?.data?.message || t('meterReadings.errors.recordFailed'))
      }
    } finally {
      setSubmitting(false)
    }
  }

  async function handleCsvSubmit(event) {
    event.preventDefault()
    setCsvError('')
    setCsvResult(null)

    if (!csvFile) {
      setCsvError(t('meterReadings.csv.noFile'))
      return
    }

    setCsvUploading(true)

    try {
      const result = await bulkUploadUsageCsv(csvFile)
      setCsvResult(result)
      setCsvFile(null)
    } catch (err) {
      setCsvError(err.response?.data?.message || t('shared.uploadError'))
    } finally {
      setCsvUploading(false)
    }
  }

  return (
    <>
      <div className="af-mr__tabs" role="tablist">
        {TABS.map((tab) => (
          <button
            key={tab.id}
            type="button"
            role="tab"
            aria-selected={activeTab === tab.id}
            className={`af-mr__tab ${activeTab === tab.id ? 'af-mr__tab--active' : ''}`}
            onClick={() => handleTabChange(tab.id)}
          >
            {t(tab.labelKey)}
          </button>
        ))}
      </div>

      {activeTab === 'single' && (
        <div className="af-mr__columns">
          <div className="af-mr__col-main">
            <div className="af-mr__combo" ref={comboRef}>
              <label className="af-mr__combo-label" htmlFor="unit-combo-trigger">
                {t('meterReadings.combo.label')} <span className="af-mr__required">*</span>
              </label>
              <button
                type="button"
                id="unit-combo-trigger"
                className="af-mr__combo-trigger"
                onClick={() => {
                  setDropdownOpen((open) => !open)
                  setTimeout(() => searchInputRef.current?.focus(), 0)
                }}
              >
                {selected ? (
                  <span className="af-mr__combo-trigger-value">
                    <strong>{t('meterReadings.combo.flat', { house: selected.houseNumber || '—' })}</strong>
                    <span className="af-mr__combo-trigger-meter">
                      🔧 {selected.meterNumber || t('meterReadings.combo.noMeter')}
                    </span>
                    <span className="af-mr__combo-trigger-name">({selected.fullName})</span>
                  </span>
                ) : (
                  <span className="af-mr__combo-placeholder">{t('meterReadings.combo.placeholder')}</span>
                )}
                <svg className="af-mr__combo-chevron" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="m6 9 6 6 6-6" />
                </svg>
              </button>

              {dropdownOpen && (
                <div className="af-mr__dropdown">
                  <div className="af-hh__search af-mr__dropdown-search">
                    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                      <circle cx="11" cy="11" r="7" />
                      <path d="m21 21-4.35-4.35" />
                    </svg>
                    <input
                      ref={searchInputRef}
                      type="text"
                      placeholder={t('meterReadings.combo.searchPlaceholder')}
                      value={search}
                      onChange={(e) => setSearch(e.target.value)}
                    />
                  </div>

                  <div className="af-mr__dropdown-header">
                    <span>{t('meterReadings.combo.unitsHeader')}</span>
                    <span>{t('meterReadings.combo.found', { total: matches.length })}</span>
                  </div>

                  <ul className="af-mr__dropdown-list">
                    {loading ? (
                      <li className="af-mr__dropdown-empty">{t('meterReadings.combo.loading')}</li>
                    ) : matches.length === 0 ? (
                      <li className="af-mr__dropdown-empty">{t('meterReadings.combo.noMatch', { search })}</li>
                    ) : (
                      matches.map((resident) => {
                        const latest = resident.meterNumber ? latestByMeter[resident.meterNumber] : null
                        const isSelected = selected?.id === resident.id
                        return (
                          <li key={resident.id}>
                            <button
                              type="button"
                              className={`af-mr__dropdown-item ${isSelected ? 'af-mr__dropdown-item--selected' : ''}`}
                              onClick={() => handleSelect(resident)}
                            >
                              <span className="af-mr__combo-avatar">{(resident.houseNumber || '?').slice(0, 4)}</span>
                              <span className="af-mr__combo-meta">
                                <strong>
                                  {t('meterReadings.combo.flat', { house: resident.houseNumber || '—' })}
                                  <span className="af-mr__combo-meter-pill">🔧 {resident.meterNumber || '—'}</span>
                                </strong>
                                <span>
                                  {t('meterReadings.combo.itemMeta', {
                                    name: resident.fullName || t('meterReadings.combo.unassigned'),
                                    last: latest ? `${latest.readingValue} kL` : '—',
                                  })}
                                </span>
                              </span>
                              {isSelected && (
                                <svg className="af-mr__combo-check" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                                  <path d="M20 6 9 17l-5-5" />
                                </svg>
                              )}
                            </button>
                          </li>
                        )
                      })
                    )}
                  </ul>
                </div>
              )}
            </div>

            {loadError && <div className="af-hh__error">{loadError}</div>}

            {selected && (
              <div className="af-mr__panel">
                <div className="af-mr__panel-heading">
                  <div>
                    <p className="af-mr__eyebrow">{t('meterReadings.panel.eyebrow')}</p>
                    <h3>{selected.fullName}</h3>
                    <p className="af-mr__context">
                      {t('meterReadings.panel.context', {
                        house: selected.houseNumber || '—',
                        meter: selected.meterNumber || '—',
                      })}
                    </p>
                  </div>
                  <button type="button" className="af-btn af-btn--outline" onClick={handleClearSelection}>
                    {t('meterReadings.panel.changeResident')}
                  </button>
                </div>

                <form onSubmit={handleSubmit} className="af-mr__form">
                  {formError && <div className="af-form__error">{formError}</div>}
                  {successMessage && <div className="af-mr__success">{successMessage}</div>}

                  <div className="af-form__row">
                    <div className="af-form__field">
                      <label htmlFor="readingDate">{t('meterReadings.panel.readingDate')}</label>
                      <input
                        id="readingDate"
                        name="readingDate"
                        type="date"
                        max={today()}
                        value={form.readingDate}
                        onChange={handleFormChange}
                      />
                    </div>
                    <div className="af-form__field">
                      <label htmlFor="readingValue">{t('meterReadings.panel.readingValue')}</label>
                      <input
                        id="readingValue"
                        name="readingValue"
                        type="number"
                        min="0"
                        step="0.001"
                        value={form.readingValue}
                        onChange={handleFormChange}
                      />
                    </div>
                  </div>

                  <button type="submit" className="af-form__submit" disabled={submitting}>
                    {submitting ? t('shared.saving') : t('meterReadings.panel.submit')}
                  </button>
                </form>

                <div className="af-mr__recent">
                  <h4>{t('meterReadings.panel.recentTitle')}</h4>
                  {recentLoading ? (
                    <p className="af-hh__empty">{t('shared.loading')}</p>
                  ) : recentReadings.length === 0 ? (
                    <p className="af-hh__empty">{t('meterReadings.panel.recentEmpty')}</p>
                  ) : (
                    <table className="af-hh__table">
                      <thead>
                        <tr>
                          <th>{t('meterReadings.table.date')}</th>
                          <th>{t('meterReadings.table.value')}</th>
                          <th>{t('meterReadings.table.consumption')}</th>
                          <th>{t('meterReadings.table.source')}</th>
                        </tr>
                      </thead>
                      <tbody>
                        {recentReadings.map((reading) => (
                          <tr key={reading.id}>
                            <td>{reading.readingDate}</td>
                            <td>{reading.readingValue}</td>
                            <td>{reading.consumption ?? '—'}</td>
                            <td>{reading.source ? (SOURCE_LABELS[reading.source] ? t(SOURCE_LABELS[reading.source]) : reading.source) : '—'}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  )}
                </div>
              </div>
            )}

            <div className="af-mr__info-banner">
              <strong>{t('meterReadings.info.title')}</strong>
              <p>{t('meterReadings.info.body')}</p>
            </div>
          </div>

          <aside className="af-mr__col-side">
            <div className="af-mr__side-card">
              <h3>{t('meterReadings.details.title')}</h3>
              {selected ? (
                <dl className="af-mr__unit-details">
                  <div>
                    <dt>{t('meterReadings.details.flatUnit')}</dt>
                    <dd>{selected.houseNumber || '—'}</dd>
                  </div>
                  <div>
                    <dt>{t('meterReadings.details.meterSerial')}</dt>
                    <dd>{selected.meterNumber || '—'}</dd>
                  </div>
                  <div>
                    <dt>{t('meterReadings.details.occupant')}</dt>
                    <dd>{selected.fullName || '—'}</dd>
                  </div>
                  <div>
                    <dt>{t('meterReadings.details.lastDial')}</dt>
                    <dd>{lastReading ? `${lastReading.readingValue} kL (${lastReading.readingDate})` : t('meterReadings.details.noReadings')}</dd>
                  </div>
                  <div>
                    <dt>{t('meterReadings.details.monthConsumption')}</dt>
                    <dd>{monthConsumption} kL</dd>
                  </div>
                </dl>
              ) : (
                <p className="af-hh__empty">{t('meterReadings.details.empty')}</p>
              )}
            </div>
          </aside>
        </div>
      )}

      {activeTab === 'csv' && (
        <div className="af-mr__panel af-mr__panel--csv">
          <h3>{t('meterReadings.tabs.csv')}</h3>
          <p className="af-mr__context">
            <Trans
              t={t}
              i18nKey="meterReadings.csv.instructions"
              values={{ columns: CSV_COLUMNS }}
              components={{ code: <code /> }}
            />
          </p>

          <form onSubmit={handleCsvSubmit} className="af-mr__csv-form">
            {csvError && <div className="af-form__error">{csvError}</div>}

            <FilePicker
              id="meterCsvFile"
              accept=".csv,text/csv"
              file={csvFile}
              onChange={(e) => setCsvFile(e.target.files?.[0] || null)}
            />

            <button type="submit" className="af-btn af-btn--cyan" disabled={csvUploading}>
              {csvUploading ? t('shared.uploading') : t('shared.upload')}
            </button>
          </form>

          {csvResult && (
            <div className="af-mr__csv-result">
              <p>
                <Trans
                  t={t}
                  i18nKey="shared.bulkSummary"
                  values={{
                    total: csvResult.totalRows,
                    success: csvResult.successCount,
                    failure: csvResult.failureCount,
                  }}
                  components={{ b: <strong /> }}
                />
              </p>
              {csvResult.failures?.length > 0 && (
                <ul className="af-mr__csv-failures">
                  {csvResult.failures.map((failure) => (
                    <li key={failure.rowNumber}>
                      {t('shared.rowFailure', { row: failure.rowNumber, reason: failure.reason })}{' '}
                      <span>({failure.rawLine})</span>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          )}
        </div>
      )}

      {activeTab === 'history' && (
        <div className="af-mr__panel">
          <h3>{t('meterReadings.tabs.history')}</h3>
          {allReadingsError && <div className="af-hh__error">{allReadingsError}</div>}
          {allReadingsLoading ? (
            <p className="af-hh__empty">{t('meterReadings.history.loading')}</p>
          ) : allReadings.length === 0 ? (
            <p className="af-hh__empty">{t('meterReadings.history.empty')}</p>
          ) : (
            <table className="af-hh__table">
              <thead>
                <tr>
                  <th>{t('meterReadings.table.meterNumber')}</th>
                  <th>{t('meterReadings.table.date')}</th>
                  <th>{t('meterReadings.table.value')}</th>
                  <th>{t('meterReadings.table.consumption')}</th>
                  <th>{t('meterReadings.table.source')}</th>
                </tr>
              </thead>
              <tbody>
                {allReadings.map((reading) => (
                  <tr key={reading.id}>
                    <td>{reading.meterNumber}</td>
                    <td>{reading.readingDate}</td>
                    <td>{reading.readingValue}</td>
                    <td>{reading.consumption ?? '—'}</td>
                    <td>{reading.source ? (SOURCE_LABELS[reading.source] ? t(SOURCE_LABELS[reading.source]) : reading.source) : '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}
    </>
  )
}

export default MeterReadings
