import { formatCredits } from './format.js'

const STRATEGY_NAMES = { OFFICIAL: 'Oficial', MOMENTUM: 'Momentum' }

const QUOTE_STATUS_LABELS = {
  CALCULATED: 'Calculada',
  INSUFFICIENT_HISTORY: 'Historial insuficiente',
  NO_STATS: 'Sin estadísticas',
}

const percentFormatter = new Intl.NumberFormat('es-AR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

export function strategyName(code) {
  if (!code) return 'sin informar'
  return STRATEGY_NAMES[code] ?? code
}

export function quoteStatusLabel(status) {
  if (!status) return 'Sin estado'
  return QUOTE_STATUS_LABELS[status] ?? 'Estado desconocido'
}

function sign(value) {
  return Number(value) > 0 ? '+' : ''
}

export function formatSignedCredits(value) {
  return `${sign(value)}${formatCredits(Number(value))}`
}

export function formatSignedPercent(value) {
  if (value == null || !Number.isFinite(Number(value))) return 'No disponible'
  return `${sign(value)}${percentFormatter.format(Number(value))} %`
}

export function differenceTone(value) {
  const number = Number(value)
  if (number > 0) return 'gain'
  if (number < 0) return 'loss'
  return 'neutral'
}
