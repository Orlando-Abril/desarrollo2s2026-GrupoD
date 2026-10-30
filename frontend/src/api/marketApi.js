import { request } from './httpClient.js'

// El backend rechaza una MARKET con precio límite: la clave sólo viaja en LIMIT.
function orderBody({ playerId, quantity, type, limitPrice }) {
  const body = { playerId, quantity, type }
  if (type === 'LIMIT') body.limitPrice = limitPrice
  return body
}

export function buyOrder(order, { signal } = {}) {
  return request('/orders/buy', { method: 'POST', body: orderBody(order), signal })
}

export function sellOrder(order, { signal } = {}) {
  return request('/orders/sell', { method: 'POST', body: orderBody(order), signal })
}

export function cancelOrder(id, { signal } = {}) {
  return request(`/orders/${encodeURIComponent(id)}`, { method: 'DELETE', signal })
}

export function getOrderBook(playerId, { signal } = {}) {
  return request(`/orders/book?playerId=${encodeURIComponent(playerId)}`, { signal })
}

export function getMyOrders({ signal } = {}) {
  return request('/orders/mine', { signal })
}
