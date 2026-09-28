import { useId, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { changePassword } from '../api/accountApi'
import './AuthForm.css'
import './ChangePasswordForm.css'

const MIN_LENGTH = 8
const EMPTY = { currentPassword: '', newPassword: '', confirmPassword: '' }

// Logged-in password change for any role (POST /api/auth/change-password). The current
// session's token stays valid afterwards.
function ChangePasswordForm() {
  const { t } = useTranslation('common')
  const id = useId()
  const [form, setForm] = useState(EMPTY)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState(false)

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
    } catch (err) {
      setError(err.response?.data?.message || t('changePassword.error'))
    } finally {
      setSaving(false)
    }
  }

  const canSubmit = form.currentPassword && form.newPassword && form.confirmPassword && !saving

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
        <button type="submit" className="af-form__submit" disabled={!canSubmit}>
          {saving ? t('changePassword.saving') : t('changePassword.submit')}
        </button>
      </div>
    </form>
  )
}

export default ChangePasswordForm
