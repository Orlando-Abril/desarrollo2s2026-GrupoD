import { ApiError } from '../../api/httpClient.js'

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

const GENERIC = {
  send: 'No pudimos enviar la orden. Probá de nuevo.',
  cancel: 'No pudimos cancelar la orden. Probá de nuevo.',
}

const STALE = {
  price_out_of_band: 'book',
  order_closed: 'orders',
  order_not_found: 'orders',
  order_forbidden: 'orders',
}

function codeOf(error) {
  return error instanceof ApiError ? error.code : null
}

// Decide sólo por el código del mercado; nunca muestra el texto del servidor (research R7).
export function marketErrorMessage(error, { action = 'send' } = {}) {
  return MESSAGES[codeOf(error)] ?? GENERIC[action]
}

export function hasMarketMessage(error) {
  return codeOf(error) in MESSAGES
}

export function staleTarget(error) {
  return STALE[codeOf(error)] ?? null
}
