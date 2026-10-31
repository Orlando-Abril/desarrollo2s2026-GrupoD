import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import PerformanceChart from './PerformanceChart.jsx'
import { prefersReducedMotion } from './portfolioMotion.js'
import { position } from './portfolioTestData.js'

afterEach(cleanup)

vi.mock('recharts', async (importOriginal) => {
  const { cloneElement } = await import('react')
  return { ...(await importOriginal()), ResponsiveContainer: ({ children }) => <div>{cloneElement(children, { width: 800, height: 300 })}</div> }
})

describe('PerformanceChart', () => {
  it('has a named figure, textual legend and a keyboard-disclosable equivalent table', () => {
    render(<PerformanceChart positions={[position()]}/>)
    const caption = 'Precio promedio y cotización actual por jugador · 1 posiciones'
    expect(screen.getByRole('img', { name: caption })).toBeTruthy()
    expect(screen.getByText(caption, { selector: 'figcaption' })).toBeTruthy()
    expect(screen.getByText('Precio promedio', { selector: 'li' })).toBeTruthy()
    const summary = screen.getByText('Ver datos en tabla', { selector: 'summary' })
    fireEvent.keyDown(summary, { key: 'Enter' }); fireEvent.click(summary)
    expect(summary.parentElement.open).toBe(true)
    expect(within(screen.getByRole('table')).getAllByRole('columnheader').map((node) => node.textContent)).toEqual(['Jugador', 'Precio promedio', 'Cotización actual'])
  })
  it('detects the reduced-motion preference used to disable chart animation', () => {
    vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: true })))
    expect(prefersReducedMotion()).toBe(true)
    expect(matchMedia).toHaveBeenCalledWith('(prefers-reduced-motion: reduce)')
    vi.unstubAllGlobals()
  })
})
