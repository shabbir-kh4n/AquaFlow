import { Link } from 'react-router-dom'
import { useAuthModal } from '../context/useAuthModal'
import Logo from './Logo'
import './Navbar.css'

function Navbar() {
  const { openAuthModal } = useAuthModal()

  return (
    <header className="af-navbar">
      <div className="af-navbar__inner">
        <Link to="/" className="af-navbar__brand">
          <Logo size={30} />
          HydroLedger
        </Link>

        <nav className="af-navbar__links">
          <a href="#features">Features</a>
          <a href="#how-it-works">How it works</a>
          <a href="#contact">Contact</a>
        </nav>

        <div className="af-navbar__actions">
          <button type="button" className="af-navbar__signin" onClick={() => openAuthModal('signin')}>
            Sign In
          </button>
          <button type="button" className="af-btn af-btn--navy" onClick={() => openAuthModal('signup')}>
            Get Started
          </button>
        </div>
      </div>
    </header>
  )
}

export default Navbar
