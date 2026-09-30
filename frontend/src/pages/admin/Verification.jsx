import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { getMyVerification, openMyVerificationDocument, submitVerification } from '../../api/verificationApi'
import FilePicker from '../../components/FilePicker'
import '../../components/AuthForm.css'
import './Verification.css'

// Keys are translation keys (admin namespace), resolved with t() at render time.
const DOCUMENT_TYPES = {
  REGISTRATION_CERTIFICATE: 'verification.documentTypes.registrationCertificate',
  OWNERSHIP_PROOF: 'verification.documentTypes.ownershipProof',
  GOVERNMENT_ID: 'verification.documentTypes.governmentId',
  OTHER: 'verification.documentTypes.other',
}

const STATUS_KEYS = {
  NOT_SUBMITTED: 'verification.status.notSubmitted',
  PENDING: 'verification.status.pending',
  APPROVED: 'verification.status.approved',
  REJECTED: 'verification.status.rejected',
}

const MAX_BYTES = 5 * 1024 * 1024

const EMPTY_FORM = {
  communityName: '',
  address: '',
  city: '',
  state: '',
  pincode: '',
  totalFlats: '',
  totalBlocks: '',
  documentType: 'REGISTRATION_CERTIFICATE',
  documentNumber: '',
  details: '',
}

const REQUIRED_FIELDS = ['communityName', 'address', 'city', 'state', 'pincode', 'totalFlats', 'documentNumber']

function formatDateTime(iso, locale) {
  if (!iso) return '—'
  return new Date(iso).toLocaleString(locale, { dateStyle: 'medium', timeStyle: 'short' })
}

