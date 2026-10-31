import { request } from './httpClient.js'

export const getActiveStrategy = ({ signal } = {}) => request('/strategies/active', { signal })

export const changeActiveStrategy = (strategy, { signal } = {}) =>
  request('/strategies/active', { method: 'PUT', body: { strategy }, signal })
