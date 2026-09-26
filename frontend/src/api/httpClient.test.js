import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, NetworkError, configureHttpClient, request } from './httpClient.js'

function response(status, body) {
  return {
    ok: status >= 200 && status < 300,
    status,
    text: vi.fn().mockResolvedValue(body === undefined ? '' : JSON.stringify(body)),
  }
}

describe('httpClient', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_API_BASE_URL', 'http://localhost:8080/')
    configureHttpClient({ getToken: () => null, onUnauthorized: () => {} })
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllEnvs()
    vi.restoreAllMocks()
  })

  it.each([
    [400, 'validation_error'],
    [401, 'invalid_credentials'],
    [409, 'duplicate_user'],
  ])('preserves contractual error for %s', async (status, code) => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response(status, { error: code, message: 'detalle' })))

    await expect(request('/auth/example', { publicRequest: true })).rejects.toMatchObject({
      status,
      code,
      message: 'detalle',
    })
  })

  it('turns malformed and 5xx responses into a safe ApiError', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: false,
      status: 503,
      text: vi.fn().mockResolvedValue('<html>private</html>'),
    }))

    await expect(request('/private')).rejects.toEqual(expect.objectContaining({
      status: 503,
      code: 'unexpected_error',
    }))
  })

  it('maps a network rejection to NetworkError', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('offline detail')))
    await expect(request('/private')).rejects.toBeInstanceOf(NetworkError)
  })

  it('aborts at exactly 10,000 ms and clears the timer', async () => {
    vi.useFakeTimers()
    const clearSpy = vi.spyOn(globalThis, 'clearTimeout')
    vi.stubGlobal('fetch', vi.fn((_, { signal }) => new Promise((resolve, reject) => {
      signal.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')))
    })))

    const result = request('/slow')
    await vi.advanceTimersByTimeAsync(9999)
    expect(fetch.mock.results[0].value).toBeInstanceOf(Promise)
    await vi.advanceTimersByTimeAsync(1)
    await expect(result).rejects.toBeInstanceOf(NetworkError)
    expect(clearSpy).toHaveBeenCalled()
  })

  it('uses the current token and normalizes the URL', async () => {
    let token = 'first'
    configureHttpClient({ getToken: () => token, onUnauthorized: () => {} })
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response(200, { ok: true })))

    token = 'current-token'
    await request('/players')

    expect(fetch).toHaveBeenCalledWith('http://localhost:8080/players', expect.objectContaining({
      headers: expect.objectContaining({ Authorization: 'Bearer current-token' }),
    }))
  })

  it('calls onUnauthorized once for a protected 401', async () => {
    const onUnauthorized = vi.fn()
    configureHttpClient({ getToken: () => 'token', onUnauthorized })
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response(401, { error: 'invalid_token', message: 'expired' })))

    await expect(request('/players')).rejects.toBeInstanceOf(ApiError)
    expect(onUnauthorized).toHaveBeenCalledTimes(1)
  })

  it('does not call onUnauthorized for a public auth 401', async () => {
    const onUnauthorized = vi.fn()
    configureHttpClient({ getToken: () => null, onUnauthorized })
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response(401, { error: 'invalid_credentials', message: 'bad' })))

    await expect(request('/auth/login', { publicRequest: true })).rejects.toBeInstanceOf(ApiError)
    expect(onUnauthorized).not.toHaveBeenCalled()
  })
})
