import { request } from './httpClient.js'

export function getRanking(
  { strategy = '', league = '', position = '', page, size } = {},
  { signal } = {},
) {
  const query = new URLSearchParams()
  if (strategy) query.set('strategy', strategy)
  if (league) query.set('league', league)
  if (position) query.set('position', position)
  if (page !== undefined) query.set('page', page)
  if (size !== undefined) query.set('size', size)
  const suffix = query.size ? `?${query.toString()}` : ''

  return request(`/players/ranking${suffix}`, { signal })
}

export function getPlayerQuotes(id, { strategy = '', from = '', to = '', at = '' } = {}, { signal } = {}) {
  if (at && (from || to)) throw new TypeError('at no puede combinarse con from ni to')
  const query = new URLSearchParams()
  if (strategy) query.set('strategy', strategy)
  if (from) query.set('from', from)
  if (to) query.set('to', to)
  if (at) query.set('at', at)
  const suffix = query.size ? `?${query.toString()}` : ''
  return request(`/players/${encodeURIComponent(id)}/quotes${suffix}`, { signal })
}

export function recalculateQuotes({ strategy } = {}, { signal } = {}) {
  const body = strategy === undefined || strategy === null ? {} : { body: { strategy } }
  return request('/quotes/recalculate', {
    method: 'POST',
    ...body,
    signal,
    timeoutMs: 70_000,
  })
}
