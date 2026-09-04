import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { deleteCommunity, getCommunityDetail, updateCommunityStatus } from '../../api/platformApi'
import './CommunityDetail.css'

const PAGE_SIZE = 10

function CommunityDetail() {
  const { adminId } = useParams()
  const navigate = useNavigate()

  const [detail, setDetail] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [actionError, setActionError] = useState('')
  const [actionBusy, setActionBusy] = useState(false)
  const [page, setPage] = useState(0)
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false)
  const [deleteConfirmText, setDeleteConfirmText] = useState('')
  const [confirmAction, setConfirmAction] = useState(null)

  useEffect(() => {
    getCommunityDetail(adminId)
      .then(setDetail)
      .catch(() => setError('Could not load this community. It may have been removed.'))
      .finally(() => setLoading(false))
  }, [adminId])

  async function handleStatusChange(status) {
    setActionError('')
    setActionBusy(true)
    try {
      const updated = await updateCommunityStatus(adminId, status)
      setDetail((prev) => (prev ? { ...prev, status: updated.status } : prev))
    } catch (err) {
      setActionError(err.response?.data?.message || 'Could not update this community\'s status.')
    } finally {
      setActionBusy(false)
    }
  }

  function requestStatusChange(status, title, message, confirmLabel, tone) {
    setActionError('')
    setConfirmAction({ status, title, message, confirmLabel, tone })
  }

  function closeConfirmAction() {
    setConfirmAction(null)
  }

  async function confirmStatusChange() {
    const action = confirmAction
    setConfirmAction(null)
    if (action) {
      await handleStatusChange(action.status)
    }
  }

  function openDeleteConfirm() {
    setActionError('')
    setDeleteConfirmText('')
    setShowDeleteConfirm(true)
  }

  function closeDeleteConfirm() {
    setShowDeleteConfirm(false)
    setDeleteConfirmText('')
  }

  async function handleDelete() {
    setActionError('')
    setActionBusy(true)
    try {
      await deleteCommunity(adminId)
      navigate('/platform/communities')
    } catch (err) {
      setActionError(err.response?.data?.message || 'Could not delete this community.')
      setActionBusy(false)
      setShowDeleteConfirm(false)
    }
  }

  if (loading) {
    return <p className="af-cd__empty">Loading community…</p>
  }

  if (error || !detail) {
    return (
      <div className="af-cd__error">
        {error || 'Community not found.'} <Link to="/platform/communities">Back to Communities</Link>
      </div>
    )
  }

  const residents = detail.residents || []
  const pageCount = Math.max(1, Math.ceil(residents.length / PAGE_SIZE))
  const pagedResidents = residents.slice(page * PAGE_SIZE, page * PAGE_SIZE + PAGE_SIZE)

  return (
    <>
      <Link to="/platform/communities" className="af-cd__back">
        ← Back to Communities
      </Link>

      {actionError && <div className="af-cd__error">{actionError}</div>}

      <div className="af-cd__header">
        <div>
          <p className="af-cd__eyebrow">Community Profile</p>
          <h2>{detail.apartmentName || 'Unnamed community'}</h2>
          <p className="af-cd__address">{detail.apartmentAddress || 'No address on file'}</p>
        </div>

        <div className="af-cd__header-stats">
          <div>
            <span className="af-cd__stat-value">{residents.length}</span>
            <span className="af-cd__stat-label">Residents</span>
          </div>
          <div>
            <span className={`af-cd__badge af-cd__badge--${detail.status.toLowerCase()}`}>{detail.status}</span>
            <span className="af-cd__stat-label">Status</span>
          </div>
          <div className="af-cd__admin-block">
            <strong>{detail.adminFullName}</strong>
            <span>{detail.adminEmail}</span>
            {detail.adminPhone && <span>{detail.adminPhone}</span>}
          </div>
        </div>
      </div>

      <div className="af-cd__actions">
        {detail.status !== 'ACTIVE' && (
          <button
            type="button"
            className="af-cd__action af-cd__action--outline"
            disabled={actionBusy}
            onClick={() => handleStatusChange('ACTIVE')}
          >
            Reactivate
          </button>
        )}
        {detail.status === 'ACTIVE' && (
          <button
            type="button"
            className="af-cd__action af-cd__action--outline"
            disabled={actionBusy}
            onClick={() =>
              requestStatusChange(
                'BLOCKED',
                'Block this community admin?',
                `Block ${detail.adminFullName}? They will be unable to log in until unblocked. No data will be affected.`,
                'Block',
                'outline',
              )
            }
          >
            Block
          </button>
        )}
        {detail.status !== 'TERMINATED' && (
          <button
            type="button"
            className="af-cd__action af-cd__action--warn"
            disabled={actionBusy}
            onClick={() =>
              requestStatusChange(
                'TERMINATED',
                'Terminate this community admin?',
                `Permanently terminate ${detail.adminFullName}'s access? This cannot be undone, though their community's data will be kept for records.`,
                'Terminate',
                'warn',
              )
            }
          >
            Terminate
          </button>
        )}
        <button type="button" className="af-cd__action af-cd__action--danger" disabled={actionBusy} onClick={openDeleteConfirm}>
          Delete Community
        </button>
      </div>

      <div className="af-cd__table-wrap">
        <div className="af-cd__table-header">
          <h3>Residents</h3>
          <span>{residents.length} registered</span>
        </div>

        {residents.length === 0 ? (
          <p className="af-cd__empty">No residents registered in this community yet.</p>
        ) : (
          <table className="af-cd__table">
            <thead>
              <tr>
                <th>Flat Unit</th>
                <th>Meter</th>
                <th>Resident</th>
                <th>Email</th>
                <th>Password Set</th>
              </tr>
            </thead>
            <tbody>
              {pagedResidents.map((r) => (
                <tr key={r.id}>
                  <td>{r.houseNumber || '—'}</td>
                  <td>{r.meterNumber || '—'}</td>
                  <td>{r.fullName}</td>
                  <td>{r.email}</td>
                  <td>{r.passwordSet ? 'Yes' : 'No'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {residents.length > PAGE_SIZE && (
        <div className="af-cd__pagination">
          <button type="button" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
            ← Prev
          </button>
          <span>
            Page {page + 1} of {pageCount}
          </span>
          <button type="button" disabled={page >= pageCount - 1} onClick={() => setPage((p) => p + 1)}>
            Next →
          </button>
        </div>
      )}

      {confirmAction && (
        <div className="af-cd__modal-backdrop" onClick={actionBusy ? undefined : closeConfirmAction}>
          <div className="af-cd__confirm-modal" onClick={(e) => e.stopPropagation()}>
            <h2>{confirmAction.title}</h2>
            <p>{confirmAction.message}</p>
            <div className="af-cd__danger-actions">
              <button type="button" className="af-cd__action af-cd__action--outline" disabled={actionBusy} onClick={closeConfirmAction}>
                Cancel
              </button>
              <button
                type="button"
                className={`af-cd__action af-cd__action--${confirmAction.tone}`}
                disabled={actionBusy}
                onClick={confirmStatusChange}
              >
                {actionBusy ? 'Working…' : confirmAction.confirmLabel}
              </button>
            </div>
          </div>
        </div>
      )}

      {showDeleteConfirm && (
        <div className="af-cd__modal-backdrop" onClick={actionBusy ? undefined : closeDeleteConfirm}>
          <div className="af-cd__danger-modal" onClick={(e) => e.stopPropagation()}>
            <div className="af-cd__danger-icon" aria-hidden="true">
              !
            </div>
            <h2>Delete this community?</h2>
            <p>
              This will permanently delete <strong>{detail.apartmentName}</strong> and all of its households,
              residents, meters, tariff/billing configuration, and invoice history. This action cannot be undone.
            </p>
            <label htmlFor="deleteConfirmText">
              Type <strong>confirm</strong> to continue
            </label>
            <input
              id="deleteConfirmText"
              type="text"
              autoComplete="off"
              autoFocus
              value={deleteConfirmText}
              onChange={(e) => setDeleteConfirmText(e.target.value)}
              placeholder="confirm"
            />
            <div className="af-cd__danger-actions">
              <button type="button" className="af-cd__action af-cd__action--outline" disabled={actionBusy} onClick={closeDeleteConfirm}>
                Cancel
              </button>
              <button
                type="button"
                className="af-cd__action af-cd__action--danger"
                disabled={actionBusy || deleteConfirmText.trim().toLowerCase() !== 'confirm'}
                onClick={handleDelete}
              >
                {actionBusy ? 'Deleting…' : 'Confirm'}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  )
}

export default CommunityDetail
