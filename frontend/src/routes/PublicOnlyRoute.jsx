import { Navigate, Outlet } from 'react-router-dom'
import { useSession } from '../session/SessionContext.jsx'

// Mientras se intenta recuperar la sesión (F5) se muestra igual el login/registro: si la cookie
// sirve, apenas termina el canje se redirige al álbum.
export default function PublicOnlyRoute() {
  const { isAuthenticated } = useSession()
  return isAuthenticated ? <Navigate to="/album" replace /> : <Outlet />
}
