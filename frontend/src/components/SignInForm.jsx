import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../context/useAuth'
import './AuthForm.css'

function SignInForm({ onSuccess, onSwitchToSignUp }) {
  const { login } = useAuth()
  const { t } = useTranslation('common')

  const [form, setForm] = useState({ email: '', password: '', remember: false })
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

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
        <a href="#forgot-password" className="af-form__forgot">
          {t('signIn.forgotPassword')}
        </a>
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
