const priceFormatter = new Intl.NumberFormat('es-AR', {
  minimumFractionDigits: 4,
  maximumFractionDigits: 4,
})

const SIDES = { BUY: 'Compra', SELL: 'Venta' }
const TYPES = { MARKET: 'Al mercado', LIMIT: 'Límite' }
const STATUSES = { OPEN: 'Abierta', PARTIALLY_FILLED: 'Parcial', FILLED: 'Ejecutada', CANCELLED: 'Cancelada' }

export function formatPriceNumber(units) {
  return priceFormatter.format(units / 10000)
}

export function formatPrice(units) {
  return `${formatPriceNumber(units)} cr`
}

export function sideLabel(side) {
  return SIDES[side] ?? side
}

export function typeLabel(type) {
  return TYPES[type] ?? type
}

export function statusLabel(status) {
  return STATUSES[status] ?? status
}
