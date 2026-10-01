import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { forgotPassword } from '../api/authApi'
import { useAuth } from '../context/useAuth'
import './AuthForm.css'

function SignInForm({ onSuccess, onSwitchToSignUp }) {
  const { login } = useAuth()
  const { t } = useTranslation('common')

  const [form, setForm] = useState({ email: '', password: '', remember: false })
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  // 'forgot' swaps the form for an email-only reset request; 'sent' confirms it.
  const [mode, setMode] = useState('signin')

  function handleChange(event) {
    const { name, value, type, checked } = event.target
    setForm((prev) => ({ ...prev, [name]: type === 'checkbox' ? checked : value }))
  }

  async function handleSubmit(event) {
    event.preventDefault()
    setError('')
    setSubmitting(true)

    try {
      const response = await login({ email: form.email, password: form.password })
      onSuccess?.(response)
    } catch (err) {
      const message = err.response?.data?.message || t('signIn.errorFallback')
      setError(message)
    } finally {
      setSubmitting(false)
    }
  }

  async function handleForgotSubmit(event) {
    event.preventDefault()
    setError('')
    setSubmitting(true)
    try {
      await forgotPassword(form.email.trim())
      setMode('sent')
    } catch (err) {
      setError(err.response?.data?.message || t('signIn.forgotError'))
    } finally {
      setSubmitting(false)
    }
  }

  function backToSignIn() {
    setError('')
    setMode('signin')
  }

  if (mode === 'sent') {
    return (
      <div>
        <p className="af-form__hint">{t('signIn.forgotSent', { email: form.email.trim() })}</p>
        <button type="button" className="af-form__submit" onClick={backToSignIn}>
          {t('signIn.backToSignIn')}
        </button>
      </div>
    )
  }

  if (mode === 'forgot') {
    return (
      <form onSubmit={handleForgotSubmit}>
        <p className="af-form__hint">{t('signIn.forgotIntro')}</p>
        {error && <div className="af-form__error">{error}</div>}

        <div className="af-form__field">
          <label htmlFor="forgotEmail">{t('signIn.emailLabel')}</label>
          <input
            id="forgotEmail"
            name="email"
            type="email"
            autoComplete="email"
            required
            autoFocus
            value={form.email}
            onChange={handleChange}
          />
        </div>

        <button type="submit" className="af-form__submit" disabled={submitting}>
          {submitting ? t('signIn.sendingLink') : t('signIn.sendLink')}
        </button>

        <p className="af-form__footer-text">
          <button type="button" className="af-form__link-button" onClick={backToSignIn}>
            {t('signIn.backToSignIn')}
          </button>
        </p>
      </form>
    )
  }

  return (
    <form onSubmit={handleSubmit}>
      {error && <div className="af-form__error">{error}</div>}

      <div className="af-form__field">
        <label htmlFor="email">{t('signIn.emailLabel')}</label>
        <input
          id="email"
          name="email"
          type="email"
          autoComplete="email"
          required
          value={form.email}
          onChange={handleChange}
        />
      </div>

      <div className="af-form__field">
        <label htmlFor="password">{t('signIn.passwordLabel')}</label>
        <input
          id="password"
          name="password"
          type="password"
          autoComplete="current-password"
          required
          value={form.password}
          onChange={handleChange}
        />
      </div>

      <div className="af-form__checkbox-row">
        <label className="af-form__checkbox" htmlFor="remember">
          <input
            id="remember"
            name="remember"
            type="checkbox"
            checked={form.remember}
            onChange={handleChange}
          />
          {t('signIn.rememberMe')}
        </label>
        <button
          type="button"
          className="af-form__link-button af-form__forgot"
          onClick={() => {
            setError('')
            setMode('forgot')
          }}
        >
          {t('signIn.forgotPassword')}
        </button>
      </div>

      <button type="submit" className="af-form__submit" disabled={submitting}>
        {submitting ? t('signIn.submitting') : t('signIn.submit')}
      </button>

      <p className="af-form__footer-text">
        {t('signIn.noAccount')}{' '}
        <button type="button" className="af-form__link-button" onClick={onSwitchToSignUp}>
          {t('signIn.signUpLink')}
        </button>
      </p>
    </form>
  )
}

export default SignInForm
