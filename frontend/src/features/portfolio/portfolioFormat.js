const credits = new Intl.NumberFormat('es-AR', {
  minimumFractionDigits: 4,
  maximumFractionDigits: 4,
})

const dateTime = new Intl.DateTimeFormat('es-AR', {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
  timeZone: 'UTC',
})

export function formatCredits(value) {
  return `${credits.format(Number(value))} cr`
}

export function resultKind(value) {
  const number = Number(value)
  if (number > 0) return 'gain'
  if (number < 0) return 'loss'
  return 'neutral'
}

export function resultLabel(value) {
  return { gain: 'Ganancia', loss: 'Pérdida', neutral: 'Sin variación' }[resultKind(value)]
}

export function formatResult(value) {
  const number = Number(value)
  return `${number > 0 ? '+' : ''}${formatCredits(number)}`
}

export function formatDateTime(value) {
  return dateTime.format(new Date(value))
}
