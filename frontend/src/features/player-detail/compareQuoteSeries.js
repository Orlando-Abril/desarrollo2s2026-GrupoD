export function compareQuoteSeries(official = [], momentum = []) {
  const points = new Map()
  for (const [code, quotes] of [['OFFICIAL', official], ['MOMENTUM', momentum]]) {
    for (const quote of quotes) {
      const t = Date.parse(quote.calculatedAt)
      const key = t
      const point = points.get(key) ?? { t, calculatedAt: quote.calculatedAt, OFFICIAL: null, MOMENTUM: null }
      point[code] = quote.value
      point[code === 'OFFICIAL' ? 'officialStatus' : 'momentumStatus'] = quote.status
      point[code === 'OFFICIAL' ? 'officialVersion' : 'momentumVersion'] = quote.version
      points.set(key, point)
    }
  }
  return [...points.values()].sort((a, b) => a.t - b.t)
}
