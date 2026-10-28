import { describe, expect, it } from 'vitest'
import { formatDate, formatDateTime, formatDecimal, formatPercent, formatSignedCredits } from './quoteFormat.js'

// Intl separa con espacios duros; se normalizan para comparar.
const plain = (text) => text.replace(/\s/g, ' ')

describe('quoteFormat', () => {
  it('formats dates in es-AR using UTC', () => {
    const expected = new Intl.DateTimeFormat('es-AR', { dateStyle: 'medium', timeZone: 'UTC' }).format(new Date('2026-10-20T00:00:00Z'))
    expect(formatDate('2026-10-20T18:00:00Z')).toBe(expected)
    expect(formatDate('2026-10-20T23:30:00Z')).toBe(expected)
  })

  it('adds UTC hours and minutes to date-times', () => {
    const text = formatDateTime('2026-10-20T23:30:00Z')
    expect(text).toContain(formatDate('2026-10-20T23:30:00Z'))
    expect(text).toContain('23:30')
  })

  it('formats signed credits', () => {
    expect(plain(formatSignedCredits(0.12))).toBe('+0,12 cr')
    expect(plain(formatSignedCredits(-0.12))).toBe('−0,12 cr')
    expect(plain(formatSignedCredits(0))).toBe('0,00 cr')
  })

  it('formats signed percents', () => {
    expect(plain(formatPercent(10.909))).toBe('+10,91 %')
    expect(plain(formatPercent(-8.8235))).toBe('−8,82 %')
    expect(plain(formatPercent(0))).toBe('0,00 %')
  })

  it('formats decimals with two digits', () => {
    expect(formatDecimal(7.42)).toBe('7,42')
    expect(formatDecimal(7)).toBe('7,00')
  })
})
