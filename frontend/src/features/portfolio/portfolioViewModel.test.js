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
  it('normalizes omitted, undefined and empty positions without changing the public argument order', () => {
    expect(toAllocationData()).toEqual([])
    expect(toAllocationData(undefined, 10)).toEqual([])
    expect(toAllocationData([], 10)).toEqual([])
  })
  it('preserves order and colors while distributing rounded tenths to exactly 100%', () => {
    const source = [
      position({ playerId: 3, currentValue: 1 }),
      position({ playerId: 1, currentValue: 1 }),
      position({ playerId: 2, currentValue: 1 }),
    ]
    const data = toAllocationData(source, 3)
    expect(data.map((row) => row.playerId)).toEqual([3, 1, 2])
    expect(data.map((row) => row.color)).toEqual(['#1f4fd1', '#e2412f', '#12805c'])
    expect(data.map((row) => row.fill)).toEqual(data.map((row) => row.color))
    expect(data.map((row) => row.share)).toEqual([1 / 3, 1 / 3, 1 / 3])
    expect(data.map((row) => row.percentage)).toEqual([33.4, 33.3, 33.3])
    expect(data.reduce((sum, row) => sum + row.percentage, 0)).toBeCloseTo(100, 10)
  })
  it('keeps the ten most valuable positions in the charts and groups the rest as Otros', () => {
    const source = Array.from({ length: 15 }, (_, index) => position({ playerId: index + 1, playerName: `J${index + 1}`, currentValue: index + 1 }))
    expect(toPerformanceData(source).map((row) => row.playerId)).toEqual([15, 14, 13, 12, 11, 10, 9, 8, 7, 6])
    const allocation = toAllocationData(source, 120)
    expect(allocation).toHaveLength(10)
    expect(allocation.slice(0, 9).map((row) => row.playerId)).toEqual([15, 14, 13, 12, 11, 10, 9, 8, 7])
    expect(allocation[9]).toMatchObject({ playerId: 'otros', playerName: 'Otros (6 jugadores)', currentValue: 21 })
    expect(allocation.reduce((sum, row) => sum + row.percentage, 0)).toBeCloseTo(100, 5)
  })
})
