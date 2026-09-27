import { Navigate, Outlet } from 'react-router-dom'
import { useSession } from '../session/SessionContext.jsx'

export default function PublicOnlyRoute() {
  const { isAuthenticated } = useSession()
  return isAuthenticated ? <Navigate to="/album" replace /> : <Outlet />
}
