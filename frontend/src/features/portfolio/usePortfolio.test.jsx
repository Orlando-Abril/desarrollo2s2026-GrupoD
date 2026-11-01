import { act, renderHook, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../api/httpClient.js'
import { account, portfolio, transaction, transactionPage } from './portfolioTestData.js'
import { StrategyViewProvider } from '../../context/StrategyViewContext.jsx'
import { marketMomentum } from '../../test/strategyFixtures.js'
import { strategyStore } from '../../test/strategyStore.js'

const api = vi.hoisted(() => ({ getCurrentAccount: vi.fn(), getUserPortfolio: vi.fn(), getUserTransactions: vi.fn() }))
vi.mock('../../api/portfolioApi.js', () => api)
const { default: usePortfolio } = await import('./usePortfolio.js')

describe('usePortfolio', () => {
  beforeEach(() => {
    api.getCurrentAccount.mockReset().mockResolvedValue(account())
    api.getUserPortfolio.mockReset().mockResolvedValue(portfolio())
    api.getUserTransactions.mockReset().mockResolvedValue(transactionPage())
  })
  it('resolves identity before loading independent resources and aborts on unmount', async () => {
    let resolveIdentity
    api.getCurrentAccount.mockImplementation(({ signal }) => new Promise((resolve) => { resolveIdentity = resolve; expect(signal).toBeInstanceOf(AbortSignal) }))
    const { result, unmount } = renderHook(() => usePortfolio())
    expect(result.current.page).toBe(1)
    expect(api.getUserPortfolio).not.toHaveBeenCalled(); expect(api.getUserTransactions).not.toHaveBeenCalled()
    await act(async () => resolveIdentity(account()))
    await waitFor(() => expect(result.current.portfolioStatus).toBe('ready'))
    expect(api.getUserTransactions).toHaveBeenCalledWith(7, { page: 0, size: 20 }, expect.objectContaining({ signal: expect.any(AbortSignal) }))
    const signal = api.getUserPortfolio.mock.calls[0][1].signal; unmount(); expect(signal.aborted).toBe(true)
  })
  it('switches to Momentum without changing transactions and aborts the prior valuation', async () => {
    const store = strategyStore()
    let resolveMarket
    api.getUserPortfolio.mockImplementationOnce(() => new Promise((resolve) => { resolveMarket = resolve })).mockResolvedValue(portfolio({ strategyValuation: { totalValue: 12 } }))
    const wrapper = ({ children }) => <StrategyViewProvider store={store}>{children}</StrategyViewProvider>
    const { result } = renderHook(() => usePortfolio(), { wrapper })
    await waitFor(() => expect(api.getUserPortfolio).toHaveBeenCalledTimes(1))
    const oldSignal = api.getUserPortfolio.mock.calls[0][1].signal
    const transactionCalls = api.getUserTransactions.mock.calls.length
    act(() => store.setViewStrategy('MOMENTUM'))
    await waitFor(() => expect(api.getUserPortfolio).toHaveBeenCalledTimes(2))
    expect(oldSignal.aborted).toBe(true)
    expect(api.getUserPortfolio.mock.calls[1][1].strategy).toBe('MOMENTUM')
    await waitFor(() => expect(result.current.portfolio.strategyValuation.totalValue).toBe(12))
    await act(async () => resolveMarket(portfolio()))
    expect(result.current.portfolio.strategyValuation.totalValue).toBe(12)
    expect(result.current.portfolioStrategy).toBe('MOMENTUM')
    expect(api.getUserTransactions).toHaveBeenCalledTimes(transactionCalls)
  })
  it('keeps the position visible and marks it as refreshing while the alternative valuation loads', async () => {
    const store = strategyStore({ activeConfig: marketMomentum })
    const wrapper = ({ children }) => <StrategyViewProvider store={store}>{children}</StrategyViewProvider>
    const { result } = renderHook(() => usePortfolio(), { wrapper })
    await waitFor(() => expect(result.current.portfolioStatus).toBe('ready'))
    const marketData = result.current.portfolio
    let resolveAlternative
    api.getUserPortfolio.mockImplementationOnce(() => new Promise((resolve) => { resolveAlternative = resolve }))
    act(() => store.setViewStrategy('MOMENTUM'))
    await waitFor(() => expect(api.getUserPortfolio).toHaveBeenCalledTimes(2))
    expect(api.getUserPortfolio.mock.calls[1][1].strategy).toBe('OFFICIAL')
    expect(result.current.portfolio).toBe(marketData)
    expect(result.current.portfolioStatus).toBe('ready')
    expect(result.current.portfolioRefreshing).toBe(true)
    expect(result.current.portfolioStrategy).toBeNull()
    await act(async () => resolveAlternative(portfolio({ strategyValuation: { totalValue: 5 } })))
    expect(result.current.portfolioRefreshing).toBe(false)
    expect(result.current.portfolioStrategy).toBe('OFFICIAL')
    expect(result.current.portfolio.strategyValuation.totalValue).toBe(5)
  })
  it('exposes human pages, requests zero-based pages and rejects invalid changes', async () => {
    api.getUserTransactions.mockImplementation((id, { page, size }) => Promise.resolve(transactionPage({
      page,
      size,
      totalElements: 41,
      totalPages: 3,
      first: page === 0,
      last: page === 2,
    })))
    const { result } = renderHook(() => usePortfolio())
    expect(result.current.page).toBe(1)
    await waitFor(() => expect(result.current.transactionsStatus).toBe('ready'))
    expect(api.getUserTransactions.mock.calls[0][1]).toEqual({ page: 0, size: 20 })

    const callsBeforeInvalidPages = api.getUserTransactions.mock.calls.length
    for (const invalidPage of [0, -1, 1, 1.5, 4]) act(() => result.current.setPage(invalidPage))
    expect(result.current.page).toBe(1)
    expect(api.getUserTransactions).toHaveBeenCalledTimes(callsBeforeInvalidPages)

    act(() => result.current.setPage(2))
    expect(result.current.page).toBe(2)
    await waitFor(() => expect(result.current.transactions.page).toBe(1))
    expect(api.getUserTransactions.mock.calls.at(-1)[1]).toEqual({ page: 1, size: 20 })
  })
  it('corrects the public page to the last valid page and requests it', async () => {
    api.getUserTransactions.mockImplementation((id, { page, size }) => {
      if (page === 0) return Promise.resolve(transactionPage({ page, size, totalElements: 41, totalPages: 3, first: true, last: false }))
      if (page === 2) return Promise.resolve(transactionPage({ items: [], page, size, numberOfElements: 0, totalElements: 21, totalPages: 2, first: false, last: true }))
      return Promise.resolve(transactionPage({ page, size, totalElements: 21, totalPages: 2, first: false, last: true }))
    })
    const { result } = renderHook(() => usePortfolio())
    await waitFor(() => expect(result.current.transactionsStatus).toBe('ready'))
    act(() => result.current.setPage(3))
    await waitFor(() => expect(result.current.page).toBe(2))
    await waitFor(() => expect(result.current.transactions.page).toBe(1))
    expect(api.getUserTransactions.mock.calls.map(([, params]) => params.page)).toEqual([0, 2, 1])
  })
  it('retries portfolio without requesting transactions', async () => {
    api.getUserPortfolio.mockRejectedValueOnce(new Error('fail')).mockResolvedValue(portfolio())
    const { result } = renderHook(() => usePortfolio())
    await waitFor(() => expect(result.current.portfolioStatus).toBe('error'))
    const txCalls = api.getUserTransactions.mock.calls.length
    act(() => result.current.retryPortfolio())
    await waitFor(() => expect(result.current.portfolioStatus).toBe('ready'))
    expect(api.getUserTransactions).toHaveBeenCalledTimes(txCalls)
  })
  it('recovers identity and transactions independently', async () => {
    api.getCurrentAccount.mockRejectedValueOnce(new Error('offline')).mockResolvedValue(account())
    api.getUserTransactions.mockRejectedValueOnce(new Error('offline')).mockResolvedValue(transactionPage())
    const { result } = renderHook(() => usePortfolio())
    await waitFor(() => expect(result.current.identityStatus).toBe('error'))
    act(() => result.current.retryIdentity())
    await waitFor(() => expect(result.current.transactionsStatus).toBe('error'))
    const portfolioCalls = api.getUserPortfolio.mock.calls.length
    act(() => result.current.retryTransactions())
    await waitFor(() => expect(result.current.transactionsStatus).toBe('ready'))
    expect(api.getUserPortfolio).toHaveBeenCalledTimes(portfolioCalls)
  })
  it('keeps loading on 401 so the global client can redirect', async () => {
    api.getCurrentAccount.mockRejectedValue(new ApiError(401, 'unauthorized', 'expired'))
    const { result } = renderHook(() => usePortfolio())
    await act(async () => {})
    expect(result.current.identityStatus).toBe('loading')
  })
  it('corrects an empty out-of-range page to human page one', async () => {
    api.getUserTransactions.mockImplementation((id, { page }) => Promise.resolve(transactionPage({ items: [], page, numberOfElements: 0, totalElements: 0, totalPages: 0, first: true, last: true })))
    const { result } = renderHook(() => usePortfolio())
    await waitFor(() => expect(result.current.transactionsStatus).toBe('empty'))
    act(() => result.current.setPage(2))
    await waitFor(() => expect(result.current.page).toBe(1))
    expect(api.getUserTransactions.mock.calls.some(([, params]) => params.page === 1)).toBe(true)
  })
  it('generates and traverses 1,000 operations with complete stable order and no duplicates', async () => {
    const all = Array.from({ length: 1000 }, (_, index) => transaction({
      id: 1000 - index,
      executedAt: new Date(Date.UTC(2026, 9, 30, 0, 0, 0) - index * 1000).toISOString(),
    }))
    api.getUserTransactions.mockImplementation((id, { page, size }) => Promise.resolve(transactionPage({
      items: all.slice(page * size, (page + 1) * size), page, size,
      numberOfElements: Math.min(size, all.length - page * size), totalElements: all.length,
      totalPages: Math.ceil(all.length / size), first: page === 0, last: page === 49,
    })))
    const { result } = renderHook(() => usePortfolio())
    await waitFor(() => expect(result.current.transactionsStatus).toBe('ready'))
    const visited = []
    for (let humanPage = 1; humanPage <= 50; humanPage += 1) {
      if (humanPage > 1) {
        act(() => result.current.setPage(humanPage))
        await waitFor(() => expect(result.current.transactions.page).toBe(humanPage - 1))
      }
      visited.push(...result.current.transactions.items)
    }
    expect(visited).toHaveLength(1000)
    expect(new Set(visited.map((item) => item.id)).size).toBe(1000)
    expect(visited.map((item) => item.id)).toEqual(all.map((item) => item.id))
    expect(visited.every((item, index) => index === 0 || item.executedAt < visited[index - 1].executedAt || (item.executedAt === visited[index - 1].executedAt && item.id < visited[index - 1].id))).toBe(true)
  }, 10_000)
})
