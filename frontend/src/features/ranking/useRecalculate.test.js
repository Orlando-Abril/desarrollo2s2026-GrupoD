import { createElement, StrictMode } from 'react'
import { act, cleanup, renderHook, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, NetworkError } from '../../api/httpClient.js'

let token = 'token-a'
const recalculateQuotesMock = vi.fn()
vi.mock('../../api/quotesApi.js', () => ({ recalculateQuotes: recalculateQuotesMock }))
vi.mock('../../session/SessionContext.jsx', () => ({ useSession: () => ({ token }) }))

const { default: useRecalculate } = await import('./useRecalculate.js')

function deferred() {
  let resolve
  let reject
  const promise = new Promise((res, rej) => { resolve = res; reject = rej })
  return { promise, resolve, reject }
}

afterEach(cleanup)

describe('useRecalculate', () => {
  beforeEach(() => {
    token = `token-${crypto.randomUUID()}`
    recalculateQuotesMock.mockReset()
  })

  it('publishes the unchanged summary, reloads once and blocks duplicate sends', async () => {
    const pending = deferred()
    const summary = { runId: 'run-1', strategies: [], skippedStrategies: [], playersProcessed: 0, playersFailed: 0 }
    const onRecalculated = vi.fn()
    recalculateQuotesMock.mockReturnValue(pending.promise)
    const { result } = renderHook(() => useRecalculate({ onRecalculated }))

    act(() => { void result.current.recalculate(); void result.current.recalculate() })
    expect(result.current.running).toBe(true)
    expect(recalculateQuotesMock).toHaveBeenCalledTimes(1)
    pending.resolve(summary)

    await waitFor(() => expect(result.current.running).toBe(false))
    expect(result.current.notification).toEqual({ type: 'success', summary })
    expect(onRecalculated).toHaveBeenCalledTimes(1)
  })

  it('publishes valid results under React Strict Mode', async () => {
    const summary = { runId: 'strict-run', strategies: [], skippedStrategies: [] }
    recalculateQuotesMock.mockResolvedValue(summary)
    const onRecalculated = vi.fn()
    const wrapper = ({ children }) => createElement(StrictMode, null, children)
    const { result } = renderHook(() => useRecalculate({ onRecalculated }), { wrapper })

    await act(() => result.current.recalculate())

    expect(result.current.notification).toEqual({ type: 'success', summary })
    expect(onRecalculated).toHaveBeenCalledTimes(1)
  })

  it.each([
    [new ApiError(409, 'recalculation_in_progress', 'private'), 'in_progress'],
    [new NetworkError(), 'network_error'],
    [new ApiError(500, 'internal', 'database password'), 'unexpected_error'],
  ])('classifies terminal errors without reloading (%s)', async (error, type) => {
    const onRecalculated = vi.fn()
    recalculateQuotesMock.mockRejectedValue(error)
    const { result } = renderHook(() => useRecalculate({ onRecalculated }))
    await act(() => result.current.recalculate())
    expect(result.current.running).toBe(false)
    expect(result.current.notification).toEqual({ type })
    expect(onRecalculated).not.toHaveBeenCalled()
    expect(JSON.stringify(result.current.notification)).not.toMatch(/private|database password/)
  })

  it('remembers a 403 for the same token and resets for a new identity', async () => {
    const deniedToken = token
    recalculateQuotesMock.mockRejectedValue(new ApiError(403, 'forbidden', 'private'))
    const first = renderHook(() => useRecalculate({ onRecalculated: vi.fn() }))
    await act(() => first.result.current.recalculate())
    expect(first.result.current.forbidden).toBe(true)
    expect(first.result.current.notification).toEqual({ type: 'forbidden' })
    first.unmount()

    token = deniedToken
    const same = renderHook(() => useRecalculate({ onRecalculated: vi.fn() }))
    expect(same.result.current.forbidden).toBe(true)
    same.unmount()

    token = `${deniedToken}-new`
    const next = renderHook(() => useRecalculate({ onRecalculated: vi.fn() }))
    expect(next.result.current.forbidden).toBe(false)
  })

  it('aborts and suppresses callbacks after unmount', () => {
    recalculateQuotesMock.mockImplementation((_params, { signal }) => new Promise((_resolve, reject) => {
      signal.addEventListener('abort', () => reject(new NetworkError()), { once: true })
    }))
    const onRecalculated = vi.fn()
    const { result, unmount } = renderHook(() => useRecalculate({ onRecalculated }))
    act(() => { void result.current.recalculate() })
    const signal = recalculateQuotesMock.mock.calls[0][1].signal
    unmount()
    expect(signal.aborted).toBe(true)
    expect(onRecalculated).not.toHaveBeenCalled()
  })
})
