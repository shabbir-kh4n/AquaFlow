import Logo from './Logo'
import './AuthLayout.css'

function AuthLayout({ title, subtitle, width = 480, children, footer, cornerAction }) {
  return (
    <div className="af-auth">
      {cornerAction && <div className="af-auth__corner">{cornerAction}</div>}
      <div className="af-auth__card" style={{ width: `${width}px` }}>
        <div className="af-auth__icon">
          <Logo size={40} />
        </div>

        <h1 className="af-auth__title">{title}</h1>
        {subtitle && <p className="af-auth__subtitle">{subtitle}</p>}

        {children}

        {footer && <div className="af-auth__footer">{footer}</div>}
      </div>
    </div>
  )
}

export default AuthLayout
