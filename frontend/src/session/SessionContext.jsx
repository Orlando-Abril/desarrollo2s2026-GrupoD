import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { configureHttpClient } from '../api/httpClient.js'

const SessionContext = createContext(null)

export function SessionProvider({ children }) {
  const [session, setSession] = useState(null)
  const sessionRef = useRef(session)
  const navigate = useNavigate()
  const navigateRef = useRef(navigate)

  useEffect(() => {
    navigateRef.current = navigate
  }, [navigate])

  // El ref se actualiza junto con el estado y no en un efecto: los efectos de la página destino
  // corren antes que los de este provider, y su primer request necesita el token ya disponible.
  const updateSession = useCallback((nextSession) => {
    sessionRef.current = nextSession
    setSession(nextSession)
  }, [])

  const login = useCallback((nextSession) => updateSession({
    token: nextSession.token,
    tokenType: nextSession.tokenType,
    username: nextSession.username,
  }), [updateSession])

  const logout = useCallback(() => updateSession(null), [updateSession])

  // Se registra una sola vez: navigate cambia con cada navegación y re-registrar dejaría al cliente
  // sin token justo cuando la página nueva hace su primer request.
  useEffect(() => configureHttpClient({
    getToken: () => sessionRef.current?.token ?? null,
    onUnauthorized: () => {
      updateSession(null)
      navigateRef.current('/ingresar', {
        replace: true,
        state: { info: 'Tu sesión venció. Volvé a ingresar.' },
      })
    },
  }), [updateSession])

  const value = useMemo(() => ({
    token: session?.token ?? null,
    tokenType: session?.tokenType ?? null,
    username: session?.username ?? null,
    login,
    logout,
    isAuthenticated: Boolean(session?.token),
  }), [login, logout, session])

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>
}

export function useSession() {
  const context = useContext(SessionContext)
  if (!context) throw new Error('useSession must be used within SessionProvider')
  return context
}
