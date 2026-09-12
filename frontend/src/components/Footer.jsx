import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import Logo from './Logo'
import './Footer.css'

function Footer() {
  const { t } = useTranslation('landing')
  const columns = t('footer.columns', { returnObjects: true })

  return (
    <footer className="af-footer af-dark-gradient">
      <div className="af-footer__top">
        <div className="af-footer__brand-col">
          <Link to="/" className="af-footer__brand">
            <Logo size={28} />
            HydroLedger
          </Link>
          <p>{t('footer.tagline')}</p>
          <div className="af-footer__socials">
            <a href="#" aria-label="Facebook">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
                <path d="M22 12a10 10 0 1 0-11.6 9.87v-6.98H7.9V12h2.5V9.8c0-2.46 1.47-3.82 3.72-3.82 1.08 0 2.2.19 2.2.19v2.43h-1.24c-1.22 0-1.6.76-1.6 1.54V12h2.73l-.44 2.89h-2.29v6.98A10 10 0 0 0 22 12z" />
              </svg>
            </a>
            <a href="#" aria-label="Twitter">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
                <path d="M22 5.9c-.74.33-1.53.55-2.36.65a4.1 4.1 0 0 0 1.8-2.27c-.8.47-1.68.81-2.62 1a4.1 4.1 0 0 0-7 3.74A11.65 11.65 0 0 1 3.16 4.6a4.1 4.1 0 0 0 1.27 5.47 4.07 4.07 0 0 1-1.86-.51 4.1 4.1 0 0 0 3.29 4.07 4.1 4.1 0 0 1-1.85.07 4.1 4.1 0 0 0 3.83 2.85A8.24 8.24 0 0 1 2 18.57a11.6 11.6 0 0 0 6.29 1.84c7.55 0 11.68-6.26 11.68-11.7 0-.18 0-.35-.01-.53A8.35 8.35 0 0 0 22 5.9z" />
              </svg>
            </a>
            <a href="#" aria-label="LinkedIn">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
                <path d="M19 3A2 2 0 0 1 21 5v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h14zM8.34 18v-8h-2.7v8h2.7zM7 8.72a1.56 1.56 0 1 0 0-3.12 1.56 1.56 0 0 0 0 3.12zM19 18v-4.4c0-2.36-1.26-3.46-2.94-3.46a2.54 2.54 0 0 0-2.3 1.26h-.04v-1.08h-2.68c.04.86 0 8 0 8h2.68v-4.47c0-.24.02-.47.09-.65.2-.47.63-.96 1.37-.96.96 0 1.34.73 1.34 1.8V18H19z" />
              </svg>
            </a>
          </div>
        </div>

        {columns.map((col) => (
          <div className="af-footer__col" key={col.title}>
            <h4>{col.title}</h4>
            <ul>
              {col.links.map((l) => (
                <li key={l}>
                  <a href="#">{l}</a>
                </li>
              ))}
            </ul>
          </div>
        ))}

        <div className="af-footer__col">
          <h4>{t('footer.corporateOfficeTitle')}</h4>
          <p className="af-footer__address">
            HydroLedger Systems Inc.
            <br />
            100 Marine Way, Suite 400
            <br />
            Seattle, WA 98101
          </p>
          <a href="mailto:support@hydroledger.com" className="af-footer__email">
            support@hydroledger.com
          </a>
        </div>
      </div>

      <div className="af-footer__bottom">
        <span>{t('footer.copyright')}</span>
        <div className="af-footer__legal">
          <a href="#">{t('footer.legalPrivacy')}</a>
          <a href="#">{t('footer.legalTerms')}</a>
        </div>
      </div>
    </footer>
  )
}

export default Footer
