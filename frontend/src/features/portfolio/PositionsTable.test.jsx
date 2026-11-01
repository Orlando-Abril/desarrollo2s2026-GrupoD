import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it } from 'vitest'
import PositionsTable from './PositionsTable.jsx'
import { position } from './portfolioTestData.js'
import { portfolioPositions } from '../../test/strategyFixtures.js'

afterEach(cleanup)

describe('PositionsTable', () => {
  it('shows four decimals, semantic results, labels and links by id with repeated names', () => {
    const rows = [position({ playerId: 1, profitLoss: 1 }), position({ playerId: 2, profitLoss: -1 }), position({ playerId: 3, profitLoss: 0 })]
    render(<MemoryRouter><PositionsTable positions={rows}/></MemoryRouter>)
    expect(screen.getAllByText('Alex Morgan').map((link) => link.getAttribute('href'))).toEqual(['/jugadores/1', '/jugadores/2', '/jugadores/3'])
    expect(screen.getByText('+1,0000 cr').closest('td').className).toContain('--gain')
    expect(screen.getByText('-1,0000 cr').closest('td').className).toContain('--loss')
    expect(screen.getByText('0,0000 cr').closest('td').className).toContain('--neutral')
    expect(within(screen.getByRole('table')).getAllByRole('columnheader')).toHaveLength(6)
    expect(screen.getAllByText('1,2500 cr').length).toBeGreaterThan(0)
  })
  it('shows Momentum value and result, with missing quotes distinct from zero', () => {
    const rows = portfolioPositions.map((fixture) => position({ ...fixture, playerName: `Jugador ${fixture.playerId}` }))
    rows.push(position({ playerId: 4, strategyValuation: { quotePrice: null, quoteStatus: null, quoteCalculatedAt: null, value: null, profitLoss: null } }))
    render(<MemoryRouter><PositionsTable positions={rows} comparisonLabel="Momentum" /></MemoryRouter>)
    expect(screen.getAllByRole('columnheader')).toHaveLength(7)
    expect(screen.getAllByRole('columnheader')[6].textContent).toBe('Momentum · valor y resultado')
    expect(document.body.textContent).toContain('Sin estadísticas')
    expect(document.body.textContent).not.toContain('NO_STATS')
    expect(document.body.textContent).toContain('12,0000 cr')
    expect(document.body.textContent).toContain('+3,0000 cr')
    expect(document.body.textContent).toContain('No disponible · sin cotización')
  })
  it('shows ten positions at a time with Ver 10 más', () => {
    const rows = Array.from({ length: 23 }, (_, index) => position({ playerId: index + 1, playerName: `Jugador ${index + 1}` }))
    render(<MemoryRouter><PositionsTable positions={rows}/></MemoryRouter>)
    expect(within(screen.getByRole('table')).getAllByRole('link')).toHaveLength(10)
    expect(screen.getByText('Mostrando 10 de 23 posiciones')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'Ver 10 más' }))
    expect(within(screen.getByRole('table')).getAllByRole('link')).toHaveLength(20)
    fireEvent.click(screen.getByRole('button', { name: 'Ver 3 más' }))
    expect(within(screen.getByRole('table')).getAllByRole('link')).toHaveLength(23)
    expect(screen.queryByRole('button', { name: /Ver \d+ más/ })).toBeNull()
  })
  it('does not show the pager with ten positions or fewer', () => {
    render(<MemoryRouter><PositionsTable positions={[position()]}/></MemoryRouter>)
    expect(screen.queryByText(/Mostrando/)).toBeNull()
  })
})
