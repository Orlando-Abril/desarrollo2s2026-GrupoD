import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { portfolio, transactionPage } from '../features/portfolio/portfolioTestData.js'

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
    expect(screen.getByText('Cargando tu portfolio…')).toBeTruthy(); expect(screen.queryByText('0,0000 cr')).toBeNull()
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
})
