import { formatInteger } from '../../utils/format.js'
import { formatPrice } from './tradeFormat.js'

// Precios y totales en diezmilésimos (enteros) para comparar igual que el backend (research R1).
const UNITS = 10000
const PRICE_TEXT = /^\d+(\.\d{1,4})?$/
const QUANTITY_TEXT = /^[1-9]\d*$/
const TOO_MANY_DECIMALS = /^\d+\.\d{5,}$/

export function toUnits(text) {
  if (!PRICE_TEXT.test(text)) return null
  const [integer, decimals = ''] = text.split('.')
  return Number(integer) * UNITS + Number(decimals.padEnd(4, '0'))
}

export function priceToUnits(n) {
  return Math.round(n * UNITS)
}

export function fromUnits(units) {
  return units / UNITS
}

export function bandFor(marketValue) {
  if (marketValue === null || marketValue === undefined) return null
  const reference = priceToUnits(marketValue)
  return { min: Math.ceil((reference * 80) / 100), max: Math.floor((reference * 120) / 100) }
}

export function isWithinBand(units, band) {
  return units >= band.min && units <= band.max
}

// Acepta la coma decimal que escribe un usuario de es-AR.
export function limitPriceUnits(text) {
  return toUnits(text.trim().replace(',', '.'))
}

/** Recorre el lado opuesto del libro, ya ordenado por el backend, como lo haría el motor de calce. */
export function estimateMarket(levels, quantity) {
  let fillable = 0
  let total = 0
  for (const level of levels) {
    if (fillable === quantity) break
    const taken = Math.min(level.quantity, quantity - fillable)
    fillable += taken
    total += taken * priceToUnits(level.price)
  }
  return { fillable, total, unfilled: quantity - fillable }
}

export function estimateLimit(quantity, priceUnits) {
  return quantity * priceUnits
}

function tokens(quantity) {
  return `${formatInteger(quantity)} ${quantity === 1 ? 'token' : 'tokens'}`
}

function executedPart({ side, filledQuantity, averagePrice }) {
  if (filledQuantity === 0) return 'No se ejecutó nada.'
  const verb = side === 'SELL' ? 'Vendiste' : 'Compraste'
  return `${verb} ${tokens(filledQuantity)} a ${formatPrice(priceToUnits(averagePrice))} promedio.`
}

function pendingPart({ status, remainingQuantity, limitPrice }) {
  if (remainingQuantity === 0) return ''
  if (status === 'CANCELLED') {
    return ` Los ${formatInteger(remainingQuantity)} restantes se cancelaron por falta de contraparte.`
  }
  return ` Quedaron ${formatInteger(remainingQuantity)} en el libro a ${formatPrice(priceToUnits(limitPrice))}.`
}

/** Resultado de una orden con lo que informó el mercado, nunca con el estimado (research R6). */
export function summarizeOrder(order) {
  return `${executedPart(order)}${pendingPart(order)}`
}

/** Lo que libera cancelar una orden de la lista: créditos de una compra o tokens de una venta (research R6). */
export function summarizeCancel(order) {
  if (order.side === 'SELL') return `Cancelaste tu venta: se liberaron ${tokens(order.remainingQuantity)}.`
  const credits = order.remainingQuantity * priceToUnits(order.limitPrice)
  return `Cancelaste tu compra: se liberaron ${formatPrice(credits)}.`
}

function validateQuantity(text, unitPrice) {
  if (!QUANTITY_TEXT.test(text)) return 'La cantidad debe ser un número entero mayor a 0'
  if (!Number.isSafeInteger(Number(text) * unitPrice)) return 'La cantidad es demasiado grande.'
  return undefined
}

function validateLimitPrice(text, band) {
  const normalized = text.trim().replace(',', '.')
  if (normalized === '') return 'Ingresá el precio límite'
  if (TOO_MANY_DECIMALS.test(normalized)) return 'El precio puede tener hasta 4 decimales'
  const units = limitPriceUnits(normalized)
  if (units === null || units === 0) return 'El precio debe ser mayor a 0'
  if (band && !isWithinBand(units, band)) {
    return `El precio debe estar entre ${formatPrice(band.min)} y ${formatPrice(band.max)}`
  }
  return undefined
}

/** Errores por campo; vacío si la orden se puede enviar. El precio sólo se valida en LIMIT. */
export function validateDraft(draft, band) {
  const errors = {}
  const isLimit = draft.type === 'LIMIT'
  const priceError = isLimit ? validateLimitPrice(draft.limitPrice, band) : undefined
  // El total más caro posible: el precio límite válido o, sin él, el máximo de la banda.
  const unitPrice = isLimit && !priceError ? limitPriceUnits(draft.limitPrice) : (band?.max ?? UNITS)
  const quantityError = validateQuantity(draft.quantity, unitPrice)
  if (quantityError) errors.quantity = quantityError
  if (priceError) errors.limitPrice = priceError
  return errors
}
