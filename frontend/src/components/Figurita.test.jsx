import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import Figurita from './Figurita.jsx'
import { comparisonPlayers, insufficientHistoryQuote } from '../test/strategyFixtures.js'
import { formatCredits } from '../utils/format.js'

const two = new Intl.NumberFormat('es-AR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

const player = {
  id: 7,
  fullName: 'Mateo Bronce',
  team: 'Club Horizonte',
  league: 'LA_LIGA',
  positions: ['FORWARD', 'DEFENDER'],
  nationality: 'Argentina',
  age: 24,
  marketValue: 1234.5,
}

afterEach(cleanup)

describe('Figurita', () => {
  it.each(comparisonPlayers)('compares player $id with expected difference', ({ id, fullName, marketValue, quote, expectedDifference, expectedPercent }) => {
    render(<Figurita id={id} fullName={fullName} team="Club" league="LA_LIGA" marketValue={marketValue} alternativeQuote={{ label: 'Momentum', quote }} />)
    const sign = expectedDifference >= 0 ? '+' : ''
    const text = document.body.textContent
    expect(text).toContain(`Momentum${formatCredits(quote.value)}`)
    expect(text).toContain(`Diferencia${sign}${formatCredits(expectedDifference)} (${sign}${two.format(expectedPercent)} %)`)
    expect(text.split(formatCredits(marketValue))).toHaveLength(2)
  })
  it('renders the player contract and fixed position order', () => {
    render(<Figurita {...player} />)
    const card = screen.getByRole('article', { name: 'Mateo Bronce, Club Horizonte' })
    expect(card).toBeTruthy()
    expect(screen.getByText('007')).toBeTruthy()
    expect(screen.getByText('DEF')).toBeTruthy()
    expect(screen.getByText(/1\.234,50 cr/)).toBeTruthy()
    expect(screen.getByText('Mateo Bronce').getAttribute('title')).toBe('Mateo Bronce')
  })

  it('renders nullable nationality and age as dashes', () => {
    render(<Figurita {...player} nationality={null} age={null} />)
    expect(screen.getByText('— · —')).toBeTruthy()
    expect(document.body.textContent).not.toContain('null')
  })

  it('shows alternative quote and difference only when requested', () => {
    const { rerender } = render(<Figurita {...player} />)
    expect(screen.getByRole('article').className).toBe('figurita')
    expect(document.body.textContent).not.toContain('Momentum')
    rerender(<Figurita {...player} alternativeQuote={{ label: 'Momentum', quote: { value: 1235.5, status: 'CALCULATED' } }} />)
    expect(screen.getByRole('article').className).toBe('figurita figurita--comparison')
    expect(document.body.textContent).toContain('Momentum1.235,50 cr')
    expect(document.body.textContent).not.toContain('Mercado actual')
    expect(document.body.textContent).not.toContain('Estado')
    expect(screen.getByText('+1,00 cr').closest('strong').className).toContain('alternative-quote__difference--gain')
  })

  it('marks a negative difference as a loss and keeps the percentage in one piece', () => {
    render(<Figurita {...player} alternativeQuote={{ label: 'Oficial', quote: { value: 1111.05, status: 'CALCULATED' } }} />)
    expect(screen.getByText('-123,45 cr').closest('strong').className).toContain('alternative-quote__difference--loss')
    expect(screen.getByText('(-10,00 %)').tagName).toBe('SPAN')
    expect(document.body.textContent).toContain('Oficial1.111,05 cr')
  })

  it('shows the quote status in Spanish, never the code', () => {
    render(<Figurita {...player} alternativeQuote={{ label: 'Momentum', quote: insufficientHistoryQuote.quote }} />)
    expect(document.body.textContent).toContain('Estado: Historial insuficiente')
    expect(document.body.textContent).not.toContain('INSUFFICIENT_HISTORY')
  })

  it('distinguishes missing alternative from zero', () => {
    render(<Figurita {...player} alternativeQuote={{ label: 'Momentum', quote: null }} />)
    expect(document.body.textContent).toContain('Sin cotización Momentum')
  })
})
