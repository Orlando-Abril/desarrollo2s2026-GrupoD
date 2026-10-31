import { beforeEach, describe, expect, it, vi } from 'vitest'

const request = vi.fn()
vi.mock('./httpClient.js', () => ({ request }))
const { getActiveStrategy, changeActiveStrategy } = await import('./strategyApi.js')

describe('strategyApi', () => {
  beforeEach(() => { request.mockReset() })

  it('obtiene la configuración activa completa', async () => {
    const config = { active: { code: 'MOMENTUM' }, alternative: { code: 'OFFICIAL' }, marketStrategy: { code: 'OFFICIAL' }, pendingRecalculation: true }
    request.mockResolvedValue(config)
    await expect(getActiveStrategy()).resolves.toBe(config)
    expect(request).toHaveBeenCalledWith('/strategies/active', { signal: undefined })
  })

  it('cambia la configuración sin asumir que el mercado ya fue recalculado', async () => {
    const config = { active: { code: 'MOMENTUM' }, marketStrategy: { code: 'OFFICIAL' }, pendingRecalculation: true }
    request.mockResolvedValue(config)
    await expect(changeActiveStrategy('MOMENTUM')).resolves.toBe(config)
    expect(request).toHaveBeenCalledWith('/strategies/active', { method: 'PUT', body: { strategy: 'MOMENTUM' }, signal: undefined })
  })

  it.each([400, 403, 'network'])('propaga error %s', async (kind) => {
    const error = Object.assign(new Error(String(kind)), { status: kind })
    request.mockRejectedValue(error)
    let received
    try { await changeActiveStrategy('OFFICIAL') } catch (caught) { received = caught }
    expect(received).toBe(error)
  })
})
