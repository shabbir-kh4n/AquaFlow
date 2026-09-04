import { useState } from 'react'
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
      setError('Passwords do not match.')
      return
    }

    if (!form.communityName.trim()) {
      setError('Community name is required.')
      return
    }

    if (!form.agreedToTerms) {
      setError('Please agree to the Terms of Service and Privacy Policy.')
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
      const message = err.response?.data?.message || 'Unable to create account. Please try again.'
      setError(message)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form onSubmit={handleSubmit}>
      {error && <div className="af-form__error">{error}</div>}

      <p className="af-form__hint">
        Only community admins register here. Once you're in, you'll add your residents'
        details yourself — they don't sign up on their own.
      </p>

      <div className="af-form__field">
        <label htmlFor="fullName">Full name</label>
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
        <label htmlFor="email">Email</label>
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
        <label htmlFor="phone">Phone number</label>
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
        <label htmlFor="communityName">Community name</label>
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
          <label htmlFor="password">Password</label>
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
          <label htmlFor="confirmPassword">Confirm password</label>
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
          I agree to the <a href="#terms">Terms of Service</a> and{' '}
          <a href="#privacy">Privacy Policy</a>
        </span>
      </label>

      <button type="submit" className="af-form__submit" disabled={submitting}>
        {submitting ? 'Creating account…' : 'Create Admin Account'}
      </button>

      <p className="af-form__footer-text">
        Already have an account?{' '}
        <button type="button" className="af-form__link-button" onClick={onSwitchToSignIn}>
          Sign in
        </button>
      </p>
    </form>
  )
}

export default SignUpForm
