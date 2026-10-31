import { beforeEach, describe, expect, it, vi } from 'vitest'
import { request } from './httpClient.js'
import { getCurrentAccount, getUserPortfolio, getUserTransactions } from './portfolioApi.js'

vi.mock('./httpClient.js', () => ({ request: vi.fn() }))

describe('portfolioApi', () => {
  beforeEach(() => request.mockReset())
  it('forwards account and portfolio payloads with signals and encoded ids', async () => {
    const signal = new AbortController().signal
    const payload = { id: 7 }
    request.mockResolvedValue(payload)
    await expect(getCurrentAccount({ signal })).resolves.toBe(payload)
    await getUserPortfolio('id con /', { signal })
    expect(request.mock.calls).toEqual([['/auth/me', { signal }], ['/users/id%20con%20%2F/portfolio', { signal }]])
  })
  it('keeps page zero, size and query order', async () => {
    const signal = new AbortController().signal
    request.mockResolvedValue({ items: [] })
    await getUserTransactions(7, { page: 0, size: 20 }, { signal })
    expect(request).toHaveBeenCalledWith('/users/7/transactions?page=0&size=20', { signal })
  })
})
