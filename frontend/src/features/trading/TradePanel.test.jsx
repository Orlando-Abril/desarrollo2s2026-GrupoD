import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, NetworkError } from '../../api/httpClient.js'
import { buyOrder, getOrderBook, sellOrder } from '../../api/marketApi.js'
import TradingSection from './TradingSection.jsx'

vi.mock('../../api/marketApi.js', () => ({
  buyOrder: vi.fn(),
  sellOrder: vi.fn(),
  getOrderBook: vi.fn(),
}))

const plain = (text) => text.replace(/\s/g, ' ')
const PLAYER = { id: 42, fullName: 'Jugador Test' }
const BOOK = { playerId: 42, marketValue: 1.2, sells: [{ price: 1.2, quantity: 100 }], buys: [] }

function filled(overrides = {}) {
  return {
    id: 1,
    playerId: 42,
    side: 'BUY',
    type: 'MARKET',
    limitPrice: null,
    quantity: 10,
    filledQuantity: 10,
    remainingQuantity: 0,
    status: 'FILLED',
    averagePrice: 1.2,
    createdAt: '2026-10-29T15:00:00Z',
    closedAt: '2026-10-29T15:00:00Z',
    trades: [],
    ...overrides,
  }
}

async function renderSection(book = BOOK) {
  getOrderBook.mockResolvedValue(book)
  const view = render(<TradingSection player={PLAYER} />)
  await waitFor(() => expect(quantityInput().matches(':disabled')).toBe(false))
  return view
}

const quantityInput = () => screen.getByLabelText('Cantidad de tokens')
const priceInput = () => screen.getByLabelText('Precio límite (cr)')
const estimate = () => plain(screen.getByTestId('trade-estimate').textContent)
const type = (value) => fireEvent.change(quantityInput(), { target: { value } })
const chooseLimit = () => fireEvent.click(screen.getByRole('radio', { name: /Con precio límite/ }))
const chooseSell = () => fireEvent.click(screen.getByRole('button', { name: 'Vender' }))
const review = (label = 'Revisar compra') => fireEvent.click(screen.getByRole('button', { name: label }))

