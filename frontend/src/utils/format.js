const creditsFormatter = new Intl.NumberFormat('es-AR', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

export function formatCredits(value) {
  return `${creditsFormatter.format(value)} cr`
}
