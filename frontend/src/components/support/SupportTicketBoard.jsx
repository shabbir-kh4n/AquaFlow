import { useEffect, useMemo, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import '../AuthForm.css'
import './SupportTicketBoard.css'

// Translation keys (common namespace), resolved with t() at render time.
const STATUS_LABEL_KEYS = {
  OPEN: 'supportBoard.status.open',
  IN_PROGRESS: 'supportBoard.status.inProgress',
  RESOLVED: 'supportBoard.status.resolved',
  CLOSED: 'supportBoard.status.closed',
}

const STATUS_OPTIONS = ['OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED']
const TABS = [
  { key: '', labelKey: 'supportBoard.tabs.all' },
  { key: 'OPEN', labelKey: 'supportBoard.status.open' },
  { key: 'IN_PROGRESS', labelKey: 'supportBoard.status.inProgress' },
  { key: 'RESOLVED', labelKey: 'supportBoard.status.resolved' },
]

function formatDateTime(value) {
  if (!value) return '—'
  return new Date(value).toLocaleString(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  })
}

function recipientLabelKey(ticket) {
  if (ticket.toAdmin && ticket.toSystemAdmin) return 'supportBoard.recipients.both'
  if (ticket.toAdmin) return 'supportBoard.recipients.admin'
  if (ticket.toSystemAdmin) return 'supportBoard.recipients.systemAdmin'
  return null
}

/**
 * Shared support-ticket UI for the resident, admin (own tickets / resident inbox), and platform
 * (System Admin) surfaces: stat cards + status tabs + search above a card list, a modal to raise a
 * new ticket (with a recipient picker for residents), and a modal detail/reply thread.
 */
