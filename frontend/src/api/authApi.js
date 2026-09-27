import { request } from './httpClient.js'

export async function register(data) {
  const result = await request('/auth/register', {
    method: 'POST',
    body: data,
    publicRequest: true,
  })
  return {
    id: result.id,
    username: result.username,
    email: result.email,
    balance: result.balance,
  }
}

export function login(data) {
  return request('/auth/login', {
    method: 'POST',
    body: data,
    publicRequest: true,
  })
}
