import { act, cleanup, renderHook, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'

const getLatestQuotes = vi.fn()
vi.mock('../../api/comparisonApi.js', () => ({ getLatestQuotes }))
const { default: useLatestPageQuotes } = await import('./useLatestPageQuotes.js')
afterEach(() => { cleanup(); getLatestQuotes.mockReset() })

describe('useLatestPageQuotes', () => {
  it('consulta los ids visibles sólo en Momentum y distingue quote nula', async () => {
    getLatestQuotes.mockResolvedValue({ items: [{ playerId: 1, quote: null }] })
    const { result, rerender } = renderHook(({ players, mode }) => useLatestPageQuotes(players, mode), { initialProps: { players: [{ id: 1 }], mode: 'MARKET_CURRENT' } })
    expect(getLatestQuotes).not.toHaveBeenCalled()
    rerender({ players: [{ id: 1 }, { id: 1 }], mode: 'MOMENTUM' })
    await waitFor(() => expect(result.current.status).toBe('ready'))
    expect(getLatestQuotes.mock.calls[0][0]).toEqual([1])
    expect(getLatestQuotes.mock.calls[0][1]).toBe('MOMENTUM')
    expect(result.current.quotes[1]).toBeNull()
  })
  it('descarta la respuesta de la página anterior', async () => {
    let resolveOld
    getLatestQuotes.mockImplementationOnce(() => new Promise((resolve) => { resolveOld = resolve })).mockResolvedValueOnce({ items: [{ playerId: 2, quote: { value: 2 } }] })
    const { result, rerender } = renderHook(({ players }) => useLatestPageQuotes(players, 'MOMENTUM'), { initialProps: { players: [{ id: 1 }] } })
    rerender({ players: [{ id: 2 }] })
    await waitFor(() => expect(result.current.quotes[2]?.value).toBe(2))
    await act(async () => resolveOld({ items: [{ playerId: 1, quote: { value: 1 } }] }))
    expect(result.current.quotes[2]?.value).toBe(2)
    expect(result.current.quotes[1]).toBeUndefined()
  })
})
