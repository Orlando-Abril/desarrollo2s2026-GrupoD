import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { configureHttpClient } from '../api/httpClient.js'

const SessionContext = createContext(null)

export function SessionProvider({ children }) {
  const [session, setSession] = useState(null)
  const sessionRef = useRef(session)
  const navigate = useNavigate()

  useEffect(() => {
    sessionRef.current = session
  }, [session])

  const login = useCallback((nextSession) => setSession({
    token: nextSession.token,
    tokenType: nextSession.tokenType,
    username: nextSession.username,
  }), [])

  const logout = useCallback(() => setSession(null), [])

  useEffect(() => configureHttpClient({
    getToken: () => sessionRef.current?.token ?? null,
    onUnauthorized: () => {
      setSession(null)
      navigate('/ingresar', {
        replace: true,
        state: { info: 'Tu sesión venció. Volvé a ingresar.' },
      })
    },
  }), [navigate])

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
