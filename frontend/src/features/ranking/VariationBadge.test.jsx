import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import VariationBadge from './VariationBadge.jsx'

afterEach(cleanup)

describe('VariationBadge', () => {
  it('renders a positive variation as an accessible rise', () => {
    render(<VariationBadge value={12.5} />)

    const badge = screen.getByLabelText('Subió 12,5 por ciento')
    expect(badge.textContent).toContain('+12,5 %')
    expect(badge.classList.contains('variation-badge--up')).toBe(true)
  })

  it('renders a negative variation as an accessible fall', () => {
    render(<VariationBadge value={-3.25} />)

    const badge = screen.getByLabelText('Bajó 3,25 por ciento')
    expect(badge.textContent).toContain('-3,25 %')
    expect(badge.classList.contains('variation-badge--down')).toBe(true)
  })

  it('distinguishes an unchanged value from missing data', () => {
    const { rerender } = render(<VariationBadge value={0} />)

    expect(screen.getByLabelText('Sin cambios').textContent).toContain('0 %')
    expect(screen.getByLabelText('Sin cambios').classList.contains('variation-badge--flat')).toBe(true)

    rerender(<VariationBadge value={null} />)

    expect(screen.getByLabelText('Sin dato').textContent).toContain('Sin dato')
    expect(screen.getByLabelText('Sin dato').classList.contains('variation-badge--unknown')).toBe(true)
  })
})
