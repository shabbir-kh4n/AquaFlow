import { useState } from 'react'
import { Trans, useTranslation } from 'react-i18next'
import { useAuth } from '../context/useAuth'
import './AuthForm.css'

const INITIAL_FORM = {
  fullName: '',
  email: '',
  password: '',
  confirmPassword: '',
  phone: '',
  communityName: '',
  agreedToTerms: false,
}

function SignUpForm({ onSuccess, onSwitchToSignIn }) {
  const { register } = useAuth()
  const { t } = useTranslation('common')

  const [form, setForm] = useState(INITIAL_FORM)
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  function handleChange(event) {
    const { name, value, type, checked } = event.target
    setForm((prev) => ({ ...prev, [name]: type === 'checkbox' ? checked : value }))
  }

  async function handleSubmit(event) {
    event.preventDefault()
    setError('')

    if (form.password !== form.confirmPassword) {
      setError(t('signUp.errors.passwordMismatch'))
      return
    }

    if (!form.communityName.trim()) {
      setError(t('signUp.errors.communityRequired'))
      return
    }

    if (!form.agreedToTerms) {
      setError(t('signUp.errors.termsRequired'))
      return
    }

    setSubmitting(true)

    try {
      const response = await register({
        fullName: form.fullName,
        email: form.email,
        password: form.password,
        role: 'ADMIN',
        phone: form.phone,
        communityName: form.communityName,
      })
      onSuccess?.(response)
    } catch (err) {
      const message = err.response?.data?.message || t('signUp.errors.fallback')
      setError(message)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form onSubmit={handleSubmit}>
      {error && <div className="af-form__error">{error}</div>}

      <p className="af-form__hint">{t('signUp.hint')}</p>

      <div className="af-form__field">
        <label htmlFor="fullName">{t('signUp.fullNameLabel')}</label>
        <input
          id="fullName"
          name="fullName"
          type="text"
          autoComplete="name"
          required
          value={form.fullName}
          onChange={handleChange}
        />
      </div>

      <div className="af-form__field">
        <label htmlFor="email">{t('signUp.emailLabel')}</label>
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
        <label htmlFor="phone">{t('signUp.phoneLabel')}</label>
        <input
          id="phone"
          name="phone"
          type="tel"
          autoComplete="tel"
          value={form.phone}
          onChange={handleChange}
        />
      </div>

      <div className="af-form__field">
        <label htmlFor="communityName">{t('signUp.communityNameLabel')}</label>
        <input
          id="communityName"
          name="communityName"
          type="text"
          required
          value={form.communityName}
          onChange={handleChange}
        />
      </div>

      <div className="af-form__row">
        <div className="af-form__field">
          <label htmlFor="password">{t('signUp.passwordLabel')}</label>
          <input
            id="password"
            name="password"
            type="password"
            autoComplete="new-password"
            minLength={8}
            required
            value={form.password}
            onChange={handleChange}
          />
        </div>
        <div className="af-form__field">
          <label htmlFor="confirmPassword">{t('signUp.confirmPasswordLabel')}</label>
          <input
            id="confirmPassword"
            name="confirmPassword"
            type="password"
            autoComplete="new-password"
            minLength={8}
            required
            value={form.confirmPassword}
            onChange={handleChange}
          />
        </div>
      </div>

      <label className="af-form__checkbox af-form__checkbox--terms" htmlFor="agreedToTerms">
        <input
          id="agreedToTerms"
          name="agreedToTerms"
          type="checkbox"
          checked={form.agreedToTerms}
          onChange={handleChange}
        />
        <span>
          <Trans
            t={t}
            i18nKey="signUp.agreeTerms"
            components={{ termsLink: <a href="#terms" />, privacyLink: <a href="#privacy" /> }}
          />
        </span>
      </label>

      <button type="submit" className="af-form__submit" disabled={submitting}>
        {submitting ? t('signUp.submitting') : t('signUp.submit')}
      </button>

      <p className="af-form__footer-text">
        {t('signUp.haveAccount')}{' '}
        <button type="button" className="af-form__link-button" onClick={onSwitchToSignIn}>
          {t('signUp.signInLink')}
        </button>
      </p>
    </form>
  )
}

export default SignUpForm
