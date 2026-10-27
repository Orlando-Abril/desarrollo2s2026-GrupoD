import { QUOTE_RANGES } from './quoteCatalog.js'

const MAX_PLAYER_ID = '9223372036854775807'
const DAY_MS = 24 * 60 * 60 * 1000

export function todayUtc(now = new Date()) {
  return now.toISOString().slice(0, 10)
}

// Los días son inclusivos, igual que from/to del backend: 30 días terminan hoy y empiezan hace 29.
export function rangeToDates(range, now = new Date()) {
  const days = QUOTE_RANGES[range]?.days
  if (!days) return {}
  return { from: todayUtc(new Date(now.getTime() - (days - 1) * DAY_MS)), to: todayUtc(now) }
}

export function parsePlayerId(raw) {
  if (typeof raw !== 'string' || !/^[1-9]\d*$/.test(raw)) return null
  if (raw.length > MAX_PLAYER_ID.length) return null
  if (raw.length === MAX_PLAYER_ID.length && raw > MAX_PLAYER_ID) return null
  return raw
}

export function findPrevious(history, current) {
  const currentTime = Date.parse(current.calculatedAt)
  return history.findLast((quote) => Date.parse(quote.calculatedAt) < currentTime) ?? null
}

export function variation(currentValue, previousValue) {
  if (currentValue == null || previousValue == null || previousValue === 0) return null
  const delta = currentValue - previousValue
  const rounded = Math.round(delta * 100) / 100
  let direction = 'flat'
  if (rounded > 0) direction = 'up'
  else if (rounded < 0) direction = 'down'
  return { delta, percent: (delta / previousValue) * 100, direction }
}

export function toQuotePoints(quotes) {
  return quotes
    .map(({ value, strategy, version, calculatedAt }) => ({ t: Date.parse(calculatedAt), value, strategy, version, calculatedAt }))
    .sort((a, b) => a.t - b.t)
}

export function hasSameDayPoints(points) {
  const days = points.map((point) => todayUtc(new Date(point.t)))
  return new Set(days).size < days.length
}
