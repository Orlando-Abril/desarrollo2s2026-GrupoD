import { useEffect } from 'react'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import MarketPage from './MarketPage.jsx'
import { SessionProvider, useSession } from '../session/SessionContext.jsx'

const plain = (text) => text.replace(/\s/g, ' ')

function myOrder(overrides = {}) {
  return {
    id: 1002,
    playerId: 42,
    playerName: 'Bukayo Saka',
    side: 'SELL',
    type: 'LIMIT',
    limitPrice: 1.25,
    quantity: 6,
    filledQuantity: 0,
    remainingQuantity: 6,
    status: 'OPEN',
    averagePrice: null,
    createdAt: '2026-10-29T16:00:00Z',
    closedAt: null,
    trades: [],
    ...overrides,
  }
}

const ORDERS = [
  myOrder(),
  myOrder({ id: 1001, playerId: 7, playerName: 'Kylian Mbappé', side: 'BUY', limitPrice: 1.3, quantity: 10, filledQuantity: 4, remainingQuantity: 6, status: 'PARTIALLY_FILLED', averagePrice: 1.2, createdAt: '2026-10-29T15:04:05Z' }),
]

function response(status, body) {
  return {
    ok: status >= 200 && status < 300,
    status,
    text: vi.fn().mockResolvedValue(body === undefined ? '' : JSON.stringify(body)),
  }
}

