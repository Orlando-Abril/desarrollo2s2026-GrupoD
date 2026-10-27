import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import VariationBadge from './VariationBadge.jsx'

const plain = (text) => text.replace(/\s/g, ' ')

afterEach(cleanup)

describe('VariationBadge', () => {
  it('shows a rise with symbol, signed values and an accessible description', () => {
    render(<VariationBadge current={1.22} previous={1.1} />)
    const badge = screen.getByRole('img', { name: 'Subió 0,12 cr (10,91 %)' })
    expect(plain(badge.textContent)).toBe('▲ +0,12 cr (+10,91 %)')
    expect(badge.className).toContain('variation-badge--up')
  })

  it('shows a fall', () => {
    render(<VariationBadge current={1.24} previous={1.36} />)
    const badge = screen.getByRole('img', { name: 'Bajó 0,12 cr (8,82 %)' })
    expect(plain(badge.textContent)).toBe('▼ −0,12 cr (−8,82 %)')
    expect(badge.className).toContain('variation-badge--down')
  })

  it('shows no change', () => {
    render(<VariationBadge current={1.1} previous={1.1} />)
    const badge = screen.getByRole('img', { name: 'Sin cambios' })
    expect(plain(badge.textContent)).toBe('= 0,00 cr (0,00 %)')
    expect(badge.className).toContain('variation-badge--flat')
  })

  it.each([
    [1.1, null],
    [null, 1.1],
    [1.1, 0],
  ])('shows a dash when variation is unavailable (current=%s, previous=%s)', (current, previous) => {
    render(<VariationBadge current={current} previous={previous} />)
    expect(screen.getByRole('img', { name: 'Sin variación disponible' }).textContent).toBe('—')
  })
})
