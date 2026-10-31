import { request } from './httpClient.js'

export const getCurrentAccount = ({ signal } = {}) => request('/auth/me', { signal })

export const getUserPortfolio = (userId, { signal, strategy } = {}) =>
  request(`/users/${encodeURIComponent(userId)}/portfolio${strategy && strategy !== 'MARKET_CURRENT' ? `?strategy=${encodeURIComponent(strategy)}` : ''}`, { signal })

export const getUserTransactions = (userId, { page = 0, size = 20 } = {}, { signal } = {}) => {
  const query = new URLSearchParams({ page: String(page), size: String(size) })
  return request(`/users/${encodeURIComponent(userId)}/transactions?${query}`, { signal })
}
