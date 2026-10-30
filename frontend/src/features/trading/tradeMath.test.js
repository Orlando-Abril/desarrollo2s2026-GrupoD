import { describe, expect, it } from 'vitest'
import {
  bandFor,
  estimateLimit,
  estimateMarket,
  fromUnits,
  isWithinBand,
  priceToUnits,
  summarizeCancel,
  summarizeOrder,
  toUnits,
  validateDraft,
} from './tradeMath.js'

const plain = (text) => text.replace(/\s/g, ' ')

function order(overrides = {}) {
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
    trades: [],
    ...overrides,
  }
}

function draft(overrides = {}) {
  return { side: 'BUY', type: 'LIMIT', quantity: '10', limitPrice: '1', ...overrides }
}

describe('priceToUnits', () => {
  it('converts API numbers to ten-thousandths', () => {
    expect(priceToUnits(1.2)).toBe(12000)
    expect(priceToUnits(1.1)).toBe(11000)
    expect(priceToUnits(1.2345)).toBe(12345)
    expect(priceToUnits(0.07)).toBe(700)
  })
})

describe('estimateMarket', () => {
  const levels = [{ price: 1.1, quantity: 3 }, { price: 1.2, quantity: 50 }]

  it('walks the levels in order until the quantity is covered', () => {
    expect(estimateMarket(levels, 8)).toEqual({ fillable: 8, total: 3 * 11000 + 5 * 12000, unfilled: 0 })
  })

  it('reports what would not find a counterpart', () => {
    expect(estimateMarket(levels, 60)).toEqual({ fillable: 53, total: 3 * 11000 + 50 * 12000, unfilled: 7 })
  })

  it('fills nothing on an empty side', () => {
    expect(estimateMarket([], 5)).toEqual({ fillable: 0, total: 0, unfilled: 5 })
  })
})

describe('estimateLimit', () => {
  it('is quantity times the limit price', () => {
    expect(estimateLimit(10, 11000)).toBe(110000)
  })
})

describe('summarizeOrder', () => {
  it('summarizes a filled order with its average price', () => {
    expect(plain(summarizeOrder(order()))).toBe('Compraste 10 tokens a 1,2000 cr promedio.')
  })

  it('summarizes a partially filled LIMIT with what stayed in the book', () => {
    const partial = order({ type: 'LIMIT', limitPrice: 1.3, filledQuantity: 4, remainingQuantity: 6, status: 'PARTIALLY_FILLED' })
    expect(plain(summarizeOrder(partial))).toBe('Compraste 4 tokens a 1,2000 cr promedio. Quedaron 6 en el libro a 1,3000 cr.')
  })

  it('summarizes an open LIMIT with nothing executed', () => {
    const open = order({ type: 'LIMIT', limitPrice: 1.1, filledQuantity: 0, remainingQuantity: 10, status: 'OPEN', averagePrice: null })
    expect(plain(summarizeOrder(open))).toBe('No se ejecutó nada. Quedaron 10 en el libro a 1,1000 cr.')
  })

  it('summarizes a MARKET whose rest was cancelled', () => {
    const cancelled = order({ side: 'SELL', filledQuantity: 4, remainingQuantity: 6, status: 'CANCELLED', averagePrice: 1.05 })
    expect(plain(summarizeOrder(cancelled)))
      .toBe('Vendiste 4 tokens a 1,0500 cr promedio. Los 6 restantes se cancelaron por falta de contraparte.')

    const empty = order({ filledQuantity: 0, remainingQuantity: 10, status: 'CANCELLED', averagePrice: null })
    expect(plain(summarizeOrder(empty)))
      .toBe('No se ejecutó nada. Los 10 restantes se cancelaron por falta de contraparte.')
  })
})

