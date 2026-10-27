import { beforeEach, describe, expect, it, vi } from 'vitest'

const requestMock = vi.fn()

vi.mock('./httpClient.js', () => ({ request: requestMock }))

const { getPlayerQuotes, getRanking } = await import('./quotesApi.js')

function query(path) {
  return Object.fromEntries(new URL(path, 'http://x').searchParams)
}

describe('quotesApi', () => {
  describe('getRanking', () => {
    beforeEach(() => {
      requestMock.mockReset().mockResolvedValue({ items: [] })
    })

    it('requests the ranking endpoint without empty filters', async () => {
      await getRanking({ strategy: '', league: '', position: '' })

      expect(requestMock).toHaveBeenCalledWith('/players/ranking', { signal: undefined })
    })

    it('serializes filters and pagination in a stable order', async () => {
      await getRanking({
        strategy: 'MOMENTUM',
        league: 'LA_LIGA',
        position: 'FORWARD',
        page: 0,
        size: 20,
      })

      expect(requestMock).toHaveBeenCalledWith(
        '/players/ranking?strategy=MOMENTUM&league=LA_LIGA&position=FORWARD&page=0&size=20',
        { signal: undefined },
      )
    })

    it('encodes values, omits blank filters and preserves page zero', async () => {
      await getRanking({ league: 'Premier League', position: '', page: 0 })

      expect(requestMock).toHaveBeenCalledWith(
        '/players/ranking?league=Premier+League&page=0',
        { signal: undefined },
      )
    })

    it('forwards the signal and returns the response unchanged', async () => {
      const payload = { items: [{ rank: 1 }], page: 0, size: 20, totalElements: 1, totalPages: 1 }
      const controller = new AbortController()
      requestMock.mockResolvedValue(payload)

      await expect(getRanking({}, { signal: controller.signal })).resolves.toBe(payload)
      expect(requestMock).toHaveBeenCalledWith('/players/ranking', { signal: controller.signal })
    })
  })

  describe('getPlayerQuotes', () => {
    beforeEach(() => requestMock.mockReset().mockResolvedValue([]))

    it('requests the history without query when no params are given', async () => {
      await getPlayerQuotes('42')
      expect(requestMock).toHaveBeenCalledWith('/players/42/quotes', { signal: undefined })
    })

    it('sends only the strategy', async () => {
      await getPlayerQuotes('42', { strategy: 'MOMENTUM' })
      expect(requestMock.mock.calls[0][0]).toBe('/players/42/quotes?strategy=MOMENTUM')
    })

    it('sends strategy and range regardless of textual order', async () => {
      await getPlayerQuotes('42', { strategy: 'OFFICIAL', from: '2026-09-28', to: '2026-10-27' })
      const [path] = requestMock.mock.calls[0]
      expect(path.startsWith('/players/42/quotes?')).toBe(true)
      expect(query(path)).toEqual({ strategy: 'OFFICIAL', from: '2026-09-28', to: '2026-10-27' })
    })

    it('sends at for a point-in-time query', async () => {
      await getPlayerQuotes('42', { strategy: 'OFFICIAL', at: '2026-10-10' })
      expect(query(requestMock.mock.calls[0][0])).toEqual({ strategy: 'OFFICIAL', at: '2026-10-10' })
    })

    it('omits blank params', async () => {
      await getPlayerQuotes('42', { strategy: '', from: '', to: '', at: '' })
      expect(requestMock.mock.calls[0][0]).toBe('/players/42/quotes')
    })

    it('encodes the id', async () => {
      await getPlayerQuotes('4/2')
      expect(requestMock.mock.calls[0][0]).toBe('/players/4%2F2/quotes')
    })

    it.each([
      [{ at: '2026-10-10', from: '2026-10-01' }],
      [{ at: '2026-10-10', to: '2026-10-20' }],
    ])('rejects at combined with a range without requesting (%o)', (params) => {
      expect(() => getPlayerQuotes('42', params)).toThrow(TypeError)
      expect(requestMock).not.toHaveBeenCalled()
    })

    it('forwards the caller signal and returns the response unchanged', async () => {
      const quote = { value: 1.2, strategy: 'OFFICIAL' }
      const controller = new AbortController()
      requestMock.mockResolvedValue(quote)
      await expect(getPlayerQuotes('42', { at: '2026-10-10' }, { signal: controller.signal })).resolves.toBe(quote)
      expect(requestMock.mock.calls[0][1]).toEqual({ signal: controller.signal })
    })
  })
})