describe('TradePanel', () => {
  beforeEach(() => {
    getOrderBook.mockReset()
    buyOrder.mockReset()
    sellOrder.mockReset()
  })

  afterEach(cleanup)

  describe('buying (US1)', () => {
    it('starts as a MARKET buy without a price field', async () => {
      await renderSection()

      expect(screen.getByRole('button', { name: 'Comprar' }).getAttribute('aria-pressed')).toBe('true')
      expect(screen.getByRole('radio', { name: /Al mercado/ }).checked).toBe(true)
      expect(screen.queryByLabelText('Precio límite (cr)')).toBeNull()
    })

    it('is disabled without a market value and still shows the book section', async () => {
      getOrderBook.mockResolvedValue({ ...BOOK, marketValue: null })
      render(<TradingSection player={PLAYER} />)

      expect(await screen.findByText('Este jugador todavía no tiene cotización')).toBeTruthy()
      expect(quantityInput().matches(':disabled')).toBe(true)
      expect(screen.getByRole('region', { name: 'Libro de órdenes' })).toBeTruthy()
    })

    it('estimates a MARKET buy walking the sells', async () => {
      await renderSection()
      type('10')

      expect(estimate()).toContain('estimado')
      expect(estimate()).toContain('12,0000 cr')
    })

    it('estimates a LIMIT buy as quantity times price', async () => {
      await renderSection()
      chooseLimit()
      type('10')
      fireEvent.change(priceInput(), { target: { value: '1.1' } })

      expect(estimate()).toContain('11,0000 cr')
    })

    it('asks for confirmation and does not send when going back', async () => {
      await renderSection()
      chooseLimit()
      type('10')
      fireEvent.change(priceInput(), { target: { value: '1.1' } })
      review()

      const dialog = screen.getByRole('dialog')
      const summary = plain(dialog.textContent)
      for (const text of ['Compra', 'Límite', 'Jugador Test', '10', '1,1000 cr', '11,0000 cr']) {
        expect(summary).toContain(text)
      }
      fireEvent.click(within(dialog).getByRole('button', { name: 'Volver' }))

      expect(screen.queryByRole('dialog')).toBeNull()
      expect(buyOrder).not.toHaveBeenCalled()
    })

    it('sends once, shows the result, clears the form and reloads the book', async () => {
      let resolve
      buyOrder.mockImplementation(() => new Promise((done) => { resolve = done }))
      await renderSection()
      type('10')
      review()

      const confirm = screen.getByRole('button', { name: 'Confirmar compra' })
      fireEvent.click(confirm)
      fireEvent.click(confirm)
      expect(buyOrder).toHaveBeenCalledTimes(1)
      expect(buyOrder.mock.calls[0][0]).toEqual({ playerId: 42, quantity: 10, type: 'MARKET' })
      expect(confirm.disabled).toBe(true)

      await act(async () => resolve(filled()))

      const message = 'Compraste 10 tokens a 1,2000 cr promedio.'
      expect(plain(screen.getByRole('status').textContent)).toBe(message)
      expect(plain(screen.getByTestId('trade-result').textContent)).toBe(message)
      expect(screen.queryByRole('dialog')).toBeNull()
      expect(quantityInput().value).toBe('')
      expect(screen.getByRole('button', { name: 'Comprar' }).getAttribute('aria-pressed')).toBe('true')
      expect(getOrderBook).toHaveBeenCalledTimes(2)
    })

    it('keeps the side and type after sending a LIMIT', async () => {
      buyOrder.mockResolvedValue(filled({ type: 'LIMIT', limitPrice: 1.2, filledQuantity: 0, remainingQuantity: 10, status: 'OPEN', averagePrice: null }))
      await renderSection()
      chooseLimit()
      type('10')
      fireEvent.change(priceInput(), { target: { value: '1.2' } })
      review()
      fireEvent.click(screen.getByRole('button', { name: 'Confirmar compra' }))

      await screen.findByRole('status')
      expect(buyOrder.mock.calls[0][0]).toEqual({ playerId: 42, quantity: 10, type: 'LIMIT', limitPrice: 1.2 })
      expect(screen.getByRole('radio', { name: /Con precio límite/ }).checked).toBe(true)
      expect(priceInput().value).toBe('')
    })

    it('ignores the response when the section unmounts while sending', async () => {
      let resolve
      buyOrder.mockImplementation(() => new Promise((done) => { resolve = done }))
      const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
      const { unmount } = await renderSection()
      type('10')
      review()
      fireEvent.click(screen.getByRole('button', { name: 'Confirmar compra' }))

      unmount()
      await act(async () => resolve(filled()))

      expect(screen.queryByRole('status')).toBeNull()
      expect(errorSpy).not.toHaveBeenCalled()
      errorSpy.mockRestore()
    })
  })

  describe('selling (US2)', () => {
    const buys = [{ price: 1.15, quantity: 2 }, { price: 1.0, quantity: 5 }]

    it('estimates a MARKET sell walking the buys from the highest price', async () => {
      await renderSection({ ...BOOK, buys })
      chooseSell()
      type('4')

      expect(estimate()).toContain('4,3000 cr')
    })

    it('warns that a MARKET sell without buyers would be cancelled and lets it be sent', async () => {
      sellOrder.mockResolvedValue(filled({ side: 'SELL', filledQuantity: 0, remainingQuantity: 3, quantity: 3, status: 'CANCELLED', averagePrice: null }))
      await renderSection()
      chooseSell()
      type('3')

      expect(estimate()).toContain('No hay compradores: la orden se cancelaría sin ejecutarse')
      review('Revisar venta')
      fireEvent.click(screen.getByRole('button', { name: 'Confirmar venta' }))

      await screen.findByRole('status')
      expect(sellOrder).toHaveBeenCalledTimes(1)
      expect(buyOrder).not.toHaveBeenCalled()
    })

    it('warns about the part without a counterpart', async () => {
      await renderSection({ ...BOOK, buys })
      chooseSell()
      type('10')

      expect(estimate()).toContain('Sólo hay 7 tokens del otro lado; los 3 restantes se cancelarían')
    })

    it('reports an open LIMIT sell that stayed in the book', async () => {
      sellOrder.mockResolvedValue(filled({ side: 'SELL', type: 'LIMIT', limitPrice: 1.2, quantity: 6, filledQuantity: 0, remainingQuantity: 6, status: 'OPEN', averagePrice: null }))
      await renderSection()
      chooseSell()
      chooseLimit()
      type('6')
      fireEvent.change(priceInput(), { target: { value: '1.2' } })
      review('Revisar venta')
      fireEvent.click(screen.getByRole('button', { name: 'Confirmar venta' }))

      expect(plain((await screen.findByRole('status')).textContent))
        .toBe('No se ejecutó nada. Quedaron 6 en el libro a 1,2000 cr.')
    })
  })

  describe('validation and errors (US3)', () => {
    it('does not open the confirmation with invalid data and focuses the first invalid field', async () => {
      await renderSection()
      chooseLimit()
      type('2.5')
      fireEvent.change(priceInput(), { target: { value: '9' } })
      review()

      expect(screen.queryByRole('dialog')).toBeNull()
      expect(quantityInput().getAttribute('aria-invalid')).toBe('true')
      const quantityError = document.getElementById(quantityInput().getAttribute('aria-describedby'))
      expect(quantityError.textContent).toBe('La cantidad debe ser un número entero mayor a 0')
      const priceError = document.getElementById(priceInput().getAttribute('aria-describedby'))
      expect(plain(priceError.textContent)).toBe('El precio debe estar entre 0,9600 cr y 1,4400 cr')
      expect(document.activeElement).toBe(quantityInput())
    })

    it('shows the allowed range next to the LIMIT price', async () => {
      await renderSection()
      chooseLimit()

      const hint = document.getElementById(priceInput().getAttribute('aria-describedby'))
      expect(plain(hint.textContent)).toBe('Rango permitido: 0,9600 cr – 1,4400 cr')
    })

    it('drops the price and its error when switching back to MARKET', async () => {
      await renderSection()
      chooseLimit()
      type('1')
      fireEvent.change(priceInput(), { target: { value: '5' } })
      review()
      fireEvent.click(screen.getByRole('radio', { name: /Al mercado/ }))

      expect(screen.queryByLabelText('Precio límite (cr)')).toBeNull()
      review()
      expect(screen.getByRole('dialog')).toBeTruthy()
    })

    const CODES = [
      ['insufficient_balance', 'No tenés saldo suficiente para esta compra.'],
      ['insufficient_tokens', 'No tenés tokens disponibles suficientes de este jugador (pueden estar reservados en órdenes abiertas).'],
      ['price_out_of_band', 'El precio está fuera del rango permitido (±20% de la cotización). Actualizamos la cotización: revisá el precio.'],
      ['player_not_quoted', 'Este jugador todavía no tiene cotización, así que no se puede operar.'],
      ['player_not_found', 'Este jugador ya no existe en el mercado.'],
      ['superuser_not_allowed', 'El superusuario no puede operar desde la aplicación.'],
      ['user_required', 'Tu credencial no está asociada a un usuario. Ingresá de nuevo.'],
      ['validation_error', 'Revisá los datos de la orden.'],
    ]

    it.each(CODES)('explains %s, keeps the data and shows no toast', async (code, message) => {
      buyOrder.mockRejectedValue(new ApiError(422, code, 'texto crudo del servidor'))
      await renderSection()
      chooseLimit()
      type('10')
      fireEvent.change(priceInput(), { target: { value: '1.1' } })
      review()
      fireEvent.click(screen.getByRole('button', { name: 'Confirmar compra' }))

      expect((await screen.findByRole('alert')).textContent).toBe(message)
      expect(screen.queryByText(/texto crudo/)).toBeNull()
      expect(quantityInput().value).toBe('10')
      expect(priceInput().value).toBe('1.1')
      expect(screen.queryByRole('status')).toBeNull()
      expect(getOrderBook).toHaveBeenCalledTimes(code === 'price_out_of_band' ? 2 : 1)
    })

    it('shows the generic message on a network error', async () => {
      buyOrder.mockRejectedValue(new NetworkError())
      await renderSection()
      type('10')
      review()
      fireEvent.click(screen.getByRole('button', { name: 'Confirmar compra' }))

      expect((await screen.findByRole('alert')).textContent).toBe('No pudimos enviar la orden. Probá de nuevo.')
      expect(quantityInput().value).toBe('10')
    })
  })

  describe('keyboard (US7)', () => {
    it('reaches every control in order, opens the confirmation with Enter and returns the focus with Escape', async () => {
      await renderSection()
      chooseLimit()

      const panel = screen.getByRole('region', { name: 'Comprar o vender' })
      const focusable = [...panel.querySelectorAll('button, input')].filter((element) => element.tabIndex >= 0)
      expect(focusable.map((element) => element.getAttribute('aria-label') ?? element.labels?.[0]?.textContent ?? element.textContent))
        .toEqual(['Comprar', 'Vender', 'Al mercado (MARKET)', 'Con precio límite (LIMIT)', 'Cantidad de tokens', 'Precio límite (cr)', 'Revisar compra'])

      type('10')
      fireEvent.change(priceInput(), { target: { value: '1.2' } })
      const reviewButton = screen.getByRole('button', { name: 'Revisar compra' })
      reviewButton.focus()
      fireEvent.submit(reviewButton.closest('form'))

      const dialog = screen.getByRole('dialog')
      expect(document.activeElement).toBe(within(dialog).getByRole('button', { name: 'Confirmar compra' }))
      fireEvent.keyDown(dialog, { key: 'Escape' })

      expect(screen.queryByRole('dialog')).toBeNull()
      expect(document.activeElement).toBe(reviewButton)
      expect(buyOrder).not.toHaveBeenCalled()
    })
  })

  describe('book (US4)', () => {
    it('shows the reloaded book after a purchase', async () => {
      buyOrder.mockResolvedValue(filled())
      await renderSection()
      getOrderBook.mockResolvedValue({ ...BOOK, sells: [{ price: 1.2, quantity: 90 }] })
      type('10')
      review()
      fireEvent.click(screen.getByRole('button', { name: 'Confirmar compra' }))

      await waitFor(() => {
        const rows = within(screen.getByRole('table')).getAllByRole('row').slice(1)
        expect(plain(rows[0].textContent)).toContain('90')
      })
    })

    it('disables the panel when the book cannot be loaded', async () => {
      getOrderBook.mockRejectedValue(new NetworkError())
      render(<TradingSection player={PLAYER} />)

      expect(await screen.findByText('No pudimos cargar el libro del jugador.')).toBeTruthy()
      expect(quantityInput().matches(':disabled')).toBe(true)
      expect(screen.getByRole('button', { name: 'Reintentar' })).toBeTruthy()
    })

    it('still shows the book rows without a market value', async () => {
      getOrderBook.mockResolvedValue({ ...BOOK, marketValue: null, sells: [{ price: 1.0, quantity: 100 }] })
      render(<TradingSection player={PLAYER} />)

      expect(await screen.findByText('Este jugador todavía no tiene cotización')).toBeTruthy()
      const rows = within(screen.getByRole('table')).getAllByRole('row').slice(1)
      expect(plain(rows[0].textContent)).toContain('Venta')
      expect(plain(rows[0].textContent)).toContain('1,0000')
    })
  })
})
