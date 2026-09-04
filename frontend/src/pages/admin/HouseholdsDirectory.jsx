import { useEffect, useMemo, useState } from 'react'
import { bulkUploadResidents, createResident, deleteResident, listResidents, updateResident } from '../../api/residentApi'
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
  { value: 'fullName', label: 'Name' },
  { value: 'houseNumber', label: 'House Number' },
  { value: 'dateOfJoining', label: 'Date of Joining' },
]

const METER_NUMBER_REGEX = /^MTR-\d{4}$/
const GOVERNMENT_ID_REGEX = /^99\d{4}$/

function joinedDate(resident) {
  return resident.dateOfJoining || resident.createdAt?.slice(0, 10) || ''
}

function validateForm(form, residents = [], editingId = null) {
  const errors = {}

  if (!form.fullName.trim()) errors.fullName = 'Full name is required.'
  if (!form.email.trim()) errors.email = 'Email is required.'
  if (!form.phone.trim()) errors.phone = 'Phone number is required.'

  if (!form.houseNumber.trim()) {
    errors.houseNumber = 'House / unit number is required.'
  } else if (
    residents.some(
      (r) => r.id !== editingId && r.houseNumber?.trim().toLowerCase() === form.houseNumber.trim().toLowerCase(),
    )
  ) {
    errors.houseNumber = 'This house number is already assigned to another resident.'
  }

  if (!form.meterNumber.trim()) {
    errors.meterNumber = 'Meter number is required.'
  } else if (!METER_NUMBER_REGEX.test(form.meterNumber)) {
    errors.meterNumber = 'Meter number must look like MTR-1234 (4 digits).'
  } else if (
    residents.some(
      (r) => r.id !== editingId && r.meterNumber?.trim().toLowerCase() === form.meterNumber.trim().toLowerCase(),
    )
  ) {
    errors.meterNumber = 'This meter number is already assigned to another resident.'
  }

  if (!form.governmentId.trim()) {
    errors.governmentId = 'Government ID is required.'
  } else if (!GOVERNMENT_ID_REGEX.test(form.governmentId)) {
    errors.governmentId = 'ID must be 6 digits starting with 99 (e.g. 991234).'
  }

  if (!form.gender) errors.gender = 'Please select a gender.'
  if (!form.dateOfBirth) errors.dateOfBirth = 'Date of birth is required.'
  if (!form.dateOfJoining) errors.dateOfJoining = 'Date of joining is required.'

  if (form.bypassSetPassword) {
    if (!form.password) {
      errors.password = 'Password is required.'
    } else if (form.password.length < 8) {
      errors.password = 'Password must be at least 8 characters.'
    }

    if (!form.confirmPassword) {
      errors.confirmPassword = 'Please confirm the password.'
    } else if (form.password !== form.confirmPassword) {
      errors.confirmPassword = 'Passwords do not match.'
    }
  }

  return errors
}

