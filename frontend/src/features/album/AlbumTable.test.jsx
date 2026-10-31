import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { comparisonPlayers } from '../../test/strategyFixtures.js'
import AlbumTable from './AlbumTable.jsx'

describe('AlbumTable comparison', () => {
  it('shows the same market and Momentum comparison as the cards', () => {
    const players = comparisonPlayers.slice(0, 2).map((player) => ({ ...player, team: 'Club', league: 'LA_LIGA', positions: ['FORWARD'], nationality: 'Argentina', age: 25 }))
    const alternativeQuotes = Object.fromEntries(players.map((player) => [player.id, player.quote]))
    render(<AlbumTable players={players} alternativeQuotes={alternativeQuotes} />)
    const rows = within(screen.getByRole('table')).getAllByRole('row').slice(1)
    expect(rows[0].textContent).toContain('Momentum: 1,2000 cr')
    expect(rows[0].textContent).toContain('+0,2000 cr (+20,00 %)')
    expect(rows[1].textContent).toContain('-0,1000 cr (-9,09 %)')
  })
})
