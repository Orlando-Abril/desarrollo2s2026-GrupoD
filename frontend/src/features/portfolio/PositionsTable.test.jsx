import { cleanup, render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it } from 'vitest'
import PositionsTable from './PositionsTable.jsx'
import { position } from './portfolioTestData.js'

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
})
