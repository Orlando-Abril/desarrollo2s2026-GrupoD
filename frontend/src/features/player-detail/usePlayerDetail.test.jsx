import { act, renderHook, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, NetworkError } from '../../api/httpClient.js'

const getPlayerDetail = vi.fn()
const getPlayerQuotes = vi.fn()

vi.mock('../../api/playerDetailApi.js', () => ({ getPlayerDetail }))
vi.mock('../../api/quotesApi.js', () => ({ getPlayerQuotes }))

const { default: usePlayerDetail } = await import('./usePlayerDetail.js')

const NOW = new Date('2026-10-27T03:00:00Z')

function quote(calculatedAt, value, overrides = {}) {
  return { value, score: null, status: 'CALCULATED', strategy: 'OFFICIAL', version: 1, calculatedAt, ...overrides }
}

function detail(overrides = {}) {
  return {
    player: { id: 42, fullName: 'Bukayo Saka', team: 'Arsenal FC', league: 'PREMIER_LEAGUE', positions: [], nationality: null, age: null, marketValue: 1 },
    stats: null,
    quote: quote('2026-10-20T18:00:00Z', 1.2),
    ...overrides,
  }
}

const HISTORY = [quote('2026-10-08T18:00:00Z', 1.1), quote('2026-10-20T18:00:00Z', 1.2)]

// Pedidos de historial del gráfico: los que no son la consulta completa de variación.
function chartCalls() {
  return getPlayerQuotes.mock.calls.filter(([, params]) => params.from || params.to)
}

function render(id = '42') {
  return renderHook(() => usePlayerDetail(id, { now: NOW }))
}

