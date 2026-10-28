import { describe, expect, it } from 'vitest'
import {
  findPrevious,
  hasSameDayPoints,
  parsePlayerId,
  rangeToDates,
  todayUtc,
  toQuotePoints,
  variation,
} from './quoteUtils.js'

const NOW = new Date('2026-10-27T03:00:00Z')

function quote(calculatedAt, value = 1, overrides = {}) {
  return { value, score: null, status: 'CALCULATED', strategy: 'OFFICIAL', version: 1, calculatedAt, ...overrides }
}

describe('quoteUtils', () => {
  it('formats today in UTC', () => {
    expect(todayUtc(NOW)).toBe('2026-10-27')
    expect(todayUtc(new Date('2026-10-27T23:59:00-03:00'))).toBe('2026-10-28')
  })

  it.each([
    ['month', { from: '2026-09-28', to: '2026-10-27' }],
    ['quarter', { from: '2026-07-30', to: '2026-10-27' }],
    ['season', {}],
  ])('maps the %s range to UTC dates', (range, expected) => {
    expect(rangeToDates(range, NOW)).toEqual(expected)
  })

  it.each(['1', '42', '9223372036854775807'])('accepts the player id %s', (raw) => {
    expect(parsePlayerId(raw)).toBe(raw)
  })

  it.each(['0', '-1', 'abc', '01', '1.5', '', undefined, '9223372036854775808', '99999999999999999999'])(
    'rejects the player id %s',
    (raw) => {
      expect(parsePlayerId(raw)).toBeNull()
    },
  )

  it('finds the last quote calculated before the current one', () => {
    const history = [quote('2026-10-01T18:00:00Z', 1), quote('2026-10-08T18:00:00Z', 1.1), quote('2026-10-15T18:00:00.000Z', 1.2)]
    expect(findPrevious(history, history[2])).toBe(history[1])
    expect(findPrevious(history, quote('2026-10-15T18:00:00Z', 1.2))).toBe(history[1])
    expect(findPrevious(history, history[0])).toBeNull()
    expect(findPrevious([], history[0])).toBeNull()
  })

  it('compares timestamps with different fractional precision as dates', () => {
    const history = [quote('2026-10-08T18:00:00.5Z', 1.1), quote('2026-10-08T18:00:00.123456Z', 1)]
    expect(findPrevious(history, quote('2026-10-08T18:00:00.2Z'))).toBe(history[1])
  })

  it('returns null variation when a value is missing or the previous is zero', () => {
    expect(variation(null, 1)).toBeNull()
    expect(variation(1, null)).toBeNull()
    expect(variation(1, 0)).toBeNull()
  })

  it('computes delta, percent and direction', () => {
    expect(variation(1.22, 1.1)).toMatchObject({ direction: 'up' })
    expect(variation(1.22, 1.1).delta).toBeCloseTo(0.12)
    expect(variation(1.22, 1.1).percent).toBeCloseTo(10.909, 2)
    expect(variation(1.24, 1.36)).toMatchObject({ direction: 'down' })
    expect(variation(1.10001, 1.1)).toMatchObject({ direction: 'flat' })
    expect(variation(1.1, 1.1)).toMatchObject({ delta: 0, percent: 0, direction: 'flat' })
  })

  it('builds chronological quote points', () => {
    const quotes = [
      quote('2026-10-15T18:00:00Z', 1.3, { version: 2 }),
      quote('2026-10-01T18:00:00Z', 1.1),
      quote('2026-10-08T18:00:00Z', 1.2, { status: 'NO_STATS' }),
    ]
    expect(toQuotePoints(quotes)).toEqual([
      { t: Date.parse('2026-10-01T18:00:00Z'), value: 1.1, strategy: 'OFFICIAL', version: 1, calculatedAt: '2026-10-01T18:00:00Z' },
      { t: Date.parse('2026-10-08T18:00:00Z'), value: 1.2, strategy: 'OFFICIAL', version: 1, calculatedAt: '2026-10-08T18:00:00Z' },
      { t: Date.parse('2026-10-15T18:00:00Z'), value: 1.3, strategy: 'OFFICIAL', version: 2, calculatedAt: '2026-10-15T18:00:00Z' },
    ])
  })

  it('detects points sharing a UTC day', () => {
    expect(hasSameDayPoints(toQuotePoints([quote('2026-10-01T01:00:00Z'), quote('2026-10-01T23:00:00Z')]))).toBe(true)
    expect(hasSameDayPoints(toQuotePoints([quote('2026-10-01T23:00:00Z'), quote('2026-10-02T01:00:00Z')]))).toBe(false)
    expect(hasSameDayPoints([])).toBe(false)
  })
})
