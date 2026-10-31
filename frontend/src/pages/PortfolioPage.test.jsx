import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { portfolio, transactionPage } from '../features/portfolio/portfolioTestData.js'
import { portfolioPositions } from '../test/strategyFixtures.js'

const hook = vi.hoisted(() => vi.fn())
vi.mock('../features/portfolio/usePortfolio.js', () => ({ default: hook }))
vi.mock('../features/portfolio/PerformanceChart.jsx', () => ({ default: () => <div>performance chart</div> }))
vi.mock('../features/portfolio/AllocationDonut.jsx', () => ({ default: () => <div>allocation donut</div> }))
const { default: PortfolioPage } = await import('./PortfolioPage.jsx')

afterEach(cleanup)

const base = {
  identityStatus: 'ready', portfolioStatus: 'ready', portfolio: portfolio(), portfolioRefreshing: false,
  transactionsStatus: 'ready', transactions: transactionPage(), transactionsRefreshing: false,
  page: 1, retryPortfolio: vi.fn(), retryTransactions: vi.fn(), setPage: vi.fn(),
}

describe('PortfolioPage', () => {
  beforeEach(() => hook.mockReset().mockReturnValue(base))
  it('shows contractual summary, balance detail, positions and charts', () => {
    render(<MemoryRouter><PortfolioPage/></MemoryRouter>)
    for (const label of ['Saldo', 'Invertido', 'Valor actual', 'Resultado']) expect(screen.getAllByText(label).length).toBeGreaterThan(0)
    expect(screen.getByText(/Disponible: 90,0000 cr · Reservado: 10,0000 cr/)).toBeTruthy()
    expect(screen.getByText('performance chart')).toBeTruthy(); expect(screen.getByText('allocation donut')).toBeTruthy()
  })
  it('shows no false zero while loading', () => {
    hook.mockReturnValue({ ...base, identityStatus: 'loading' })
    render(<MemoryRouter><PortfolioPage/></MemoryRouter>)
    const status = screen.getByRole('status')
    expect(status.tagName).toBe('OUTPUT'); expect(status.textContent).toBe('Cargando tu portfolio…')
    expect(status.className).toBe('portfolio-loading-status'); expect(screen.queryByText('0,0000 cr')).toBeNull()
  })
  it('announces portfolio and transaction loading through implicit status roles', () => {
    hook.mockReturnValue({
      ...base,
      portfolioStatus: 'loading',
      portfolio: null,
      transactionsStatus: 'loading',
      transactions: transactionPage({ items: [], numberOfElements: 0, totalElements: 0, totalPages: 0 }),
    })
    render(<MemoryRouter><PortfolioPage/></MemoryRouter>)
    const statuses = screen.getAllByRole('status')
    expect(statuses.map((status) => status.textContent)).toEqual(['Cargando posición…', 'Cargando operaciones…'])
    expect(statuses.every((status) => status.tagName === 'OUTPUT' && status.className === 'portfolio-loading-status')).toBe(true)
  })
  it('shows the exact empty message and keeps transaction history', () => {
    hook.mockReturnValue({ ...base, portfolioStatus: 'empty', portfolio: portfolio({ positions: [], totalCost: 0, totalCurrentValue: 0, totalProfitLoss: 0 }) })
    render(<MemoryRouter><PortfolioPage/></MemoryRouter>)
    expect(screen.getByText('Todavía no compraste tokens')).toBeTruthy(); expect(screen.getByRole('link', { name: 'Ir al álbum' }).getAttribute('href')).toBe('/album')
    expect(screen.getByText('Operaciones ejecutadas')).toBeTruthy(); expect(screen.queryByText('performance chart')).toBeNull()
  })
  it('retries only the failed resource', () => {
    hook.mockReturnValue({ ...base, portfolioStatus: 'error' })
    render(<MemoryRouter><PortfolioPage/></MemoryRouter>); fireEvent.click(screen.getByRole('button', { name: 'Reintentar' }))
    expect(base.retryPortfolio).toHaveBeenCalled(); expect(base.retryTransactions).not.toHaveBeenCalled()
  })
  it('shows nullable Momentum totals without changing market summary', () => {
    hook.mockReturnValue({ ...base, portfolioStrategy: 'MOMENTUM', portfolio: portfolio({ strategyValuation: { totalValue: null, totalProfitLoss: null, totalEquity: null, positionsWithoutQuote: 1 } }) })
    render(<MemoryRouter><PortfolioPage/></MemoryRouter>)
    expect(screen.getByRole('region', { name: 'Valuación Momentum' }).textContent).toContain('No disponible')
    expect(screen.getByRole('region', { name: 'Valuación Momentum' }).textContent).toContain('1 posiciones sin cotización')
    expect(screen.getAllByText('Valor actual').length).toBeGreaterThan(0)
  })
  it('shows independently calculated totals for three positions', () => {
    hook.mockReturnValue({ ...base, portfolioStrategy: 'MOMENTUM', portfolio: portfolio({ positions: portfolioPositions.map((item) => ({ ...item, playerName: `Jugador ${item.playerId}`, averagePurchasePrice: item.costBasis / item.quantity })), totalCost: 22, totalCurrentValue: 23.6, totalProfitLoss: 1.6, totalEquity: 123.6, strategyValuation: { totalValue: 23.5, totalProfitLoss: 1.5, totalEquity: 123.5, positionsWithoutQuote: 0 } }) })
    render(<MemoryRouter><PortfolioPage/></MemoryRouter>)
    const summary = screen.getByRole('region', { name: 'Valuación Momentum' }).textContent
    expect(summary).toContain('23,5000 cr')
    expect(summary).toContain('+1,5000 cr')
    expect(summary).toContain('123,5000 cr')
    expect(document.body.textContent).toContain('23,6000 cr')
  })
  it('names the comparison after the strategy of the loaded valuation', () => {
    hook.mockReturnValue({ ...base, portfolioStrategy: 'OFFICIAL', portfolio: portfolio({ strategyValuation: { totalValue: 4, totalProfitLoss: 0.25, totalEquity: 104, positionsWithoutQuote: 0 } }) })
    render(<MemoryRouter><PortfolioPage/></MemoryRouter>)
    expect(screen.getByRole('region', { name: 'Valuación Oficial' }).textContent).toContain('Valor Oficial')
    expect(screen.getByRole('columnheader', { name: 'Oficial · valor y resultado' })).toBeTruthy()
    expect(document.body.textContent).not.toContain('Momentum')
  })
  it('keeps the market position visible while the comparison is refreshing', () => {
    hook.mockReturnValue({ ...base, portfolioRefreshing: true, portfolioStrategy: null })
    render(<MemoryRouter><PortfolioPage/></MemoryRouter>)
    expect(screen.getByRole('region', { name: 'Posición actual' }).getAttribute('aria-busy')).toBe('true')
    expect(screen.getByRole('table', { name: 'Posiciones actuales' })).toBeTruthy()
    expect(screen.queryByRole('region', { name: /Valuación/ })).toBeNull()
  })
})