// mine: lista o [status, body] de GET /orders/mine; cancel: [status, body] o Error para DELETE.
function routeFetch({ mine = () => [200, ORDERS], cancel = (id) => [200, { ...ORDERS.find((o) => o.id === id), status: 'CANCELLED' }] } = {}) {
  const fetchMock = vi.fn((url, options = {}) => {
    const { pathname } = new URL(url)
    const result = options.method === 'DELETE' ? cancel(Number(pathname.split('/').at(-1))) : mine()
    if (result instanceof Error) return Promise.reject(result)
    if (result instanceof Promise) return result
    return Promise.resolve(response(...result))
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

const mineCalls = (fetchMock) => fetchMock.mock.calls.filter(([url, options = {}]) => url.endsWith('/orders/mine') && options.method === 'GET')
const deleteCalls = (fetchMock) => fetchMock.mock.calls.filter(([, options = {}]) => options.method === 'DELETE')

function AutoLogin({ children }) {
  const { login } = useSession()
  useEffect(() => {
    login({ token: 'jwt', tokenType: 'Bearer', username: 'abril' })
  }, [login])
  return children
}

function renderMarket() {
  return render(
    <MemoryRouter initialEntries={['/mercado']}>
      <SessionProvider>
        <AutoLogin>
          <Routes>
            <Route path="/mercado" element={<MarketPage />} />
            <Route path="/album" element={<p>Álbum</p>} />
            <Route path="/jugadores/:id" element={<p>Ficha</p>} />
          </Routes>
        </AutoLogin>
      </SessionProvider>
    </MemoryRouter>,
  )
}

const rows = () => within(screen.getByRole('table')).getAllByRole('row').slice(1)
const cancelButton = (name) => screen.getByRole('button', { name })

describe('MarketPage', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_API_BASE_URL', 'http://localhost:8080')
  })

  afterEach(() => {
    cleanup()
    vi.unstubAllEnvs()
    vi.unstubAllGlobals()
  })

  it('announces the loading state under the page heading', () => {
    routeFetch({ mine: () => new Promise(() => {}) })
    renderMarket()

    expect(screen.getByRole('heading', { level: 1, name: 'Mercado' })).toBeTruthy()
    expect(screen.getByText('Cargando tus órdenes…').getAttribute('aria-busy')).toBe('true')
  })

  it('lists the active orders in the received order with every column', async () => {
    routeFetch()
    renderMarket()

    await screen.findByRole('table')
    const [first, second] = rows().map((row) => plain(row.textContent))
    for (const text of ['Bukayo Saka', 'Venta', 'Límite', '1,2500 cr', '6', '0', 'Abierta']) expect(first).toContain(text)
    for (const text of ['Kylian Mbappé', 'Compra', '1,3000 cr', '10', '4', 'Parcial']) expect(second).toContain(text)
    expect(first).toContain('29 oct 2026')
    expect(screen.getByRole('link', { name: 'Bukayo Saka' }).getAttribute('href')).toBe('/jugadores/42')
  })

  it('shows the empty state with a way to the album', async () => {
    routeFetch({ mine: () => [200, []] })
    renderMarket()

    expect(await screen.findByText('No tenés órdenes abiertas')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'Ir al álbum' }))
    expect(await screen.findByText('Álbum')).toBeTruthy()
  })

  it('retries after a load error', async () => {
    let fail = true
    routeFetch({ mine: () => (fail ? [500, { error: 'unexpected_error', message: 'falló' }] : [200, ORDERS]) })
    renderMarket()

    expect(await screen.findByText('No pudimos cargar tus órdenes')).toBeTruthy()
    fail = false
    fireEvent.click(screen.getByRole('button', { name: 'Reintentar' }))
    expect(await screen.findByRole('table')).toBeTruthy()
  })

  it('explains a superuser rejection instead of the connection text', async () => {
    routeFetch({ mine: () => [403, { error: 'superuser_not_allowed', message: 'no' }] })
    renderMarket()

    expect(await screen.findByText('El superusuario no puede operar desde la aplicación.')).toBeTruthy()
    expect(screen.queryByText(/No se pudo conectar/)).toBeNull()
  })

  it('asks for confirmation and does not cancel when going back', async () => {
    const fetchMock = routeFetch()
    renderMarket()
    await screen.findByRole('table')

    fireEvent.click(cancelButton('Cancelar orden de venta de Bukayo Saka'))
    const dialog = screen.getByRole('dialog')
    for (const text of ['Bukayo Saka', 'Venta', '6', '1,2500 cr']) expect(plain(dialog.textContent)).toContain(text)
    fireEvent.click(within(dialog).getByRole('button', { name: 'Volver' }))

    expect(screen.queryByRole('dialog')).toBeNull()
    expect(deleteCalls(fetchMock)).toHaveLength(0)
  })

  it('cancels once, removes the row and shows what was released', async () => {
    let resolve
    const fetchMock = routeFetch({ cancel: () => new Promise((done) => { resolve = done }) })
    renderMarket()
    await screen.findByRole('table')

    fireEvent.click(cancelButton('Cancelar orden de compra de Kylian Mbappé'))
    const confirm = within(screen.getByRole('dialog')).getByRole('button', { name: 'Cancelar orden' })
    fireEvent.click(confirm)
    fireEvent.click(confirm)
    expect(deleteCalls(fetchMock)).toHaveLength(1)
    expect(deleteCalls(fetchMock)[0][0]).toBe('http://localhost:8080/orders/1001')

    resolve(response(200, { ...ORDERS[1], status: 'CANCELLED' }))

    expect(plain((await screen.findByRole('status')).textContent)).toBe('Cancelaste tu compra: se liberaron 7,8000 cr.')
    expect(rows()).toHaveLength(1)
    expect(screen.queryByText('Kylian Mbappé')).toBeNull()
  })

  it.each([
    [409, 'order_closed', 'Esta orden ya no está activa.'],
    [404, 'order_not_found', 'Esta orden ya no existe.'],
  ])('reloads the list after a %s %s', async (status, code, message) => {
    const fetchMock = routeFetch({ cancel: () => [status, { error: code, message: 'x' }] })
    renderMarket()
    await screen.findByRole('table')

    fireEvent.click(cancelButton('Cancelar orden de venta de Bukayo Saka'))
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Cancelar orden' }))

    expect((await screen.findByRole('alert')).textContent).toBe(message)
    await waitFor(() => expect(mineCalls(fetchMock)).toHaveLength(2))
  })

  it('returns the focus to the cancel button when the confirmation closes with Escape', async () => {
    routeFetch()
    renderMarket()
    await screen.findByRole('table')

    const button = cancelButton('Cancelar orden de venta de Bukayo Saka')
    button.focus()
    fireEvent.click(button)
    const dialog = screen.getByRole('dialog')
    expect(document.activeElement).toBe(within(dialog).getByRole('button', { name: 'Cancelar orden' }))
    fireEvent.keyDown(dialog, { key: 'Escape' })

    expect(screen.queryByRole('dialog')).toBeNull()
    expect(document.activeElement).toBe(button)
  })

  it('keeps the row after a network error', async () => {
    const fetchMock = routeFetch({ cancel: () => new TypeError('Failed to fetch') })
    renderMarket()
    await screen.findByRole('table')

    fireEvent.click(cancelButton('Cancelar orden de venta de Bukayo Saka'))
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Cancelar orden' }))

    expect((await screen.findByRole('alert')).textContent).toBe('No pudimos cancelar la orden. Probá de nuevo.')
    expect(rows()).toHaveLength(2)
    expect(mineCalls(fetchMock)).toHaveLength(1)
  })
})