describe('usePlayerDetail', () => {
  beforeEach(() => {
    getPlayerDetail.mockReset().mockResolvedValue(detail())
    getPlayerQuotes.mockReset().mockResolvedValue(HISTORY)
  })

  describe('historial', () => {
    it('loads the chart history of the current quote strategy for the last month', async () => {
      getPlayerDetail.mockResolvedValue(detail({ quote: quote('2026-10-20T18:00:00Z', 1.2, { strategy: 'MOMENTUM' }) }))
      const { result } = render()
      await waitFor(() => expect(result.current.history.status).toBe('ready'))
      expect(result.current.strategy).toBe('MOMENTUM')
      expect(result.current.range).toBe('month')
      expect(chartCalls()[0].slice(0, 2)).toEqual(['42', { strategy: 'MOMENTUM', from: '2026-09-28', to: '2026-10-27' }])
      expect(result.current.history.points.map((point) => point.value)).toEqual([1.1, 1.2])
    })

    it.each([
      ['without a current quote', null],
      ['with an unknown strategy', quote('2026-10-20T18:00:00Z', 1, { strategy: 'LEGACY' })],
    ])('starts with OFFICIAL %s', async (_, currentQuote) => {
      getPlayerDetail.mockResolvedValue(detail({ quote: currentQuote }))
      const { result } = render()
      await waitFor(() => expect(result.current.history.status).toBe('ready'))
      expect(result.current.strategy).toBe('OFFICIAL')
      expect(chartCalls()[0][1]).toMatchObject({ strategy: 'OFFICIAL' })
    })

    it('keeps the range when the strategy changes and the strategy when the range changes', async () => {
      const { result } = render()
      await waitFor(() => expect(result.current.history.status).toBe('ready'))
      act(() => result.current.setStrategy('MOMENTUM'))
      await waitFor(() => expect(result.current.history.status).toBe('ready'))
      expect(getPlayerQuotes.mock.calls.at(-1)[1]).toEqual({ strategy: 'MOMENTUM', from: '2026-09-28', to: '2026-10-27' })
      act(() => result.current.setRange('season'))
      await waitFor(() => expect(result.current.history.status).toBe('ready'))
      expect(getPlayerQuotes.mock.calls.at(-1)[1]).toEqual({ strategy: 'MOMENTUM' })
      act(() => result.current.setRange('quarter'))
      await waitFor(() => expect(result.current.history.status).toBe('ready'))
      expect(getPlayerQuotes.mock.calls.at(-1)[1]).toEqual({ strategy: 'MOMENTUM', from: '2026-07-30', to: '2026-10-27' })
    })

    it('discards a stale response after a quick change', async () => {
      const { result } = render()
      await waitFor(() => expect(result.current.history.status).toBe('ready'))
      const pending = []
      getPlayerQuotes.mockImplementation((id, params, { signal }) => new Promise((resolve) => pending.push({ params, signal, resolve })))
      act(() => result.current.setStrategy('MOMENTUM'))
      act(() => result.current.setRange('season'))
      await waitFor(() => expect(pending).toHaveLength(2))
      expect(pending[0].signal.aborted).toBe(true)
      await act(async () => pending[1].resolve([quote('2026-10-21T18:00:00Z', 2, { strategy: 'MOMENTUM' })]))
      await act(async () => pending[0].resolve([quote('2026-10-21T18:00:00Z', 9, { strategy: 'MOMENTUM' })]))
      expect(result.current.history.points.map((point) => point.value)).toEqual([2])
    })

    it('reports an empty history', async () => {
      getPlayerQuotes.mockImplementation((id, params) => Promise.resolve(params.from ? [] : HISTORY))
      const { result } = render()
      await waitFor(() => expect(result.current.history.status).toBe('empty'))
      expect(result.current.history.points).toEqual([])
    })

    it('reports a history error and retries with the same selection', async () => {
      getPlayerQuotes.mockImplementation((id, params) => (params.from ? Promise.reject(new NetworkError()) : Promise.resolve(HISTORY)))
      const { result } = render()
      await waitFor(() => expect(result.current.history.status).toBe('error'))
      expect(result.current.status).toBe('ready')
      getPlayerQuotes.mockResolvedValue(HISTORY)
      act(() => result.current.retryHistory())
      await waitFor(() => expect(result.current.history.status).toBe('ready'))
      expect(getPlayerQuotes.mock.calls.at(-1)[1]).toEqual({ strategy: 'OFFICIAL', from: '2026-09-28', to: '2026-10-27' })
    })

    it('does not request the chart history before the detail is ready', async () => {
      getPlayerDetail.mockImplementation(() => new Promise(() => {}))
      const { result } = render()
      await act(async () => {})
      expect(result.current.history.status).toBe('loading')
      expect(getPlayerQuotes).not.toHaveBeenCalled()
    })
  })
  describe('at', () => {
    async function ready() {
      const view = render()
      await waitFor(() => expect(view.result.current.history.status).toBe('ready'))
      return view
    }

    it('queries the quote in force at a date with the chart strategy and no range', async () => {
      const { result } = await ready()
      const found = quote('2026-10-08T18:00:00Z', 1.1)
      getPlayerQuotes.mockResolvedValue(found)
      expect(result.current.atQuery.status).toBe('idle')
      act(() => result.current.queryAt('2026-10-10'))
      await waitFor(() => expect(result.current.atQuery.status).toBe('found'))
      expect(getPlayerQuotes.mock.calls.at(-1).slice(0, 2)).toEqual(['42', { strategy: 'OFFICIAL', at: '2026-10-10' }])
      expect(result.current.atQuery).toMatchObject({ quote: found, date: '2026-10-10' })
    })

    it('treats a 404 as an empty result', async () => {
      const { result } = await ready()
      getPlayerQuotes.mockRejectedValue(new ApiError(404, 'quote_not_found', 'No hay cotización'))
      act(() => result.current.queryAt('2026-01-01'))
      await waitFor(() => expect(result.current.atQuery.status).toBe('empty'))
    })

    it('reports other errors and retries the same date', async () => {
      const { result } = await ready()
      getPlayerQuotes.mockRejectedValue(new NetworkError())
      act(() => result.current.queryAt('2026-10-10'))
      await waitFor(() => expect(result.current.atQuery.status).toBe('error'))
      getPlayerQuotes.mockResolvedValue(quote('2026-10-08T18:00:00Z', 1.1))
      act(() => result.current.retryAt())
      await waitFor(() => expect(result.current.atQuery.status).toBe('found'))
      expect(getPlayerQuotes.mock.calls.at(-1)[1]).toEqual({ strategy: 'OFFICIAL', at: '2026-10-10' })
    })

    it.each(['2026-10-28', ''])('rejects the date %s without requesting', async (date) => {
      const { result } = await ready()
      const calls = getPlayerQuotes.mock.calls.length
      act(() => result.current.queryAt(date))
      expect(result.current.atQuery).toMatchObject({ status: 'idle', validationError: 'Elegí una fecha de hoy o anterior' })
      expect(getPlayerQuotes.mock.calls).toHaveLength(calls)
    })

    it('aborts the query and returns to idle when the strategy changes', async () => {
      const { result } = await ready()
      let signal
      getPlayerQuotes.mockImplementation((id, params, options) => {
        if (params.at) {
          signal = options.signal
          return new Promise(() => {})
        }
        return Promise.resolve(HISTORY)
      })
      act(() => result.current.queryAt('2026-10-10'))
      await waitFor(() => expect(signal).toBeDefined())
      act(() => result.current.setStrategy('MOMENTUM'))
      expect(signal.aborted).toBe(true)
      expect(result.current.atQuery.status).toBe('idle')
    })
  })
  describe('estados', () => {
    it.each(['abc', '0', '9223372036854775808'])('marks the invalid id %s as not found without requesting', async (id) => {
      const { result } = render(id)
      await act(async () => {})
      expect(result.current.status).toBe('notFound')
      expect(getPlayerDetail).not.toHaveBeenCalled()
    })

    it.each([404, 400])('marks an ApiError %s as not found', async (status) => {
      getPlayerDetail.mockRejectedValue(new ApiError(status, 'player_not_found', 'No existe'))
      const { result } = render()
      await waitFor(() => expect(result.current.status).toBe('notFound'))
    })

    it.each([
      ['a server error', new ApiError(500, 'unexpected_error', 'falló')],
      ['a network error', new NetworkError()],
    ])('reports %s and recovers with retry', async (_, error) => {
      getPlayerDetail.mockRejectedValueOnce(error)
      const { result } = render()
      await waitFor(() => expect(result.current.status).toBe('error'))
      act(() => result.current.retry())
      expect(result.current.status).toBe('loading')
      await waitFor(() => expect(result.current.status).toBe('ready'))
      expect(getPlayerDetail).toHaveBeenCalledTimes(2)
    })

    it('stays loading on a 401 until the session redirect unmounts it', async () => {
      getPlayerDetail.mockRejectedValue(new ApiError(401, 'unauthorized', 'expired'))
      const { result } = render()
      await act(async () => {})
      expect(result.current.status).toBe('loading')
    })

    it('ignores an aborted response', async () => {
      let resolveDetail
      getPlayerDetail.mockImplementation(() => new Promise((resolve) => {
        resolveDetail = resolve
      }))
      const { result, unmount } = render()
      unmount()
      await act(async () => resolveDetail(detail()))
      expect(result.current.status).toBe('loading')
    })
  })
})
