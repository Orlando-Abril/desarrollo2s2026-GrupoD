import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it } from 'vitest'
import RankingTable from './RankingTable.jsx'

afterEach(cleanup)

const entry = (id, positions) => ({
  rank: id, value: 1, percentageChange: null, strategy: 'MOMENTUM', version: 1,
  player: { id, name: `Jugador ${id}`, team: 'Club', league: 'BUNDESLIGA', positions },
})

describe('RankingTable', () => {
  it('shows a dash for players without a reported position instead of failing', () => {
    render(<MemoryRouter><RankingTable items={[entry(1, []), entry(2, ['FORWARD'])]} /></MemoryRouter>)
    expect(screen.getByLabelText('Sin posición informada').textContent).toBe('—')
    expect(screen.getByText('Jugador 2')).toBeTruthy()
  })
})
