import { useEffect, useMemo, useState } from 'react'
import i18next from 'i18next'
import { Trans, useTranslation } from 'react-i18next'
import { useSearchParams } from 'react-router-dom'
import { bulkUploadResidents, createResident, deleteResident, listResidents, updateResident } from '../../api/residentApi'
import Avatar from '../../components/Avatar'
import '../../components/AuthForm.css'
import './HouseholdsDirectory.css'

const EMPTY_FORM = {
  fullName: '',
  email: '',
  phone: '',
  houseNumber: '',
  meterNumber: '',
  governmentId: '',
  gender: '',
  dateOfBirth: '',
  dateOfJoining: '',
  bypassSetPassword: false,
  password: '',
  confirmPassword: '',
  flatSizeSqft: '',
}

const PAGE_SIZE = 10

const SORT_OPTIONS = [
  { value: 'fullName', labelKey: 'households.sort.name' },
  { value: 'houseNumber', labelKey: 'households.sort.houseNumber' },
  { value: 'dateOfJoining', labelKey: 'households.sort.dateOfJoining' },
]

// Values are sent to the backend as-is; only the displayed label is translated.
const GENDER_OPTIONS = [
  { value: 'Male', labelKey: 'households.gender.male' },
  { value: 'Female', labelKey: 'households.gender.female' },
  { value: 'Other', labelKey: 'households.gender.other' },
]

// Literal column names the backend bulk-upload parser expects - not translated.
const BULK_UPLOAD_COLUMNS =
  'fullName, email, phone, houseNumber, meterNumber, governmentId, gender, dateOfBirth, dateOfJoining, password, flatSizeSqft'

const METER_NUMBER_REGEX = /^MTR-\d{4}$/
const GOVERNMENT_ID_REGEX = /^99\d{4}$/

function joinedDate(resident) {
  return resident.dateOfJoining || resident.createdAt?.slice(0, 10) || ''
}

// Returns translation keys (resolved with t() at render time), not display strings.
function validateForm(form, residents = [], editingId = null) {
  const errors = {}

  if (!form.fullName.trim()) errors.fullName = 'households.validation.fullNameRequired'
  if (!form.email.trim()) errors.email = 'households.validation.emailRequired'
  if (!form.phone.trim()) errors.phone = 'households.validation.phoneRequired'

  if (!form.houseNumber.trim()) {
    errors.houseNumber = 'households.validation.houseNumberRequired'
  } else if (
    residents.some(
      (r) => r.id !== editingId && r.houseNumber?.trim().toLowerCase() === form.houseNumber.trim().toLowerCase(),
    )
  ) {
    errors.houseNumber = 'households.validation.houseNumberTaken'
  }

  if (!form.meterNumber.trim()) {
    errors.meterNumber = 'households.validation.meterNumberRequired'
  } else if (!METER_NUMBER_REGEX.test(form.meterNumber)) {
    errors.meterNumber = 'households.validation.meterNumberFormat'
  } else if (
    residents.some(
      (r) => r.id !== editingId && r.meterNumber?.trim().toLowerCase() === form.meterNumber.trim().toLowerCase(),
    )
  ) {
    errors.meterNumber = 'households.validation.meterNumberTaken'
  }

  if (!form.governmentId.trim()) {
    errors.governmentId = 'households.validation.governmentIdRequired'
  } else if (!GOVERNMENT_ID_REGEX.test(form.governmentId)) {
    errors.governmentId = 'households.validation.governmentIdFormat'
  }

  if (!form.gender) errors.gender = 'households.validation.genderRequired'
  if (!form.dateOfBirth) errors.dateOfBirth = 'households.validation.dateOfBirthRequired'
  if (!form.dateOfJoining) errors.dateOfJoining = 'households.validation.dateOfJoiningRequired'

  if (form.bypassSetPassword) {
    if (!form.password) {
      errors.password = 'households.validation.passwordRequired'
    } else if (form.password.length < 8) {
      errors.password = 'households.validation.passwordLength'
    }

    if (!form.confirmPassword) {
      errors.confirmPassword = 'households.validation.confirmPasswordRequired'
    } else if (form.password !== form.confirmPassword) {
      errors.confirmPassword = 'households.validation.passwordMismatch'
    }
  }

  return errors
}

