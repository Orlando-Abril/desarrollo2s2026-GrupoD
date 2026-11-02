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

// Con muchas posiciones (el superusuario tiene una por jugador) los gráficos se vuelven ilegibles:
// se muestran las de mayor valor actual y, en la dona, el resto se agrupa en "Otros".
export const CHART_LIMIT = 10

function byCurrentValueDesc(a, b) {
  return Number(b.currentValue) - Number(a.currentValue)
}

export function topPositions(positions = [], limit = CHART_LIMIT) {
  if (positions.length <= limit) return positions
  return [...positions].sort(byCurrentValueDesc).slice(0, limit)
}

function groupOthers(positions) {
  if (positions.length <= CHART_LIMIT) return positions
  const sorted = [...positions].sort(byCurrentValueDesc)
  const kept = sorted.slice(0, CHART_LIMIT - 1)
  const rest = sorted.slice(CHART_LIMIT - 1)
  const othersValue = rest.reduce((sum, position) => sum + Number(position.currentValue || 0), 0)
  return [...kept, { playerId: 'otros', playerName: `Otros (${rest.length} jugadores)`, currentValue: othersValue }]
}

export function toPerformanceData(positions = []) {
  return topPositions(positions).map(({ playerId, playerName, averagePurchasePrice, marketValue }) => ({
    playerId, playerName, averagePurchasePrice, marketValue,
  }))
}

export function toAllocationData(positions, totalCurrentValue) {
  const safePositions = groupOthers(positions ?? [])
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
