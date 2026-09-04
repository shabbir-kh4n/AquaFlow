import { useState } from 'react'
import { useAuth } from '../context/useAuth'
import './AuthForm.css'

function SignInForm({ onSuccess, onSwitchToSignUp }) {
  const { login } = useAuth()

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
      const message = err.response?.data?.message || 'Unable to sign in. Please try again.'
      setError(message)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form onSubmit={handleSubmit}>
      {error && <div className="af-form__error">{error}</div>}

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
        <label htmlFor="password">Password</label>
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
          Remember me
        </label>
        <a href="#forgot-password" className="af-form__forgot">
          Forgot password?
        </a>
      </div>

      <button type="submit" className="af-form__submit" disabled={submitting}>
        {submitting ? 'Signing in…' : 'Sign In'}
      </button>

      <p className="af-form__footer-text">
        Don&apos;t have an account?{' '}
        <button type="button" className="af-form__link-button" onClick={onSwitchToSignUp}>
          Sign up
        </button>
      </p>
    </form>
  )
}

export default SignInForm
