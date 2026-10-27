import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, NetworkError, configureHttpClient } from './httpClient.js'
import { getPlayerDetail } from './playerDetailApi.js'

function response(status, body) {
  return {
    ok: status >= 200 && status < 300,
    status,
    text: vi.fn().mockResolvedValue(body === undefined ? '' : JSON.stringify(body)),
  }
}

describe('playerDetailApi', () => {
  let fetchMock

  beforeEach(() => {
    vi.stubEnv('VITE_API_BASE_URL', '')
    configureHttpClient({ getToken: () => 'jwt', onUnauthorized: () => {} })
    fetchMock = vi.fn().mockResolvedValue(response(200, { player: { id: 42 }, stats: null, quote: null }))
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllEnvs()
    vi.unstubAllGlobals()
  })

  it('requests /players/{id} with the session bearer token and no api key', async () => {
    await expect(getPlayerDetail('42')).resolves.toEqual({ player: { id: 42 }, stats: null, quote: null })
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/players/42')
    expect(init.headers.Authorization).toBe('Bearer jwt')
    expect(init.headers).not.toHaveProperty('X-API-KEY')
  })

  it('encodes the id', async () => {
    await getPlayerDetail('4 2')
    expect(fetchMock.mock.calls[0][0]).toBe('/players/4%202')
  })

  it('forwards the caller signal', async () => {
    const controller = new AbortController()
    controller.abort()
    await getPlayerDetail('42', { signal: controller.signal })
    expect(fetchMock.mock.calls[0][1].signal.aborted).toBe(true)
  })

  it('propagates a 404 ApiError unchanged', async () => {
    fetchMock.mockResolvedValue(response(404, { error: 'player_not_found', message: 'No existe el jugador 42' }))
    const error = await getPlayerDetail('42').catch((caught) => caught)
    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ status: 404, code: 'player_not_found' })
  })

  it('propagates network failures as NetworkError', async () => {
    fetchMock.mockRejectedValue(new TypeError('offline'))
    await expect(getPlayerDetail('42')).rejects.toBeInstanceOf(NetworkError)
  })
})
