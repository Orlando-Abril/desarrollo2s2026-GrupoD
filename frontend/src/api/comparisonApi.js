import { request } from './httpClient.js'

export function getLatestQuotes(playerIds, strategy, { signal } = {}) {
  const ids = [...new Set(playerIds)]
  if (ids.length < 1 || ids.length > 100 || ids.some((id) => !Number.isInteger(Number(id)) || Number(id) < 1)) {
    throw new TypeError('Se requieren entre 1 y 100 IDs de jugadores válidos')
  }
  const query = new URLSearchParams({ playerIds: ids.join(','), strategy })
  return request(`/quotes/latest?${query}`, { signal })
}

export function getOpportunities({ league = '', position = '' } = {}, { signal } = {}) {
  const query = new URLSearchParams()
  if (league) query.set('league', league)
  if (position) query.set('position', position)
  const suffix = query.size ? `?${query}` : ''
  return request(`/quotes/opportunities${suffix}`, { signal })
}
