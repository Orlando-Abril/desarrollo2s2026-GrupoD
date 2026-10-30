import { act, cleanup, renderHook, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { getOrderBook } from '../../api/marketApi.js'
import useOrderBook from './useOrderBook.js'

vi.mock('../../api/marketApi.js', () => ({ getOrderBook: vi.fn() }))

const BOOK = { playerId: 42, marketValue: 1.2, sells: [{ price: 1.2, quantity: 100 }], buys: [] }

describe('useOrderBook', () => {
  beforeEach(() => {
    getOrderBook.mockReset()
  })

  afterEach(cleanup)

  it('starts loading and becomes ready with the book', async () => {
    getOrderBook.mockResolvedValue(BOOK)

    const { result } = renderHook(() => useOrderBook(42))

    expect(result.current.status).toBe('loading')
    await waitFor(() => expect(result.current.status).toBe('ready'))
    expect(result.current.book).toBe(BOOK)
    expect(getOrderBook).toHaveBeenCalledWith(42, { signal: expect.any(AbortSignal) })
  })

  it('becomes error and reload recovers it', async () => {
    getOrderBook.mockRejectedValueOnce(new Error('boom')).mockResolvedValueOnce(BOOK)

    const { result } = renderHook(() => useOrderBook(42))

    await waitFor(() => expect(result.current.status).toBe('error'))
    act(() => result.current.reload())
    await waitFor(() => expect(result.current.status).toBe('ready'))
    expect(result.current.book).toBe(BOOK)
  })

  it('requests the book again on reload', async () => {
    const updated = { ...BOOK, sells: [{ price: 1.2, quantity: 90 }] }
    getOrderBook.mockResolvedValueOnce(BOOK).mockResolvedValueOnce(updated)

    const { result } = renderHook(() => useOrderBook(42))
    await waitFor(() => expect(result.current.book).toBe(BOOK))

    act(() => result.current.reload())

    await waitFor(() => expect(result.current.book).toBe(updated))
    expect(getOrderBook).toHaveBeenCalledTimes(2)
  })

  it('aborts the request on unmount and ignores its response', async () => {
    let resolve
    getOrderBook.mockImplementation(() => new Promise((done) => { resolve = done }))

    const { result, unmount } = renderHook(() => useOrderBook(42))
    const { signal } = getOrderBook.mock.calls[0][1]
    unmount()
    await act(async () => resolve(BOOK))

    expect(signal.aborted).toBe(true)
    expect(result.current.status).toBe('loading')
  })
})
