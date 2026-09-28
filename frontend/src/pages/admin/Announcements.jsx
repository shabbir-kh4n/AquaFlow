import { useEffect, useState } from 'react'
import i18next from 'i18next'
import { useTranslation } from 'react-i18next'
import {
  createAnnouncement,
  deleteAnnouncement,
  listAnnouncements,
  updateAnnouncement,
} from '../../api/announcementApi'
import '../../components/AuthForm.css'
import './Announcements.css'

// Keys are translation keys (admin namespace), resolved with t() at render time.
const CATEGORY_LABEL_KEYS = {
  GENERAL: 'announcements.category.general',
  MAINTENANCE: 'announcements.category.maintenance',
  WATER_SUPPLY: 'announcements.category.waterSupply',
  BILLING: 'announcements.category.billing',
  URGENT: 'announcements.category.urgent',
}

const CATEGORIES = Object.keys(CATEGORY_LABEL_KEYS)

const EMPTY_FORM = {
  title: '',
  body: '',
  category: 'GENERAL',
  pinned: false,
  expiresAt: '',
}

// <input type="datetime-local"> works in local wall-clock time without a zone suffix.
function toLocalInputValue(iso) {
  if (!iso) return ''
  const date = new Date(iso)
  const offsetMs = date.getTimezoneOffset() * 60000
  return new Date(date.getTime() - offsetMs).toISOString().slice(0, 16)
}

function formatDateTime(iso, locale) {
  if (!iso) return '—'
  return new Date(iso).toLocaleString(locale, { dateStyle: 'medium', timeStyle: 'short' })
}

