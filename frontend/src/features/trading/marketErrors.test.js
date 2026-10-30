import { describe, expect, it } from 'vitest'
import { ApiError, NetworkError } from '../../api/httpClient.js'
import { hasMarketMessage, marketErrorMessage, staleTarget } from './marketErrors.js'

const MESSAGES = {
  insufficient_balance: 'No tenés saldo suficiente para esta compra.',
  insufficient_tokens: 'No tenés tokens disponibles suficientes de este jugador (pueden estar reservados en órdenes abiertas).',
  price_out_of_band: 'El precio está fuera del rango permitido (±20% de la cotización). Actualizamos la cotización: revisá el precio.',
  player_not_quoted: 'Este jugador todavía no tiene cotización, así que no se puede operar.',
  player_not_found: 'Este jugador ya no existe en el mercado.',
  superuser_not_allowed: 'El superusuario no puede operar desde la aplicación.',
  user_required: 'Tu credencial no está asociada a un usuario. Ingresá de nuevo.',
  order_not_found: 'Esta orden ya no existe.',
  order_forbidden: 'No podés cancelar esta orden.',
  order_closed: 'Esta orden ya no está activa.',
  validation_error: 'Revisá los datos de la orden.',
}

const apiError = (code, message = 'server text') => new ApiError(400, code, message)

describe('marketErrorMessage', () => {
  it.each(Object.entries(MESSAGES))('translates %s', (code, message) => {
    expect(marketErrorMessage(apiError(code))).toBe(message)
  })

  it('uses the generic message for network, unexpected and unknown errors', () => {
    const generic = 'No pudimos enviar la orden. Probá de nuevo.'
    expect(marketErrorMessage(new NetworkError())).toBe(generic)
    expect(marketErrorMessage(apiError('unexpected_error'))).toBe(generic)
    expect(marketErrorMessage(apiError('something_new'))).toBe(generic)
    expect(marketErrorMessage(new Error('boom'))).toBe(generic)
  })

  it('uses the cancel generic message for the cancel action', () => {
    expect(marketErrorMessage(new NetworkError(), { action: 'cancel' }))
      .toBe('No pudimos cancelar la orden. Probá de nuevo.')
  })

  it('never shows the server message', () => {
    expect(marketErrorMessage(apiError('something_new', 'Detalle interno'))).not.toContain('Detalle interno')
    expect(marketErrorMessage(apiError('insufficient_balance', 'Detalle interno'))).not.toContain('Detalle interno')
  })
})

describe('hasMarketMessage', () => {
  it('is true only for codes with a specific message', () => {
    expect(hasMarketMessage(apiError('superuser_not_allowed'))).toBe(true)
    expect(hasMarketMessage(apiError('unexpected_error'))).toBe(false)
    expect(hasMarketMessage(new NetworkError())).toBe(false)
  })
})

describe('staleTarget', () => {
  it('reloads the book after a price out of band', () => {
    expect(staleTarget(apiError('price_out_of_band'))).toBe('book')
  })

  it('reloads the orders when the order is no longer cancellable', () => {
    for (const code of ['order_closed', 'order_not_found', 'order_forbidden']) {
      expect(staleTarget(apiError(code))).toBe('orders')
    }
  })

  it('returns null for the rest', () => {
    expect(staleTarget(apiError('insufficient_balance'))).toBeNull()
    expect(staleTarget(new NetworkError())).toBeNull()
  })
})
