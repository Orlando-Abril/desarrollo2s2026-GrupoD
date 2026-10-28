import { beforeEach, describe, expect, it, vi } from 'vitest'

const requestMock = vi.fn()

vi.mock('./httpClient.js', () => ({ request: requestMock }))

const { getRanking } = await import('./quotesApi.js')

describe('quotesApi', () => {
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