function HouseholdsDirectory() {
  const { t } = useTranslation('admin')
  const [residents, setResidents] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const [showForm, setShowForm] = useState(false)
  const [editingId, setEditingId] = useState(null)
  const [form, setForm] = useState(EMPTY_FORM)
  const [formError, setFormError] = useState('')
  const [fieldErrors, setFieldErrors] = useState({})
  const [submitting, setSubmitting] = useState(false)

  // ?q= comes from the topbar search; re-applied whenever it changes (adjust-during-render,
  // per the project's react-hooks/set-state-in-effect rule), while still letting the admin edit it.
  const [searchParams] = useSearchParams()
  const queryParam = searchParams.get('q') || ''
  const [search, setSearch] = useState(queryParam)
  const [lastQueryParam, setLastQueryParam] = useState(queryParam)
  if (queryParam !== lastQueryParam) {
    setLastQueryParam(queryParam)
    setSearch(queryParam)
  }
  const [sortField, setSortField] = useState('fullName')
  const [sortDirection, setSortDirection] = useState('asc')
  const [page, setPage] = useState(0)
  const [pageResetKey, setPageResetKey] = useState('')

  const [showBulkUpload, setShowBulkUpload] = useState(false)
  const [bulkFile, setBulkFile] = useState(null)
  const [bulkResult, setBulkResult] = useState(null)
  const [bulkError, setBulkError] = useState('')
  const [bulkUploading, setBulkUploading] = useState(false)

  const visibleResidents = useMemo(() => {
    const term = search.trim().toLowerCase()
    const filtered = term
      ? residents.filter((r) =>
          [r.fullName, r.houseNumber, r.email, r.meterNumber].some((field) =>
            field?.toLowerCase().includes(term),
          ),
        )
      : residents

    const sorted = [...filtered].sort((a, b) => {
      const aVal = sortField === 'dateOfJoining' ? joinedDate(a) : a[sortField] || ''
      const bVal = sortField === 'dateOfJoining' ? joinedDate(b) : b[sortField] || ''
      return aVal.localeCompare(bVal, undefined, { numeric: true, sensitivity: 'base' })
    })

    return sortDirection === 'asc' ? sorted : sorted.reverse()
  }, [residents, search, sortField, sortDirection])

  const pageCount = Math.max(1, Math.ceil(visibleResidents.length / PAGE_SIZE))

  const resetKey = `${search}|${sortField}|${sortDirection}|${residents.length}`
  if (resetKey !== pageResetKey) {
    setPageResetKey(resetKey)
    setPage(0)
  }

  const pagedResidents = useMemo(
    () => visibleResidents.slice(page * PAGE_SIZE, page * PAGE_SIZE + PAGE_SIZE),
    [visibleResidents, page],
  )

  function toggleSortDirection() {
    setSortDirection((prev) => (prev === 'asc' ? 'desc' : 'asc'))
  }

  useEffect(() => {
    listResidents()
      .then(setResidents)
      .catch(() => setError(i18next.t('households.loadError', { ns: 'admin' })))
      .finally(() => setLoading(false))
  }, [])

  function openAddForm() {
    setEditingId(null)
    setForm(EMPTY_FORM)
    setFormError('')
    setFieldErrors({})
    setShowForm(true)
  }

  function openEditForm(resident) {
    setEditingId(resident.id)
    setForm({
      fullName: resident.fullName,
      email: resident.email,
      phone: resident.phone || '',
      houseNumber: resident.houseNumber,
      meterNumber: resident.meterNumber || '',
      governmentId: resident.governmentId || '',
      gender: resident.gender || '',
      dateOfBirth: resident.dateOfBirth || '',
      dateOfJoining: resident.dateOfJoining || '',
      bypassSetPassword: false,
      password: '',
      confirmPassword: '',
      flatSizeSqft: resident.flatSizeSqft ?? '',
    })
    setFormError('')
    setFieldErrors({})
    setShowForm(true)
  }

  function closeForm() {
    setShowForm(false)
    setEditingId(null)
  }

  function handleChange(event) {
    const { name, value, type, checked } = event.target
    setForm((prev) => ({ ...prev, [name]: type === 'checkbox' ? checked : value }))
    setFieldErrors((prev) => (prev[name] ? { ...prev, [name]: undefined } : prev))
  }

  async function handleSubmit(event) {
    event.preventDefault()
    setFormError('')

    const errors = validateForm(form, residents, editingId)
    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors)
      setFormError(t('households.fixFields'))
      return
    }

    setSubmitting(true)

    const payload = { ...form }
    delete payload.confirmPassword
    payload.flatSizeSqft = payload.flatSizeSqft === '' ? null : Number(payload.flatSizeSqft)

    try {
      if (editingId) {
        const updated = await updateResident(editingId, payload)
        setResidents((prev) => prev.map((r) => (r.id === editingId ? updated : r)))
      } else {
        const created = await createResident(payload)
        setResidents((prev) => [created, ...prev])
      }
      closeForm()
    } catch (err) {
      setFormError(err.response?.data?.message || t('households.saveError'))
    } finally {
      setSubmitting(false)
    }
  }

  async function handleDelete(resident) {
    if (!window.confirm(t('households.confirmRemove', { name: resident.fullName }))) {
      return
    }

    try {
      await deleteResident(resident.id)
      setResidents((prev) => prev.filter((r) => r.id !== resident.id))
    } catch {
      setError(t('households.removeError'))
    }
  }

  function openBulkUpload() {
    setBulkFile(null)
    setBulkResult(null)
    setBulkError('')
    setShowBulkUpload(true)
  }

  function closeBulkUpload() {
    setShowBulkUpload(false)
  }

  async function handleBulkSubmit(event) {
    event.preventDefault()
    setBulkError('')
    setBulkResult(null)

    if (!bulkFile) {
      setBulkError(t('households.bulk.noFile'))
      return
    }

    setBulkUploading(true)

    try {
      const result = await bulkUploadResidents(bulkFile)
      setBulkResult(result)
      setBulkFile(null)
      if (result.successCount > 0) {
        listResidents().then(setResidents).catch(() => {})
      }
    } catch (err) {
      setBulkError(err.response?.data?.message || t('shared.uploadError'))
    } finally {
      setBulkUploading(false)
    }
  }

  return (
    <>
      <div className="af-hh__toolbar">
        <span className="af-hh__count">{t('households.count', { total: residents.length })}</span>
        <div className="af-hh__toolbar-actions">
          <button type="button" className="af-btn af-btn--outline" onClick={openBulkUpload}>
            {t('households.bulkUploadButton')}
          </button>
          <button type="button" className="af-btn af-btn--cyan" onClick={openAddForm}>
            {t('households.addResidentButton')}
          </button>
        </div>
      </div>

      <div className="af-hh__filters">
        <div className="af-hh__search">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="11" cy="11" r="7" />
            <path d="m21 21-4.35-4.35" />
          </svg>
          <input
            type="text"
            placeholder={t('households.searchPlaceholder')}
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>

        <div className="af-hh__sort">
          <label htmlFor="sortField">{t('households.sort.label')}</label>
          <select id="sortField" value={sortField} onChange={(e) => setSortField(e.target.value)}>
            {SORT_OPTIONS.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {t(opt.labelKey)}
              </option>
            ))}
          </select>
          <button type="button" className="af-hh__sort-dir" onClick={toggleSortDirection} title={t('households.sort.toggleDirection')}>
            {sortDirection === 'asc' ? t('households.sort.asc') : t('households.sort.desc')}
          </button>
        </div>
      </div>

      {error && <div className="af-hh__error">{error}</div>}

      <div className="af-hh__table-wrap">
        {loading ? (
          <p className="af-hh__empty">{t('households.loading')}</p>
        ) : residents.length === 0 ? (
          <p className="af-hh__empty">
            {t('households.empty')}
          </p>
        ) : visibleResidents.length === 0 ? (
          <p className="af-hh__empty">{t('households.noMatch', { search })}</p>
        ) : (
          <table className="af-hh__table">
            <thead>
              <tr>
                <th className="af-hh__avatar-col"></th>
                <th>{t('households.table.name')}</th>
                <th>{t('households.table.houseUnit')}</th>
                <th>{t('households.table.email')}</th>
                <th>{t('households.table.phone')}</th>
                <th>{t('households.table.meterNumber')}</th>
                <th>{t('households.table.sqft')}</th>
                <th>{t('households.table.joined')}</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {pagedResidents.map((resident) => (
                <tr key={resident.id}>
                  <td className="af-hh__avatar-col">
                    <Avatar avatarKey={resident.avatarKey} name={resident.fullName} className="af-hh__avatar" />
                  </td>
                  <td>{resident.fullName}</td>
                  <td>{resident.houseNumber}</td>
                  <td>{resident.email}</td>
                  <td>{resident.phone || '—'}</td>
                  <td>{resident.meterNumber || '—'}</td>
                  <td>{resident.flatSizeSqft ?? '—'}</td>
                  <td>{joinedDate(resident) || '—'}</td>
                  <td>
                    <div className="af-hh__row-actions">
                      <button type="button" onClick={() => openEditForm(resident)}>
                        {t('shared.edit')}
                      </button>
                      <button type="button" className="af-hh__delete" onClick={() => handleDelete(resident)}>
                        {t('shared.remove')}
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {visibleResidents.length > 0 && (
        <div className="af-hh__pagination">
          <span className="af-hh__pagination-info">
            {t('pagination.showing', {
              from: page * PAGE_SIZE + 1,
              to: Math.min(visibleResidents.length, page * PAGE_SIZE + PAGE_SIZE),
              total: visibleResidents.length,
            })}
          </span>
          <div className="af-hh__pagination-controls">
            <button type="button" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
              {t('pagination.prev')}
            </button>
            <span className="af-hh__pagination-page">
              {t('pagination.page', { page: page + 1, pageCount })}
            </span>
            <button type="button" disabled={page >= pageCount - 1} onClick={() => setPage((p) => p + 1)}>
              {t('pagination.next')}
            </button>
          </div>
        </div>
      )}

      {showForm && (
        <div className="af-hh__modal-backdrop" onClick={closeForm}>
          <div className="af-hh__modal" onClick={(e) => e.stopPropagation()}>
            <h2>{editingId ? t('households.form.editTitle') : t('households.form.addTitle')}</h2>
            <p className="af-hh__modal-sub">
              {editingId
                ? t('households.form.editSubtitle')
                : form.bypassSetPassword
                  ? t('households.form.bypassSubtitle')
                  : t('households.form.addSubtitle')}
            </p>

            <form onSubmit={handleSubmit}>
              {formError && <div className="af-form__error">{formError}</div>}

              <div className="af-form__row">
                <div className="af-form__field">
                  <label htmlFor="fullName">{t('households.form.fullName')}</label>
                  <input
                    id="fullName"
                    name="fullName"
                    aria-invalid={Boolean(fieldErrors.fullName)}
                    className={fieldErrors.fullName ? 'af-form__input--invalid' : ''}
                    value={form.fullName}
                    onChange={handleChange}
                  />
                  {fieldErrors.fullName && <span className="af-form__field-error">{t(fieldErrors.fullName)}</span>}
                </div>
                <div className="af-form__field">
                  <label htmlFor="houseNumber">{t('households.form.houseNumber')}</label>
                  <input
                    id="houseNumber"
                    name="houseNumber"
                    aria-invalid={Boolean(fieldErrors.houseNumber)}
                    className={fieldErrors.houseNumber ? 'af-form__input--invalid' : ''}
                    value={form.houseNumber}
                    onChange={handleChange}
                  />
                  {fieldErrors.houseNumber && <span className="af-form__field-error">{t(fieldErrors.houseNumber)}</span>}
                </div>
              </div>

              <div className="af-form__row">
                <div className="af-form__field">
                  <label htmlFor="email">{t('households.form.email')}</label>
                  <input
                    id="email"
                    name="email"
                    type="email"
                    aria-invalid={Boolean(fieldErrors.email)}
                    className={fieldErrors.email ? 'af-form__input--invalid' : ''}
                    value={form.email}
                    onChange={handleChange}
                  />
                  {fieldErrors.email && <span className="af-form__field-error">{t(fieldErrors.email)}</span>}
                </div>
                <div className="af-form__field">
                  <label htmlFor="phone">{t('households.form.phone')}</label>
                  <input
                    id="phone"
                    name="phone"
                    type="tel"
                    aria-invalid={Boolean(fieldErrors.phone)}
                    className={fieldErrors.phone ? 'af-form__input--invalid' : ''}
                    value={form.phone}
                    onChange={handleChange}
                  />
                  {fieldErrors.phone && <span className="af-form__field-error">{t(fieldErrors.phone)}</span>}
                </div>
              </div>

              <div className="af-form__row">
                <div className="af-form__field">
                  <label htmlFor="meterNumber">{t('households.form.meterNumber')}</label>
                  <input
                    id="meterNumber"
                    name="meterNumber"
                    placeholder="MTR-1234"
                    aria-invalid={Boolean(fieldErrors.meterNumber)}
                    className={fieldErrors.meterNumber ? 'af-form__input--invalid' : ''}
                    value={form.meterNumber}
                    onChange={handleChange}
                  />
                  {fieldErrors.meterNumber && <span className="af-form__field-error">{t(fieldErrors.meterNumber)}</span>}
                </div>
                <div className="af-form__field">
                  <label htmlFor="governmentId">{t('households.form.governmentId')}</label>
                  <input
                    id="governmentId"
                    name="governmentId"
                    placeholder="99XXXX"
                    aria-invalid={Boolean(fieldErrors.governmentId)}
                    className={fieldErrors.governmentId ? 'af-form__input--invalid' : ''}
                    value={form.governmentId}
                    onChange={handleChange}
                  />
                  {fieldErrors.governmentId && <span className="af-form__field-error">{t(fieldErrors.governmentId)}</span>}
                </div>
              </div>

              <div className="af-form__row">
                <div className="af-form__field">
                  <label>{t('households.form.gender')}</label>
                  <div className="af-hh__radio-group" role="radiogroup" aria-label={t('households.form.gender')}>
                    {GENDER_OPTIONS.map((option) => (
                      <label className="af-hh__radio" key={option.value}>
                        <input
                          type="radio"
                          name="gender"
                          value={option.value}
                          checked={form.gender === option.value}
                          onChange={handleChange}
                        />
                        {t(option.labelKey)}
                      </label>
                    ))}
                  </div>
                  {fieldErrors.gender && <span className="af-form__field-error">{t(fieldErrors.gender)}</span>}
                </div>
                <div className="af-form__field">
                  <label htmlFor="dateOfBirth">{t('households.form.dateOfBirth')}</label>
                  <input
                    id="dateOfBirth"
                    name="dateOfBirth"
                    type="date"
                    aria-invalid={Boolean(fieldErrors.dateOfBirth)}
                    className={fieldErrors.dateOfBirth ? 'af-form__input--invalid' : ''}
                    value={form.dateOfBirth}
                    onChange={handleChange}
                  />
                  {fieldErrors.dateOfBirth && <span className="af-form__field-error">{t(fieldErrors.dateOfBirth)}</span>}
                </div>
              </div>

              <div className="af-form__row">
                <div className="af-form__field">
                  <label htmlFor="dateOfJoining">{t('households.form.dateOfJoining')}</label>
                  <input
                    id="dateOfJoining"
                    name="dateOfJoining"
                    type="date"
                    aria-invalid={Boolean(fieldErrors.dateOfJoining)}
                    className={fieldErrors.dateOfJoining ? 'af-form__input--invalid' : ''}
                    value={form.dateOfJoining}
                    onChange={handleChange}
                  />
                  {fieldErrors.dateOfJoining && <span className="af-form__field-error">{t(fieldErrors.dateOfJoining)}</span>}
                </div>
                <div className="af-form__field">
                  <label htmlFor="flatSizeSqft">{t('households.form.flatSize')}</label>
                  <input
                    id="flatSizeSqft"
                    name="flatSizeSqft"
                    type="number"
                    step="0.01"
                    min="0"
                    value={form.flatSizeSqft}
                    onChange={handleChange}
                  />
                </div>
              </div>

              {!editingId && (
                <>
                  <label className="af-form__checkbox" htmlFor="bypassSetPassword">
                    <input
                      id="bypassSetPassword"
                      name="bypassSetPassword"
                      type="checkbox"
                      checked={form.bypassSetPassword}
                      onChange={handleChange}
                    />
                    {t('households.form.bypassLabel')}
                  </label>

                  {form.bypassSetPassword && (
                    <div className="af-form__row">
                      <div className="af-form__field">
                        <label htmlFor="password">{t('households.form.password')}</label>
                        <input
                          id="password"
                          name="password"
                          type="password"
                          autoComplete="new-password"
                          aria-invalid={Boolean(fieldErrors.password)}
                          className={fieldErrors.password ? 'af-form__input--invalid' : ''}
                          value={form.password}
                          onChange={handleChange}
                        />
                        {fieldErrors.password && <span className="af-form__field-error">{t(fieldErrors.password)}</span>}
                      </div>
                      <div className="af-form__field">
                        <label htmlFor="confirmPassword">{t('households.form.confirmPassword')}</label>
                        <input
                          id="confirmPassword"
                          name="confirmPassword"
                          type="password"
                          autoComplete="new-password"
                          aria-invalid={Boolean(fieldErrors.confirmPassword)}
                          className={fieldErrors.confirmPassword ? 'af-form__input--invalid' : ''}
                          value={form.confirmPassword}
                          onChange={handleChange}
                        />
                        {fieldErrors.confirmPassword && (
                          <span className="af-form__field-error">{t(fieldErrors.confirmPassword)}</span>
                        )}
                      </div>
                    </div>
                  )}
                </>
              )}

              <div className="af-hh__modal-actions">
                <button type="button" className="af-btn af-btn--outline" onClick={closeForm}>
                  {t('shared.cancel')}
                </button>
                <button type="submit" className="af-form__submit" disabled={submitting}>
                  {submitting ? t('shared.saving') : editingId ? t('shared.saveChanges') : t('households.form.addTitle')}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {showBulkUpload && (
        <div className="af-hh__modal-backdrop" onClick={closeBulkUpload}>
          <div className="af-hh__modal" onClick={(e) => e.stopPropagation()}>
            <h2>{t('households.bulk.title')}</h2>
            <p className="af-hh__modal-sub">
              <Trans
                t={t}
                i18nKey="households.bulk.instructions"
                values={{ columns: BULK_UPLOAD_COLUMNS }}
                components={{ code: <code /> }}
              />
            </p>

            <form onSubmit={handleBulkSubmit}>
              {bulkError && <div className="af-form__error">{bulkError}</div>}

              <div className="af-hh__bulk-upload-input">
                <input
                  type="file"
                  accept=".csv,.xlsx,.xls,text/csv,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,application/vnd.ms-excel"
                  onChange={(e) => setBulkFile(e.target.files?.[0] || null)}
                />
              </div>

              {bulkResult && (
                <div className="af-hh__bulk-result">
                  <p>
                    <Trans
                      t={t}
                      i18nKey="shared.bulkSummary"
                      values={{
                        total: bulkResult.totalRows,
                        success: bulkResult.successCount,
                        failure: bulkResult.failureCount,
                      }}
                      components={{ b: <strong /> }}
                    />
                  </p>
                  {bulkResult.failures?.length > 0 && (
                    <ul className="af-hh__bulk-failures">
                      {bulkResult.failures.map((failure) => (
                        <li key={failure.rowNumber}>
                          {t('shared.rowFailure', { row: failure.rowNumber, reason: failure.reason })}{' '}
                          <span>({failure.rawLine})</span>
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
              )}

              <div className="af-hh__modal-actions">
                <button type="button" className="af-btn af-btn--outline" onClick={closeBulkUpload}>
                  {t('shared.close')}
                </button>
                <button type="submit" className="af-form__submit" disabled={bulkUploading}>
                  {bulkUploading ? t('shared.uploading') : t('shared.upload')}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </>
  )
}

export default HouseholdsDirectory
