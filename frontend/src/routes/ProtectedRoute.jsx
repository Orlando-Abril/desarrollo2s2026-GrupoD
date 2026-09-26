import { Navigate, Outlet } from 'react-router-dom'
import { useSession } from '../session/SessionContext.jsx'

export default function ProtectedRoute() {
  const { isAuthenticated } = useSession()
  return isAuthenticated ? <Outlet /> : <Navigate to="/ingresar" replace />
}
