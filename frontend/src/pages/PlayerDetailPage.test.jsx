import { useEffect } from 'react'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { Link, MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import PlayerDetailPage from './PlayerDetailPage.jsx'
import { SessionProvider, useSession } from '../session/SessionContext.jsx'
import { StrategyViewProvider, createStrategyViewStore } from '../context/StrategyViewContext.jsx'
import { marketMomentum, pendingMomentum } from '../test/strategyFixtures.js'
import { strategyStore } from '../test/strategyStore.js'

// jsdom mide 0×0 y no tiene ResizeObserver: se fija el tamaño del gráfico (research R2).
vi.mock('recharts', async (importOriginal) => {
  const { cloneElement } = await import('react')
  return {
    ...(await importOriginal()),
    ResponsiveContainer: ({ children }) => <div style={{ width: 800, height: 300 }}>{cloneElement(children, { width: 800, height: 300 })}</div>,
  }
})

const NOW = new Date('2026-10-27T03:00:00Z')
const plain = (text) => text.replace(/\s/g, ' ')

const STATS = {
  minutesPlayed: 1980,
  goals: 12,
  assists: 9,
  shots: 61,
  keyPasses: 48,
  tackles: 21,
  dribbles: 37,
  yellowCards: 4,
  redCards: 0,
  rating: 7.42,
  fetchedAt: '2026-10-20T12:00:00Z',
}

function quote(calculatedAt, value, overrides = {}) {
  return { value, score: 8.75, status: 'CALCULATED', strategy: 'OFFICIAL', version: 1, calculatedAt, ...overrides }
}

function detail(id = 42, overrides = {}) {
  return {
    player: {
      id,
      externalId: '3180',
      fullName: id === 42 ? 'Bukayo Saka' : 'Martin Ødegaard',
      team: 'Arsenal FC',
      league: 'PREMIER_LEAGUE',
      positions: ['FORWARD'],
      nationality: 'England',
      age: 25,
      marketValue: 1.245,
    },
    stats: STATS,
    quote: quote('2026-10-20T18:00:00Z', 1.245),
    ...overrides,
  }
}

const MOMENTUM_HISTORY = [quote('2026-10-05T18:00:00Z', 1.5, { strategy: 'MOMENTUM' }), quote('2026-10-19T18:00:00Z', 1.7, { strategy: 'MOMENTUM', version: 2 })]
const OFFICIAL_HISTORY = [quote('2026-10-01T18:00:00Z', 1.1), quote('2026-10-08T18:00:00Z', 1.145), quote('2026-10-20T18:00:00Z', 1.245)]

function response(status, body) {
  return {
    ok: status >= 200 && status < 300,
    status,
    text: vi.fn().mockResolvedValue(body === undefined ? '' : JSON.stringify(body)),
  }
}

const emptyBook = (url) => [200, { playerId: Number(url.searchParams.get('playerId')), marketValue: 1.245, sells: [], buys: [] }]

// Responde según la ruta pedida; cada handler recibe la URL parseada y devuelve [status, body].
// El libro de la consola de trading (spec 029) responde vacío salvo que el test pase otro handler.
function routeFetch(handler, bookHandler = emptyBook) {
  const fetchMock = vi.fn((url, options) => {
    const parsed = new URL(url)
    const result = parsed.pathname === '/orders/book' ? bookHandler(parsed) : handler(parsed, options)
    if (result instanceof Promise) return result
    return Promise.resolve(response(...result))
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

function defaultHandler({ pathname }) {
  const [, , id, quotes] = pathname.split('/')
  if (quotes === 'quotes') return [200, OFFICIAL_HISTORY]
  return [200, detail(Number(id))]
}

function historyFor(url) {
  return url.searchParams.get('strategy') === 'MOMENTUM' ? MOMENTUM_HISTORY : OFFICIAL_HISTORY
}

// La ficha tiene también la tabla del libro: la del gráfico se identifica por su caption.
const CHART_TABLE = { name: /cotizaciones/ }

function chartRows() {
  const table = screen.getByRole('table', CHART_TABLE)
  return within(table).getAllByRole('row').slice(1).map((row) => within(row).getAllByRole('cell').map((cell) => plain(cell.textContent)))
}

function quoteCalls(fetchMock) {
  return fetchMock.mock.calls
    .map(([url]) => new URL(url))
    .filter((url) => url.pathname.endsWith('/quotes'))
    .map((url) => Object.fromEntries(url.searchParams))
}

function requestedStrategy(fetchMock, strategy) {
  return quoteCalls(fetchMock).some((query) => query.strategy === strategy)
}

function AutoLogin({ children }) {
  const { login } = useSession()
  useEffect(() => {
    login({ token: 'jwt', tokenType: 'Bearer', username: 'abril' })
  }, [login])
  return children
}

function LocationProbe() {
  return <span data-testid="location">{useLocation().pathname}</span>
}

function renderDetail(path = '/jugadores/42', store = createStrategyViewStore()) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <SessionProvider>
        <StrategyViewProvider store={store}>
        <AutoLogin>
          <LocationProbe />
          <Link to="/jugadores/43">Ir al 43</Link>
          <Routes>
            <Route path="/jugadores/:id" element={<PlayerDetailPage />} />
            <Route path="/album" element={<p>Álbum</p>} />
            <Route path="/ingresar" element={<p>Ingreso</p>} />
          </Routes>
        </AutoLogin>
        </StrategyViewProvider>
      </SessionProvider>
    </MemoryRouter>,
  )
}

describe('PlayerDetailPage', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_API_BASE_URL', 'http://localhost:8080')
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(NOW)
  })

  afterEach(() => {
    cleanup()
    vi.useRealTimers()
    vi.unstubAllEnvs()
    vi.unstubAllGlobals()
    document.title = ''
  })

  describe('ficha', () => {
    it('keeps persisted market price and OFFICIAL history while MOMENTUM activation awaits recalculation', async () => {
      const store = createStrategyViewStore()
      const snapshot = { viewStrategy: 'MARKET_CURRENT', activeConfig: pendingMomentum, configStatus: 'ready', configError: null }
      store.getSnapshot = () => snapshot
      const fetchMock = routeFetch((url) => url.pathname.endsWith('/quotes') ? [200, historyFor(url)] : [200, detail(42, { quote: quote('2026-10-20T18:00:00Z', 1.5, { strategy: 'MOMENTUM' }) })])
      renderDetail('/jugadores/42', store)
      const header = await screen.findByRole('region', { name: 'Bukayo Saka' })
      expect(within(header).getByTestId('current-quote').textContent).toBe('1,25 cr')
      expect(within(header).getByText(/recálculo/i).textContent).toContain('OFFICIAL')
      await waitFor(() => expect(requestedStrategy(fetchMock, 'OFFICIAL')).toBe(true))
    })
    it('loads market and alternative histories when the alternative view is selected', async () => {
      const store = strategyStore({ viewStrategy: 'MOMENTUM' })
      const fetchMock = routeFetch((url) => url.pathname.endsWith('/quotes') ? [200, historyFor(url)] : [200, detail()])
      renderDetail('/jugadores/42', store)
      await screen.findByRole('heading', { name: 'Bukayo Saka' })
      await waitFor(() => expect(screen.getByRole('table', { name: /Oficial \/ Momentum/ })).toBeTruthy())
      const strategies = quoteCalls(fetchMock).filter((query) => query.from).map((query) => query.strategy)
      expect(strategies).toEqual(['OFFICIAL', 'MOMENTUM'])
    })
    it('compares MOMENTUM market against the OFFICIAL alternative once MOMENTUM fixed the price', async () => {
      const store = strategyStore({ viewStrategy: 'MOMENTUM', activeConfig: marketMomentum })
      const fetchMock = routeFetch((url) => url.pathname.endsWith('/quotes') ? [200, historyFor(url)] : [200, detail()])
      renderDetail('/jugadores/42', store)
      await waitFor(() => expect(screen.getByRole('table', { name: /Momentum \/ Oficial/ })).toBeTruthy())
      const strategies = quoteCalls(fetchMock).filter((query) => query.from).map((query) => query.strategy)
      expect(strategies).toEqual(['MOMENTUM', 'OFFICIAL'])
    })
    it('shows the player header, current quote, variation and season stats', async () => {
      routeFetch(defaultHandler)
      renderDetail()

      expect(await screen.findByRole('heading', { level: 1, name: 'Bukayo Saka' })).toBeTruthy()
      const header = screen.getByRole('region', { name: 'Bukayo Saka' })
      expect(within(header).getByText('Arsenal FC')).toBeTruthy()
      expect(within(header).getByText('Premier League')).toBeTruthy()
      expect(within(header).getByText('DEL')).toBeTruthy()
      expect(within(header).getByText('England · 25')).toBeTruthy()
      expect(plain(within(header).getByText(/042/).textContent)).toContain('042')
      expect(within(header).getByText('Cotización actual')).toBeTruthy()
      expect(within(header).getByText('1,25 cr')).toBeTruthy()
      expect(await within(header).findByRole('img', { name: 'Subió 0,10 cr (8,73 %)' })).toBeTruthy()
      expect(document.title).toBe('Bukayo Saka · Figuritas')

      const stats = screen.getByRole('region', { name: 'Estadísticas de la temporada' })
      const terms = within(stats).getAllByRole('term').map((term) => term.textContent)
      const values = within(stats).getAllByRole('definition').map((value) => value.textContent)
      expect(terms).toEqual(['Minutos', 'Goles', 'Asistencias', 'Tiros', 'Pases clave', 'Tackles', 'Amarillas', 'Rojas', 'Rating'])
      expect(values).toEqual(['1.980', '12', '9', '61', '48', '21', '4', '0', '7,42'])
      expect(within(stats).getByText(/Actualizado el/)).toBeTruthy()

      expect(screen.getByRole('link', { name: '← Volver al álbum' }).getAttribute('href')).toBe('/album')
    })

    it('uses the full history of the current quote strategy for the variation', async () => {
      const fetchMock = routeFetch(defaultHandler)
      renderDetail()
      await screen.findByRole('img', { name: /Subió/ })
      expect(quoteCalls(fetchMock)).toContainEqual({ strategy: 'OFFICIAL' })
    })

    it('shows dashes for every stat when stats are missing', async () => {
      routeFetch((url) => (url.pathname.endsWith('/quotes') ? [200, OFFICIAL_HISTORY] : [200, detail(42, { stats: null })]))
      renderDetail()
      const stats = await screen.findByRole('region', { name: 'Estadísticas de la temporada' })
      expect(within(stats).getAllByRole('definition').map((value) => value.textContent)).toEqual(Array(9).fill('—'))
      expect(within(stats).queryByText(/Actualizado el/)).toBeNull()
    })

    it('shows a dash for a single missing stat, never zero', async () => {
      routeFetch((url) => (url.pathname.endsWith('/quotes')
        ? [200, OFFICIAL_HISTORY]
        : [200, detail(42, { stats: { ...STATS, goals: null, rating: null } })]))
      renderDetail()
      const stats = await screen.findByRole('region', { name: 'Estadísticas de la temporada' })
      const values = within(stats).getAllByRole('definition').map((value) => value.textContent)
      expect(values[1]).toBe('—')
      expect(values[8]).toBe('—')
    })

    it('shows dashes for quote and variation without requesting the variation history', async () => {
      const fetchMock = routeFetch((url) => (url.pathname.endsWith('/quotes') ? [200, []] : [200, detail(42, { quote: null })]))
      renderDetail()
      const header = await screen.findByRole('region', { name: 'Bukayo Saka' })
      expect(within(header).getByTestId('current-quote').textContent).toBe('1,25 cr')
      expect(within(header).getByRole('img', { name: 'Sin variación disponible' })).toBeTruthy()
      await waitFor(() => expect(fetchMock).toHaveBeenCalled())
      expect(quoteCalls(fetchMock).filter((params) => !params.from && !params.at)).toEqual([])
    })

    it('uses dashes and the neutral color for missing player data or an unknown league', async () => {
      routeFetch((url) => (url.pathname.endsWith('/quotes')
        ? [200, OFFICIAL_HISTORY]
        : [200, detail(42, { player: { ...detail().player, league: 'MLS', positions: [], nationality: null, age: null } })]))
      renderDetail()
      const header = await screen.findByRole('region', { name: 'Bukayo Saka' })
      expect(within(header).getByText('— · —')).toBeTruthy()
      expect(header.style.getPropertyValue('--figurita-liga')).toBe('var(--tinta-2)')
      expect(within(header).getAllByText('—').length).toBeGreaterThanOrEqual(2)
    })

    it('shows the trading section with the book of the player', async () => {
      const fetchMock = routeFetch(defaultHandler)
      renderDetail()

      const trading = await screen.findByRole('region', { name: 'Operar' })
      expect(await within(trading).findByRole('table', { name: 'Libro de órdenes de Bukayo Saka' })).toBeTruthy()
      expect(within(trading).getByRole('button', { name: 'Revisar compra' })).toBeTruthy()
      const bookCalls = fetchMock.mock.calls.map(([url]) => new URL(url)).filter((url) => url.pathname === '/orders/book')
      expect(bookCalls.map((url) => url.searchParams.get('playerId'))).toEqual(['42'])
    })

    it('keeps the header, chart and stats when the book fails', async () => {
      routeFetch(defaultHandler, () => [500, { error: 'unexpected_error', message: 'falló' }])
      renderDetail()

      const trading = await screen.findByRole('region', { name: 'Operar' })
      expect(await within(trading).findByRole('button', { name: 'Reintentar' })).toBeTruthy()
      expect(screen.getByRole('heading', { level: 1, name: 'Bukayo Saka' })).toBeTruthy()
      expect(await screen.findByRole('table', CHART_TABLE)).toBeTruthy()
      expect(screen.getByRole('region', { name: 'Estadísticas de la temporada' })).toBeTruthy()
    })

    it('does not keep data from the previous player when the id changes', async () => {
      routeFetch(defaultHandler)
      renderDetail()
      await screen.findByRole('heading', { level: 1, name: 'Bukayo Saka' })
      fireEvent.click(screen.getByRole('link', { name: 'Ir al 43' }))
      expect(await screen.findByRole('heading', { level: 1, name: 'Martin Ødegaard' })).toBeTruthy()
      expect(screen.queryByText('Bukayo Saka')).toBeNull()
    })
  })
  describe('evolución', () => {
    it('shows the last month of the current quote strategy as an accessible table', async () => {
      const fetchMock = routeFetch((url) => (url.pathname.endsWith('/quotes') ? [200, historyFor(url)] : [200, detail()]))
      renderDetail()
      expect(await screen.findByRole('table', CHART_TABLE)).toBeTruthy()
      expect(chartRows().map((row) => row[1])).toEqual(['1,10 cr', '1,15 cr', '1,25 cr'])
      expect(quoteCalls(fetchMock)).toContainEqual({ strategy: 'OFFICIAL', from: '2026-09-28', to: '2026-10-27' })
    })

    it('requests and shows the selected strategy and range', async () => {
      const fetchMock = routeFetch((url) => (url.pathname.endsWith('/quotes') ? [200, historyFor(url)] : [200, detail()]))
      renderDetail()
      await screen.findByRole('table', CHART_TABLE)
      fireEvent.click(screen.getByRole('radio', { name: 'Momentum' }))
      await waitFor(() => expect(chartRows().map((row) => row[2])).toEqual(['Momentum', 'Momentum']))
      expect(quoteCalls(fetchMock)).toContainEqual({ strategy: 'MOMENTUM', from: '2026-09-28', to: '2026-10-27' })
      fireEvent.click(screen.getByRole('radio', { name: 'Toda la temporada' }))
      await waitFor(() => expect(quoteCalls(fetchMock).at(-1)).toEqual({ strategy: 'MOMENTUM' }))
      expect(await screen.findByText('Momentum · Toda la temporada · 2 cotizaciones', { selector: 'figcaption' })).toBeTruthy()
      expect(chartRows().map((row) => row[3])).toEqual(['1', '2'])
    })

    it('shows the empty history state next to the header and stats', async () => {
      routeFetch((url) => {
        if (!url.pathname.endsWith('/quotes')) return [200, detail()]
        return [200, url.searchParams.has('from') ? [] : OFFICIAL_HISTORY]
      })
      renderDetail()
      expect(await screen.findByText('Todavía sin cotizaciones')).toBeTruthy()
      expect(screen.getByRole('heading', { level: 1, name: 'Bukayo Saka' })).toBeTruthy()
      expect(screen.getByRole('region', { name: 'Estadísticas de la temporada' })).toBeTruthy()
      expect(screen.getAllByRole('radio').every((radio) => !radio.disabled)).toBe(true)
    })

    it('keeps the rest of the page when only the history fails and recovers it with retry', async () => {
      let failChart = true
      routeFetch((url) => {
        if (!url.pathname.endsWith('/quotes')) return [200, detail()]
        if (url.searchParams.has('from') && failChart) return [503, { error: 'unexpected_error', message: 'falló' }]
        return [200, OFFICIAL_HISTORY]
      })
      renderDetail()
      const chart = await screen.findByRole('region', { name: 'Evolución de la cotización' })
      expect(await within(chart).findByText('No pudimos cargar la evolución')).toBeTruthy()
      expect(screen.getByRole('heading', { level: 1, name: 'Bukayo Saka' })).toBeTruthy()
      expect(screen.getByRole('region', { name: 'Estadísticas de la temporada' })).toBeTruthy()
      failChart = false
      fireEvent.click(within(chart).getByRole('button', { name: 'Reintentar' }))
      expect(await within(chart).findByRole('table')).toBeTruthy()
    })
  })
  describe('fecha puntual', () => {
    function atHandler(atResponse) {
      return (url) => {
        if (!url.pathname.endsWith('/quotes')) return [200, detail()]
        if (url.searchParams.has('at')) return atResponse(url)
        return [200, historyFor(url)]
      }
    }

    it('shows the quote in force at the chosen date', async () => {
      const fetchMock = routeFetch(atHandler(() => [200, quote('2026-10-08T18:00:00Z', 1.145, { version: 2 })]))
      renderDetail()
      const section = await screen.findByRole('region', { name: 'Cotización en una fecha' })
      const input = within(section).getByLabelText('Fecha')
      expect(input.getAttribute('max')).toBe('2026-10-27')
      fireEvent.change(input, { target: { value: '2026-10-10' } })
      fireEvent.click(within(section).getByRole('button', { name: 'Consultar' }))
      const result = within(section).getByTestId('at-result')
      expect(result.getAttribute('aria-live')).toBe('polite')
      expect(await within(result).findByText('1,15 cr')).toBeTruthy()
      expect(within(result).getByText(/Calculada el/).textContent).toContain('2026')
      expect(within(result).getByText('Oficial')).toBeTruthy()
      expect(within(result).getByText('Versión 2')).toBeTruthy()
      expect(quoteCalls(fetchMock)).toContainEqual({ strategy: 'OFFICIAL', at: '2026-10-10' })
    })

    it('shows an empty result for a 404 without a page error', async () => {
      routeFetch(atHandler(() => [404, { error: 'quote_not_found', message: 'No hay cotización' }]))
      renderDetail()
      const section = await screen.findByRole('region', { name: 'Cotización en una fecha' })
      fireEvent.change(within(section).getByLabelText('Fecha'), { target: { value: '2025-01-01' } })
      fireEvent.click(within(section).getByRole('button', { name: 'Consultar' }))
      expect(await within(section).findByText('Sin cotización para esa fecha')).toBeTruthy()
      expect(screen.queryByText('Jugador inexistente')).toBeNull()
      expect(screen.getByRole('heading', { level: 1, name: 'Bukayo Saka' })).toBeTruthy()
    })

    it('shows a section error for a failed query and retries the same date', async () => {
      let fail = true
      routeFetch(atHandler(() => (fail ? [503, { error: 'unexpected_error', message: 'falló' }] : [200, quote('2026-10-08T18:00:00Z', 1.145)])))
      renderDetail()
      const section = await screen.findByRole('region', { name: 'Cotización en una fecha' })
      fireEvent.change(within(section).getByLabelText('Fecha'), { target: { value: '2026-10-10' } })
      fireEvent.click(within(section).getByRole('button', { name: 'Consultar' }))
      expect(await within(section).findByText('No pudimos consultar la cotización.')).toBeTruthy()
      fail = false
      fireEvent.click(within(section).getByRole('button', { name: 'Reintentar' }))
      expect(await within(section).findByText('1,15 cr')).toBeTruthy()
    })

    it('rejects a future date without requesting', async () => {
      const fetchMock = routeFetch(atHandler(() => [200, quote('2026-10-08T18:00:00Z', 1.145)]))
      renderDetail()
      const section = await screen.findByRole('region', { name: 'Cotización en una fecha' })
      fireEvent.change(within(section).getByLabelText('Fecha'), { target: { value: '2026-11-01' } })
      fireEvent.click(within(section).getByRole('button', { name: 'Consultar' }))
      expect(within(section).getByText('Elegí una fecha de hoy o anterior')).toBeTruthy()
      expect(within(section).getByLabelText('Fecha').getAttribute('aria-invalid')).toBe('true')
      expect(quoteCalls(fetchMock).some((params) => params.at)).toBe(false)
    })

    it('discards the result when the strategy changes', async () => {
      routeFetch(atHandler(() => [200, quote('2026-10-08T18:00:00Z', 1.145)]))
      renderDetail()
      const section = await screen.findByRole('region', { name: 'Cotización en una fecha' })
      fireEvent.change(within(section).getByLabelText('Fecha'), { target: { value: '2026-10-10' } })
      fireEvent.click(within(section).getByRole('button', { name: 'Consultar' }))
      expect(await within(section).findByText('1,15 cr')).toBeTruthy()
      fireEvent.click(screen.getByRole('radio', { name: 'Momentum' }))
      expect(within(section).queryByText('1,15 cr')).toBeNull()
    })
  })
  describe('estados', () => {
    it('announces the loading state', async () => {
      routeFetch(() => new Promise(() => {}))
      renderDetail()
      const busy = await screen.findByLabelText('Cargando jugador…')
      expect(busy.getAttribute('aria-busy')).toBe('true')
    })

    it('shows a recoverable error and retries into the full detail', async () => {
      let fail = true
      routeFetch((url) => {
        if (url.pathname.endsWith('/quotes')) return [200, OFFICIAL_HISTORY]
        return fail ? [500, { error: 'unexpected_error', message: 'falló' }] : [200, detail()]
      })
      renderDetail()
      expect(await screen.findByText('No pudimos cargar el jugador')).toBeTruthy()
      fail = false
      fireEvent.click(screen.getByRole('button', { name: 'Reintentar' }))
      expect(await screen.findByRole('heading', { level: 1, name: 'Bukayo Saka' })).toBeTruthy()
      expect(await screen.findByRole('table', CHART_TABLE)).toBeTruthy()
    })

    it.each([
      ['a 404', '/jugadores/999999', true],
      ['an invalid id', '/jugadores/abc', false],
    ])('shows the missing player state for %s without retry', async (_, path, requests) => {
      const fetchMock = routeFetch(() => [404, { error: 'player_not_found', message: 'No existe' }])
      renderDetail(path)
      expect(await screen.findByText('Jugador inexistente')).toBeTruthy()
      expect(screen.getByText('404')).toBeTruthy()
      expect(screen.queryByRole('button', { name: 'Reintentar' })).toBeNull()
      expect(fetchMock.mock.calls.length > 0).toBe(requests)
      fireEvent.click(screen.getByRole('button', { name: 'Volver al álbum' }))
      expect(await screen.findByText('Álbum')).toBeTruthy()
      expect(screen.getByTestId('location').textContent).toBe('/album')
    })

    it('delegates a 401 to the shared session flow', async () => {
      routeFetch(() => [401, { error: 'unauthorized', message: 'expired' }])
      renderDetail()
      expect(await screen.findByText('Ingreso')).toBeTruthy()
      expect(screen.getByTestId('location').textContent).toBe('/ingresar')
    })

    it('focuses the existing trade panel when opened from an opportunity', async () => {
      routeFetch(defaultHandler)
      renderDetail('/jugadores/42#operar')
      const heading = await screen.findByRole('heading', { name: 'Operar' })
      await waitFor(() => expect(document.activeElement).toBe(heading))
      expect(screen.getByTestId('current-quote').textContent).toBe('1,25 cr')
    })
  })
})