function HouseholdsDirectory() {
  const [residents, setResidents] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const [showForm, setShowForm] = useState(false)
  const [editingId, setEditingId] = useState(null)
  const [form, setForm] = useState(EMPTY_FORM)
  const [formError, setFormError] = useState('')
  const [fieldErrors, setFieldErrors] = useState({})
  const [submitting, setSubmitting] = useState(false)

  const [search, setSearch] = useState('')
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
      .catch(() => setError('Could not load residents. Please try again.'))
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
      setFormError('Please fix the highlighted fields.')
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
      setFormError(err.response?.data?.message || 'Could not save this resident. Please check the details.')
    } finally {
      setSubmitting(false)
    }
  }

  async function handleDelete(resident) {
    if (!window.confirm(`Remove ${resident.fullName} from your households directory?`)) {
      return
    }

    try {
      await deleteResident(resident.id)
      setResidents((prev) => prev.filter((r) => r.id !== resident.id))
    } catch {
      setError('Could not remove this resident. Please try again.')
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
      setBulkError('Please choose a .csv or .xlsx/.xls file to upload.')
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
      setBulkError(err.response?.data?.message || 'Could not upload this file. Please try again.')
    } finally {
      setBulkUploading(false)
    }
  }

  return (
    <>
      <div className="af-hh__toolbar">
        <span className="af-hh__count">{residents.length} household{residents.length === 1 ? '' : 's'}</span>
        <div className="af-hh__toolbar-actions">
          <button type="button" className="af-btn af-btn--outline" onClick={openBulkUpload}>
            ⇪ Bulk Upload
          </button>
          <button type="button" className="af-btn af-btn--cyan" onClick={openAddForm}>
            + Add Resident
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
            placeholder="Filter by name, house, email, or meter no."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>

        <div className="af-hh__sort">
          <label htmlFor="sortField">Sort by</label>
          <select id="sortField" value={sortField} onChange={(e) => setSortField(e.target.value)}>
            {SORT_OPTIONS.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))}
          </select>
          <button type="button" className="af-hh__sort-dir" onClick={toggleSortDirection} title="Toggle sort direction">
            {sortDirection === 'asc' ? '↑ Asc' : '↓ Desc'}
          </button>
        </div>
      </div>

      {error && <div className="af-hh__error">{error}</div>}

      <div className="af-hh__table-wrap">
        {loading ? (
          <p className="af-hh__empty">Loading residents…</p>
        ) : residents.length === 0 ? (
          <p className="af-hh__empty">
            No residents yet. Click "Add Resident" to register the first household.
          </p>
        ) : visibleResidents.length === 0 ? (
          <p className="af-hh__empty">No residents match "{search}".</p>
        ) : (
          <table className="af-hh__table">
            <thead>
              <tr>
                <th>Name</th>
                <th>House / Unit</th>
                <th>Email</th>
                <th>Phone</th>
                <th>Meter No.</th>
                <th>Sqft</th>
                <th>Joined</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {pagedResidents.map((resident) => (
                <tr key={resident.id}>
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
                        Edit
                      </button>
                      <button type="button" className="af-hh__delete" onClick={() => handleDelete(resident)}>
                        Remove
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
            Showing {page * PAGE_SIZE + 1}–{Math.min(visibleResidents.length, page * PAGE_SIZE + PAGE_SIZE)} of{' '}
            {visibleResidents.length}
          </span>
          <div className="af-hh__pagination-controls">
            <button type="button" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
              ← Prev
            </button>
            <span className="af-hh__pagination-page">
              Page {page + 1} of {pageCount}
            </span>
            <button type="button" disabled={page >= pageCount - 1} onClick={() => setPage((p) => p + 1)}>
              Next →
            </button>
          </div>
        </div>
      )}

      {showForm && (
        <div className="af-hh__modal-backdrop" onClick={closeForm}>
          <div className="af-hh__modal" onClick={(e) => e.stopPropagation()}>
            <h2>{editingId ? 'Edit Resident' : 'Add Resident'}</h2>
            <p className="af-hh__modal-sub">
              {editingId
                ? "This updates the resident's details in your directory."
                : form.bypassSetPassword
                  ? "You're setting this resident's password directly. No email will be sent."
                  : "A set-password link will be emailed to this resident so they can activate their own login."}
            </p>

            <form onSubmit={handleSubmit}>
              {formError && <div className="af-form__error">{formError}</div>}

              <div className="af-form__row">
                <div className="af-form__field">
                  <label htmlFor="fullName">Full name</label>
                  <input
                    id="fullName"
                    name="fullName"
                    aria-invalid={Boolean(fieldErrors.fullName)}
                    className={fieldErrors.fullName ? 'af-form__input--invalid' : ''}
                    value={form.fullName}
                    onChange={handleChange}
                  />
                  {fieldErrors.fullName && <span className="af-form__field-error">{fieldErrors.fullName}</span>}
                </div>
                <div className="af-form__field">
                  <label htmlFor="houseNumber">House / unit no.</label>
                  <input
                    id="houseNumber"
                    name="houseNumber"
                    aria-invalid={Boolean(fieldErrors.houseNumber)}
                    className={fieldErrors.houseNumber ? 'af-form__input--invalid' : ''}
                    value={form.houseNumber}
                    onChange={handleChange}
                  />
                  {fieldErrors.houseNumber && <span className="af-form__field-error">{fieldErrors.houseNumber}</span>}
                </div>
              </div>

              <div className="af-form__row">
                <div className="af-form__field">
                  <label htmlFor="email">Email</label>
                  <input
                    id="email"
                    name="email"
                    type="email"
                    aria-invalid={Boolean(fieldErrors.email)}
                    className={fieldErrors.email ? 'af-form__input--invalid' : ''}
                    value={form.email}
                    onChange={handleChange}
                  />
                  {fieldErrors.email && <span className="af-form__field-error">{fieldErrors.email}</span>}
                </div>
                <div className="af-form__field">
                  <label htmlFor="phone">Phone number</label>
                  <input
                    id="phone"
                    name="phone"
                    type="tel"
                    aria-invalid={Boolean(fieldErrors.phone)}
                    className={fieldErrors.phone ? 'af-form__input--invalid' : ''}
                    value={form.phone}
                    onChange={handleChange}
                  />
                  {fieldErrors.phone && <span className="af-form__field-error">{fieldErrors.phone}</span>}
                </div>
              </div>

              <div className="af-form__row">
                <div className="af-form__field">
                  <label htmlFor="meterNumber">Meter number</label>
                  <input
                    id="meterNumber"
                    name="meterNumber"
                    placeholder="MTR-1234"
                    aria-invalid={Boolean(fieldErrors.meterNumber)}
                    className={fieldErrors.meterNumber ? 'af-form__input--invalid' : ''}
                    value={form.meterNumber}
                    onChange={handleChange}
                  />
                  {fieldErrors.meterNumber && <span className="af-form__field-error">{fieldErrors.meterNumber}</span>}
                </div>
                <div className="af-form__field">
                  <label htmlFor="governmentId">Government ID</label>
                  <input
                    id="governmentId"
                    name="governmentId"
                    placeholder="99XXXX"
                    aria-invalid={Boolean(fieldErrors.governmentId)}
                    className={fieldErrors.governmentId ? 'af-form__input--invalid' : ''}
                    value={form.governmentId}
                    onChange={handleChange}
                  />
                  {fieldErrors.governmentId && <span className="af-form__field-error">{fieldErrors.governmentId}</span>}
                </div>
              </div>

              <div className="af-form__row">
                <div className="af-form__field">
                  <label>Gender</label>
                  <div className="af-hh__radio-group" role="radiogroup" aria-label="Gender">
                    {['Male', 'Female', 'Other'].map((option) => (
                      <label className="af-hh__radio" key={option}>
                        <input
                          type="radio"
                          name="gender"
                          value={option}
                          checked={form.gender === option}
                          onChange={handleChange}
                        />
                        {option}
                      </label>
                    ))}
                  </div>
                  {fieldErrors.gender && <span className="af-form__field-error">{fieldErrors.gender}</span>}
                </div>
                <div className="af-form__field">
                  <label htmlFor="dateOfBirth">Date of birth</label>
                  <input
                    id="dateOfBirth"
                    name="dateOfBirth"
                    type="date"
                    aria-invalid={Boolean(fieldErrors.dateOfBirth)}
                    className={fieldErrors.dateOfBirth ? 'af-form__input--invalid' : ''}
                    value={form.dateOfBirth}
                    onChange={handleChange}
                  />
                  {fieldErrors.dateOfBirth && <span className="af-form__field-error">{fieldErrors.dateOfBirth}</span>}
                </div>
              </div>

              <div className="af-form__row">
                <div className="af-form__field">
                  <label htmlFor="dateOfJoining">Date of joining</label>
                  <input
                    id="dateOfJoining"
                    name="dateOfJoining"
                    type="date"
                    aria-invalid={Boolean(fieldErrors.dateOfJoining)}
                    className={fieldErrors.dateOfJoining ? 'af-form__input--invalid' : ''}
                    value={form.dateOfJoining}
                    onChange={handleChange}
                  />
                  {fieldErrors.dateOfJoining && <span className="af-form__field-error">{fieldErrors.dateOfJoining}</span>}
                </div>
                <div className="af-form__field">
                  <label htmlFor="flatSizeSqft">Flat size (sq ft)</label>
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
                    Bypass set-password email — set a password for this resident now
                  </label>

                  {form.bypassSetPassword && (
                    <div className="af-form__row">
                      <div className="af-form__field">
                        <label htmlFor="password">Password</label>
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
                        {fieldErrors.password && <span className="af-form__field-error">{fieldErrors.password}</span>}
                      </div>
                      <div className="af-form__field">
                        <label htmlFor="confirmPassword">Confirm password</label>
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
                          <span className="af-form__field-error">{fieldErrors.confirmPassword}</span>
                        )}
                      </div>
                    </div>
                  )}
                </>
              )}

              <div className="af-hh__modal-actions">
                <button type="button" className="af-btn af-btn--outline" onClick={closeForm}>
                  Cancel
                </button>
                <button type="submit" className="af-form__submit" disabled={submitting}>
                  {submitting ? 'Saving…' : editingId ? 'Save Changes' : 'Add Resident'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {showBulkUpload && (
        <div className="af-hh__modal-backdrop" onClick={closeBulkUpload}>
          <div className="af-hh__modal" onClick={(e) => e.stopPropagation()}>
            <h2>Bulk Upload Residents</h2>
            <p className="af-hh__modal-sub">
              Upload a .csv or .xlsx/.xls file with columns <code>fullName, email, phone, houseNumber,
              meterNumber, governmentId, gender, dateOfBirth, dateOfJoining, password, flatSizeSqft</code>{' '}
              (password and flatSizeSqft are both optional). Rows with a password set it directly; rows
              without one get a set-password email, same as adding a resident manually. Duplicate
              house/meter numbers or emails are rejected per row — one bad row won't fail the whole batch.
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
                    Processed <strong>{bulkResult.totalRows}</strong> rows —{' '}
                    <strong>{bulkResult.successCount}</strong> succeeded, <strong>{bulkResult.failureCount}</strong> failed.
                  </p>
                  {bulkResult.failures?.length > 0 && (
                    <ul className="af-hh__bulk-failures">
                      {bulkResult.failures.map((failure) => (
                        <li key={failure.rowNumber}>
                          Row {failure.rowNumber}: {failure.reason} <span>({failure.rawLine})</span>
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
              )}

              <div className="af-hh__modal-actions">
                <button type="button" className="af-btn af-btn--outline" onClick={closeBulkUpload}>
                  Close
                </button>
                <button type="submit" className="af-form__submit" disabled={bulkUploading}>
                  {bulkUploading ? 'Uploading…' : 'Upload'}
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
