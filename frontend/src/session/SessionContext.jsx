import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { configureHttpClient } from '../api/httpClient.js'
import { logout as logoutRequest, refreshSession } from '../api/authApi.js'

const SessionContext = createContext(null)

// El JWT vive solo en memoria (no en localStorage), así un script inyectado no lo puede leer.
// Para no perder la sesión al recargar, el back deja una cookie HttpOnly con un token de renovación:
// al abrir la página se canjea por un JWT nuevo. Lo mismo pasa cuando el JWT vence a mitad de uso.
export function SessionProvider({ children }) {
  const [session, setSession] = useState(null)
  const [restoring, setRestoring] = useState(true)
  const sessionRef = useRef(session)
  const navigate = useNavigate()
  const navigateRef = useRef(navigate)
  const renewalRef = useRef(null)
  const generationRef = useRef(0)

  useEffect(() => {
    navigateRef.current = navigate
  }, [navigate])

  // El ref se actualiza junto con el estado y no en un efecto: los efectos de la página destino
  // corren antes que los de este provider, y su primer request necesita el token ya disponible.
  const updateSession = useCallback((nextSession) => {
    sessionRef.current = nextSession
    setSession(nextSession)
  }, [])

  // sessionId cambia al entrar o salir, pero no cuando solo se renueva el JWT: lo que depende de
  // "quién está logueado" (por ejemplo, la estrategia elegida) no se reinicia en cada renovación.
  const startSession = useCallback((nextSession) => {
    generationRef.current += 1
    updateSession({
      token: nextSession.token,
      tokenType: nextSession.tokenType,
      username: nextSession.username,
      sessionId: `${nextSession.username}#${generationRef.current}`,
    })
  }, [updateSession])

  // Un solo canje a la vez: la cookie se rota en cada uso, así que dos canjes simultáneos
  // harían que el segundo llegue con una cookie ya usada.
  const renew = useCallback(() => {
    if (!renewalRef.current) {
      renewalRef.current = refreshSession()
        .then((result) => {
          const current = sessionRef.current
          if (current && current.username === result.username) {
            updateSession({ ...current, token: result.token, tokenType: result.tokenType })
          } else {
            startSession(result)
          }
          return result.token
        })
        .catch(() => null)
        .finally(() => {
          renewalRef.current = null
        })
    }
    return renewalRef.current
  }, [startSession, updateSession])

  useEffect(() => {
    let active = true
    renew().finally(() => {
      if (active) setRestoring(false)
    })
    return () => {
      active = false
    }
  }, [renew])

  const login = useCallback((nextSession) => startSession(nextSession), [startSession])

  const logout = useCallback(() => {
    updateSession(null)
    logoutRequest().catch(() => {})
  }, [updateSession])

  // Se registra una sola vez: navigate cambia con cada navegación y re-registrar dejaría al cliente
  // sin token justo cuando la página nueva hace su primer request.
  useEffect(() => configureHttpClient({
    getToken: () => sessionRef.current?.token ?? null,
    refreshSession: renew,
    onUnauthorized: () => {
      updateSession(null)
      void navigateRef.current('/ingresar', {
        replace: true,
        state: { info: 'Tu sesión venció. Volvé a ingresar.' },
      })
    },
  }), [renew, updateSession])

  const value = useMemo(() => ({
    token: session?.token ?? null,
    tokenType: session?.tokenType ?? null,
    username: session?.username ?? null,
    sessionId: session?.sessionId ?? null,
    login,
    logout,
    isAuthenticated: Boolean(session?.token),
    isRestoring: restoring,
  }), [login, logout, restoring, session])

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>
}

export function useSession() {
  const context = useContext(SessionContext)
  if (!context) throw new Error('useSession must be used within SessionProvider')
  return context
}
