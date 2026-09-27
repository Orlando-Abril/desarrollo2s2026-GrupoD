import { useCallback, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import Toast from '../components/Toast.jsx'
import Casillero from '../components/Casillero.jsx'
import './AlbumPage.css'

export default function AlbumPage() {
  const location = useLocation()
  const navigate = useNavigate()
  const [toast, setToast] = useState(location.state?.toast ?? null)
  const dismissToast = useCallback(() => {
    setToast(null)
    navigate(location.pathname, { replace: true, state: null })
  }, [location.pathname, navigate])

  return (
    <div className="page-content album-page">
      <header className="page-heading">
        <h1>El álbum</h1>
        <p>Tu colección de LaFigu</p>
      </header>
      <Casillero>Las figuritas llegan en la próxima feature</Casillero>
      {toast ? <Toast onDismiss={dismissToast}>{toast}</Toast> : null}
    </div>
  )
}
