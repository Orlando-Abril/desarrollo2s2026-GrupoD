import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import App from '../App.jsx'
import { ApiError } from '../api/httpClient.js'
import { SessionProvider } from '../session/SessionContext.jsx'

const loginMock = vi.fn()

vi.mock('../api/authApi.js', () => ({
  login: (...args) => loginMock(...args),
  register: vi.fn(),
}))

function renderApp(path = '/ingresar') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <SessionProvider><App /></SessionProvider>
    </MemoryRouter>,
  )
}

function fillLogin() {
  fireEvent.change(screen.getByLabelText('Usuario'), { target: { value: 'abril' } })
  fireEvent.change(screen.getByLabelText('Contraseña'), { target: { value: 'secreto123' } })
}

describe('LoginPage', () => {
  let localSpy
  let sessionSpy

  beforeEach(() => {
    loginMock.mockReset()
    localSpy = vi.spyOn(window.localStorage, 'setItem')
    sessionSpy = vi.spyOn(window.sessionStorage, 'setItem')
  })

  afterEach(() => {
    cleanup()
    vi.restoreAllMocks()
  })

  it('opens the album with an in-memory session', async () => {
    loginMock.mockResolvedValue({ token: 'jwt', tokenType: 'Bearer' })
    renderApp()
    fillLogin()
    fireEvent.click(screen.getByRole('button', { name: 'Abrir mi álbum' }))

    expect(await screen.findByRole('heading', { name: 'El álbum' })).toBeTruthy()
    expect(screen.getByText('abril')).toBeTruthy()
    expect(localSpy).not.toHaveBeenCalled()
    expect(sessionSpy).not.toHaveBeenCalled()
  })

  it('shows invalid credentials', async () => {
    loginMock.mockRejectedValue(new ApiError(401, 'invalid_credentials', 'bad'))
    renderApp()
    fillLogin()
    fireEvent.submit(screen.getByRole('button', { name: 'Abrir mi álbum' }).closest('form'))

    expect((await screen.findByRole('alert')).textContent).toBe('✕ Usuario o contraseña incorrectos.')
  })

  it('locks submission while opening', async () => {
    loginMock.mockReturnValue(new Promise(() => {}))
    renderApp()
    fillLogin()
    const button = screen.getByRole('button', { name: 'Abrir mi álbum' })
    fireEvent.click(button)
    fireEvent.click(button)

    await waitFor(() => expect(screen.getByRole('button', { name: 'Abriendo…' }).disabled).toBe(true))
    expect(loginMock).toHaveBeenCalledTimes(1)
  })

  it('logs out and a remount has no session', async () => {
    loginMock.mockResolvedValue({ token: 'jwt', tokenType: 'Bearer' })
    const mounted = renderApp()
    fillLogin()
    fireEvent.click(screen.getByRole('button', { name: 'Abrir mi álbum' }))
    await screen.findByRole('heading', { name: 'El álbum' })
    fireEvent.click(screen.getByRole('button', { name: 'Salir' }))
    expect(await screen.findByRole('heading', { name: 'Ingresar' })).toBeTruthy()

    mounted.unmount()
    renderApp('/album')
    expect(await screen.findByRole('heading', { name: 'Ingresar' })).toBeTruthy()
  })
})
