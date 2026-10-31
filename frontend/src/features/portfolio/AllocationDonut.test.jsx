import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import AllocationDonut from './AllocationDonut.jsx'
import allocationDonutSource from './AllocationDonut.jsx?raw'
import { prefersReducedMotion } from './portfolioMotion.js'
import { position } from './portfolioTestData.js'

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})

vi.mock('recharts', async (importOriginal) => {
  const { cloneElement } = await import('react')
  return { ...(await importOriginal()), ResponsiveContainer: ({ children }) => <div>{cloneElement(children, { width: 800, height: 300 })}</div> }
})

describe('AllocationDonut', () => {
  it('uses Recharts sectors instead of the deprecated Cell API and preserves each data color', () => {
    vi.stubGlobal('matchMedia', vi.fn(() => ({
      matches: true,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })))
    const { container } = render(<AllocationDonut positions={[
      position({ playerId: 1, playerName: 'Primero', currentValue: 6 }),
      position({ playerId: 2, playerName: 'Segundo', currentValue: 4 }),
    ]} totalCurrentValue={10}/>)
    expect(allocationDonutSource).not.toMatch(/\bCell\b/)
    const sectors = [...container.querySelectorAll('path.recharts-sector')]
    expect(sectors).toHaveLength(2)
    expect(sectors.map((sector) => sector.getAttribute('fill'))).toEqual(['#1f4fd1', '#e2412f'])
  })
  it('presents 60/40 and a table disclosed by keyboard', () => {
    render(<AllocationDonut positions={[position({ playerId: 1, playerName: 'Igual', currentValue: 6 }), position({ playerId: 2, playerName: 'Igual', currentValue: 4 })]} totalCurrentValue={10}/>)
    const legend = screen.getByRole('list', { name: 'Leyenda' })
    expect(within(legend).getAllByRole('listitem').map((item) => item.textContent)).toEqual(['Igual: 60,0%', 'Igual: 40,0%'])
    const summary = screen.getByText('Ver datos en tabla', { selector: 'summary' }); fireEvent.keyDown(summary, { key: 'Enter' }); fireEvent.click(summary)
    expect(summary.parentElement.open).toBe(true)
    const table = screen.getByRole('table')
    expect(within(table).getAllByRole('columnheader').map((header) => header.textContent)).toEqual(['Jugador', 'Valor actual', 'Participación'])
    expect(within(table).getAllByRole('row').slice(1).map((row) => row.textContent)).toEqual(['Igual6,0000 cr60,0%', 'Igual4,0000 cr40,0%'])
  })
  it('shows one position as 100% and satisfies the 0.1 percentage-point tolerance', () => {
    render(<AllocationDonut positions={[position({ currentValue: 7 })]} totalCurrentValue={7}/>)
    const shown = screen.getByText(/Alex Morgan: 100,0%/).textContent.match(/([0-9]+,[0-9])%/)[1]
    expect(Math.abs(Number(shown.replace(',', '.')) - 100)).toBeLessThanOrEqual(.1)
  })
  it('does not expose an image or invalid percentage when total is zero', () => {
    render(<AllocationDonut positions={[position({ currentValue: 0 })]} totalCurrentValue={0}/>)
    expect(screen.queryByRole('img')).toBeNull(); expect(screen.getByText(/valor actual total es cero/)).toBeTruthy(); expect(screen.queryByText(/NaN|Infinity/)).toBeNull()
  })
  it('detects reduced motion for the donut animation', () => {
    vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: true })))
    expect(prefersReducedMotion()).toBe(true)
  })
})
