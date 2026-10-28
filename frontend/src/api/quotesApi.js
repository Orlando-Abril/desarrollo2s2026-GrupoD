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
