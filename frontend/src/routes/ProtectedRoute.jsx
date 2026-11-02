import { Navigate, Outlet } from 'react-router-dom'
import { useSession } from '../session/SessionContext.jsx'

export default function ProtectedRoute() {
  const { isAuthenticated, isRestoring } = useSession()
  // Mientras se intenta recuperar la sesión con la cookie (F5), no se redirige a ningún lado.
  if (isRestoring) return null
  return isAuthenticated ? <Outlet /> : <Navigate to="/ingresar" replace />
}
