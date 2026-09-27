const creditsFormatter = new Intl.NumberFormat('es-AR', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

const integerFormatter = new Intl.NumberFormat('es-AR', {
  maximumFractionDigits: 0,
})

export function formatCredits(value) {
  return `${creditsFormatter.format(value)} cr`
}

export function formatInteger(value) {
  return integerFormatter.format(value)
}
