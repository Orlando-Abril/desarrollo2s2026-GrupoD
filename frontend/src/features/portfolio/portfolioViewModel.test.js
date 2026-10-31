import { describe, expect, it } from 'vitest'
import { position } from './portfolioTestData.js'
import { toAllocationData, toPerformanceData, toPositions, toSummary } from './portfolioViewModel.js'

describe('portfolioViewModel', () => {
  it('preserves contractual values and order without recomputing totals', () => {
    const source = [position({ playerId: 2, playerName: 'B', profitLoss: -1 }), position({ playerId: 1, playerName: 'A', profitLoss: 0 })]
    expect(toPositions(source).map(({ playerId, resultKind }) => [playerId, resultKind])).toEqual([[2, 'loss'], [1, 'neutral']])
    expect(toPerformanceData(source).map((row) => row.playerId)).toEqual([2, 1])
    expect(toSummary({ balance: 8, totalCost: 9, totalCurrentValue: 10, totalProfitLoss: 1, reservedCredits: 2, availableBalance: 6 })).toEqual({ balance: 8, totalCost: 9, totalCurrentValue: 10, totalProfitLoss: 1, reservedCredits: 2, availableBalance: 6 })
  })
  it('calculates shares only with a finite positive total and displays within 0.1pp', () => {
    const source = [position({ playerId: 1, currentValue: 6 }), position({ playerId: 2, currentValue: 4 })]
    const data = toAllocationData(source, 10)
    expect(data.map((row) => row.percentage)).toEqual([60, 40])
    expect(Math.abs(data.reduce((sum, row) => sum + row.percentage, 0) - 100)).toBeLessThanOrEqual(.1)
    expect(toAllocationData(source, 0).every((row) => row.share === null && row.percentage === null)).toBe(true)
    expect(toAllocationData(source, Number.NaN).every((row) => row.share === null)).toBe(true)
  })
})
