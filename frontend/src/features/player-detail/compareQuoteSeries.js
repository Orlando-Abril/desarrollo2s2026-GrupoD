// Une varias series por instante: cada punto tiene el valor de cada estrategia (o null) y su estado y versión.
export function compareQuoteSeries(series = []) {
  const points = new Map()
  for (const [code, quotes = []] of series) {
    for (const quote of quotes) {
      const t = Date.parse(quote.calculatedAt)
      const point = points.get(t) ?? { t, calculatedAt: quote.calculatedAt, details: {}, ...Object.fromEntries(series.map(([key]) => [key, null])) }
      point[code] = quote.value
      point.details[code] = { status: quote.status, version: quote.version }
      points.set(t, point)
    }
  }
  return [...points.values()].sort((a, b) => a.t - b.t)
}
