import { beforeEach, describe, expect, it, vi } from 'vitest'

const requestMock = vi.fn()

vi.mock('./httpClient.js', () => ({ request: requestMock }))

const { buyOrder, cancelOrder, getMyOrders, getOrderBook, sellOrder } = await import('./marketApi.js')

describe('marketApi', () => {
  beforeEach(() => {
    requestMock.mockReset().mockResolvedValue({ id: 1 })
  })

  it('posts a LIMIT buy with its limit price', async () => {
    await buyOrder({ playerId: 42, quantity: 10, type: 'LIMIT', limitPrice: 1.3 })

    expect(requestMock).toHaveBeenCalledWith('/orders/buy', {
      method: 'POST',
      body: { playerId: 42, quantity: 10, type: 'LIMIT', limitPrice: 1.3 },
      signal: undefined,
    })
  })

  it('posts a MARKET sell without the limitPrice key', async () => {
    await sellOrder({ playerId: 42, quantity: 5, type: 'MARKET', limitPrice: 1.2 })

    const [path, options] = requestMock.mock.calls[0]
    expect(path).toBe('/orders/sell')
    expect(options.method).toBe('POST')
    expect(options.body).toEqual({ playerId: 42, quantity: 5, type: 'MARKET' })
    expect(Object.hasOwn(options.body, 'limitPrice')).toBe(false)
  })

  it('deletes an order by its encoded id', async () => {
    await cancelOrder(1001)

    expect(requestMock).toHaveBeenCalledWith('/orders/1001', { method: 'DELETE', signal: undefined })
  })

  it('requests the book of a player', async () => {
    await getOrderBook(42)

    expect(requestMock).toHaveBeenCalledWith('/orders/book?playerId=42', { signal: undefined })
  })

  it('requests the active orders of the authenticated user', async () => {
    await getMyOrders()

    expect(requestMock).toHaveBeenCalledWith('/orders/mine', { signal: undefined })
  })

  it('forwards the signal and returns the response unchanged', async () => {
    const signal = new AbortController().signal
    const payload = { playerId: 42, marketValue: 1, sells: [], buys: [] }
    requestMock.mockResolvedValue(payload)

    await expect(getOrderBook(42, { signal })).resolves.toBe(payload)
    await buyOrder({ playerId: 42, quantity: 1, type: 'MARKET' }, { signal })
    await cancelOrder(7, { signal })
    await getMyOrders({ signal })

    for (const [, options] of requestMock.mock.calls) {
      expect(options.signal).toBe(signal)
    }
  })
})
