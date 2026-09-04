import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import AuthLayout from '../components/AuthLayout'
import { getSetPasswordInfo, setPassword as setPasswordRequest } from '../api/authApi'
import { useAuth } from '../context/useAuth'
import '../components/AuthForm.css'

function SetPassword() {
  const { token } = useParams()
  const navigate = useNavigate()
  const { setSession } = useAuth()

  const [info, setInfo] = useState(null)
  const [loadError, setLoadError] = useState('')
  const [loading, setLoading] = useState(true)

  const [password, setPasswordValue] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [formError, setFormError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    getSetPasswordInfo(token)
      .then(setInfo)
      .catch((err) => {
        setLoadError(err.response?.data?.message || 'This link is invalid or has expired.')
      })
      .finally(() => setLoading(false))
  }, [token])

  async function handleSubmit(event) {
    event.preventDefault()
    setFormError('')

    if (password.length < 8) {
      setFormError('Password must be at least 8 characters.')
      return
    }
    if (password !== confirmPassword) {
      setFormError('Passwords do not match.')
      return
    }

    setSubmitting(true)
    try {
      const response = await setPasswordRequest(token, password)
      setSession(response)
      navigate('/resident/home')
    } catch (err) {
      setFormError(err.response?.data?.message || 'Could not set your password. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  if (loading) {
    return (
      <AuthLayout title="Set your password" width={480}>
        <p className="af-form__hint">Loading…</p>
      </AuthLayout>
    )
  }

  if (loadError) {
    return (
      <AuthLayout title="Set your password" width={480}>
        <div className="af-form__error">{loadError}</div>
      </AuthLayout>
    )
  }

  return (
    <AuthLayout
      title="Set your password"
      subtitle={`Hi ${info.fullName}, choose a password for ${info.email} to activate your HydroLedger account.`}
      width={480}
    >
      <form onSubmit={handleSubmit}>
        {formError && <div className="af-form__error">{formError}</div>}

        <div className="af-form__field">
          <label htmlFor="password">Password</label>
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
          <label htmlFor="confirmPassword">Confirm password</label>
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
          {submitting ? 'Setting password…' : 'Set password & sign in'}
        </button>
      </form>
    </AuthLayout>
  )
}

export default SetPassword
