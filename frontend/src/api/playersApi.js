import { request } from './httpClient.js'

export function getPlayers({ league = '', team = '', position = '' } = {}, { signal } = {}) {
  const query = new URLSearchParams()
  if (league) query.set('league', league)
  if (team) query.set('team', team)
  if (position) query.set('position', position)
  const suffix = query.size ? `?${query.toString()}` : ''
  return request(`/players${suffix}`, { signal })
}
