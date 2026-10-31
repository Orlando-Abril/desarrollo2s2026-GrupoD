import { describe, expect, it } from 'vitest'
import { formatCredits, formatDateTime, formatResult, resultKind, resultLabel } from './portfolioFormat.js'

describe('portfolioFormat', () => {
  it('uses es-AR and exactly four decimals', () => expect(formatCredits(1234.5)).toBe('1.234,5000 cr'))
  it.each([[2, 'gain', 'Ganancia', '+2,0000 cr'], [-2, 'loss', 'Pérdida', '-2,0000 cr'], [0, 'neutral', 'Sin variación', '0,0000 cr']])('formats result %s', (value, kind, label, text) => {
    expect(resultKind(value)).toBe(kind); expect(resultLabel(value)).toBe(label); expect(formatResult(value)).toBe(text)
  })
  it('includes date and time in UTC', () => expect(formatDateTime('2026-10-20T18:00:00Z')).toMatch(/20\/10\/2026.*18:00/))
})
