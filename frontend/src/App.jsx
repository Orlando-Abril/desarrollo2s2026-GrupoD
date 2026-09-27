import { Navigate, Route, Routes } from 'react-router-dom'
import AppLayout from './layout/AppLayout.jsx'
import AlbumPage from './pages/AlbumPage.jsx'
import LoginPage from './pages/LoginPage.jsx'
import NotFound from './pages/NotFound.jsx'
import RegisterPage from './pages/RegisterPage.jsx'
import ProtectedRoute from './routes/ProtectedRoute.jsx'
import PublicOnlyRoute from './routes/PublicOnlyRoute.jsx'
import { useSession } from './session/SessionContext.jsx'

function RootRoute() {
  const { isAuthenticated } = useSession()
  return <Navigate to={isAuthenticated ? '/album' : '/ingresar'} replace />
}

export default function App() {
  return (
    <Routes>
      <Route element={<AppLayout />}>
        <Route path="/" element={<RootRoute />} />
        <Route element={<PublicOnlyRoute />}>
          <Route path="/ingresar" element={<LoginPage />} />
          <Route path="/registro" element={<RegisterPage />} />
        </Route>
        <Route element={<ProtectedRoute />}>
          <Route path="/album" element={<AlbumPage />} />
        </Route>
        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  )
}