describe('summarizeCancel', () => {
  it('reports the credits released by a buy', () => {
    const buy = order({ type: 'LIMIT', limitPrice: 1.1, filledQuantity: 4, remainingQuantity: 6, status: 'PARTIALLY_FILLED' })
    expect(plain(summarizeCancel(buy))).toBe('Cancelaste tu compra: se liberaron 6,6000 cr.')
  })

  it('reports the tokens released by a sell', () => {
    const sell = order({ side: 'SELL', type: 'LIMIT', limitPrice: 1.2, filledQuantity: 0, remainingQuantity: 6, status: 'OPEN' })
    expect(plain(summarizeCancel(sell))).toBe('Cancelaste tu venta: se liberaron 6 tokens.')
  })
})

describe('validateDraft', () => {
  const band = bandFor(1)

  it('requires a positive integer quantity', () => {
    for (const quantity of ['', '0', '-3', '2.5', 'abc', '010']) {
      expect(validateDraft(draft({ quantity }), band).quantity)
        .toBe('La cantidad debe ser un número entero mayor a 0')
    }
    expect(validateDraft(draft({ quantity: '7' }), band).quantity).toBeUndefined()
  })

  it('rejects a quantity whose total is not a safe integer', () => {
    const big = draft({ quantity: '999999999999999', limitPrice: '1.2' })
    expect(validateDraft(big, bandFor(1.2)).quantity).toBe('La cantidad es demasiado grande.')
    expect(validateDraft(draft({ type: 'MARKET', quantity: '999999999999999' }), band).quantity)
      .toBe('La cantidad es demasiado grande.')
  })

  it('validates the LIMIT price', () => {
    const priceError = (limitPrice) => plain(validateDraft(draft({ limitPrice }), band).limitPrice ?? '')
    expect(priceError('')).toBe('Ingresá el precio límite')
    expect(priceError('0')).toBe('El precio debe ser mayor a 0')
    expect(priceError('1.23456')).toBe('El precio puede tener hasta 4 decimales')
    expect(priceError('0.7999')).toBe('El precio debe estar entre 0,8000 cr y 1,2000 cr')
    expect(priceError('1.2001')).toBe('El precio debe estar entre 0,8000 cr y 1,2000 cr')
    expect(priceError('0.8')).toBe('')
    expect(priceError('1.2')).toBe('')
  })

  it('accepts a comma as the decimal separator', () => {
    expect(validateDraft(draft({ limitPrice: '1,2' }), band).limitPrice).toBeUndefined()
  })

  it('never validates the price of a MARKET order', () => {
    expect(validateDraft(draft({ type: 'MARKET', limitPrice: 'abc' }), band)).toEqual({})
  })
})

describe('toUnits', () => {
  it('converts the input text to ten-thousandths', () => {
    expect(toUnits('1.2345')).toBe(12345)
    expect(toUnits('1')).toBe(10000)
    expect(toUnits('0.8')).toBe(8000)
    expect(toUnits('12.05')).toBe(120500)
  })

  it('returns null for text that is not a price with up to 4 decimals', () => {
    for (const text of ['', 'abc', '-1', '1.23456', '1.', '.5', '1,2']) {
      expect(toUnits(text)).toBeNull()
    }
  })
})

describe('fromUnits', () => {
  it('returns the number for the request body', () => {
    expect(fromUnits(12345)).toBe(1.2345)
    expect(fromUnits(11000)).toBe(1.1)
  })
})

describe('bandFor', () => {
  it('computes the ±20% band in ten-thousandths', () => {
    expect(bandFor(1)).toEqual({ min: 8000, max: 12000 })
  })

  it('rounds the minimum up and the maximum down', () => {
    expect(bandFor(1.2345)).toEqual({ min: 9876, max: 14814 })
  })

  it('returns null without a market value', () => {
    expect(bandFor(null)).toBeNull()
    expect(bandFor(undefined)).toBeNull()
  })
})

describe('isWithinBand', () => {
  const band = bandFor(1)

  it('accepts the exact limits', () => {
    expect(isWithinBand(toUnits('0.8000'), band)).toBe(true)
    expect(isWithinBand(toUnits('1.2000'), band)).toBe(true)
  })

  it('rejects one ten-thousandth outside the limits', () => {
    expect(isWithinBand(toUnits('0.7999'), band)).toBe(false)
    expect(isWithinBand(toUnits('1.2001'), band)).toBe(false)
  })
})
