export const activeOfficial = {
  active: { code: 'OFFICIAL', version: 1, label: 'OFFICIAL v1' },
  alternative: { code: 'MOMENTUM', version: 1, label: 'MOMENTUM v1' },
  marketStrategy: { code: 'OFFICIAL', version: 1, label: 'OFFICIAL v1' },
  pendingRecalculation: false,
  changedAt: null,
  changedBy: null,
}

export const pendingMomentum = {
  ...activeOfficial,
  active: { code: 'MOMENTUM', version: 1, label: 'MOMENTUM v1' },
  alternative: { code: 'OFFICIAL', version: 1, label: 'OFFICIAL v1' },
  pendingRecalculation: true,
}

export const marketMomentum = {
  ...pendingMomentum,
  marketStrategy: { code: 'MOMENTUM', version: 1, label: 'MOMENTUM v1' },
  pendingRecalculation: false,
}

// Casos esperados independientes del cálculo de la interfaz: mercado, Momentum, diferencia y porcentaje.
const comparisonCases = [
  [1.0, 1.2, 0.2, 20], [1.1, 1.0, -0.1, -9.09],
  [1.2, 1.4, 0.2, 16.67], [1.3, 1.2, -0.1, -7.69],
  [1.4, 1.6, 0.2, 14.29], [1.5, 1.4, -0.1, -6.67],
  [1.6, 1.8, 0.2, 12.5], [1.7, 1.6, -0.1, -5.88],
  [1.8, 2.0, 0.2, 11.11], [1.9, 1.8, -0.1, -5.26],
  [2.0, 2.2, 0.2, 10], [2.1, 2.0, -0.1, -4.76],
  [2.2, 2.4, 0.2, 9.09], [2.3, 2.2, -0.1, -4.35],
  [2.4, 2.6, 0.2, 8.33], [2.5, 2.4, -0.1, -4],
  [2.6, 2.8, 0.2, 7.69], [2.7, 2.6, -0.1, -3.7],
  [2.8, 3.0, 0.2, 7.14], [2.9, 2.8, -0.1, -3.45],
]

export const comparisonPlayers = comparisonCases.map(([marketValue, momentumValue, expectedDifference, expectedPercent], index) => ({
  id: index + 1,
  fullName: `Jugador ${index + 1}`,
  marketValue,
  quote: { value: momentumValue, status: 'CALCULATED', strategyVersion: 1, calculatedAt: '2026-10-30T12:00:00Z' },
  expectedDifference,
  expectedPercent,
}))

export const portfolioPositions = [
  { playerId: 1, quantity: 10, costBasis: 9, marketValue: 1, currentValue: 10, profitLoss: 1, strategyValuation: { quotePrice: 1.2, quoteStatus: 'CALCULATED', quoteCalculatedAt: '2026-10-30T12:00:00Z', value: 12, profitLoss: 3 } },
  { playerId: 2, quantity: 5, costBasis: 10, marketValue: 2, currentValue: 10, profitLoss: 0, strategyValuation: { quotePrice: 1.5, quoteStatus: 'CALCULATED', quoteCalculatedAt: '2026-10-30T12:00:00Z', value: 7.5, profitLoss: -2.5 } },
  { playerId: 3, quantity: 2, costBasis: 3, marketValue: 1.8, currentValue: 3.6, profitLoss: 0.6, strategyValuation: { quotePrice: 2, quoteStatus: 'NO_STATS', quoteCalculatedAt: '2026-10-30T12:00:00Z', value: 4, profitLoss: 1 } },
]

export const missingMomentumQuote = { playerId: 4, quote: null }
export const insufficientHistoryQuote = { playerId: 5, quote: { value: 1, status: 'INSUFFICIENT_HISTORY', strategyVersion: 1, calculatedAt: '2026-10-30T12:00:00Z' } }