function SupportTicketBoard({
  listFn,
  createFn,
  getDetailFn,
  addMessageFn,
  updateStatusFn,
  showRaiser = false,
  showRecipients = false,
  recipientPicker = false,
  emptyCreateHint,
  selfRole,
  title,
  subtitle = '',
  createButtonLabel,
}) {
  const { t } = useTranslation('common')
  const boardTitle = title || t('supportBoard.defaultTitle')
  const createLabel = createButtonLabel || t('supportBoard.defaultCreateLabel')

  function recipientLabel(ticket) {
    const key = recipientLabelKey(ticket)
    return key ? t(key) : '—'
  }

  const [tickets, setTickets] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [activeTab, setActiveTab] = useState('')
  const [search, setSearch] = useState('')

  const [showCreateModal, setShowCreateModal] = useState(false)
  const [formSubject, setFormSubject] = useState('')
  const [formMessage, setFormMessage] = useState('')
  const [formToAdmin, setFormToAdmin] = useState(false)
  const [formToSystemAdmin, setFormToSystemAdmin] = useState(true)
  const [formError, setFormError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const [selectedId, setSelectedId] = useState(null)
  const [detail, setDetail] = useState(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [detailError, setDetailError] = useState('')

  const [replyText, setReplyText] = useState('')
  const [replySubmitting, setReplySubmitting] = useState(false)
  const [statusSaving, setStatusSaving] = useState(false)

  const threadRef = useRef(null)

  useEffect(() => {
    if (threadRef.current) {
      threadRef.current.scrollTop = threadRef.current.scrollHeight
    }
  }, [detail])

  function refreshTickets() {
    return listFn()
      .then((data) => {
        setTickets(data)
        setError('')
      })
      .catch(() => setError(t('supportBoard.errors.loadList')))
  }

  useEffect(() => {
    listFn()
      .then((data) => {
        setTickets(data)
        setError('')
      })
      .catch(() => setError(t('supportBoard.errors.loadList')))
      .finally(() => setLoading(false))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const stats = useMemo(
    () => ({
      OPEN: tickets.filter((ticket) => ticket.status === 'OPEN').length,
      IN_PROGRESS: tickets.filter((ticket) => ticket.status === 'IN_PROGRESS').length,
      RESOLVED: tickets.filter((ticket) => ticket.status === 'RESOLVED').length,
    }),
    [tickets],
  )

  const filteredTickets = useMemo(() => {
    const q = search.trim().toLowerCase()
    return tickets.filter((ticket) => {
      if (activeTab && ticket.status !== activeTab) return false
      if (q && !ticket.subject.toLowerCase().includes(q) && !(ticket.lastMessagePreview || '').toLowerCase().includes(q)) {
        return false
      }
      return true
    })
  }, [tickets, activeTab, search])

  function loadDetail(id, silent = false) {
    if (!silent) setDetailLoading(true)
    setDetailError('')
    return getDetailFn(id)
      .then(setDetail)
      .catch(() => setDetailError(t('supportBoard.errors.loadDetail')))
      .finally(() => {
        if (!silent) setDetailLoading(false)
      })
  }

  function handleSelect(id) {
    setSelectedId(id)
    setDetail(null)
    setReplyText('')
    loadDetail(id)
  }

  function closeDetail() {
    setSelectedId(null)
    setDetail(null)
  }

  function openCreateModal() {
    setFormSubject('')
    setFormMessage('')
    setFormToAdmin(false)
    setFormToSystemAdmin(true)
    setFormError('')
    setShowCreateModal(true)
  }

  async function handleCreate(event) {
    event.preventDefault()
    setFormError('')

    if (!formSubject.trim() || !formMessage.trim()) {
      setFormError(t('supportBoard.errors.required'))
      return
    }
    if (recipientPicker && !formToAdmin && !formToSystemAdmin) {
      setFormError(t('supportBoard.errors.recipientRequired'))
      return
    }

    setSubmitting(true)
    try {
      const payload = recipientPicker
        ? { subject: formSubject.trim(), message: formMessage.trim(), toAdmin: formToAdmin, toSystemAdmin: formToSystemAdmin }
        : { subject: formSubject.trim(), message: formMessage.trim() }
      await createFn(payload)
      setShowCreateModal(false)
      await refreshTickets()
    } catch (err) {
      setFormError(err.response?.data?.message || t('supportBoard.errors.submitFallback'))
    } finally {
      setSubmitting(false)
    }
  }

  async function handleReply(event) {
    event.preventDefault()
    if (!replyText.trim() || !selectedId) return

    setReplySubmitting(true)
    try {
      const updated = await addMessageFn(selectedId, replyText.trim())
      setDetail(updated)
      setReplyText('')
      await refreshTickets()
    } catch {
      setDetailError(t('supportBoard.errors.sendReply'))
    } finally {
      setReplySubmitting(false)
    }
  }

  async function handleStatusChange(status) {
    if (!selectedId || !updateStatusFn) return
    setStatusSaving(true)
    try {
      await updateStatusFn(selectedId, status)
      await Promise.all([loadDetail(selectedId, true), refreshTickets()])
    } catch {
      setDetailError(t('supportBoard.errors.updateStatus'))
    } finally {
      setStatusSaving(false)
    }
  }

  return (
    <div className="af-support">
      <div className="af-support__header">
        <div className="af-support__header-text">
          <h2>{boardTitle}</h2>
          {subtitle && <p>{subtitle}</p>}
        </div>
        <div className="af-support__header-actions">
          <button type="button" className="af-btn af-btn--outline" onClick={() => refreshTickets()}>
            ↻ {t('supportBoard.refresh')}
          </button>
          {createFn && (
            <button type="button" className="af-btn af-btn--cyan" onClick={openCreateModal}>
              + {createLabel}
            </button>
          )}
        </div>
      </div>

      <div className="af-support__stats">
        <div className="af-support__stat-card">
          <span className="af-support__stat-label">{t('supportBoard.stats.openLabel')}</span>
          <strong>{stats.OPEN}</strong>
          <span className="af-support__stat-hint">{t('supportBoard.stats.openHint')}</span>
        </div>
        <div className="af-support__stat-card">
          <span className="af-support__stat-label">{t('supportBoard.stats.inProgressLabel')}</span>
          <strong>{stats.IN_PROGRESS}</strong>
          <span className="af-support__stat-hint">{t('supportBoard.stats.inProgressHint')}</span>
        </div>
        <div className="af-support__stat-card">
          <span className="af-support__stat-label">{t('supportBoard.stats.resolvedLabel')}</span>
          <strong>{stats.RESOLVED}</strong>
          <span className="af-support__stat-hint">{t('supportBoard.stats.resolvedHint')}</span>
        </div>
      </div>

      <div className="af-support__toolbar">
        <div className="af-support__tabs">
          {TABS.map((tab) => (
            <button
              key={tab.key}
              type="button"
              className={`af-support__tab${activeTab === tab.key ? ' af-support__tab--active' : ''}`}
              onClick={() => setActiveTab(tab.key)}
            >
              {t(tab.labelKey)}
            </button>
          ))}
        </div>
        <label className="af-support__search">
          <input
            type="search"
            placeholder={t('supportBoard.searchPlaceholder')}
            value={search}
            onChange={(event) => setSearch(event.target.value)}
          />
        </label>
      </div>

      {error && <div className="af-form__error">{error}</div>}

      {loading ? (
        <p className="af-support__empty">{t('supportBoard.loadingTickets')}</p>
      ) : filteredTickets.length === 0 ? (
        <div className="af-support__empty-state">
          <div className="af-support__empty-icon">🛟</div>
          <h3>{t('supportBoard.empty.title')}</h3>
          <p>{t('supportBoard.empty.body')}</p>
          {createFn && (
            <button type="button" className="af-btn af-btn--cyan" onClick={openCreateModal}>
              + {t('supportBoard.empty.cta')}
            </button>
          )}
        </div>
      ) : (
        <ul className="af-support__list">
          {filteredTickets.map((ticket) => (
            <li key={ticket.id}>
              <button type="button" className="af-support__list-item" onClick={() => handleSelect(ticket.id)}>
                <div className="af-support__list-item-top">
                  <strong>{ticket.subject}</strong>
                  <span className={`af-support__badge af-support__badge--${ticket.status.toLowerCase()}`}>
                    {t(STATUS_LABEL_KEYS[ticket.status])}
                  </span>
                </div>
                {showRaiser && (
                  <span className="af-support__list-item-meta">
                    {ticket.raisedByRole === 'RESIDENT' ? t('supportBoard.roles.resident') : t('supportBoard.roles.admin')} · {ticket.raisedByName} ·{' '}
                    {ticket.communityName}
                  </span>
                )}
                {showRecipients && (
                  <span className="af-support__list-item-meta">
                    {t('supportBoard.toRecipients', { recipients: recipientLabel(ticket) })}
                  </span>
                )}
                <span className="af-support__list-item-preview">{ticket.lastMessagePreview}</span>
                <span className="af-support__list-item-date">{formatDateTime(ticket.updatedAt)}</span>
              </button>
            </li>
          ))}
        </ul>
      )}

      {showCreateModal && (
        <div className="af-support__modal-backdrop" onClick={() => setShowCreateModal(false)}>
          <div className="af-support__modal" onClick={(event) => event.stopPropagation()}>
            <h2>{createLabel}</h2>
            <p className="af-support__modal-sub">
              {emptyCreateHint || t('supportBoard.form.defaultHint')}
            </p>

            <form onSubmit={handleCreate}>
              {formError && <div className="af-form__error">{formError}</div>}

              <div className="af-form__field">
                <label htmlFor="support-subject">{t('supportBoard.form.subjectLabel')}</label>
                <input
                  id="support-subject"
                  type="text"
                  maxLength={200}
                  value={formSubject}
                  onChange={(event) => setFormSubject(event.target.value)}
                  placeholder={t('supportBoard.form.subjectPlaceholder')}
                />
              </div>

              <div className="af-form__field">
                <label htmlFor="support-message">{t('supportBoard.form.messageLabel')}</label>
                <textarea
                  id="support-message"
                  rows={4}
                  maxLength={2000}
                  value={formMessage}
                  onChange={(event) => setFormMessage(event.target.value)}
                  placeholder={t('supportBoard.form.messagePlaceholder')}
                />
              </div>

              {recipientPicker && (
                <div className="af-form__field">
                  <label>{t('supportBoard.form.recipientsLabel')}</label>
                  <div className="af-support__recipient-options">
                    <label className="af-support__checkbox">
                      <input
                        type="checkbox"
                        checked={formToAdmin}
                        onChange={(event) => setFormToAdmin(event.target.checked)}
                      />
                      {t('supportBoard.recipients.admin')}
                    </label>
                    <label className="af-support__checkbox">
                      <input
                        type="checkbox"
                        checked={formToSystemAdmin}
                        onChange={(event) => setFormToSystemAdmin(event.target.checked)}
                      />
                      {t('supportBoard.recipients.systemAdmin')}
                    </label>
                  </div>
                </div>
              )}

              <div className="af-support__modal-actions">
                <button type="button" className="af-btn af-btn--outline" onClick={() => setShowCreateModal(false)}>
                  {t('supportBoard.form.cancel')}
                </button>
                <button type="submit" className="af-form__submit" disabled={submitting}>
                  {submitting ? t('supportBoard.form.submitting') : t('supportBoard.form.submit')}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {selectedId && (
        <div className="af-support__modal-backdrop" onClick={closeDetail}>
          <div className="af-support__modal af-support__modal--detail" onClick={(event) => event.stopPropagation()}>
            {detailLoading ? (
              <p className="af-support__empty">{t('supportBoard.detail.loading')}</p>
            ) : detailError ? (
              <div className="af-form__error">{detailError}</div>
            ) : detail ? (
              <>
                <div className="af-support__detail-heading">
                  <div>
                    <h2>{detail.subject}</h2>
                    {showRaiser && (
                      <p className="af-support__detail-sub">
                        {detail.raisedByRole === 'RESIDENT'
                          ? t('supportBoard.raisedByResident', { name: detail.raisedByName })
                          : t('supportBoard.raisedByAdmin', { name: detail.raisedByName })}{' '}
                        ·{' '}
                        {detail.communityName}
                      </p>
                    )}
                    {showRecipients && (
                      <p className="af-support__detail-sub">
                        {t('supportBoard.toRecipients', { recipients: recipientLabel(detail) })}
                      </p>
                    )}
                  </div>
                  {updateStatusFn ? (
                    <select
                      value={detail.status}
                      disabled={statusSaving}
                      onChange={(event) => handleStatusChange(event.target.value)}
                    >
                      {STATUS_OPTIONS.map((status) => (
                        <option key={status} value={status}>
                          {t(STATUS_LABEL_KEYS[status])}
                        </option>
                      ))}
                    </select>
                  ) : (
                    <span className={`af-support__badge af-support__badge--${detail.status.toLowerCase()}`}>
                      {t(STATUS_LABEL_KEYS[detail.status])}
                    </span>
                  )}
                </div>

                <div className="af-support__thread" ref={threadRef}>
                  {detail.messages.map((message) => {
                    const isMine = message.senderRole === selfRole
                    return (
                      <div key={message.id} className={`af-support__row${isMine ? ' af-support__row--mine' : ''}`}>
                        <div className={`af-support__bubble${isMine ? ' af-support__bubble--mine' : ''}`}>
                          {!isMine && <strong className="af-support__bubble-sender">{message.senderName}</strong>}
                          <p>{message.message}</p>
                          <span className="af-support__bubble-time">{formatDateTime(message.createdAt)}</span>
                        </div>
                      </div>
                    )
                  })}
                </div>

                <form className="af-support__reply-form" onSubmit={handleReply}>
                  <textarea
                    rows={3}
                    maxLength={2000}
                    value={replyText}
                    onChange={(event) => setReplyText(event.target.value)}
                    placeholder={t('supportBoard.detail.replyPlaceholder')}
                  />
                  <div className="af-support__modal-actions">
                    <button type="button" className="af-btn af-btn--outline" onClick={closeDetail}>
                      {t('supportBoard.detail.close')}
                    </button>
                    <button type="submit" className="af-form__submit" disabled={replySubmitting || !replyText.trim()}>
                      {replySubmitting ? t('supportBoard.detail.sending') : t('supportBoard.detail.sendReply')}
                    </button>
                  </div>
                </form>
              </>
            ) : null}
          </div>
        </div>
      )}
    </div>
  )
}

export default SupportTicketBoard
