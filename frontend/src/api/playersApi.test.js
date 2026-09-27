import { beforeEach, describe, expect, it, vi } from 'vitest'

const requestMock = vi.fn()

vi.mock('./httpClient.js', () => ({ request: requestMock }))

const { getPlayers } = await import('./playersApi.js')

describe('playersApi', () => {
  beforeEach(() => requestMock.mockReset().mockResolvedValue([]))

  it('requests /players without a trailing query when filters are empty', async () => {
    await getPlayers({})
    expect(requestMock).toHaveBeenCalledWith('/players', { signal: undefined })
  })

  it('omits blank filters', async () => {
    await getPlayers({ league: '', team: '', position: '' })
    expect(requestMock).toHaveBeenCalledWith('/players', { signal: undefined })
  })

  it('combines all populated filters and encodes team spaces', async () => {
    await getPlayers({ league: 'LA_LIGA', team: 'Real Madrid CF', position: 'FORWARD' })
    const [path] = requestMock.mock.calls[0]
    expect(path).toBe('/players?league=LA_LIGA&team=Real+Madrid+CF&position=FORWARD')
  })

  it('forwards the caller signal and returns the response unchanged', async () => {
    const players = [{ id: 1, fullName: 'Ángel' }]
    const controller = new AbortController()
    requestMock.mockResolvedValue(players)

    await expect(getPlayers({ league: 'SERIE_A' }, { signal: controller.signal })).resolves.toBe(players)
    expect(requestMock).toHaveBeenCalledWith('/players?league=SERIE_A', { signal: controller.signal })
  })
})
