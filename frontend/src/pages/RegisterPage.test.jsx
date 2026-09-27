import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import App from '../App.jsx'
import { ApiError } from '../api/httpClient.js'
import { SessionProvider } from '../session/SessionContext.jsx'

const registerMock = vi.fn()
const loginMock = vi.fn()

vi.mock('../api/authApi.js', () => ({
  register: (...args) => registerMock(...args),
  login: (...args) => loginMock(...args),
}))

function renderRegister() {
  return render(
    <MemoryRouter initialEntries={['/registro']}>
      <SessionProvider><App /></SessionProvider>
    </MemoryRouter>,
  )
}

function fill({ username = 'abril', email = 'abril@example.com', password = 'secreto123' } = {}) {
  fireEvent.change(screen.getByLabelText('Usuario'), { target: { value: username } })
  fireEvent.change(screen.getByLabelText('Email'), { target: { value: email } })
  fireEvent.change(screen.getByLabelText('Contraseña'), { target: { value: password } })
}

describe('RegisterPage', () => {
  beforeEach(() => {
    registerMock.mockReset()
    loginMock.mockReset()
  })

  afterEach(() => {
    cleanup()
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  it('validates required fields, email, password, focus and per-field clearing', async () => {
    renderRegister()
    fireEvent.submit(screen.getByRole('button', { name: 'Crear mi carnet' }).closest('form'))
    expect(await screen.findAllByText('Obligatorio.')).toHaveLength(3)
    expect(document.activeElement).toBe(screen.getByLabelText('Usuario'))
    expect(registerMock).not.toHaveBeenCalled()

    fireEvent.change(screen.getByLabelText('Usuario'), { target: { value: 'abril' } })
    expect(screen.getAllByText('Obligatorio.')).toHaveLength(2)
    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'abril@' } })
    fireEvent.change(screen.getByLabelText('Contraseña'), { target: { value: '1234567' } })
    fireEvent.submit(screen.getByRole('button', { name: 'Crear mi carnet' }).closest('form'))
    expect(await screen.findByText('Ingresá un email válido.')).toBeTruthy()
    expect(screen.getByText('Mínimo 8 caracteres.')).toBeTruthy()
  })

  it.each(['abril@example.com', 'a+b@example.com'])('accepts native-valid email %s', async (email) => {
    registerMock.mockRejectedValue(new ApiError(400, 'validation_error', 'backend stop'))
    renderRegister()
    fill({ email })
    fireEvent.click(screen.getByRole('button', { name: 'Crear mi carnet' }))
    await waitFor(() => expect(registerMock).toHaveBeenCalled())
  })

  it.each(['abril', 'abril@', '@example.com', 'abril example.com'])('rejects native-invalid email %s', async (email) => {
    renderRegister()
    fill({ email })
    fireEvent.submit(screen.getByRole('button', { name: 'Crear mi carnet' }).closest('form'))
    expect(await screen.findByText('Ingresá un email válido.')).toBeTruthy()
    expect(registerMock).not.toHaveBeenCalled()
  })

  it('maps duplicate registration', async () => {
    registerMock.mockRejectedValue(new ApiError(409, 'duplicate_user', 'duplicate'))
    renderRegister()
    fill()
    fireEvent.click(screen.getByRole('button', { name: 'Crear mi carnet' }))
    expect((await screen.findByRole('alert')).textContent).toBe('✕ El usuario o el email ya está registrado.')
  })

  it('registers, logs in sequentially, stores nothing and never renders apiKey', async () => {
    const nativeSetTimeout = globalThis.setTimeout
    let dismissToast
    const timeoutSpy = vi.spyOn(globalThis, 'setTimeout').mockImplementation((callback, delay, ...args) => {
      if (delay === 4000) dismissToast = callback
      return nativeSetTimeout(callback, delay, ...args)
    })
    const localSpy = vi.spyOn(window.localStorage, 'setItem')
    const sessionSpy = vi.spyOn(window.sessionStorage, 'setItem')
    registerMock.mockResolvedValue({ id: 1, username: 'abril', email: 'abril@example.com', balance: 1000, apiKey: 'never-show' })
    loginMock.mockResolvedValue({ token: 'jwt', tokenType: 'Bearer' })
    renderRegister()
    fill()
    fireEvent.click(screen.getByRole('button', { name: 'Crear mi carnet' }))

    expect(await screen.findByRole('heading', { name: 'El álbum' })).toBeTruthy()
    expect(await screen.findByRole('status')).toBeTruthy()
    expect(screen.getByRole('status').textContent).toBe('¡Bienvenida/o, abril! Tu álbum ya está abierto.')
    expect(timeoutSpy).toHaveBeenCalledWith(expect.any(Function), 4000)
    expect(document.body.textContent).not.toContain('never-show')
    expect(registerMock.mock.invocationCallOrder[0]).toBeLessThan(loginMock.mock.invocationCallOrder[0])
    expect(localSpy).not.toHaveBeenCalled()
    expect(sessionSpy).not.toHaveBeenCalled()
    act(() => dismissToast())
    expect(screen.queryByRole('status')).toBeNull()
  })

  it('falls back to prefilling login after automatic login fails', async () => {
    registerMock.mockResolvedValue({ id: 1, username: 'abril', email: 'abril@example.com', balance: 1000 })
    loginMock.mockRejectedValue(new ApiError(401, 'invalid_credentials', 'bad'))
    renderRegister()
    fill()
    fireEvent.click(screen.getByRole('button', { name: 'Crear mi carnet' }))

    expect(await screen.findByRole('heading', { name: 'Ingresar' })).toBeTruthy()
    expect(screen.getByLabelText('Usuario').value).toBe('abril')
    expect(screen.getByText('Cuenta creada. Ingresá con tu contraseña.')).toBeTruthy()
    expect(screen.getByLabelText('Contraseña').value).toBe('')
  })
})
