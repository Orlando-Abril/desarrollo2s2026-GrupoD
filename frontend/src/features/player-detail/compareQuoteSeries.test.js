import { describe, expect, it } from 'vitest'
import { compareQuoteSeries } from './compareQuoteSeries.js'

describe('compareQuoteSeries', () => {
  it('joins by instant, leaves absent values null and keeps metadata', () => {
    const result = compareQuoteSeries(
      [{ value: 1, strategy: 'OFFICIAL', version: 2, status: 'CALCULATED', calculatedAt: '2026-10-02T00:00:00Z' }],
      [{ value: 1.2, strategy: 'MOMENTUM', version: 1, status: 'NO_STATS', calculatedAt: '2026-10-01T00:00:00Z' }],
    )
    expect(result.map((point) => point.calculatedAt)).toEqual(['2026-10-01T00:00:00Z', '2026-10-02T00:00:00Z'])
    expect(result[0]).toMatchObject({ OFFICIAL: null, MOMENTUM: 1.2, momentumStatus: 'NO_STATS' })
    expect(result[1]).toMatchObject({ OFFICIAL: 1, MOMENTUM: null, officialVersion: 2 })
  })
  it('coalesces different offsets that describe the same instant', () => {
    const result = compareQuoteSeries(
      [{ value: 1, calculatedAt: '2026-10-01T00:00:00Z' }],
      [{ value: 1.2, calculatedAt: '2026-09-30T21:00:00-03:00' }],
    )
    expect(result).toHaveLength(1)
    expect(result[0]).toMatchObject({ OFFICIAL: 1, MOMENTUM: 1.2 })
  })
})
