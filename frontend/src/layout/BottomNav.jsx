import { NavLink } from 'react-router-dom'
import './BottomNav.css'

export default function BottomNav({ items }) {
  return (
    <nav className="bottom-nav" aria-label="Navegación móvil">
      {items.map((item) => item.habilitado ? (
        <NavLink key={item.id} to={item.ruta} className="bottom-nav__item">
          <span>{item.numero}</span>{item.label}
        </NavLink>
      ) : (
        <span key={item.id} className="bottom-nav__item bottom-nav__item--disabled" aria-disabled="true">
          <span>{item.numero}</span>{item.label}<small>Pronto</small>
        </span>
      ))}
    </nav>
  )
}
