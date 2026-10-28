import { formatCredits } from '../../utils/format.js'

const decimalFormatter = new Intl.NumberFormat('es-AR', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

const dateFormatter = new Intl.DateTimeFormat('es-AR', { dateStyle: 'medium', timeZone: 'UTC' })

const timeFormatter = new Intl.DateTimeFormat('es-AR', {
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
  timeZone: 'UTC',
})

const MINUS = '−'

export function formatDecimal(value) {
  return decimalFormatter.format(value)
}

export function formatDate(iso) {
  return dateFormatter.format(new Date(iso))
}

export function formatDateTime(iso) {
  const date = new Date(iso)
  return `${dateFormatter.format(date)} ${timeFormatter.format(date)} UTC`
}

// Signo explícito: "+" para suba, "−" tipográfico para baja y sin signo para cero.
function signed(value, text) {
  if (value > 0) return `+${text}`
  if (value < 0) return `${MINUS}${text}`
  return text
}

export function formatSignedCredits(value) {
  const rounded = Math.round(value * 100) / 100
  return signed(rounded, formatCredits(Math.abs(rounded)))
}

export function formatPercent(value) {
  const rounded = Math.round(value * 100) / 100
  return signed(rounded, `${decimalFormatter.format(Math.abs(rounded))} %`)
}
