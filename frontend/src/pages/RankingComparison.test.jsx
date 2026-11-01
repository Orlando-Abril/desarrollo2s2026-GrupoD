import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'

const rankingReload = vi.fn()
const refreshConfig = vi.fn()
vi.mock('../features/ranking/useRanking.js', () => ({ default: () => ({ status: 'ready', total: 0, filters: {}, changeFilter: vi.fn(), effectiveStrategy: 'OFFICIAL', reload: rankingReload }) }))
vi.mock('../context/StrategyViewContext.jsx', () => ({ useStrategyView: () => ({ refreshConfig }) }))
vi.mock('../features/admin/ActiveStrategyControl.jsx', () => ({ default: ({ onChanged, onRecalculate }) => <><button type="button" onClick={onChanged}>Mock cambiar estrategia</button><button type="button" onClick={onRecalculate}>Mock recalcular ahora</button></> }))
vi.mock('../features/ranking/RecalculateButton.jsx', () => ({ default: ({ onRecalculated, buttonRef }) => <button type="button" ref={buttonRef} onClick={onRecalculated}>Mock recalcular</button> }))
vi.mock('../features/ranking/OpportunitiesSection.jsx', () => ({ default: ({ refreshKey }) => <p>Oportunidades versión {refreshKey}</p> }))
const { default: RankingPage } = await import('./RankingPage.jsx')

afterEach(() => { cleanup(); vi.clearAllMocks() })

describe('Ranking comparison refresh', () => {
  it('refreshes opportunities after PUT and strategy, opportunities and market after recalculation', () => {
    render(<MemoryRouter><RankingPage /></MemoryRouter>)
    expect(screen.getByText('Oportunidades versión 0')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'Mock cambiar estrategia' }))
    expect(screen.getByText('Oportunidades versión 1')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'Mock recalcular' }))
    expect(screen.getByText('Oportunidades versión 2')).toBeTruthy()
    expect(refreshConfig).toHaveBeenCalledTimes(1)
    expect(rankingReload).toHaveBeenCalledTimes(1)
  })
  it('runs the existing recalculation flow when the Toast offers to recalculate now', () => {
    render(<MemoryRouter><RankingPage /></MemoryRouter>)
    fireEvent.click(screen.getByRole('button', { name: 'Mock recalcular ahora' }))
    expect(rankingReload).toHaveBeenCalledTimes(1)
    expect(refreshConfig).toHaveBeenCalledTimes(1)
    expect(screen.getByText('Oportunidades versión 1')).toBeTruthy()
  })
})
