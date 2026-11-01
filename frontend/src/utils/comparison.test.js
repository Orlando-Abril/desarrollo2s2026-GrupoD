import { describe, expect, it } from 'vitest'
import { differenceTone, formatSignedCredits, formatSignedPercent, quoteStatusLabel, strategyName } from './comparison.js'

describe('comparison utils', () => {
  it('names strategies in Spanish and never shows a missing code as text', () => {
    expect(strategyName('OFFICIAL')).toBe('Oficial')
    expect(strategyName('MOMENTUM')).toBe('Momentum')
    expect(strategyName(null)).toBe('sin informar')
  })

  it('translates quote statuses instead of showing the code', () => {
    expect(quoteStatusLabel('INSUFFICIENT_HISTORY')).toBe('Historial insuficiente')
    expect(quoteStatusLabel('NO_STATS')).toBe('Sin estadísticas')
    expect(quoteStatusLabel('CALCULATED')).toBe('Calculada')
    expect(quoteStatusLabel(null)).toBe('Sin estado')
    expect(quoteStatusLabel('OTRO')).toBe('Estado desconocido')
  })

  it('formats signed credits with two decimals and signed percentages', () => {
    expect(formatSignedCredits(0.2)).toBe('+0,20 cr')
    expect(formatSignedCredits(-0.1)).toBe('-0,10 cr')
    expect(formatSignedCredits(0)).toBe('0,00 cr')
    expect(formatSignedPercent(20)).toBe('+20,00 %')
    expect(formatSignedPercent(-9.0909)).toBe('-9,09 %')
    expect(formatSignedPercent(null)).toBe('No disponible')
  })

  it('classifies the difference tone', () => {
    expect([differenceTone(1), differenceTone(-1), differenceTone(0)]).toEqual(['gain', 'loss', 'neutral'])
  })
})
