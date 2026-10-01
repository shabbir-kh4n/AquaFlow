import { useId, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { changePassword } from '../api/accountApi'
import { forgotPassword } from '../api/authApi'
import { useAuth } from '../context/useAuth'
import './AuthForm.css'
import './ChangePasswordForm.css'

const MIN_LENGTH = 8
const EMPTY = { currentPassword: '', newPassword: '', confirmPassword: '' }

// Logged-in password change for any role (POST /api/auth/change-password). The current
// session's token stays valid afterwards.
function ChangePasswordForm() {
  const { t } = useTranslation('common')
  const id = useId()
  const { user } = useAuth()
  const [forgotSending, setForgotSending] = useState(false)
  const [forgotSent, setForgotSent] = useState(false)
  const [form, setForm] = useState(EMPTY)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState(false)
  // Collapsed behind a button until asked for, so the profile page doesn't show an always-open form.
  const [open, setOpen] = useState(false)

  function update(field) {
    return (event) => {
      setForm((prev) => ({ ...prev, [field]: event.target.value }))
      setError('')
      setSuccess(false)
    }
  }

  async function handleSubmit(event) {
    event.preventDefault()
    setSuccess(false)

    if (form.newPassword.length < MIN_LENGTH) {
      setError(t('changePassword.tooShort', { min: MIN_LENGTH }))
      return
    }
    if (form.newPassword !== form.confirmPassword) {
      setError(t('changePassword.mismatch'))
      return
    }
    if (form.newPassword === form.currentPassword) {
      setError(t('changePassword.sameAsCurrent'))
      return
    }

    setSaving(true)
    setError('')
    try {
      await changePassword({ currentPassword: form.currentPassword, newPassword: form.newPassword })
      setForm(EMPTY)
      setSuccess(true)
      setOpen(false)
    } catch (err) {
      setError(err.response?.data?.message || t('changePassword.error'))
    } finally {
      setSaving(false)
    }
  }

  // Emails a reset link to the signed-in account's own address (same public endpoint as the signed-out flow).
  async function handleForgot() {
    setError('')
    setForgotSending(true)
    try {
      await forgotPassword(user.email)
      setForgotSent(true)
    } catch {
      setError(t('changePassword.forgotError'))
    } finally {
      setForgotSending(false)
    }
  }

  function handleCancel() {
    setForgotSent(false)
    setForm(EMPTY)
    setError('')
    setOpen(false)
  }

  const canSubmit = form.currentPassword && form.newPassword && form.confirmPassword && !saving

  if (!open) {
    return (
      <div className="af-change-password">
        {success && (
          <div className="af-change-password__success" role="status">
            {t('changePassword.success')}
          </div>
        )}
        <button
          type="button"
          className="af-change-password__open"
          onClick={() => {
            setSuccess(false)
            setOpen(true)
          }}
        >
          {t('changePassword.open')}
        </button>
      </div>
    )
  }

  return (
    <form className="af-change-password" onSubmit={handleSubmit} noValidate>
      {error && <div className="af-form__error">{error}</div>}
      {success && (
        <div className="af-change-password__success" role="status">
          {t('changePassword.success')}
        </div>
      )}

      <div className="af-form__field">
        <label htmlFor={`${id}-current`}>{t('changePassword.current')}</label>
        <input
          id={`${id}-current`}
          type="password"
          autoComplete="current-password"
          value={form.currentPassword}
          onChange={update('currentPassword')}
        />
      </div>
      <div className="af-change-password__forgot">
        {forgotSent ? (
          <span className="af-change-password__hint">{t('changePassword.forgotSent', { email: user?.email })}</span>
        ) : (
          <button type="button" className="af-change-password__forgot-link" disabled={forgotSending} onClick={handleForgot}>
            {forgotSending ? t('changePassword.forgotSending') : t('changePassword.forgot')}
          </button>
        )}
      </div>
      <div className="af-form__field">
        <label htmlFor={`${id}-new`}>{t('changePassword.new')}</label>
        <input
          id={`${id}-new`}
          type="password"
          autoComplete="new-password"
          value={form.newPassword}
          onChange={update('newPassword')}
        />
        <span className="af-change-password__hint">{t('changePassword.hint', { min: MIN_LENGTH })}</span>
      </div>
      <div className="af-form__field">
        <label htmlFor={`${id}-confirm`}>{t('changePassword.confirm')}</label>
        <input
          id={`${id}-confirm`}
          type="password"
          autoComplete="new-password"
          value={form.confirmPassword}
          onChange={update('confirmPassword')}
        />
      </div>

      <div className="af-change-password__actions">
        <button type="button" className="af-change-password__cancel" disabled={saving} onClick={handleCancel}>
          {t('changePassword.cancel')}
        </button>
        <button type="submit" className="af-form__submit" disabled={!canSubmit}>
          {saving ? t('changePassword.saving') : t('changePassword.submit')}
        </button>
      </div>
    </form>
  )
}

export default ChangePasswordForm
