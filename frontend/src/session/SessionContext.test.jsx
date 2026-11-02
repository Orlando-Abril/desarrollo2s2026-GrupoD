import { useEffect } from 'react'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes, useNavigate } from 'react-router-dom'
import { request } from '../api/httpClient.js'
import ProtectedRoute from '../routes/ProtectedRoute.jsx'
import { SessionProvider, useSession } from './SessionContext.jsx'

const refreshSessionMock = vi.fn()
const logoutMock = vi.fn()

vi.mock('../api/authApi.js', () => ({
  refreshSession: (...args) => refreshSessionMock(...args),
  logout: (...args) => logoutMock(...args),
}))

function LoginButton() {
  const { login } = useSession()
  const navigate = useNavigate()
  const handleClick = () => {
    login({ token: 'jwt', tokenType: 'Bearer', username: 'abril' })
    navigate('/privada')
  }
  return <button type="button" onClick={handleClick}>Ingresar</button>
}

function RequestOnMount() {
  useEffect(() => {
    request('/players').catch(() => {})
  }, [])
  return <p>Privada</p>
}

function SessionInfo() {
  const { username, sessionId, logout } = useSession()
  return (
    <>
      <p>Usuario: {username ?? 'nadie'}</p>
      <p>Sesión: {sessionId ?? 'ninguna'}</p>
      <button type="button" onClick={logout}>Salir</button>
    </>
  )
}

function renderApp(initialPath) {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <SessionProvider>
        <Routes>
          <Route path="/ingresar" element={<><p>Pantalla de ingreso</p><LoginButton /></>} />
          <Route element={<ProtectedRoute />}>
            <Route path="/privada" element={<><RequestOnMount /><SessionInfo /></>} />
          </Route>
        </Routes>
      </SessionProvider>
    </MemoryRouter>,
  )
}

function ok(body) {
  return { ok: true, status: 200, text: async () => JSON.stringify(body) }
}

function unauthorized() {
  return { ok: false, status: 401, text: async () => '' }
}

describe('SessionProvider', () => {
  beforeEach(() => {
    refreshSessionMock.mockReset().mockRejectedValue(new Error('sin cookie'))
    logoutMock.mockReset().mockResolvedValue(null)
  })

  afterEach(() => {
    cleanup()
    vi.unstubAllGlobals()
  })

  it('envía el token en el primer request de la página a la que navega el login', async () => {
    const fetchMock = vi.fn().mockResolvedValue(ok([]))
    vi.stubGlobal('fetch', fetchMock)
    renderApp('/ingresar')

    fireEvent.click(await screen.findByRole('button', { name: 'Ingresar' }))

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1))
    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBe('Bearer jwt')
  })

  it('al recargar una página protegida recupera la sesión con la cookie en vez de mandar al login', async () => {
    refreshSessionMock.mockResolvedValue({ token: 'jwt-renovado', tokenType: 'Bearer', username: 'abril' })
    const fetchMock = vi.fn().mockResolvedValue(ok([]))
    vi.stubGlobal('fetch', fetchMock)

    renderApp('/privada')

    expect(await screen.findByText('Usuario: abril')).toBeTruthy()
    expect(screen.queryByText('Pantalla de ingreso')).toBeNull()
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1))
    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBe('Bearer jwt-renovado')
    expect(refreshSessionMock).toHaveBeenCalledTimes(1)
  })

  it('sin cookie válida, al recargar una página protegida manda al login', async () => {
    vi.stubGlobal('fetch', vi.fn())

    renderApp('/privada')

    expect(await screen.findByText('Pantalla de ingreso')).toBeTruthy()
  })

  it('si el JWT vence a mitad de uso lo renueva una vez y repite el request sin cerrar la sesión', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(unauthorized())
      .mockResolvedValueOnce(ok([]))
    vi.stubGlobal('fetch', fetchMock)
    renderApp('/ingresar')
    fireEvent.click(await screen.findByRole('button', { name: 'Ingresar' }))
    refreshSessionMock.mockResolvedValue({ token: 'jwt-nuevo', tokenType: 'Bearer', username: 'abril' })

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2))
    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBe('Bearer jwt')
    expect(fetchMock.mock.calls[1][1].headers.Authorization).toBe('Bearer jwt-nuevo')
    expect(screen.getByText('Usuario: abril')).toBeTruthy()
    expect(screen.getByText('Sesión: abril#1')).toBeTruthy()
  })

  it('si la renovación falla cierra la sesión y vuelve al login', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(unauthorized()))
    renderApp('/ingresar')

    fireEvent.click(await screen.findByRole('button', { name: 'Ingresar' }))

    expect(await screen.findByText('Pantalla de ingreso')).toBeTruthy()
  })

  it('dos 401 a la vez comparten un solo canje de la cookie', async () => {
    let resolveRefresh
    const fetchMock = vi.fn().mockResolvedValue(ok([]))
    vi.stubGlobal('fetch', fetchMock)
    renderApp('/ingresar')
    fireEvent.click(await screen.findByRole('button', { name: 'Ingresar' }))
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1))
    refreshSessionMock.mockReset().mockReturnValue(new Promise((resolve) => { resolveRefresh = resolve }))
    fetchMock.mockReset()
      .mockResolvedValueOnce(unauthorized())
      .mockResolvedValueOnce(unauthorized())
      .mockResolvedValue(ok([]))

    const first = request('/a')
    const second = request('/b')
    await waitFor(() => expect(refreshSessionMock).toHaveBeenCalledTimes(1))
    resolveRefresh({ token: 'jwt-nuevo', tokenType: 'Bearer', username: 'abril' })
    await Promise.all([first, second])

    expect(refreshSessionMock).toHaveBeenCalledTimes(1)
    expect(fetchMock).toHaveBeenCalledTimes(4)
  })

  it('cerrar sesión avisa al back para que invalide la cookie', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(ok([])))
    renderApp('/ingresar')
    fireEvent.click(await screen.findByRole('button', { name: 'Ingresar' }))

    fireEvent.click(await screen.findByRole('button', { name: 'Salir' }))

    expect(logoutMock).toHaveBeenCalledTimes(1)
    expect(await screen.findByText('Pantalla de ingreso')).toBeTruthy()
  })
})
