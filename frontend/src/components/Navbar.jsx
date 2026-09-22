import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useAuthModal } from '../context/useAuthModal'
import Logo from './Logo'
import LanguageSwitcher from './LanguageSwitcher'
import './Navbar.css'

function Navbar() {
  const { openAuthModal } = useAuthModal()
  const { t } = useTranslation('landing')

  return (
    <header className="af-navbar">
      <div className="af-navbar__inner">
        <Link to="/" className="af-navbar__brand">
          <Logo size={30} />
          <span>HydroLedger</span>
        </Link>

        <nav className="af-navbar__links">
          <a href="#features">{t('navbar.linkFeatures')}</a>
          <a href="#how-it-works">{t('navbar.linkHowItWorks')}</a>
          <a href="#contact">{t('navbar.linkContact')}</a>
        </nav>

        <div className="af-navbar__actions">
          <LanguageSwitcher />
          <button type="button" className="af-navbar__signin" onClick={() => openAuthModal('signin')}>
            {t('navbar.signIn')}
          </button>
          <button type="button" className="af-btn af-btn--navy" onClick={() => openAuthModal('signup')}>
            {t('navbar.getStarted')}
          </button>
        </div>
      </div>
    </header>
  )
}

export default Navbar
