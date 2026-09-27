import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import AuthLayout from '../components/AuthLayout'
import LanguageSwitcher from '../components/LanguageSwitcher'
import { getSetPasswordInfo, setPassword as setPasswordRequest } from '../api/authApi'
import { useAuth } from '../context/useAuth'
import '../components/AuthForm.css'

function SetPassword() {
  const { token } = useParams()
  const navigate = useNavigate()
  const { setSession } = useAuth()
  const { t } = useTranslation('common')

  const [info, setInfo] = useState(null)
  // Holds the backend's message (or null) so the English-only fallback is translated at render time.
  const [loadError, setLoadError] = useState(null)
  const [loading, setLoading] = useState(true)

  const [password, setPasswordValue] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [formError, setFormError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    getSetPasswordInfo(token)
      .then(setInfo)
      .catch((err) => {
        setLoadError({ message: err.response?.data?.message || null })
      })
      .finally(() => setLoading(false))
  }, [token])

  async function handleSubmit(event) {
    event.preventDefault()
    setFormError('')

    if (password.length < 8) {
      setFormError(t('setPassword.errors.tooShort'))
      return
    }
    if (password !== confirmPassword) {
      setFormError(t('setPassword.errors.mismatch'))
      return
    }

    setSubmitting(true)
    try {
      const response = await setPasswordRequest(token, password)
      setSession(response)
      navigate('/resident/home')
    } catch (err) {
      setFormError(err.response?.data?.message || t('setPassword.errors.fallback'))
    } finally {
      setSubmitting(false)
    }
  }

  if (loading) {
    return (
      <AuthLayout title={t('setPassword.title')} width={480} cornerAction={<LanguageSwitcher />}>
        <p className="af-form__hint">{t('setPassword.loading')}</p>
      </AuthLayout>
    )
  }

  if (loadError) {
    return (
      <AuthLayout title={t('setPassword.title')} width={480} cornerAction={<LanguageSwitcher />}>
        <div className="af-form__error">{loadError.message || t('setPassword.errors.invalidLink')}</div>
      </AuthLayout>
    )
  }

  return (
    <AuthLayout
      title={t('setPassword.title')}
      subtitle={t('setPassword.subtitle', { name: info.fullName, email: info.email })}
      width={480}
      cornerAction={<LanguageSwitcher />}
    >
      <form onSubmit={handleSubmit}>
        {formError && <div className="af-form__error">{formError}</div>}

        <div className="af-form__field">
          <label htmlFor="password">{t('setPassword.passwordLabel')}</label>
          <input
            id="password"
            type="password"
            autoComplete="new-password"
            required
            value={password}
            onChange={(e) => setPasswordValue(e.target.value)}
          />
        </div>

        <div className="af-form__field">
          <label htmlFor="confirmPassword">{t('setPassword.confirmPasswordLabel')}</label>
          <input
            id="confirmPassword"
            type="password"
            autoComplete="new-password"
            required
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
          />
        </div>

        <button type="submit" className="af-form__submit" disabled={submitting}>
          {submitting ? t('setPassword.submitting') : t('setPassword.submit')}
        </button>
      </form>
    </AuthLayout>
  )
}

export default SetPassword
