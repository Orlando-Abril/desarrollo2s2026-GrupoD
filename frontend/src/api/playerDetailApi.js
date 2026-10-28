import { request } from './httpClient.js'

export function getPlayerDetail(id, { signal } = {}) {
  return request(`/players/${encodeURIComponent(id)}`, { signal })
}
