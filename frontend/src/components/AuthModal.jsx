import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useAuthModal } from '../context/useAuthModal'
import Logo from './Logo'
import SignInForm from './SignInForm'
import SignUpForm from './SignUpForm'
import './AuthModal.css'

function AuthModal() {
  const { isOpen, mode, setMode, closeAuthModal } = useAuthModal()
  const navigate = useNavigate()
  const { t } = useTranslation('common')

  if (!isOpen) return null

  function handleSuccess(response) {
    closeAuthModal()
    if (mode === 'signup') {
      navigate('/admin/dashboard')
    } else if (response.role === 'RESIDENT') {
      navigate('/resident/home')
    } else if (response.role === 'SYSTEM_ADMIN') {
      navigate('/platform/dashboard')
    } else {
      navigate('/admin/dashboard')
    }
  }

  const isSignUp = mode === 'signup'

  return (
    <div className="af-auth-modal__backdrop" onClick={closeAuthModal}>
      <div className="af-auth-modal" onClick={(e) => e.stopPropagation()}>
        <button type="button" className="af-auth-modal__close" onClick={closeAuthModal} aria-label={t('authModal.close')}>
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M18 6 6 18" />
            <path d="m6 6 12 12" />
          </svg>
        </button>

        <div className="af-auth-modal__icon">
          <Logo size={40} />
        </div>

        <h2 className="af-auth-modal__title">{isSignUp ? t('authModal.signUpTitle') : t('authModal.signInTitle')}</h2>
        <p className="af-auth-modal__subtitle">
          {isSignUp
            ? t('authModal.signUpSubtitle')
            : t('authModal.signInSubtitle')}
        </p>

        {isSignUp ? (
          <SignUpForm onSuccess={handleSuccess} onSwitchToSignIn={() => setMode('signin')} />
        ) : (
          <SignInForm onSuccess={handleSuccess} onSwitchToSignUp={() => setMode('signup')} />
        )}
      </div>
    </div>
  )
}

export default AuthModal
