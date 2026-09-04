import { useEffect, useMemo, useRef, useState } from 'react'
import { listResidents } from '../../api/residentApi'
import { bulkUploadUsageCsv, listUsageReadings, recordUsageReading } from '../../api/meterUsageApi'
import '../../components/AuthForm.css'
import './MeterReadings.css'

function today() {
  return new Date().toISOString().slice(0, 10)
}

const EMPTY_READING_FORM = {
  readingDate: today(),
  readingValue: '',
}

const TABS = [
  { id: 'single', label: 'Single Entry' },
  { id: 'csv', label: 'Bulk CSV Upload' },
  { id: 'history', label: 'Reading History' },
]

function MeterReadings() {
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
      .catch(() => setLoadError('Could not load residents. Please try again.'))
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
        .catch(() => setAllReadingsError('Could not load the reading history. Please try again.'))
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

  const monthConsumption = useMemo(() => {
    const currentMonth = today().slice(0, 7)
    return recentReadings
      .filter((reading) => reading.readingDate?.startsWith(currentMonth))
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
      setFormError('This resident has no meter number on file.')
      return
    }

    if (!form.readingDate) {
      setFormError('Reading date is required.')
      return
    }

    if (form.readingValue === '' || Number(form.readingValue) < 0) {
      setFormError('Please enter a valid, non-negative reading value.')
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
        `Successfully recorded reading of ${result.readingValue} kL for ${selected.houseNumber} ` +
          `(Meter: ${selected.meterNumber})! Calculated consumption: ${result.consumption} kL.`,
      )
      setForm(EMPTY_READING_FORM)
      loadRecentReadings(selected.meterNumber)
    } catch (err) {
      const status = err.response?.status
      if (status === 409) {
        setFormError('A reading for this meter already exists on that date.')
      } else if (status === 404) {
        setFormError('This meter could not be found. Please re-select the resident.')
      } else if (status === 422) {
        setFormError(err.response?.data?.message || 'This reading is lower than the previous recorded reading.')
      } else {
        setFormError(err.response?.data?.message || 'Could not record this reading. Please try again.')
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
      setCsvError('Please choose a CSV file to upload.')
      return
    }

    setCsvUploading(true)

    try {
      const result = await bulkUploadUsageCsv(csvFile)
      setCsvResult(result)
      setCsvFile(null)
    } catch (err) {
      setCsvError(err.response?.data?.message || 'Could not upload this file. Please try again.')
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
            {tab.label}
          </button>
        ))}
      </div>

      {activeTab === 'single' && (
        <div className="af-mr__columns">
          <div className="af-mr__col-main">
            <div className="af-mr__combo" ref={comboRef}>
              <label className="af-mr__combo-label" htmlFor="unit-combo-trigger">
                Select Flat &amp; Meter Serial <span className="af-mr__required">*</span>
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
                    <strong>Flat {selected.houseNumber || '—'}</strong>
                    <span className="af-mr__combo-trigger-meter">
                      🔧 {selected.meterNumber || 'No meter'}
                    </span>
                    <span className="af-mr__combo-trigger-name">({selected.fullName})</span>
                  </span>
                ) : (
                  <span className="af-mr__combo-placeholder">Select Flat &amp; Meter Serial…</span>
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
                      placeholder="Search flat no, meter serial, resident…"
                      value={search}
                      onChange={(e) => setSearch(e.target.value)}
                    />
                  </div>

                  <div className="af-mr__dropdown-header">
                    <span>Residential Units</span>
                    <span>{matches.length} flat{matches.length === 1 ? '' : 's'} found</span>
                  </div>

                  <ul className="af-mr__dropdown-list">
                    {loading ? (
                      <li className="af-mr__dropdown-empty">Loading residents…</li>
                    ) : matches.length === 0 ? (
                      <li className="af-mr__dropdown-empty">No residents match "{search}".</li>
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
                                  Flat {resident.houseNumber || '—'}
                                  <span className="af-mr__combo-meter-pill">🔧 {resident.meterNumber || '—'}</span>
                                </strong>
                                <span>
                                  {resident.fullName || 'Unassigned'} · Last: {latest ? `${latest.readingValue} kL` : '—'}
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
                    <p className="af-mr__eyebrow">Recording a reading for</p>
                    <h3>{selected.fullName}</h3>
                    <p className="af-mr__context">
                      House {selected.houseNumber || '—'} · Meter {selected.meterNumber || '—'}
                    </p>
                  </div>
                  <button type="button" className="af-btn af-btn--outline" onClick={handleClearSelection}>
                    Change resident
                  </button>
                </div>

                <form onSubmit={handleSubmit} className="af-mr__form">
                  {formError && <div className="af-form__error">{formError}</div>}
                  {successMessage && <div className="af-mr__success">{successMessage}</div>}

                  <div className="af-form__row">
                    <div className="af-form__field">
                      <label htmlFor="readingDate">Reading date</label>
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
                      <label htmlFor="readingValue">Current Meter Reading (kL)</label>
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
                    {submitting ? 'Saving…' : 'Submit Validated Reading'}
                  </button>
                </form>

                <div className="af-mr__recent">
                  <h4>Recent readings</h4>
                  {recentLoading ? (
                    <p className="af-hh__empty">Loading…</p>
                  ) : recentReadings.length === 0 ? (
                    <p className="af-hh__empty">No readings recorded yet for this meter.</p>
                  ) : (
                    <table className="af-hh__table">
                      <thead>
                        <tr>
                          <th>Date</th>
                          <th>Value (kL)</th>
                          <th>Consumption (kL)</th>
                          <th>Source</th>
                        </tr>
                      </thead>
                      <tbody>
                        {recentReadings.map((reading) => (
                          <tr key={reading.id}>
                            <td>{reading.readingDate}</td>
                            <td>{reading.readingValue}</td>
                            <td>{reading.consumption ?? '—'}</td>
                            <td>{reading.source || '—'}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  )}
                </div>
              </div>
            )}

            <div className="af-mr__info-banner">
              <strong>Monotonic Meter Validation</strong>
              <p>Every new reading must be greater than or equal to the previous log for that meter — this keeps consumption calculations accurate and catches transposed digits early.</p>
            </div>
          </div>

          <aside className="af-mr__col-side">
            <div className="af-mr__side-card">
              <h3>Selected Unit Details</h3>
              {selected ? (
                <dl className="af-mr__unit-details">
                  <div>
                    <dt>Flat Unit</dt>
                    <dd>{selected.houseNumber || '—'}</dd>
                  </div>
                  <div>
                    <dt>Meter Serial</dt>
                    <dd>{selected.meterNumber || '—'}</dd>
                  </div>
                  <div>
                    <dt>Occupant Name</dt>
                    <dd>{selected.fullName || '—'}</dd>
                  </div>
                  <div>
                    <dt>Last Recorded Dial</dt>
                    <dd>{lastReading ? `${lastReading.readingValue} kL (${lastReading.readingDate})` : 'No readings yet'}</dd>
                  </div>
                  <div>
                    <dt>Month Consumption</dt>
                    <dd>{monthConsumption} kL</dd>
                  </div>
                </dl>
              ) : (
                <p className="af-hh__empty">No household selected. Search and pick a resident to see their meter details here.</p>
              )}
            </div>
          </aside>
        </div>
      )}

      {activeTab === 'csv' && (
        <div className="af-mr__panel af-mr__panel--csv">
          <h3>Bulk CSV Upload</h3>
          <p className="af-mr__context">
            Upload a CSV with columns <code>meterNumber,readingDate,readingValue</code>. Rows are processed
            independently — one bad row won't fail the whole batch.
          </p>

          <form onSubmit={handleCsvSubmit} className="af-mr__csv-form">
            {csvError && <div className="af-form__error">{csvError}</div>}

            <input
              type="file"
              accept=".csv,text/csv"
              onChange={(e) => setCsvFile(e.target.files?.[0] || null)}
            />

            <button type="submit" className="af-btn af-btn--cyan" disabled={csvUploading}>
              {csvUploading ? 'Uploading…' : 'Upload'}
            </button>
          </form>

          {csvResult && (
            <div className="af-mr__csv-result">
              <p>
                Processed <strong>{csvResult.totalRows}</strong> rows —{' '}
                <strong>{csvResult.successCount}</strong> succeeded, <strong>{csvResult.failureCount}</strong> failed.
              </p>
              {csvResult.failures?.length > 0 && (
                <ul className="af-mr__csv-failures">
                  {csvResult.failures.map((failure) => (
                    <li key={failure.rowNumber}>
                      Row {failure.rowNumber}: {failure.reason} <span>({failure.rawLine})</span>
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
          <h3>Reading History</h3>
          {allReadingsError && <div className="af-hh__error">{allReadingsError}</div>}
          {allReadingsLoading ? (
            <p className="af-hh__empty">Loading readings…</p>
          ) : allReadings.length === 0 ? (
            <p className="af-hh__empty">No readings recorded yet for your community.</p>
          ) : (
            <table className="af-hh__table">
              <thead>
                <tr>
                  <th>Meter No.</th>
                  <th>Date</th>
                  <th>Value (kL)</th>
                  <th>Consumption (kL)</th>
                  <th>Source</th>
                </tr>
              </thead>
              <tbody>
                {allReadings.map((reading) => (
                  <tr key={reading.id}>
                    <td>{reading.meterNumber}</td>
                    <td>{reading.readingDate}</td>
                    <td>{reading.readingValue}</td>
                    <td>{reading.consumption ?? '—'}</td>
                    <td>{reading.source || '—'}</td>
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
