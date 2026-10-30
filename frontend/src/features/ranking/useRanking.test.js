import { act, cleanup, renderHook, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const getRankingMock = vi.fn()

vi.mock('../../api/quotesApi.js', () => ({ getRanking: getRankingMock }))

const { default: useRanking } = await import('./useRanking.js')

function rankingPage(overrides = {}) {
  return {
    items: [],
    page: 0,
    size: 20,
    totalElements: 0,
    totalPages: 0,
    ...overrides,
  }
}

function deferred() {
  let resolve
  let reject
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}

afterEach(cleanup)

describe('useRanking', () => {
  beforeEach(() => {
    getRankingMock.mockReset()
  })

  it('loads the first page with active strategy criteria', async () => {
    const payload = rankingPage({
      items: [{ rank: 3, player: { id: 9 } }, { rank: 1, player: { id: 2 } }],
      totalElements: 2,
      totalPages: 1,
    })
    getRankingMock.mockResolvedValue(payload)

    const { result } = renderHook(() => useRanking())

    expect(result.current.status).toBe('loading')
    await waitFor(() => expect(result.current.status).toBe('ready'))
    expect(result.current.items).toBe(payload.items)
    expect(result.current.items.map(({ rank }) => rank)).toEqual([3, 1])
    expect(getRankingMock).toHaveBeenCalledWith(
      { strategy: '', league: '', position: '', page: 0, size: 20 },
      { signal: expect.any(AbortSignal) },
    )
  })

  it('aborts its current request when the hook unmounts', () => {
    getRankingMock.mockImplementation((_criteria, { signal }) => new Promise((_resolve, reject) => {
      signal.addEventListener('abort', () => reject(new Error('aborted')), { once: true })
    }))
    const { unmount } = renderHook(() => useRanking())
    const signal = getRankingMock.mock.calls[0][1].signal

    unmount()

    expect(signal.aborted).toBe(true)
  })

  it('keeps active strategy implicit and infers its effective code only from results', async () => {
    getRankingMock.mockResolvedValue(rankingPage({
      items: [{ rank: 1, strategy: 'MOMENTUM', player: { id: 1 } }],
      totalElements: 1,
      totalPages: 1,
    }))
    const { result, unmount } = renderHook(() => useRanking())

    expect(result.current.filters).toEqual({ strategy: '', league: '', position: '' })
    await waitFor(() => expect(result.current.effectiveStrategy).toBe('MOMENTUM'))
    expect(getRankingMock.mock.calls[0][0].strategy).toBe('')
    unmount()

    getRankingMock.mockResolvedValue(rankingPage())
    const emptyHook = renderHook(() => useRanking())
    await waitFor(() => expect(emptyHook.result.current.status).toBe('ready'))
    expect(emptyHook.result.current.effectiveStrategy).toBeNull()
  })

  it('combines explicit filters and resets a later page to one', async () => {
    const nextRequest = deferred()
    getRankingMock
      .mockResolvedValueOnce(rankingPage({ totalElements: 60, totalPages: 3 }))
      .mockResolvedValueOnce(rankingPage({ page: 2, totalElements: 60, totalPages: 3 }))
      .mockReturnValueOnce(nextRequest.promise)
    const { result } = renderHook(() => useRanking())
    await waitFor(() => expect(result.current.status).toBe('ready'))

    act(() => result.current.setPage(3))
    await waitFor(() => expect(getRankingMock).toHaveBeenCalledTimes(2))
    act(() => result.current.changeFilter('strategy', 'OFFICIAL'))

    expect(result.current.page).toBe(1)
    expect(result.current.status).toBe('loading')
    expect(getRankingMock.mock.calls[1][1].signal.aborted).toBe(true)
    await waitFor(() => expect(getRankingMock).toHaveBeenCalledTimes(3))
    expect(getRankingMock.mock.calls[2][0]).toEqual({
      strategy: 'OFFICIAL', league: '', position: '', page: 0, size: 20,
    })

    nextRequest.resolve(rankingPage())
    await waitFor(() => expect(result.current.status).toBe('ready'))
  })

  it('translates human pages and derives range and page window', async () => {
    const firstItems = Array.from({ length: 20 }, (_, index) => ({ rank: index + 1, player: { id: index + 1 } }))
    const lastItems = Array.from({ length: 13 }, (_, index) => ({ rank: index + 41, player: { id: index + 41 } }))
    getRankingMock
      .mockResolvedValueOnce(rankingPage({ items: firstItems, totalElements: 53, totalPages: 3 }))
      .mockResolvedValueOnce(rankingPage({ items: lastItems, page: 2, totalElements: 53, totalPages: 3 }))
    const { result } = renderHook(() => useRanking())
    await waitFor(() => expect(result.current.status).toBe('ready'))

    act(() => result.current.setPage(3))
    await waitFor(() => expect(result.current.from).toBe(41))
    expect(result.current.to).toBe(53)
    expect(result.current.pageWindow).toEqual([1, 2, 3])
    expect(getRankingMock.mock.calls[1][0].page).toBe(2)
  })

  it('shows an error and retries the same criteria', async () => {
    getRankingMock
      .mockRejectedValueOnce(new Error('private server detail'))
      .mockResolvedValueOnce(rankingPage())
    const { result } = renderHook(() => useRanking())
    await waitFor(() => expect(result.current.status).toBe('error'))

    act(() => result.current.retry())

    expect(result.current.status).toBe('loading')
    await waitFor(() => expect(result.current.status).toBe('ready'))
    expect(getRankingMock.mock.calls[1][0]).toEqual(getRankingMock.mock.calls[0][0])
  })

  it('reloads the current criteria and page while aborting the replaced request', async () => {
    const stale = deferred()
    const fresh = deferred()
    getRankingMock
      .mockResolvedValueOnce(rankingPage({ totalElements: 60, totalPages: 3 }))
      .mockResolvedValueOnce(rankingPage({ page: 1, totalElements: 60, totalPages: 3 }))
      .mockReturnValueOnce(stale.promise)
      .mockReturnValueOnce(fresh.promise)
    const { result } = renderHook(() => useRanking())
    await waitFor(() => expect(result.current.status).toBe('ready'))
    act(() => result.current.setPage(2))
    await waitFor(() => expect(result.current.status).toBe('ready'))
    act(() => result.current.changeFilter('league', 'LA_LIGA'))
    await waitFor(() => expect(getRankingMock).toHaveBeenCalledTimes(3))

    act(() => result.current.reload())

    expect(result.current.status).toBe('loading')
    expect(result.current.page).toBe(1)
    expect(result.current.filters.league).toBe('LA_LIGA')
    expect(getRankingMock.mock.calls[2][1].signal.aborted).toBe(true)
    await waitFor(() => expect(getRankingMock).toHaveBeenCalledTimes(4))
    expect(getRankingMock.mock.calls[3][0]).toEqual({
      strategy: '', league: 'LA_LIGA', position: '', page: 0, size: 20,
    })
    fresh.resolve(rankingPage())
    await waitFor(() => expect(result.current.status).toBe('ready'))
  })

  it('aborts a replaced request and ignores its stale completion', async () => {
    const stale = deferred()
    const current = deferred()
    getRankingMock
      .mockResolvedValueOnce(rankingPage())
      .mockReturnValueOnce(stale.promise)
      .mockReturnValueOnce(current.promise)
    const { result } = renderHook(() => useRanking())
    await waitFor(() => expect(result.current.status).toBe('ready'))

    act(() => result.current.changeFilter('league', 'LA_LIGA'))
    await waitFor(() => expect(getRankingMock).toHaveBeenCalledTimes(2))
    act(() => result.current.changeFilter('position', 'FORWARD'))
    await waitFor(() => expect(getRankingMock).toHaveBeenCalledTimes(3))
    expect(getRankingMock.mock.calls[1][1].signal.aborted).toBe(true)

    stale.resolve(rankingPage({
      items: [{ rank: 99, strategy: 'OFFICIAL', player: { id: 99 } }],
      totalElements: 1,
      totalPages: 1,
    }))
    await Promise.resolve()
    expect(result.current.status).toBe('loading')

    current.resolve(rankingPage({
      items: [{ rank: 1, strategy: 'MOMENTUM', player: { id: 1 } }],
      totalElements: 1,
      totalPages: 1,
    }))
    await waitFor(() => expect(result.current.status).toBe('ready'))
    expect(result.current.items[0].rank).toBe(1)
  })

  it('corrects an out-of-range page before publishing an empty result', async () => {
    const finalPage = rankingPage({
      items: [{ rank: 21, strategy: 'OFFICIAL', player: { id: 21 } }],
      page: 1,
      totalElements: 21,
      totalPages: 2,
    })
    getRankingMock
      .mockResolvedValueOnce(rankingPage({ totalElements: 41, totalPages: 3 }))
      .mockResolvedValueOnce(rankingPage({ page: 2, totalElements: 21, totalPages: 2 }))
      .mockResolvedValueOnce(finalPage)
    const { result } = renderHook(() => useRanking())
    await waitFor(() => expect(result.current.status).toBe('ready'))

    act(() => result.current.setPage(3))

    await waitFor(() => expect(getRankingMock).toHaveBeenCalledTimes(3))
    expect(getRankingMock.mock.calls[2][0].page).toBe(1)
    await waitFor(() => expect(result.current.status).toBe('ready'))
    expect(result.current.page).toBe(2)
    expect(result.current.items).toBe(finalPage.items)
  })
})
