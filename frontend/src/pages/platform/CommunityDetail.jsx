import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { Trans, useTranslation } from 'react-i18next'
import { deleteCommunity, getCommunityDetail, updateCommunityStatus } from '../../api/platformApi'
import Avatar from '../../components/Avatar'
import './CommunityDetail.css'

const PAGE_SIZE = 10

const DELETE_CONFIRM_WORD = 'confirm'

const STATUS_KEYS = {
  ACTIVE: 'status.active',
  BLOCKED: 'status.blocked',
  TERMINATED: 'status.terminated',
}

function CommunityDetail() {
  const { t } = useTranslation('platform')
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
      .catch(() => setError('communityDetail.loadError'))
      .finally(() => setLoading(false))
  }, [adminId])

  async function handleStatusChange(status) {
    setActionError('')
    setActionBusy(true)
    try {
      const updated = await updateCommunityStatus(adminId, status)
      setDetail((prev) => (prev ? { ...prev, status: updated.status } : prev))
    } catch (err) {
      setActionError(err.response?.data?.message || t('communityDetail.statusUpdateError'))
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
      setActionError(err.response?.data?.message || t('communityDetail.deleteError'))
      setActionBusy(false)
      setShowDeleteConfirm(false)
    }
  }

  if (loading) {
    return <p className="af-cd__empty">{t('communityDetail.loading')}</p>
  }

  if (error || !detail) {
    return (
      <div className="af-cd__error">
        {error ? t(error) : t('communityDetail.notFound')}{' '}
        <Link to="/platform/communities">{t('communityDetail.backLinkShort')}</Link>
      </div>
    )
  }

  const residents = detail.residents || []
  const pageCount = Math.max(1, Math.ceil(residents.length / PAGE_SIZE))
  const pagedResidents = residents.slice(page * PAGE_SIZE, page * PAGE_SIZE + PAGE_SIZE)

  return (
    <>
      <Link to="/platform/communities" className="af-cd__back">
        {t('communityDetail.backLink')}
      </Link>

      {actionError && <div className="af-cd__error">{actionError}</div>}

      <div className="af-cd__header">
        <div>
          <p className="af-cd__eyebrow">{t('communityDetail.eyebrow')}</p>
          <h2>{detail.apartmentName || t('communityDetail.unnamed')}</h2>
          <p className="af-cd__address">{detail.apartmentAddress || t('communityDetail.noAddress')}</p>
        </div>

        <div className="af-cd__header-stats">
          <div>
            <span className="af-cd__stat-value">{residents.length}</span>
            <span className="af-cd__stat-label">{t('communityDetail.stats.residents')}</span>
          </div>
          <div>
            <span className={`af-cd__badge af-cd__badge--${detail.status.toLowerCase()}`}>
              {STATUS_KEYS[detail.status] ? t(STATUS_KEYS[detail.status]) : detail.status}
            </span>
            <span className="af-cd__stat-label">{t('communityDetail.stats.status')}</span>
          </div>
          <div className="af-cd__admin-block">
            <Avatar avatarKey={detail.avatarKey} name={detail.adminFullName} className="af-cd__admin-avatar" />
            <span className="af-cd__admin-block-text">
              <strong>{detail.adminFullName}</strong>
              <span>{detail.adminEmail}</span>
              {detail.adminPhone && <span>{detail.adminPhone}</span>}
            </span>
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
            {t('communityDetail.actions.reactivate')}
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
                t('communityDetail.blockDialog.title'),
                t('communityDetail.blockDialog.message', { name: detail.adminFullName }),
                t('communityDetail.blockDialog.confirm'),
                'outline',
              )
            }
          >
            {t('communityDetail.actions.block')}
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
                t('communityDetail.terminateDialog.title'),
                t('communityDetail.terminateDialog.message', { name: detail.adminFullName }),
                t('communityDetail.terminateDialog.confirm'),
                'warn',
              )
            }
          >
            {t('communityDetail.actions.terminate')}
          </button>
        )}
        <button type="button" className="af-cd__action af-cd__action--danger" disabled={actionBusy} onClick={openDeleteConfirm}>
          {t('communityDetail.actions.delete')}
        </button>
      </div>

      <div className="af-cd__table-wrap">
        <div className="af-cd__table-header">
          <h3>{t('communityDetail.residents.title')}</h3>
          <span>{t('communityDetail.residents.registeredCount', { count: residents.length })}</span>
        </div>

        {residents.length === 0 ? (
          <p className="af-cd__empty">{t('communityDetail.residents.empty')}</p>
        ) : (
          <table className="af-cd__table">
            <thead>
              <tr>
                <th className="af-cd__avatar-col"></th>
                <th>{t('communityDetail.residents.table.flatUnit')}</th>
                <th>{t('communityDetail.residents.table.meter')}</th>
                <th>{t('communityDetail.residents.table.resident')}</th>
                <th>{t('communityDetail.residents.table.email')}</th>
                <th>{t('communityDetail.residents.table.passwordSet')}</th>
              </tr>
            </thead>
            <tbody>
              {pagedResidents.map((r) => (
                <tr key={r.id}>
                  <td className="af-cd__avatar-col">
                    <Avatar avatarKey={r.avatarKey} name={r.fullName} className="af-cd__avatar" />
                  </td>
                  <td>{r.houseNumber || '—'}</td>
                  <td>{r.meterNumber || '—'}</td>
                  <td>{r.fullName}</td>
                  <td>{r.email}</td>
                  <td>{r.passwordSet ? t('communityDetail.residents.yes') : t('communityDetail.residents.no')}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {residents.length > PAGE_SIZE && (
        <div className="af-cd__pagination">
          <button type="button" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
            {t('pagination.prev')}
          </button>
          <span>
            {t('pagination.page', { page: page + 1, pageCount })}
          </span>
          <button type="button" disabled={page >= pageCount - 1} onClick={() => setPage((p) => p + 1)}>
            {t('pagination.next')}
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
                {t('communityDetail.cancel')}
              </button>
              <button
                type="button"
                className={`af-cd__action af-cd__action--${confirmAction.tone}`}
                disabled={actionBusy}
                onClick={confirmStatusChange}
              >
                {actionBusy ? t('communityDetail.working') : confirmAction.confirmLabel}
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
            <h2>{t('communityDetail.deleteDialog.title')}</h2>
            <p>
              <Trans
                t={t}
                i18nKey="communityDetail.deleteDialog.message"
                values={{ name: detail.apartmentName }}
                components={{ strong: <strong /> }}
              />
            </p>
            <label htmlFor="deleteConfirmText">
              <Trans
                t={t}
                i18nKey="communityDetail.deleteDialog.typeToConfirm"
                values={{ word: DELETE_CONFIRM_WORD }}
                components={{ strong: <strong /> }}
              />
            </label>
            <input
              id="deleteConfirmText"
              type="text"
              autoComplete="off"
              autoFocus
              value={deleteConfirmText}
              onChange={(e) => setDeleteConfirmText(e.target.value)}
              placeholder={DELETE_CONFIRM_WORD}
            />
            <div className="af-cd__danger-actions">
              <button type="button" className="af-cd__action af-cd__action--outline" disabled={actionBusy} onClick={closeDeleteConfirm}>
                {t('communityDetail.cancel')}
              </button>
              <button
                type="button"
                className="af-cd__action af-cd__action--danger"
                disabled={actionBusy || deleteConfirmText.trim().toLowerCase() !== DELETE_CONFIRM_WORD}
                onClick={handleDelete}
              >
                {actionBusy ? t('communityDetail.deleting') : t('communityDetail.deleteDialog.confirm')}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  )
}

export default CommunityDetail
