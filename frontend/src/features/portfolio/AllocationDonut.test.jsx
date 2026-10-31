import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import AllocationDonut from './AllocationDonut.jsx'
import { prefersReducedMotion } from './portfolioMotion.js'
import { position } from './portfolioTestData.js'

afterEach(cleanup)

vi.mock('recharts', async (importOriginal) => {
  const { cloneElement } = await import('react')
  return { ...(await importOriginal()), ResponsiveContainer: ({ children }) => <div>{cloneElement(children, { width: 800, height: 300 })}</div> }
})

describe('AllocationDonut', () => {
  it('presents 60/40 and a table disclosed by keyboard', () => {
    render(<AllocationDonut positions={[position({ playerId: 1, playerName: 'Igual', currentValue: 6 }), position({ playerId: 2, playerName: 'Igual', currentValue: 4 })]} totalCurrentValue={10}/>)
    expect(screen.getByText(/Igual: 60,0%/)).toBeTruthy(); expect(screen.getByText(/Igual: 40,0%/)).toBeTruthy()
    const summary = screen.getByText('Ver datos en tabla', { selector: 'summary' }); fireEvent.keyDown(summary, { key: 'Enter' }); fireEvent.click(summary)
    expect(summary.parentElement.open).toBe(true); expect(screen.getAllByText('Igual').length).toBeGreaterThan(1)
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
    vi.unstubAllGlobals()
  })
})