function Announcements() {
  const { t, i18n } = useTranslation('admin')
  const locale = i18n.resolvedLanguage || 'en'
  const [announcements, setAnnouncements] = useState([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')

  const [showForm, setShowForm] = useState(false)
  const [editingId, setEditingId] = useState(null)
  const [form, setForm] = useState(EMPTY_FORM)
  const [formError, setFormError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const [pendingDelete, setPendingDelete] = useState(null)
  const [deleting, setDeleting] = useState(false)

  function reload() {
    setLoadError('')
    return listAnnouncements()
      .then(setAnnouncements)
      .catch(() => setLoadError(t('announcements.loadError')))
  }

  useEffect(() => {
    listAnnouncements()
      .then(setAnnouncements)
      .catch(() => setLoadError(i18next.t('announcements.loadError', { ns: 'admin' })))
      .finally(() => setLoading(false))
  }, [])

  function openAddForm() {
    setEditingId(null)
    setForm(EMPTY_FORM)
    setFormError('')
    setShowForm(true)
  }

  function openEditForm(announcement) {
    setEditingId(announcement.id)
    setForm({
      title: announcement.title,
      body: announcement.body,
      category: announcement.category,
      pinned: announcement.pinned,
      expiresAt: toLocalInputValue(announcement.expiresAt),
    })
    setFormError('')
    setShowForm(true)
  }

  function closeForm() {
    setShowForm(false)
    setEditingId(null)
  }

  function handleChange(event) {
    const { name, value, type, checked } = event.target
    setForm((prev) => ({ ...prev, [name]: type === 'checkbox' ? checked : value }))
  }

  async function handleSubmit(event) {
    event.preventDefault()
    setFormError('')

    if (!form.title.trim() || !form.body.trim()) {
      setFormError(t('announcements.form.requiredFields'))
      return
    }

    const payload = {
      title: form.title.trim(),
      body: form.body.trim(),
      category: form.category,
      pinned: form.pinned,
      expiresAt: form.expiresAt ? new Date(form.expiresAt).toISOString() : null,
    }

    setSubmitting(true)
    try {
      if (editingId) {
        await updateAnnouncement(editingId, payload)
      } else {
        await createAnnouncement(payload)
      }
      closeForm()
      await reload()
    } catch (err) {
      setFormError(err.response?.data?.message || t('announcements.form.saveError'))
    } finally {
      setSubmitting(false)
    }
  }

  async function confirmDelete() {
    if (!pendingDelete) return
    setDeleting(true)
    try {
      await deleteAnnouncement(pendingDelete.id)
      setPendingDelete(null)
      await reload()
    } catch {
      setPendingDelete(null)
      setLoadError(t('announcements.removeError'))
    } finally {
      setDeleting(false)
    }
  }

  return (
    <>
      <div className="af-ann__toolbar">
        <span className="af-ann__count">{t('announcements.count', { total: announcements.length })}</span>
        <button type="button" className="af-btn af-btn--cyan" onClick={openAddForm}>
          {t('announcements.newButton')}
        </button>
      </div>

      {loadError && <div className="af-form__error">{loadError}</div>}

      {loading ? (
        <p className="af-ann__empty">{t('announcements.loading')}</p>
      ) : announcements.length === 0 ? (
        <div className="af-ann__panel">
          <p className="af-ann__empty">{t('announcements.empty')}</p>
        </div>
      ) : (
        <div className="af-ann__list">
          {announcements.map((a) => (
            <article
              key={a.id}
              className={`af-ann__card${a.pinned ? ' af-ann__card--pinned' : ''}${a.expired ? ' af-ann__card--expired' : ''}`}
            >
              <div className="af-ann__card-head">
                <div className="af-ann__badges">
                  <span className={`af-ann__badge af-ann__badge--${a.category.toLowerCase()}`}>
                    {CATEGORY_LABEL_KEYS[a.category] ? t(CATEGORY_LABEL_KEYS[a.category]) : a.category}
                  </span>
                  {a.pinned && <span className="af-ann__badge af-ann__badge--pin">{t('announcements.pinned')}</span>}
                  {a.expired && <span className="af-ann__badge af-ann__badge--expired">{t('announcements.expired')}</span>}
                </div>
                <div className="af-ann__actions">
                  <button type="button" onClick={() => openEditForm(a)}>
                    {t('shared.edit')}
                  </button>
                  <button type="button" className="af-ann__delete" onClick={() => setPendingDelete(a)}>
                    {t('shared.remove')}
                  </button>
                </div>
              </div>
              <h3>{a.title}</h3>
              <p className="af-ann__body">{a.body}</p>
              <div className="af-ann__meta">
                <span>{t('announcements.postedAt', { date: formatDateTime(a.createdAt, locale) })}</span>
                {a.updatedAt && a.createdAt && a.updatedAt !== a.createdAt && (
                  <span>{t('announcements.updatedAt', { date: formatDateTime(a.updatedAt, locale) })}</span>
                )}
                {a.expiresAt && (
                  <span>{t('announcements.expiresAt', { date: formatDateTime(a.expiresAt, locale) })}</span>
                )}
              </div>
            </article>
          ))}
        </div>
      )}

      {showForm && (
        <div className="af-ann__modal-backdrop" onClick={closeForm}>
          <div className="af-ann__modal" onClick={(e) => e.stopPropagation()}>
            <h2>{editingId ? t('announcements.form.editTitle') : t('announcements.form.addTitle')}</h2>
            <p className="af-ann__modal-sub">
              {editingId ? t('announcements.form.editSubtitle') : t('announcements.form.addSubtitle')}
            </p>

            <form onSubmit={handleSubmit}>
              {formError && <div className="af-form__error">{formError}</div>}

              <div className="af-form__field">
                <label htmlFor="annTitle">{t('announcements.form.title')}</label>
                <input id="annTitle" name="title" maxLength={200} value={form.title} onChange={handleChange} />
              </div>

              <div className="af-form__row">
                <div className="af-form__field">
                  <label htmlFor="annCategory">{t('announcements.form.category')}</label>
                  <select id="annCategory" name="category" className="af-ann__select" value={form.category} onChange={handleChange}>
                    {CATEGORIES.map((c) => (
                      <option key={c} value={c}>
                        {t(CATEGORY_LABEL_KEYS[c])}
                      </option>
                    ))}
                  </select>
                </div>
                <div className="af-form__field">
                  <label htmlFor="annExpires">{t('announcements.form.expiresAt')}</label>
                  <input id="annExpires" name="expiresAt" type="datetime-local" value={form.expiresAt} onChange={handleChange} />
                </div>
              </div>

              <div className="af-form__field">
                <label htmlFor="annBody">{t('announcements.form.body')}</label>
                <textarea
                  id="annBody"
                  name="body"
                  rows={6}
                  maxLength={5000}
                  className="af-ann__textarea"
                  value={form.body}
                  onChange={handleChange}
                />
              </div>

              <label className="af-ann__checkbox">
                <input type="checkbox" name="pinned" checked={form.pinned} onChange={handleChange} />
                {t('announcements.form.pinned')}
              </label>

              <div className="af-ann__modal-actions">
                <button type="button" className="af-btn af-btn--outline" onClick={closeForm}>
                  {t('shared.cancel')}
                </button>
                <button type="submit" className="af-form__submit" disabled={submitting}>
                  {submitting
                    ? t('shared.saving')
                    : editingId
                      ? t('shared.saveChanges')
                      : t('announcements.form.publish')}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {pendingDelete && (
        <div className="af-ann__modal-backdrop" onClick={() => !deleting && setPendingDelete(null)}>
          <div className="af-ann__modal af-ann__modal--confirm" role="alertdialog" onClick={(e) => e.stopPropagation()}>
            <h2>{t('announcements.confirm.title')}</h2>
            <p className="af-ann__modal-sub">{t('announcements.confirm.body', { title: pendingDelete.title })}</p>
            <div className="af-ann__modal-actions">
              <button type="button" className="af-btn af-btn--outline" disabled={deleting} onClick={() => setPendingDelete(null)}>
                {t('shared.cancel')}
              </button>
              <button type="button" className="af-ann__danger" disabled={deleting} onClick={confirmDelete}>
                {deleting ? t('announcements.confirm.deleting') : t('announcements.confirm.delete')}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  )
}

export default Announcements
