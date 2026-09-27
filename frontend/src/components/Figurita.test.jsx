import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import Figurita from './Figurita.jsx'

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
})
