import { Outlet } from 'react-router-dom'
import { useSession } from '../session/SessionContext.jsx'
import BottomNav from './BottomNav.jsx'
import Header from './Header.jsx'
import './AppLayout.css'

export const NAV_ITEMS = [
  { id: 'album', label: 'Álbum', numero: '01', ruta: '/album', habilitado: true },
  { id: 'ranking', label: 'Ranking', numero: '02', ruta: null, habilitado: false },
  { id: 'mercado', label: 'Mercado', numero: '03', ruta: null, habilitado: false },
  { id: 'portfolio', label: 'Mi portfolio', numero: '04', ruta: null, habilitado: false },
]

export default function AppLayout() {
  const { isAuthenticated } = useSession()
  return (
    <div className="app-shell">
      <Header items={NAV_ITEMS} />
      <main className="app-main"><Outlet /></main>
      {isAuthenticated ? <BottomNav items={NAV_ITEMS} /> : null}
    </div>
  )
}
