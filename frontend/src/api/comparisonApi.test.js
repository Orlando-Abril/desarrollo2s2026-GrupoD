import { beforeEach, describe, expect, it, vi } from 'vitest'

const request = vi.fn()
vi.mock('./httpClient.js', () => ({ request }))
const { getLatestQuotes, getOpportunities } = await import('./comparisonApi.js')

describe('comparisonApi', () => {
  beforeEach(() => { request.mockReset() })

  it('consulta sólo ids distintos y Momentum explícito', async () => {
    await getLatestQuotes([3, 1, 3], 'MOMENTUM')
    expect(request).toHaveBeenCalledWith('/quotes/latest?playerIds=3%2C1&strategy=MOMENTUM', { signal: undefined })
  })

  it('acepta hasta 100 jugadores y rechaza listas vacías o mayores', async () => {
    await getLatestQuotes(Array.from({ length: 100 }, (_, index) => index + 1), 'MOMENTUM')
    expect(() => getLatestQuotes([], 'MOMENTUM')).toThrow(TypeError)
    expect(() => getLatestQuotes(Array.from({ length: 101 }, (_, index) => index + 1), 'MOMENTUM')).toThrow(TypeError)
  })

  it('omite filtros vacíos de oportunidades', async () => {
    await getOpportunities({ league: 'ARG', position: '' })
    expect(request).toHaveBeenCalledWith('/quotes/opportunities?league=ARG', { signal: undefined })
  })
})
