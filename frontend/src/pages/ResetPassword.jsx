import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import AuthLayout from '../components/AuthLayout'
import LanguageSwitcher from '../components/LanguageSwitcher'
import { resetPassword } from '../api/authApi'
import '../components/AuthForm.css'
import './ResetPassword.css'

// Landing page for the emailed "forgot password" link (/reset-password/:token).
function ResetPassword() {
  const { token } = useParams()
  const { t } = useTranslation('common')

  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [formError, setFormError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [done, setDone] = useState(false)

  async function handleSubmit(event) {
    event.preventDefault()
    setFormError('')

    if (password.length < 8) {
      setFormError(t('resetPassword.errors.tooShort'))
      return
    }
    if (password !== confirmPassword) {
      setFormError(t('resetPassword.errors.mismatch'))
      return
    }

    setSubmitting(true)
    try {
      await resetPassword(token, password)
      setDone(true)
    } catch (err) {
      setFormError(err.response?.data?.message || t('resetPassword.errors.fallback'))
    } finally {
      setSubmitting(false)
    }
  }

  if (done) {
    return (
      <AuthLayout title={t('resetPassword.title')} width={480} cornerAction={<LanguageSwitcher />}>
        <p className="af-form__hint">{t('resetPassword.success')}</p>
        <Link to="/" className="af-form__submit af-reset__home">
          {t('resetPassword.backToSignIn')}
        </Link>
      </AuthLayout>
    )
  }

  return (
    <AuthLayout
      title={t('resetPassword.title')}
      subtitle={t('resetPassword.subtitle')}
      width={480}
      cornerAction={<LanguageSwitcher />}
    >
      <form onSubmit={handleSubmit}>
        {formError && <div className="af-form__error">{formError}</div>}

        <div className="af-form__field">
          <label htmlFor="resetPassword">{t('resetPassword.passwordLabel')}</label>
          <input
            id="resetPassword"
            type="password"
            autoComplete="new-password"
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </div>

        <div className="af-form__field">
          <label htmlFor="resetConfirmPassword">{t('resetPassword.confirmPasswordLabel')}</label>
          <input
            id="resetConfirmPassword"
            type="password"
            autoComplete="new-password"
            required
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
          />
        </div>

        <button type="submit" className="af-form__submit" disabled={submitting}>
          {submitting ? t('resetPassword.submitting') : t('resetPassword.submit')}
        </button>
      </form>
    </AuthLayout>
  )
}

export default ResetPassword
