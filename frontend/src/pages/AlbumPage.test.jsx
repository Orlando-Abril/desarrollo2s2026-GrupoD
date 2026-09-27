import { useEffect } from 'react'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import AlbumPage from './AlbumPage.jsx'
import { SessionProvider, useSession } from '../session/SessionContext.jsx'

function player(id, overrides = {}) {
  return {
    id,
    externalId: String(1000 + id),
    fullName: `Jugador ${String(id).padStart(4, '0')}`,
    team: id % 2 ? 'Écija FC' : 'Arsenal FC',
    league: id % 2 ? 'LA_LIGA' : 'PREMIER_LEAGUE',
    positions: ['FORWARD'],
    nationality: 'Argentina',
    age: 20 + (id % 15),
    marketValue: id,
    ...overrides,
  }
}

function response(status, body) {
  return {
    ok: status >= 200 && status < 300,
    status,
    text: vi.fn().mockResolvedValue(body === undefined ? '' : JSON.stringify(body)),
  }
}

function AutoLogin({ children }) {
  const { login } = useSession()
  useEffect(() => {
    login({ token: 'jwt', tokenType: 'Bearer', username: 'abril' })
  }, [login])
  return children
}

function LoginDestination() {
  const location = useLocation()
  return <><span>{location.pathname}</span><p>{location.state?.info}</p></>
}

function renderAlbum() {
  return render(
    <MemoryRouter initialEntries={['/album']}>
      <SessionProvider>
        <AutoLogin>
          <Routes>
            <Route path="/album" element={<AlbumPage />} />
            <Route path="/ingresar" element={<LoginDestination />} />
          </Routes>
        </AutoLogin>
      </SessionProvider>
    </MemoryRouter>,
  )
}

