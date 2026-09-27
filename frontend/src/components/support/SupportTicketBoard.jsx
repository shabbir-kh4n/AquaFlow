import { useEffect, useMemo, useRef, useState } from 'react'
import '../AuthForm.css'
import './SupportTicketBoard.css'

const STATUS_LABELS = {
  OPEN: 'Open',
  IN_PROGRESS: 'In Progress',
  RESOLVED: 'Resolved',
  CLOSED: 'Closed',
}

const STATUS_OPTIONS = ['OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED']
const TABS = [
  { key: '', label: 'All Tickets' },
  { key: 'OPEN', label: 'Open' },
  { key: 'IN_PROGRESS', label: 'In Progress' },
  { key: 'RESOLVED', label: 'Resolved' },
]

function formatDateTime(value) {
  if (!value) return '—'
  return new Date(value).toLocaleString(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  })
}

function recipientLabel(ticket) {
  const parts = []
  if (ticket.toAdmin) parts.push('Community Admin')
  if (ticket.toSystemAdmin) parts.push('System Admin')
  return parts.join(' + ') || '—'
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
  title = 'Support Tickets',
  subtitle = '',
  createButtonLabel = 'Raise New Concern',
}) {
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
      .catch(() => setError('Could not load support tickets. Please try again.'))
  }

  useEffect(() => {
    listFn()
      .then((data) => {
        setTickets(data)
        setError('')
      })
      .catch(() => setError('Could not load support tickets. Please try again.'))
      .finally(() => setLoading(false))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const stats = useMemo(
    () => ({
      OPEN: tickets.filter((t) => t.status === 'OPEN').length,
      IN_PROGRESS: tickets.filter((t) => t.status === 'IN_PROGRESS').length,
      RESOLVED: tickets.filter((t) => t.status === 'RESOLVED').length,
    }),
    [tickets],
  )

  const filteredTickets = useMemo(() => {
    const q = search.trim().toLowerCase()
    return tickets.filter((t) => {
      if (activeTab && t.status !== activeTab) return false
      if (q && !t.subject.toLowerCase().includes(q) && !(t.lastMessagePreview || '').toLowerCase().includes(q)) {
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
      .catch(() => setDetailError('Could not load this ticket. Please try again.'))
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
      setFormError('Subject and message are both required.')
      return
    }
    if (recipientPicker && !formToAdmin && !formToSystemAdmin) {
      setFormError('Select at least one recipient (Community Admin and/or System Admin).')
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
      setFormError(err.response?.data?.message || 'Could not submit this ticket. Please try again.')
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
      setDetailError('Could not send this message. Please try again.')
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
      setDetailError('Could not update this ticket status. Please try again.')
    } finally {
      setStatusSaving(false)
    }
  }

  return (
    <div className="af-support">
      <div className="af-support__header">
        <div className="af-support__header-text">
          <h2>{title}</h2>
          {subtitle && <p>{subtitle}</p>}
        </div>
        <div className="af-support__header-actions">
          <button type="button" className="af-btn af-btn--outline" onClick={() => refreshTickets()}>
            ↻ Refresh
          </button>
          {createFn && (
            <button type="button" className="af-btn af-btn--cyan" onClick={openCreateModal}>
              + {createButtonLabel}
            </button>
          )}
        </div>
      </div>

      <div className="af-support__stats">
        <div className="af-support__stat-card">
          <span className="af-support__stat-label">Open Submissions</span>
          <strong>{stats.OPEN}</strong>
          <span className="af-support__stat-hint">Awaiting review</span>
        </div>
        <div className="af-support__stat-card">
          <span className="af-support__stat-label">In Progress</span>
          <strong>{stats.IN_PROGRESS}</strong>
          <span className="af-support__stat-hint">Being worked on</span>
        </div>
        <div className="af-support__stat-card">
          <span className="af-support__stat-label">Resolved</span>
          <strong>{stats.RESOLVED}</strong>
          <span className="af-support__stat-hint">Successfully closed</span>
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
              {tab.label}
            </button>
          ))}
        </div>
        <label className="af-support__search">
          <input
            type="search"
            placeholder="Search tickets by subject, issue…"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
          />
        </label>
      </div>

      {error && <div className="af-form__error">{error}</div>}

      {loading ? (
        <p className="af-support__empty">Loading tickets…</p>
      ) : filteredTickets.length === 0 ? (
        <div className="af-support__empty-state">
          <div className="af-support__empty-icon">🛟</div>
          <h3>No Support Tickets Found</h3>
          <p>You currently have no open or recorded support requests matching this filter.</p>
          {createFn && (
            <button type="button" className="af-btn af-btn--cyan" onClick={openCreateModal}>
              + Submit a Concern
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
                    {STATUS_LABELS[ticket.status]}
                  </span>
                </div>
                {showRaiser && (
                  <span className="af-support__list-item-meta">
                    {ticket.raisedByRole === 'RESIDENT' ? 'Resident' : 'Admin'} · {ticket.raisedByName} ·{' '}
                    {ticket.communityName}
                  </span>
                )}
                {showRecipients && (
                  <span className="af-support__list-item-meta">To: {recipientLabel(ticket)}</span>
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
            <h2>{createButtonLabel}</h2>
            <p className="af-support__modal-sub">
              {emptyCreateHint || 'Describe your issue or concern below.'}
            </p>

            <form onSubmit={handleCreate}>
              {formError && <div className="af-form__error">{formError}</div>}

              <div className="af-form__field">
                <label htmlFor="support-subject">Subject</label>
                <input
                  id="support-subject"
                  type="text"
                  maxLength={200}
                  value={formSubject}
                  onChange={(event) => setFormSubject(event.target.value)}
                  placeholder="Brief summary of your issue"
                />
              </div>

              <div className="af-form__field">
                <label htmlFor="support-message">Message</label>
                <textarea
                  id="support-message"
                  rows={4}
                  maxLength={2000}
                  value={formMessage}
                  onChange={(event) => setFormMessage(event.target.value)}
                  placeholder="Describe what's happening in detail…"
                />
              </div>

              {recipientPicker && (
                <div className="af-form__field">
                  <label>Send this concern to</label>
                  <div className="af-support__recipient-options">
                    <label className="af-support__checkbox">
                      <input
                        type="checkbox"
                        checked={formToAdmin}
                        onChange={(event) => setFormToAdmin(event.target.checked)}
                      />
                      Community Admin
                    </label>
                    <label className="af-support__checkbox">
                      <input
                        type="checkbox"
                        checked={formToSystemAdmin}
                        onChange={(event) => setFormToSystemAdmin(event.target.checked)}
                      />
                      System Admin
                    </label>
                  </div>
                </div>
              )}

              <div className="af-support__modal-actions">
                <button type="button" className="af-btn af-btn--outline" onClick={() => setShowCreateModal(false)}>
                  Cancel
                </button>
                <button type="submit" className="af-form__submit" disabled={submitting}>
                  {submitting ? 'Submitting…' : 'Submit Ticket'}
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
              <p className="af-support__empty">Loading ticket…</p>
            ) : detailError ? (
              <div className="af-form__error">{detailError}</div>
            ) : detail ? (
              <>
                <div className="af-support__detail-heading">
                  <div>
                    <h2>{detail.subject}</h2>
                    {showRaiser && (
                      <p className="af-support__detail-sub">
                        Raised by {detail.raisedByRole === 'RESIDENT' ? 'resident' : 'admin'} {detail.raisedByName} ·{' '}
                        {detail.communityName}
                      </p>
                    )}
                    {showRecipients && (
                      <p className="af-support__detail-sub">To: {recipientLabel(detail)}</p>
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
                          {STATUS_LABELS[status]}
                        </option>
                      ))}
                    </select>
                  ) : (
                    <span className={`af-support__badge af-support__badge--${detail.status.toLowerCase()}`}>
                      {STATUS_LABELS[detail.status]}
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
                    placeholder="Write a reply…"
                  />
                  <div className="af-support__modal-actions">
                    <button type="button" className="af-btn af-btn--outline" onClick={closeDetail}>
                      Close
                    </button>
                    <button type="submit" className="af-form__submit" disabled={replySubmitting || !replyText.trim()}>
                      {replySubmitting ? 'Sending…' : 'Send Reply'}
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
