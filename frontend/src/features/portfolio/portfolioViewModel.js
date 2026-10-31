import { resultKind } from './portfolioFormat.js'

const COLORS = ['#1f4fd1', '#e2412f', '#12805c', '#6b3fa0', '#9a7000', '#4a4740']

export function toSummary(portfolio) {
  return {
    balance: portfolio.balance,
    totalCost: portfolio.totalCost,
    totalCurrentValue: portfolio.totalCurrentValue,
    totalProfitLoss: portfolio.totalProfitLoss,
    reservedCredits: portfolio.reservedCredits,
    availableBalance: portfolio.availableBalance,
  }
}

export function toPositions(positions = []) {
  return positions.map((position) => ({ ...position, resultKind: resultKind(position.profitLoss) }))
}

export function toPerformanceData(positions = []) {
  return positions.map(({ playerId, playerName, averagePurchasePrice, marketValue }) => ({
    playerId, playerName, averagePurchasePrice, marketValue,
  }))
}

export function toAllocationData(positions, totalCurrentValue) {
  const safePositions = positions ?? []
  const total = Number(totalCurrentValue)
  const valid = Number.isFinite(total) && total > 0
  const rows = safePositions.map((position, index) => {
    const color = COLORS[index % COLORS.length]
    return {
      playerId: position.playerId,
      playerName: position.playerName,
      currentValue: position.currentValue,
      share: valid ? Number(position.currentValue) / total : null,
      percentage: null,
      color,
      fill: color,
    }
  })
  if (!valid || !rows.length) return rows

  const raw = rows.map((row) => row.share * 1000)
  const tenths = raw.map(Math.floor)
  let remaining = 1000 - tenths.reduce((sum, value) => sum + value, 0)
  const order = raw.map((value, index) => ({ index, fraction: value - tenths[index] }))
    .sort((a, b) => b.fraction - a.fraction || a.index - b.index)
  for (let index = 0; index < remaining; index += 1) tenths[order[index % order.length].index] += 1
  return rows.map((row, index) => ({ ...row, percentage: tenths[index] / 10 }))
}
