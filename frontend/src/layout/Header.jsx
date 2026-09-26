import { Link, NavLink, useLocation, useNavigate } from 'react-router-dom'
import { useSession } from '../session/SessionContext.jsx'
import './Header.css'

function Logo() {
  return (
    <Link className="brand" to="/" aria-label="LaFigu, Mercado de Tokens">
      <span className="brand__sticker">10</span>
      <span className="brand__copy">
        <strong>LaFigu</strong>
        <small>Mercado de tokens</small>
      </span>
    </Link>
  )
}

function Navigation({ items }) {
  return (
    <nav className="header-nav" aria-label="Navegación principal">
      {items.map((item) => item.habilitado ? (
        <NavLink key={item.id} to={item.ruta} className="nav-item">
          <span className="nav-item__number">{item.numero}</span>{item.label}
        </NavLink>
      ) : (
        <span key={item.id} className="nav-item nav-item--disabled" aria-disabled="true">
          <span className="nav-item__number">{item.numero}</span>{item.label}<small>Pronto</small>
        </span>
      ))}
    </nav>
  )
}

export default function Header({ items }) {
  const location = useLocation()
  const navigate = useNavigate()
  const { isAuthenticated, username, logout } = useSession()
  const trimmed = username?.trim() ?? ''
  const initial = trimmed.charAt(0).toUpperCase() || '—'

  const handleLogout = () => {
    logout()
    navigate('/ingresar', { replace: true })
  }

  return (
    <header className="site-header">
      <div className="site-header__inner">
        <Logo />
        {isAuthenticated ? <Navigation items={items} /> : null}
        <div className="site-header__actions">
          {isAuthenticated ? (
            <>
              <div className="user-chip">
                <span className="user-chip__avatar" aria-hidden="true">{initial}</span>
                <span className="user-chip__name">{username}</span>
              </div>
              <button className="header-action" type="button" onClick={handleLogout}>Salir</button>
            </>
          ) : (
            <Link className="header-action" to={location.pathname === '/registro' ? '/ingresar' : '/registro'}>
              {location.pathname === '/registro' ? 'Ingresar' : 'Crear cuenta'}
            </Link>
          )}
        </div>
      </div>
    </header>
  )
}
