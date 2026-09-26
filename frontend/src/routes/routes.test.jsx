import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes, useLocation, useNavigationType } from 'react-router-dom'
import App from '../App.jsx'
import ProtectedRoute from './ProtectedRoute.jsx'
import PublicOnlyRoute from './PublicOnlyRoute.jsx'

let authenticated = false
vi.mock('../session/SessionContext.jsx', () => ({
  useSession: () => ({ isAuthenticated: authenticated, username: 'abril', login: vi.fn(), logout: vi.fn() }),
}))

function LocationProbe() {
  const location = useLocation()
  const navigationType = useNavigationType()
  return <span data-testid="location" data-navigation={navigationType}>{location.pathname}</span>
}

function renderRoutes(initialPath) {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <LocationProbe />
      <Routes>
        <Route element={<ProtectedRoute />}>
          <Route path="/album" element={<div>album</div>} />
        </Route>
        <Route element={<PublicOnlyRoute />}>
          <Route path="/ingresar" element={<div>login</div>} />
          <Route path="/registro" element={<div>register</div>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  )
}

function renderApp(initialPath) {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <LocationProbe />
      <App />
    </MemoryRouter>,
  )
}

afterEach(cleanup)

describe('route guards', () => {
  it('redirects unauthenticated album access to login', () => {
    authenticated = false
    renderRoutes('/album')
    expect(screen.getByTestId('location').textContent).toBe('/ingresar')
  })

  it('redirects authenticated public-only access to album', () => {
    authenticated = true
    renderRoutes('/ingresar')
    expect(screen.getByTestId('location').textContent).toBe('/album')
  })

  it.each([
    [false, '/', '/ingresar'],
    [true, '/', '/album'],
    [false, '/album', '/ingresar'],
    [true, '/registro', '/album'],
    [false, '/desconocida', '/ingresar'],
    [true, '/desconocida', '/album'],
  ])('resolves session=%s path=%s to %s', (session, initial, expected) => {
    authenticated = session
    renderApp(initial)
    expect(screen.getByTestId('location').textContent).toBe(expected)
    expect(screen.getByTestId('location').dataset.navigation).toBe('REPLACE')
  })
})