function Verification() {
  const { t, i18n } = useTranslation('admin')
  const locale = i18n.resolvedLanguage || 'en'
  const [verification, setVerification] = useState(null)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState(false)
  const [form, setForm] = useState(EMPTY_FORM)
  const [file, setFile] = useState(null)
  const [formError, setFormError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    getMyVerification()
      .then(setVerification)
      .catch(() => setLoadError(true))
      .finally(() => setLoading(false))
  }, [])

  function handleChange(e) {
    const { name, value } = e.target
    setForm((prev) => ({ ...prev, [name]: value }))
  }

  function handleFile(e) {
    setFormError('')
    const picked = e.target.files?.[0] || null
    if (picked && picked.size > MAX_BYTES) {
      setFormError(t('verification.form.tooLarge'))
      e.target.value = ''
      setFile(null)
      return
    }
    setFile(picked)
  }

  async function handleSubmit(e) {
    e.preventDefault()
    setFormError('')
    if (REQUIRED_FIELDS.some((field) => !String(form[field]).trim())) {
      setFormError(t('verification.form.fieldsRequired'))
      return
    }
    if (Number(form.totalFlats) < 1 || (form.totalBlocks !== '' && Number(form.totalBlocks) < 1)) {
      setFormError(t('verification.form.countInvalid'))
      return
    }
    if (!file) {
      setFormError(t('verification.form.fileRequired'))
      return
    }
    setSubmitting(true)
    try {
      const saved = await submitVerification({
        communityName: form.communityName.trim(),
        address: form.address.trim(),
        city: form.city.trim(),
        state: form.state.trim(),
        pincode: form.pincode.trim(),
        totalFlats: form.totalFlats,
        totalBlocks: form.totalBlocks,
        documentType: t(DOCUMENT_TYPES[form.documentType], { lng: 'en' }),
        documentNumber: form.documentNumber.trim(),
        details: form.details.trim(),
        file,
      })
      setVerification(saved)
      setForm(EMPTY_FORM)
      setFile(null)
    } catch (err) {
      setFormError(err.response?.data?.message || t('verification.form.submitError'))
    } finally {
      setSubmitting(false)
    }
  }

  async function handleViewDocument() {
    try {
      await openMyVerificationDocument()
    } catch {
      setFormError(t('verification.viewError'))
    }
  }

  if (loading) {
    return <p className="af-ver__empty">{t('verification.loading')}</p>
  }

  if (loadError || !verification) {
    return <div className="af-form__error">{t('verification.loadError')}</div>
  }

  const status = verification.status
  const canSubmit = status !== 'APPROVED' && status !== 'PENDING'
  const submitted = status !== 'NOT_SUBMITTED'

  return (
    <>
      <div className="af-ver__panel">
        <div className="af-ver__head">
          <h3>{t('verification.statusTitle')}</h3>
          <span className={`af-ver__badge af-ver__badge--${status.toLowerCase().replace('_', '-')}`}>
            {t(STATUS_KEYS[status])}
          </span>
        </div>

        {status === 'NOT_SUBMITTED' && <p className="af-ver__text">{t('verification.messages.notSubmitted')}</p>}
        {status === 'PENDING' && <p className="af-ver__text">{t('verification.messages.pending')}</p>}
        {status === 'APPROVED' && <p className="af-ver__text">{t('verification.messages.approved')}</p>}
        {status === 'REJECTED' && (
          <div className="af-ver__rejection">
            <strong>{t('verification.rejectionReason')}</strong>
            <p>{verification.rejectionReason}</p>
            <span>{t('verification.messages.rejectedHint')}</span>
          </div>
        )}

        {submitted && (
          <dl className="af-ver__details">
            <div>
              <dt>{t('verification.details.communityName')}</dt>
              <dd>{verification.communityName || '—'}</dd>
            </div>
            <div>
              <dt>{t('verification.details.address')}</dt>
              <dd>
                {[verification.address, verification.city, verification.state, verification.pincode]
                  .filter(Boolean)
                  .join(', ') || '—'}
              </dd>
            </div>
            <div>
              <dt>{t('verification.details.totalFlats')}</dt>
              <dd>{verification.totalFlats ?? '—'}</dd>
            </div>
            <div>
              <dt>{t('verification.details.totalBlocks')}</dt>
              <dd>{verification.totalBlocks ?? '—'}</dd>
            </div>
            <div>
              <dt>{t('verification.details.type')}</dt>
              <dd>{verification.documentType}</dd>
            </div>
            <div>
              <dt>{t('verification.details.number')}</dt>
              <dd>{verification.documentNumber}</dd>
            </div>
            {verification.details && (
              <div>
                <dt>{t('verification.details.notes')}</dt>
                <dd>{verification.details}</dd>
              </div>
            )}
            <div>
              <dt>{t('verification.details.file')}</dt>
              <dd>
                <button type="button" className="af-ver__link" onClick={handleViewDocument}>
                  {verification.fileName}
                </button>
              </dd>
            </div>
            <div>
              <dt>{t('verification.details.submittedAt')}</dt>
              <dd>{formatDateTime(verification.submittedAt, locale)}</dd>
            </div>
            {verification.reviewedAt && (
              <div>
                <dt>{t('verification.details.reviewedAt')}</dt>
                <dd>{formatDateTime(verification.reviewedAt, locale)}</dd>
              </div>
            )}
          </dl>
        )}
      </div>

      {canSubmit && (
        <form className="af-ver__panel" onSubmit={handleSubmit} noValidate>
          <h3>{status === 'REJECTED' ? t('verification.form.resubmitTitle') : t('verification.form.title')}</h3>
          {formError && <div className="af-form__error">{formError}</div>}

          <h4 className="af-ver__section">{t('verification.form.communitySection')}</h4>
          <div className="af-form__field">
            <label htmlFor="verCommunity">{t('verification.form.communityName')}</label>
            <input id="verCommunity" name="communityName" maxLength={200} value={form.communityName} onChange={handleChange} />
          </div>
          <div className="af-form__field">
            <label htmlFor="verAddress">{t('verification.form.address')}</label>
            <input id="verAddress" name="address" maxLength={500} value={form.address} onChange={handleChange} />
          </div>
          <div className="af-form__row">
            <div className="af-form__field">
              <label htmlFor="verCity">{t('verification.form.city')}</label>
              <input id="verCity" name="city" maxLength={100} value={form.city} onChange={handleChange} />
            </div>
            <div className="af-form__field">
              <label htmlFor="verState">{t('verification.form.state')}</label>
              <input id="verState" name="state" maxLength={100} value={form.state} onChange={handleChange} />
            </div>
            <div className="af-form__field">
              <label htmlFor="verPincode">{t('verification.form.pincode')}</label>
              <input id="verPincode" name="pincode" maxLength={12} value={form.pincode} onChange={handleChange} />
            </div>
          </div>
          <div className="af-form__row">
            <div className="af-form__field">
              <label htmlFor="verFlats">{t('verification.form.totalFlats')}</label>
              <input id="verFlats" name="totalFlats" type="number" min="1" value={form.totalFlats} onChange={handleChange} />
            </div>
            <div className="af-form__field">
              <label htmlFor="verBlocks">{t('verification.form.totalBlocks')}</label>
              <input id="verBlocks" name="totalBlocks" type="number" min="1" value={form.totalBlocks} onChange={handleChange} />
            </div>
          </div>

          <h4 className="af-ver__section">{t('verification.form.documentSection')}</h4>
          <div className="af-form__row">
            <div className="af-form__field">
              <label htmlFor="verType">{t('verification.form.documentType')}</label>
              <select id="verType" name="documentType" className="af-ver__select" value={form.documentType} onChange={handleChange}>
                {Object.entries(DOCUMENT_TYPES).map(([value, key]) => (
                  <option key={value} value={value}>
                    {t(key)}
                  </option>
                ))}
              </select>
            </div>
            <div className="af-form__field">
              <label htmlFor="verNumber">{t('verification.form.documentNumber')}</label>
              <input id="verNumber" name="documentNumber" maxLength={100} value={form.documentNumber} onChange={handleChange} />
            </div>
          </div>

          <div className="af-form__field">
            <label htmlFor="verDetails">{t('verification.form.details')}</label>
            <textarea
              id="verDetails"
              name="details"
              rows={4}
              maxLength={2000}
              className="af-ver__textarea"
              value={form.details}
              onChange={handleChange}
            />
          </div>

          <div className="af-form__field">
            <label htmlFor="verFile">{t('verification.form.file')}</label>
            <FilePicker id="verFile" accept="application/pdf,.pdf" file={file} onChange={handleFile} />
            <span className="af-ver__hint">{t('verification.form.fileHint')}</span>
          </div>

          <div className="af-ver__actions">
            <button type="submit" className="af-btn af-btn--cyan" disabled={submitting}>
              {submitting ? t('verification.form.submitting') : t('verification.form.submit')}
            </button>
          </div>
        </form>
      )}
    </>
  )
}

export default Verification