describe('AlbumPage', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_API_BASE_URL', 'http://localhost:8080')
  })

  afterEach(() => {
    cleanup()
    vi.unstubAllEnvs()
    vi.restoreAllMocks()
  })

  it('shows eight loading slots with busy semantics and no pager', async () => {
    vi.stubGlobal('fetch', vi.fn(() => new Promise(() => {})))
    renderAlbum()
    const busy = await screen.findByLabelText('Cargando figuritas…')
    expect(busy.getAttribute('aria-busy')).toBe('true')
    expect(screen.getAllByText('Cargando figuritas…')).toHaveLength(8)
    expect(screen.queryByLabelText('Paginación')).toBeNull()
  })

  it('loads, sorts and paginates the full catalog while keeping the initial total', async () => {
    const players = Array.from({ length: 25 }, (_, index) => player(25 - index))
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response(200, players)))
    const scrollIntoView = vi.fn()
    Element.prototype.scrollIntoView = scrollIntoView
    renderAlbum()

    expect(await screen.findByText('25 jugadores · 5 ligas')).toBeTruthy()
    const cards = screen.getAllByRole('article')
    expect(cards).toHaveLength(24)
    expect(cards[0].getAttribute('aria-label')).toContain('Jugador 0001')
    expect(screen.getByText('Mostrando 1–24 de 25')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'Página siguiente' }))
    expect(await screen.findByText('Mostrando 25–25 de 25')).toBeTruthy()
    expect(screen.getAllByRole('article')).toHaveLength(1)
    expect(screen.getByRole('button', { name: 'Página 2' }).getAttribute('aria-current')).toBe('page')
    expect(scrollIntoView).toHaveBeenCalled()
  })

  it('makes the first segment of about 2600 players available in under two seconds', async () => {
    const players = Array.from({ length: 2600 }, (_, index) => player(index + 1))
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response(200, players)))
    const started = performance.now()
    renderAlbum()
    await screen.findByText('2.600 jugadores · 5 ligas')
    expect(screen.getAllByRole('article')).toHaveLength(24)
    expect(performance.now() - started).toBeLessThan(2000)
  })

  it('searches locally without accents or an additional request', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response(200, [
      player(1, { fullName: 'Ángel Di María' }),
      player(2, { fullName: 'Lionel Messi' }),
    ])))
    renderAlbum()
    await screen.findByText('2 jugadores · 5 ligas')
    fireEvent.change(screen.getByLabelText('Buscar por nombre'), { target: { value: 'ANGEL' } })
    expect(await screen.findByRole('article', { name: /Ángel Di María/ })).toBeTruthy()
    expect(screen.queryByRole('article', { name: /Lionel Messi/ })).toBeNull()
    expect(fetch).toHaveBeenCalledTimes(1)
  })

  it('derives unique teams and resets team and page when league changes', async () => {
    const initial = Array.from({ length: 25 }, (_, index) => player(index + 1))
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response(200, initial)))
    renderAlbum()
    await screen.findByText('25 jugadores · 5 ligas')
    expect(within(screen.getByLabelText('Equipo')).getAllByRole('option').map((option) => option.textContent)).toEqual([
      'Todos', 'Arsenal FC', 'Écija FC',
    ])
    fireEvent.click(screen.getByRole('button', { name: 'Página 2' }))
    fireEvent.change(screen.getByLabelText('Equipo'), { target: { value: 'Écija FC' } })
    await waitFor(() => expect(fetch.mock.calls.at(-1)[0]).toContain('team=%C3%89cija+FC'))
    fireEvent.change(screen.getByLabelText('Liga'), { target: { value: 'LA_LIGA' } })
    expect(screen.getByLabelText('Equipo').value).toBe('')
    await waitFor(() => expect(screen.getByRole('button', { name: 'Página 1' }).getAttribute('aria-current')).toBe('page'))
  })

  it('aborts a superseded request so stale data cannot replace the latest response', async () => {
    let call = 0
    const pending = []
    vi.stubGlobal('fetch', vi.fn((url, options) => {
      call += 1
      if (call === 1) return Promise.resolve(response(200, [player(1)]))
      return new Promise((resolve, reject) => {
        options.signal.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')))
        pending.push({ url, signal: options.signal, resolve })
      })
    }))
    renderAlbum()
    await screen.findByText('1 jugadores · 5 ligas')
    fireEvent.change(screen.getByLabelText('Liga'), { target: { value: 'LA_LIGA' } })
    await waitFor(() => expect(pending).toHaveLength(1))
    fireEvent.change(screen.getByLabelText('Liga'), { target: { value: 'BUNDESLIGA' } })
    await waitFor(() => expect(pending).toHaveLength(2))
    expect(pending[0].signal.aborted).toBe(true)
    pending[1].resolve(response(200, [player(3, { fullName: 'Último vigente', league: 'BUNDESLIGA' })]))
    expect(await screen.findByRole('article', { name: /Último vigente/ })).toBeTruthy()
  })

  it('clears all filters from the no-results state', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response(200, [player(1, { fullName: 'Lionel Messi' })])))
    renderAlbum()
    await screen.findByText('1 jugadores · 5 ligas')
    fireEvent.change(screen.getByLabelText('Buscar por nombre'), { target: { value: 'nadie' } })
    expect(await screen.findByText('No hay figuritas con esos filtros')).toBeTruthy()
    expect(screen.getByText('0 resultados')).toBeTruthy()
    const statePanel = screen.getByText('No hay figuritas con esos filtros').closest('section')
    fireEvent.click(within(statePanel).getByRole('button', { name: 'Limpiar filtros' }))
    expect(await screen.findByRole('article', { name: /Lionel Messi/ })).toBeTruthy()
    expect(screen.getByLabelText('Buscar por nombre').value).toBe('')
  })

  it('shows the empty catalog state without an action', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response(200, [])))
    renderAlbum()
    expect(await screen.findByText('Todavía no hay figuritas')).toBeTruthy()
    expect(screen.getByText('Álbum vacío')).toBeTruthy()
    const statePanel = screen.getByText('Todavía no hay figuritas').closest('section')
    expect(within(statePanel).queryByRole('button')).toBeNull()
  })

  it.each([400, 503])('shows a recoverable error for HTTP %s and retries', async (status) => {
    vi.stubGlobal('fetch', vi.fn()
      .mockResolvedValueOnce(response(status, { error: 'unexpected_error', message: 'falló' }))
      .mockResolvedValueOnce(response(200, [player(1)])))
    renderAlbum()
    expect(await screen.findByText('No pudimos abrir el álbum')).toBeTruthy()
    expect(screen.getByText('Sin conexión')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'Reintentar' }))
    expect(await screen.findByRole('article')).toBeTruthy()
    expect(fetch).toHaveBeenCalledTimes(2)
  })

  it('shows a recoverable error for a network failure', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('offline')))
    renderAlbum()
    expect(await screen.findByText('No pudimos abrir el álbum')).toBeTruthy()
  })

  it('shows the same recoverable state for a timeout abort', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new DOMException('Timed out', 'AbortError')))
    renderAlbum()
    expect(await screen.findByText('No pudimos abrir el álbum')).toBeTruthy()
  })

  it('retries with the current remote filters', async () => {
    vi.stubGlobal('fetch', vi.fn()
      .mockResolvedValueOnce(response(200, [player(1)]))
      .mockResolvedValueOnce(response(503, { error: 'unexpected_error', message: 'falló' }))
      .mockResolvedValueOnce(response(200, [player(2, { league: 'LA_LIGA' })])))
    renderAlbum()
    await screen.findByRole('article')
    fireEvent.change(screen.getByLabelText('Liga'), { target: { value: 'LA_LIGA' } })
    expect(await screen.findByText('No pudimos abrir el álbum')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'Reintentar' }))
    await screen.findByRole('article')
    expect(fetch.mock.calls.at(-1)[0]).toContain('league=LA_LIGA')
  })

  it('delegates a protected 401 to the shared session flow', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response(401, { error: 'unauthorized', message: 'expired' })))
    renderAlbum()
    expect(await screen.findByText('/ingresar')).toBeTruthy()
    expect(screen.getByText('Tu sesión venció. Volvé a ingresar.')).toBeTruthy()
  })

  it('toggles between cards and the exact non-interactive table without changing results', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response(200, [player(7, {
      fullName: 'Julián Álvarez', positions: ['FORWARD', 'MIDFIELDER'], nationality: null, age: null,
    }), player(8, { positions: [] })])))
    renderAlbum()
    await screen.findByRole('article', { name: /Julián Álvarez/ })
    expect(screen.getByRole('button', { name: '▦ Cartas' }).getAttribute('aria-pressed')).toBe('true')
    fireEvent.click(screen.getByRole('button', { name: '☰ Lista' }))
    expect(screen.getByRole('button', { name: '☰ Lista' }).getAttribute('aria-pressed')).toBe('true')
    expect(screen.getAllByRole('columnheader').map((cell) => cell.textContent)).toEqual([
      'N°', 'Jugador', 'Equipo', 'Liga', 'Posición', 'Nacionalidad', 'Edad', 'Valor',
    ])
    const table = screen.getByRole('table')
    expect(within(table).getByText('Delantero')).toBeTruthy()
    expect(within(table).getByText('Mediocampista')).toBeTruthy()
    expect(screen.getAllByText('—').length).toBeGreaterThanOrEqual(3)
    const rows = screen.getAllByRole('row')
    expect(rows[1].getAttribute('tabindex')).toBeNull()
    expect(within(rows[1]).queryByRole('link')).toBeNull()
  })

  it('uses native accessible buttons and keeps cards non-interactive', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response(200, Array.from({ length: 25 }, (_, index) => player(index + 1)))))
    renderAlbum()
    const card = await screen.findByRole('article', { name: /Jugador 0001/ })
    expect(card.getAttribute('tabindex')).toBeNull()
    expect(within(card).queryByRole('link')).toBeNull()
    expect(screen.getByText('Mostrando 1–24 de 25').getAttribute('aria-live')).toBe('polite')
    expect(screen.getByRole('button', { name: 'Página 1' }).getAttribute('aria-current')).toBe('page')
  })
})
