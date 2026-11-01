import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'

const getOpportunities = vi.fn()
vi.mock('../../api/comparisonApi.js', () => ({ getOpportunities }))
const { default: OpportunitiesSection } = await import('./OpportunitiesSection.jsx')
afterEach(() => { cleanup(); getOpportunities.mockReset() })

describe('OpportunitiesSection', () => {
  it('shows both server ordered lists and links to the existing trade panel', async () => {
    getOpportunities.mockResolvedValue({ activeStrategy: { code: 'OFFICIAL' }, alternativeStrategy: { code: 'MOMENTUM' }, marketStrategy: { code: 'OFFICIAL' }, pendingRecalculation: false, undervalued: [{ playerId: 7, name: 'Uno', marketValue: 1, alternativeQuote: 1.2, difference: 0.2, differencePercent: 20 }], overvalued: [{ playerId: 8, name: 'Dos', marketValue: 2, alternativeQuote: 1.5, difference: -0.5, differencePercent: -25 }] })
    render(<MemoryRouter><OpportunitiesSection /></MemoryRouter>)
    await waitFor(() => expect(screen.getAllByRole('link', { name: 'Operar' })).toHaveLength(2))
    expect(screen.getAllByRole('link', { name: 'Operar' }).map((link) => link.getAttribute('href'))).toEqual(['/jugadores/7#operar', '/jugadores/8#operar'])
    expect(document.body.textContent).toContain('Subvaluados')
    expect(document.body.textContent).toContain('Sobrevaluados')
    expect(document.body.textContent).toContain('Mercado actual (Oficial): 1,00 cr')
    expect(document.body.textContent).toContain('Cotización alternativa (Momentum): 1,20 cr')
    expect(document.body.textContent).toContain('Diferencia: +0,20 cr (+20,00 %)')
    expect(document.body.textContent).toContain('Diferencia: -0,50 cr (-25,00 %)')
    expect(document.body.textContent).not.toMatch(/OFFICIAL|MOMENTUM/)
    expect(screen.getAllByRole('listitem').every((item) => item.className === 'opportunity-card')).toBe(true)
    expect(screen.getByText('+0,20 cr', { exact: false }).className).toContain('--gain')
    expect(screen.getByRole('heading', { level: 2, name: 'Oportunidades' })).toBeTruthy()
  })
  it('filters independently and explains pending recalculation', async () => {
    getOpportunities.mockResolvedValue({ activeStrategy: { code: 'MOMENTUM' }, alternativeStrategy: { code: 'OFFICIAL' }, marketStrategy: { code: 'OFFICIAL' }, pendingRecalculation: true, undervalued: [], overvalued: [] })
    render(<MemoryRouter><OpportunitiesSection /></MemoryRouter>)
    await waitFor(() => expect(document.body.textContent).toContain('recálculo pendiente'))
    fireEvent.change(screen.getByLabelText('Liga de oportunidades'), { target: { value: 'LA_LIGA' } })
    await waitFor(() => expect(getOpportunities).toHaveBeenLastCalledWith({ league: 'LA_LIGA', position: '' }, expect.anything()))
    expect(document.body.textContent).toContain('No hay oportunidades')
  })
  it('offers a retry after a network error', async () => {
    getOpportunities.mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce({ activeStrategy: { code: 'OFFICIAL' }, alternativeStrategy: { code: 'MOMENTUM' }, marketStrategy: { code: 'OFFICIAL' }, pendingRecalculation: false, undervalued: [], overvalued: [] })
    render(<MemoryRouter><OpportunitiesSection /></MemoryRouter>)
    await screen.findByText('No pudimos cargar las oportunidades')
    fireEvent.click(screen.getByRole('button', { name: 'Reintentar' }))
    await waitFor(() => expect(document.body.textContent).toContain('No hay oportunidades'))
  })
})
