import { useEffect } from 'react'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes, useNavigate } from 'react-router-dom'
import { request } from '../api/httpClient.js'
import { SessionProvider, useSession } from './SessionContext.jsx'

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

describe('SessionProvider', () => {
  afterEach(() => {
    cleanup()
    vi.unstubAllGlobals()
  })

  it('envía el token en el primer request de la página a la que navega el login', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, status: 200, text: async () => '[]' })
    vi.stubGlobal('fetch', fetchMock)
    render(
      <MemoryRouter initialEntries={['/ingresar']}>
        <SessionProvider>
          <Routes>
            <Route path="/ingresar" element={<LoginButton />} />
            <Route path="/privada" element={<RequestOnMount />} />
          </Routes>
        </SessionProvider>
      </MemoryRouter>,
    )

    fireEvent.click(screen.getByRole('button', { name: 'Ingresar' }))

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1))
    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBe('Bearer jwt')
  })
})
