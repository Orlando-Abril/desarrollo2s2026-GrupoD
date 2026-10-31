import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import Figurita from './Figurita.jsx'
import { comparisonPlayers } from '../test/strategyFixtures.js'

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
    render(<Figurita id={id} fullName={fullName} team="Club" league="LA_LIGA" marketValue={marketValue} alternativeQuote={quote} />)
    const four = new Intl.NumberFormat('es-AR', { minimumFractionDigits: 4, maximumFractionDigits: 4 })
    const two = new Intl.NumberFormat('es-AR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
    expect(document.body.textContent).toContain(`Mercado actual: ${four.format(marketValue)} cr`)
    expect(document.body.textContent).toContain(`Momentum: ${four.format(quote.value)} cr`)
    expect(document.body.textContent).toContain(`${expectedDifference >= 0 ? '+' : ''}${four.format(expectedDifference)} cr (${expectedPercent >= 0 ? '+' : ''}${two.format(expectedPercent)} %)`)
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
    expect(document.body.textContent).not.toContain('Momentum')
    rerender(<Figurita {...player} alternativeQuote={{ value: 1235.5, status: 'CALCULATED' }} />)
    expect(document.body.textContent).toContain('Momentum')
    expect(document.body.textContent).toContain('Mercado actual')
    expect(document.body.textContent).toContain('+1,0000')
  })

  it('distinguishes missing alternative from zero', () => {
    render(<Figurita {...player} alternativeQuote={null} />)
    expect(document.body.textContent).toContain('Sin cotización')
  })
})
